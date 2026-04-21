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
    private List<Invite> myInvites = new ArrayList<>();
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

    @PostConstruct
    public void openConnection() {
        try {
            if (conn != null && !conn.isClosed()) {
                return;
            }
            Context ctx = new InitialContext();
            DataSource ds = (DataSource) ctx.lookup("java:comp/env/jdbc/java_project");
            conn = ds.getConnection();
            loadServers();
        } catch (Exception e) {
            conn = null;
            e.printStackTrace();
            message = "Database not available: " + e.getMessage() + ". Using demo data.";
            loadDemoData();
        }
    }

    private boolean ensureConnection() {
        try {
            if (conn == null || conn.isClosed()) {
                openConnection();
            }
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            message = "Database connection error: " + e.getMessage();
            return false;
        }
    }

    @PreDestroy
    public void closeConnection() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (Exception ignored) {
        }
    }

    private void addMembership(int serverId, int userId) {
        if (!ensureConnection()) {
            return;
        }

        String sql = "INSERT INTO server_members (userID, serverID) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, serverId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            message = "Membership error: " + e.getMessage();
        }
    }

    public void createServer() {
        if (serverName == null || serverName.trim().isEmpty()) {
            message = "Server name cannot be empty.";
            return;
        }

        if (login == null || login.getUserId() <= 0) {
            message = "You must be logged in.";
            return;
        }

        if (!ensureConnection()) {
            return;
        }

        String sql = "INSERT INTO servers (name, ownerID, is_public) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, serverName.trim());
            stmt.setInt(2, login.getUserId());
            stmt.setBoolean(3, publicServer);

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                message = "Creating server failed, no rows affected.";
                return;
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int newServerID = generatedKeys.getInt(1);
                    addMembership(newServerID, login.getUserId());
                    message = "Server created successfully.";
                    loadUserServers();
                    loadPublicServers();
                } else {
                    message = "Creating server failed, no ID obtained.";
                }
            }
        } catch (SQLException e) {
            message = "Create server failed: " + e.getMessage();
        }
    }

    public void loadServers() {
        loadUserServers();
        loadPublicServers();
        myInvites = loadInvites();
    }

    public void loadUserServers() {
        servers.clear();

        if (login == null || login.getUserId() <= 0) {
            message = "User not logged in.";
            return;
        }

        if (!ensureConnection()) {
            return;
        }

        String sql =
                "SELECT s.serverID, s.name, s.ownerID, s.is_public " +
                "FROM servers s " +
                "JOIN server_members sm ON s.serverID = sm.serverID " +
                "WHERE sm.userID = ? " +
                "ORDER BY s.serverID DESC";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, login.getUserId());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Server server = new Server();
                    server.setServerID(rs.getInt("serverID"));
                    server.setName(rs.getString("name"));
                    server.setOwnerID(rs.getInt("ownerID"));
                    server.setPublic(rs.getBoolean("is_public"));
                    servers.add(server);
                }
            }
        } catch (SQLException e) {
            message = "Failed to load user servers: " + e.getMessage();
        }
    }

    public void loadPublicServers() {
        publicServers.clear();

        if (login == null || login.getUserId() <= 0) {
            message = "User not logged in.";
            return;
        }

        if (!ensureConnection()) {
            return;
        }

        String sql =
                "SELECT s.serverID, s.name, s.ownerID, s.is_public " +
                "FROM servers s " +
                "WHERE s.is_public = TRUE " +
                "AND s.serverID NOT IN (" +
                "    SELECT sm.serverID FROM server_members sm WHERE sm.userID = ?" +
                ") " +
                "ORDER BY s.serverID DESC";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, login.getUserId());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Server server = new Server();
                    server.setServerID(rs.getInt("serverID"));
                    server.setName(rs.getString("name"));
                    server.setOwnerID(rs.getInt("ownerID"));
                    server.setPublic(rs.getBoolean("is_public"));
                    publicServers.add(server);
                }
            }
        } catch (SQLException e) {
            message = "Failed to load public servers: " + e.getMessage();
        }
    }

    public void joinPublicServer(int serverId) {
        if (login == null || login.getUserId() <= 0) {
            message = "User not logged in.";
            return;
        }

        if (!ensureConnection()) {
            return;
        }

        String sql = "INSERT INTO server_members (userID, serverID) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, login.getUserId());
            stmt.setInt(2, serverId);
            stmt.executeUpdate();
            message = "Joined server successfully.";
            loadUserServers();
            loadPublicServers();
        } catch (SQLException e) {
            message = "Failed to join server: " + e.getMessage();
        }
    }

    public void inviteUserToServer() {
        String serverName = this.inviteServerName;
        String targetUserName = this.inviteTargetUserName;

        if (login == null || login.getUserId() <= 0) {
            message = "User not logged in.";
            return;
        }

        if (!ensureConnection()) {
            return;
        }

        int serverId = -1;
        boolean isPublic = false;

        String getServerSql = "SELECT serverID, is_public FROM servers WHERE name = ?";
        try (PreparedStatement serverStmt = conn.prepareStatement(getServerSql)) {
            serverStmt.setString(1, serverName);

            try (ResultSet rs = serverStmt.executeQuery()) {
                if (rs.next()) {
                    serverId = rs.getInt("serverID");
                    isPublic = rs.getBoolean("is_public");
                } else {
                    message = "Server not found.";
                    return;
                }
            }
        } catch (SQLException e) {
            message = "Error finding server: " + e.getMessage();
            return;
        }

        boolean canInvite = false;

        if (isPublic) {
            String memberSql = "SELECT 1 FROM server_members WHERE userID = ? AND serverID = ?";
            try (PreparedStatement memberStmt = conn.prepareStatement(memberSql)) {
                memberStmt.setInt(1, login.getUserId());
                memberStmt.setInt(2, serverId);

                try (ResultSet rs = memberStmt.executeQuery()) {
                    canInvite = rs.next();
                }
            } catch (SQLException e) {
                message = "Error checking membership: " + e.getMessage();
                return;
            }
        } else {
            String permSql =
                    "SELECT 1 FROM server_member_roles smr " +
                    "JOIN server_roles sr ON smr.roleID = sr.roleID " +
                    "WHERE smr.userID = ? AND smr.serverID = ? AND sr.can_invite = TRUE";

            try (PreparedStatement permStmt = conn.prepareStatement(permSql)) {
                permStmt.setInt(1, login.getUserId());
                permStmt.setInt(2, serverId);

                try (ResultSet rs = permStmt.executeQuery()) {
                    canInvite = rs.next();
                }
            } catch (SQLException e) {
                message = "Error checking permissions: " + e.getMessage();
                return;
            }
        }

        if (!canInvite) {
            message = "You do not have permission to invite users to this server.";
            return;
        }

        int targetUserId = -1;
        String getUserSql = "SELECT id FROM users WHERE username = ?";
        try (PreparedStatement userStmt = conn.prepareStatement(getUserSql)) {
            userStmt.setString(1, targetUserName);

            try (ResultSet rs = userStmt.executeQuery()) {
                if (rs.next()) {
                    targetUserId = rs.getInt("id");
                } else {
                    message = "User not found.";
                    return;
                }
            }
        } catch (SQLException e) {
            message = "Error finding user: " + e.getMessage();
            return;
        }

        String sql = "INSERT INTO server_invites (serverID, invitedID, invited_by) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, serverId);
            stmt.setInt(2, targetUserId);
            stmt.setInt(3, login.getUserId());
            stmt.executeUpdate();
            message = "Invite sent successfully.";
        } catch (SQLException e) {
            message = "Failed to send invite: " + e.getMessage();
        }
    }

    public List<Invite> loadInvites() {
        List<Invite> invites = new ArrayList<>();

        if (login == null || login.getUserId() <= 0) {
            message = "User not logged in.";
            return invites;
        }

        if (!ensureConnection()) {
            return invites;
        }

        int userId = login.getUserId();

        String sql =
                "SELECT i.inviteID, s.name AS serverName, u.username AS inviterName " +
                "FROM server_invites i " +
                "JOIN servers s ON i.serverID = s.serverID " +
                "JOIN users u ON i.invited_by = u.id " +
                "WHERE i.invitedID = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Invite invite = new Invite();
                    invite.setInviteID(rs.getInt("inviteID"));
                    invite.setServerName(rs.getString("serverName"));
                    invite.setInviterName(rs.getString("inviterName"));
                    invites.add(invite);
                }
            }
        } catch (SQLException e) {
            message = "Failed to load invites: " + e.getMessage();
        }

        return invites;
    }

    public void acceptInvite(int inviteId) {
        if (login == null || login.getUserId() <= 0) {
            message = "User not logged in.";
            return;
        }

        if (!ensureConnection()) {
            return;
        }

        int serverId = -1;
        String getInviteSql = "SELECT serverID FROM server_invites WHERE inviteID = ? AND invitedID = ?";

        try (PreparedStatement stmt = conn.prepareStatement(getInviteSql)) {
            stmt.setInt(1, inviteId);
            stmt.setInt(2, login.getUserId());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    serverId = rs.getInt("serverID");
                } else {
                    message = "Invite not found.";
                    return;
                }
            }
        } catch (SQLException e) {
            message = "Error finding invite: " + e.getMessage();
            return;
        }

        addMembership(serverId, login.getUserId());

        String deleteSql = "DELETE FROM server_invites WHERE inviteID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setInt(1, inviteId);
            stmt.executeUpdate();
            message = "Invite accepted and joined server successfully.";
            loadUserServers();
            loadPublicServers();
        } catch (SQLException e) {
            message = "Failed to accept invite: " + e.getMessage();
        }
    }

    public void declineInvite(int inviteId) {
        if (login == null || login.getUserId() <= 0) {
            message = "User not logged in.";
            return;
        }

        if (!ensureConnection()) {
            return;
        }

        String deleteSql = "DELETE FROM server_invites WHERE inviteID = ? AND invitedID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setInt(1, inviteId);
            stmt.setInt(2, login.getUserId());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                message = "Invite declined successfully.";
            } else {
                message = "Invite not found or you do not have permission to decline this invite.";
            }
        } catch (SQLException e) {
            message = "Failed to decline invite: " + e.getMessage();
        }
    }

    public void kickUserFromServer() {
        if (login == null || login.getUserId() <= 0) {
            message = "User not logged in.";
            return;
        }

        if (!ensureConnection()) {
            return;
        }

        int serverId = -1;
        int targetUserId = -1;

        try {
            String serverSql = "SELECT serverID, is_public FROM servers WHERE name = ?";
            PreparedStatement stmt = conn.prepareStatement(serverSql);
            stmt.setString(1, kickServerName);
            ResultSet rs = stmt.executeQuery();

            boolean isPublic = false;

            if (rs.next()) {
                serverId = rs.getInt("serverID");
                isPublic = rs.getBoolean("is_public");
            } else {
                message = "Server not found.";
                return;
            }

            if (isPublic) {
                message = "Cannot kick from public server.";
                return;
            }

            String permSql =
                    "SELECT 1 FROM server_member_roles smr " +
                    "JOIN server_roles sr ON smr.roleID = sr.roleID " +
                    "WHERE smr.userID = ? AND smr.serverID = ? AND sr.can_kick = TRUE";

            PreparedStatement permStmt = conn.prepareStatement(permSql);
            permStmt.setInt(1, login.getUserId());
            permStmt.setInt(2, serverId);

            ResultSet permRs = permStmt.executeQuery();
            if (!permRs.next()) {
                message = "You do not have permission to kick users.";
                return;
            }

            String userSql = "SELECT id FROM users WHERE username = ?";
            stmt = conn.prepareStatement(userSql);
            stmt.setString(1, kickTargetUserName);
            rs = stmt.executeQuery();

            if (rs.next()) {
                targetUserId = rs.getInt("id");
            } else {
                message = "User not found.";
                return;
            }

            String deleteSql = "DELETE FROM server_members WHERE userID = ? AND serverID = ?";
            stmt = conn.prepareStatement(deleteSql);
            stmt.setInt(1, targetUserId);
            stmt.setInt(2, serverId);

            int rows = stmt.executeUpdate();
            if (rows > 0) {
                message = "User kicked successfully.";
            } else {
                message = "User not in server.";
            }

        } catch (SQLException e) {
            message = "Error: " + e.getMessage();
        }
    }

    public void transferServerOwnership() {
        if (login == null || login.getUserId() <= 0) {
            message = "User not logged in.";
            return;
        }

        if (!ensureConnection()) {
            return;
        }

        int serverId = -1;
        int targetUserId = -1;

        try {
            String serverSql = "SELECT serverID, ownerID FROM servers WHERE LOWER(name) = LOWER(?)";
            PreparedStatement stmt = conn.prepareStatement(serverSql);
            stmt.setString(1, transferServerName.trim());
            ResultSet rs = stmt.executeQuery();

            int oldOwnerId;
            if (rs.next()) {
                serverId = rs.getInt("serverID");
                oldOwnerId = rs.getInt("ownerID");
                if (oldOwnerId != login.getUserId()) {
                    message = "You are not the owner.";
                    return;
                }
            } else {
                message = "Server not found.";
                return;
            }

            String userSql = "SELECT id FROM users WHERE username = ?";
            stmt = conn.prepareStatement(userSql);
            stmt.setString(1, transferTargetUserName);
            rs = stmt.executeQuery();

            if (rs.next()) {
                targetUserId = rs.getInt("id");
            } else {
                message = "User not found.";
                return;
            }

            if (targetUserId == login.getUserId()) {
                message = "You are already the owner.";
                return;
            }

            String memberSql = "SELECT 1 FROM server_members WHERE userID = ? AND serverID = ?";
            stmt = conn.prepareStatement(memberSql);
            stmt.setInt(1, targetUserId);
            stmt.setInt(2, serverId);
            rs = stmt.executeQuery();

            if (!rs.next()) {
                message = "New owner must be a member.";
                return;
            }

            String ensureMember = "INSERT IGNORE INTO server_members (userID, serverID) VALUES (?, ?)";
            stmt = conn.prepareStatement(ensureMember);
            stmt.setInt(1, targetUserId);
            stmt.setInt(2, serverId);
            stmt.executeUpdate();

            String updateSql = "UPDATE servers SET ownerID = ? WHERE serverID = ?";
            stmt = conn.prepareStatement(updateSql);
            stmt.setInt(1, targetUserId);
            stmt.setInt(2, serverId);
            stmt.executeUpdate();

            String removeRoles = "DELETE FROM server_member_roles WHERE userID = ? AND serverID = ?";
            stmt = conn.prepareStatement(removeRoles);
            stmt.setInt(1, targetUserId);
            stmt.setInt(2, serverId);
            stmt.executeUpdate();

            String giveAdmin =
                    "INSERT INTO server_member_roles (userID, serverID, roleID) " +
                    "SELECT ?, ?, roleID FROM server_roles WHERE serverID = ? AND role_name = 'Admin'";
            stmt = conn.prepareStatement(giveAdmin);
            stmt.setInt(1, targetUserId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, serverId);
            stmt.executeUpdate();

            stmt = conn.prepareStatement(removeRoles);
            stmt.setInt(1, login.getUserId());
            stmt.setInt(2, serverId);
            stmt.executeUpdate();

            String giveMember =
                    "INSERT INTO server_member_roles (userID, serverID, roleID) " +
                    "SELECT ?, ?, roleID FROM server_roles WHERE serverID = ? AND is_default_role = TRUE";
            stmt = conn.prepareStatement(giveMember);
            stmt.setInt(1, login.getUserId());
            stmt.setInt(2, serverId);
            stmt.setInt(3, serverId);
            stmt.executeUpdate();

            message = "Ownership transferred successfully.";
        } catch (SQLException e) {
            message = "Error: " + e.getMessage();
        }
    }

    private void loadDemoData() {
        if (servers.isEmpty()) {
            Server demo = new Server();
            demo.setServerID(1);
            demo.setName("Demo Server");
            demo.setOwnerID(1);
            demo.setPublic(true);
            servers.add(demo);
        }
    }

    public List<Invite> getMyInvites() {
        myInvites = loadInvites();
        return myInvites;
    }

    public void setMyInvites(List<Invite> myInvites) {
        this.myInvites = myInvites;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public boolean isPublicServer() {
        return publicServer;
    }

    public void setPublicServer(boolean publicServer) {
        this.publicServer = publicServer;
    }

    public String getMessage() {
        return message;
    }

    public List<Server> getServers() {
        return servers;
    }

    public List<Server> getPublicServers() {
        return publicServers;
    }

    public String getInviteServerName() {
        return inviteServerName;
    }

    public void setInviteServerName(String inviteServerName) {
        this.inviteServerName = inviteServerName;
    }

    public String getInviteTargetUserName() {
        return inviteTargetUserName;
    }

    public void setInviteTargetUserName(String inviteTargetUserName) {
        this.inviteTargetUserName = inviteTargetUserName;
    }

    public String getKickServerName() {
        return kickServerName;
    }

    public void setKickServerName(String kickServerName) {
        this.kickServerName = kickServerName;
    }

    public String getKickTargetUserName() {
        return kickTargetUserName;
    }

    public void setKickTargetUserName(String kickTargetUserName) {
        this.kickTargetUserName = kickTargetUserName;
    }

    public String getTransferServerName() {
        return transferServerName;
    }

    public void setTransferServerName(String transferServerName) {
        this.transferServerName = transferServerName;
    }

    public String getTransferTargetUserName() {
        return transferTargetUserName;
    }

    public void setTransferTargetUserName(String transferTargetUserName) {
        this.transferTargetUserName = transferTargetUserName;
    }

    public String getLeaveServerName() {
        return leaveServerName;
    }

    public void setLeaveServerName(String leaveServerName) {
        this.leaveServerName = leaveServerName;
    }
}