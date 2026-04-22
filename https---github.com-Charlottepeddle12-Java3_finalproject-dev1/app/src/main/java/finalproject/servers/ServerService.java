package finalproject.servers;

import finalproject.Users.UserLogin;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Named("serverBean")
@SessionScoped
public class ServerService implements Serializable {

    @Inject
    private UserLogin login;

    private Connection conn;
    private String serverName;
    private boolean publicServer;
    private String message = "";
   
    private List<Server> servers = new ArrayList<>();
    private List<Server> publicServers = new ArrayList<>();
   
    private String inviteServerName;
    private String inviteTargetUserName;
    private String kickServerName;
    private String kickTargetUserName;
    private String transferServerName;
    private String transferTargetUserName;
    private String leaveServerName;

    // --- DB Lifecycle Management ---

    @PostConstruct
    public void openConnection() {
        try {
            Context ctx = new InitialContext();
            DataSource ds = (DataSource) ctx.lookup("java:/comp/env/jdbc/java_project");
            conn = ds.getConnection();
        } catch (Exception e) {
            message = "Connection Error: " + e.getMessage();
        }
    }

    @PreDestroy
    public void closeConnection() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException ignored) {
        }
    }

    // --- Helper Methods ---

    private void addMembership(int serverId, int userId) {
        String sql = "INSERT INTO server_members (id, serverID) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, serverId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            message = "Membership error: " + e.getMessage();
        }
    }

    // --- Server Actions ---

    public void createServer() {
        if (serverName == null || serverName.trim().isEmpty()) {
            message = "Server name cannot be empty.";
            return;
        }
        String sql = "INSERT INTO servers (name, ownerId, is_public) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, serverName);
            stmt.setInt(2, login.getUserId());
            stmt.setBoolean(3, publicServer);
           
            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int newServerID = generatedKeys.getInt(1);
                        addMembership(newServerID, login.getUserId());
                        message = "Server created successfully.";
                    }
                }
            } else {
                message = "Creating server failed, no rows affected.";
            }
        } catch (SQLException e) {
            message = "DB Error: " + e.getMessage();
        }
    }

    public void loadUserServers() {
        servers.clear();
        if (conn == null || login == null) return;

        String sql = "SELECT s.serverID, s.name, s.ownerID, s.is_public " +
                     "FROM servers s JOIN server_members sm ON s.serverID = sm.serverID " +
                     "WHERE sm.id = ? ORDER BY s.created_at DESC";
       
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, login.getUserId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Server s = new Server();
                    s.setServerID(rs.getInt("serverID"));
                    s.setName(rs.getString("name"));
                    s.setOwnerID(rs.getInt("ownerID"));
                    s.setPublic(rs.getBoolean("is_public"));
                    servers.add(s);
                }
            }
        } catch (SQLException e) {
            message = "Load error: " + e.getMessage();
        }
    }

    public void loadPublicServers() {
        publicServers.clear();
        if (conn == null || login == null) return;

        String sql = "SELECT s.serverID, s.name, s.ownerID, s.is_public " +
                     "FROM servers s WHERE s.is_public = TRUE " +
                     "AND s.serverID NOT IN (SELECT sm.serverID FROM server_members sm WHERE sm.id = ?) " +
                     "ORDER BY s.created_at DESC";
       
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, login.getUserId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Server s = new Server();
                    s.setServerID(rs.getInt("serverID"));
                    s.setName(rs.getString("name"));
                    s.setOwnerID(rs.getInt("ownerID"));
                    s.setPublic(rs.getBoolean("is_public"));
                    publicServers.add(s);
                }
            }
        } catch (SQLException e) {
            message = "Public load error: " + e.getMessage();
        }
    }

    public void joinPublicServer(int serverId) {
        if (conn == null || login == null) return;
        addMembership(serverId, login.getUserId());
        message = "Joined server successfully.";
    }

    public void inviteUserToServer() {
        if (conn == null || login == null) return;

        try {
            int sId = -1;
            boolean isPub = false;
           
            // 1. Get Server Info
            try (PreparedStatement ps = conn.prepareStatement("SELECT serverID, is_public FROM servers WHERE name = ?")) {
                ps.setString(1, inviteServerName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        sId = rs.getInt("serverID");
                        isPub = rs.getBoolean("is_public");
                    } else {
                        message = "Server not found.";
                        return;
                    }
                }
            }

            // 2. Check Permissions
            boolean canInvite = false;
            if (isPub) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM server_members WHERE id = ? AND serverID = ?")) {
                    ps.setInt(1, login.getUserId());
                    ps.setInt(2, sId);
                    try (ResultSet rs = ps.executeQuery()) { canInvite = rs.next(); }
                }
            } else {
                String pSql = "SELECT 1 FROM server_member_roles smr JOIN server_roles sr ON smr.roleID = sr.roleID " +
                              "WHERE smr.id = ? AND smr.serverID = ? AND sr.can_invite = TRUE";
                try (PreparedStatement ps = conn.prepareStatement(pSql)) {
                    ps.setInt(1, login.getUserId());
                    ps.setInt(2, sId);
                    try (ResultSet rs = ps.executeQuery()) { canInvite = rs.next(); }
                }
            }

            if (!canInvite) {
                message = "Permission denied.";
                return;
            }

            // 3. Send Invite
            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE username = ?")) {
                ps.setString(1, inviteTargetUserName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int targetId = rs.getInt("id");
                        try (PreparedStatement ins = conn.prepareStatement("INSERT INTO server_invites (serverID, invitedID, invited_by) VALUES (?, ?, ?)")) {
                            ins.setInt(1, sId);
                            ins.setInt(2, targetId);
                            ins.setInt(3, login.getUserId());
                            ins.executeUpdate();
                            message = "Invite sent.";
                        }
                    } else {
                        message = "User not found.";
                    }
                }
            }
        } catch (SQLException e) {
            message = "Error: " + e.getMessage();
        }
    }

    public List<Invite> loadInvites() {
        List<Invite> list = new ArrayList<>();
        if (conn == null || login == null) return list;

        String sql = "SELECT i.inviteID, s.name AS sName, u.username AS uName " +
                     "FROM server_invites i JOIN servers s ON i.serverID = s.serverID " +
                     "JOIN users u ON i.invited_by = u.id WHERE i.invitedID = ?";
       
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, login.getUserId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Invite inv = new Invite();
                    inv.setInviteID(rs.getInt("inviteID"));
                    inv.setServerName(rs.getString("sName"));
                    inv.setInviterName(rs.getString("uName"));
                    list.add(inv);
                }
            }
        } catch (SQLException e) {
            message = e.getMessage();
        }
        return list;
    }

    public void acceptInvite(int inviteId) {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT serverID FROM server_invites WHERE inviteID = ? AND invitedID = ?")) {
            stmt.setInt(1, inviteId);
            stmt.setInt(2, login.getUserId());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int sId = rs.getInt("serverID");
                    addMembership(sId, login.getUserId());
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM server_invites WHERE inviteID = ?")) {
                        del.setInt(1, inviteId);
                        del.executeUpdate();
                        message = "Invite accepted.";
                    }
                }
            }
        } catch (SQLException e) {
            message = e.getMessage();
        }
    }

    public void declineInvite(int inviteId) {
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM server_invites WHERE inviteID = ? AND invitedID = ?")) {
            stmt.setInt(1, inviteId);
            stmt.setInt(2, login.getUserId());
            if (stmt.executeUpdate() > 0) message = "Invite declined.";
        } catch (SQLException e) {
            message = e.getMessage();
        }
    }

    public void kickUserFromServer() {
        try {
            int sId = -1;
            try (PreparedStatement ps = conn.prepareStatement("SELECT serverID, is_public FROM servers WHERE name = ?")) {
                ps.setString(1, kickServerName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        sId = rs.getInt("serverID");
                        if (rs.getBoolean("is_public")) { message = "Cannot kick from public."; return; }
                    }
                }
            }
            if (sId == -1) return;

            // Permission Check
            String pSql = "SELECT 1 FROM server_member_roles smr JOIN server_roles sr ON smr.roleID = sr.roleID " +
                          "WHERE smr.id = ? AND smr.serverID = ? AND sr.can_kick = TRUE";
            try (PreparedStatement ps = conn.prepareStatement(pSql)) {
                ps.setInt(1, login.getUserId());
                ps.setInt(2, sId);
                try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) return; }
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE username = ?")) {
                ps.setString(1, kickTargetUserName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        try (PreparedStatement del = conn.prepareStatement("DELETE FROM server_members WHERE id = ? AND serverID = ?")) {
                            del.setInt(1, rs.getInt("id"));
                            del.setInt(2, sId);
                            del.executeUpdate();
                            message = "User kicked.";
                        }
                    }
                }
            }
        } catch (SQLException e) { message = e.getMessage(); }
    }

    public void transferServerOwnership() {
        try {
            int sId = -1;
            try (PreparedStatement ps = conn.prepareStatement("SELECT serverID, ownerID FROM servers WHERE name = ?")) {
                ps.setString(1, transferServerName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        sId = rs.getInt("serverID");
                        if (rs.getInt("ownerID") != login.getUserId()) { message = "Not owner."; return; }
                    }
                }
            }
            if (sId == -1) return;

            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE username = ?")) {
                ps.setString(1, transferTargetUserName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int targetId = rs.getInt("id");
                        try (PreparedStatement upd = conn.prepareStatement("UPDATE servers SET ownerID = ? WHERE serverID = ?")) {
                            upd.setInt(1, targetId);
                            upd.setInt(2, sId);
                            upd.executeUpdate();
                            message = "Ownership transferred.";
                        }
                    }
                }
            }
        } catch (SQLException e) { message = e.getMessage(); }
    }

    // --- Getters and Setters ---

    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    public boolean isPublicServer() { return publicServer; }
    public void setPublicServer(boolean publicServer) { this.publicServer = publicServer; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<Server> getServers() { return servers; }
    public List<Server> getPublicServers() { return publicServers; }
    public String getInviteServerName() { return inviteServerName; }
    public void setInviteServerName(String inviteServerName) { this.inviteServerName = inviteServerName; }
    public String getInviteTargetUserName() { return inviteTargetUserName; }
    public void setInviteTargetUserName(String inviteTargetUserName) { this.inviteTargetUserName = inviteTargetUserName; }
    public String getKickServerName() { return kickServerName; }
    public void setKickServerName(String kickServerName) { this.kickServerName = kickServerName; }
    public String getKickTargetUserName() { return kickTargetUserName; }
    public void setKickTargetUserName(String kickTargetUserName) { this.kickTargetUserName = kickTargetUserName; }
    public String getTransferServerName() { return transferServerName; }
    public void setTransferServerName(String transferServerName) { this.transferServerName = transferServerName; }
    public String getTransferTargetUserName() { return transferTargetUserName; }
    public void setTransferTargetUserName(String transferTargetUserName) { this.transferTargetUserName = transferTargetUserName; }