
package finalproject.servers;

import finalproject.Users.UserLogin;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
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
    private int serverID;
    private String serverName;
    private boolean publicServer;
    private String message = "";
    private String targetUserName;
    private int inviteID;
    private List<Server> servers = new ArrayList<>();
    private List<Server> publicServers = new ArrayList<>();
    private String inviteServerName;
    private String inviteTargetUserName;
    private String kickServerName;
    private String kickTargetUserName;
    private String transferServerName;
    private String transferTargetUserName;
    private String leaveServerName;
    //  DB
    @PostConstruct
    public void openConnection() {
        try {
            Context ctx = new InitialContext();
            DataSource ds = (DataSource) ctx.lookup("java:/comp/env/jdbc/javaproject");
            conn = ds.getConnection();
        } catch (Exception e) {
            message = e.getMessage();
        }
    }

    @PreDestroy
    public void closeConnection() {
        try { if (conn != null) conn.close(); } catch (Exception ignored) {}
    }
    // Helper method to add a user as a member to a server
    private void addMembership(int serverId, int userId) {
        String sql = "INSERT INTO server_members (userID, serverID) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);   
            stmt.setInt(2, serverId); 
            stmt.executeUpdate();
        } catch (SQLException e) {
            message = "Membership error: " + e.getMessage();
        }
    }
    //  Server Creation
    public void createServer() {
        if (serverName == null || serverName.trim().isEmpty()) {
            message = "Server name cannot be empty.";
            return;
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO servers (name, ownerId, is_public) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, serverName);
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
                } else {
                    message = "Creating server failed, no ID obtained.";
                }
            }
        } catch (SQLException e) {
            message = e.getMessage();
        }
    }
    //  Load User Servers 
    public void loadUserServers() {
        servers.clear();
        if (conn == null || login == null) {
            message = "Not connected to database or user not logged in.";
            return;
        }
        String sql = "SELECT s.serverID, s.name, s.ownerID, s.is_public " +
            "FROM servers s " +
            "JOIN server_members sm ON s.serverID = sm.serverID " +
            "WHERE sm.userID = ? " +
            "ORDER BY s.created_at DESC";
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
    //  Load Public Servers 
    public void loadPublicServers() {
        publicServers.clear(); 
        if (conn == null || login == null) {
            message = "Not connected to database or user not logged in.";
            return;
        }
        String sql = "SELECT s.serverID, s.name, s.ownerID, s.is_public " +
                     "FROM servers s " +
                     "WHERE s.is_public = TRUE " +
                     "AND s.serverID NOT IN (SELECT sm.serverID FROM server_members sm WHERE sm.userID = ?) " +
                     "ORDER BY s.created_at DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, login.getUserId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Server server = new Server();
                    server.setServerID(rs.getInt("serverID"));
                    server.setName(rs.getString("name"));
                    server.setOwnerID(rs.getInt("ownerID"));
                    server.setPublic(rs.getBoolean("is_public"));
                    publicServers.add(server); // ← store in field
                }
            }
        } catch (SQLException e) {
            message = "Failed to load public servers: " + e.getMessage();
        }
    }
    // Join Public Server 
    public void joinPublicServer(int serverId) {
        if (conn == null || login == null) {
            message = "Not connected to database or user not logged in.";
            return;
        }
        String sql = "INSERT INTO server_members (userID, serverID) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, login.getUserId());
            stmt.setInt(2, serverId);
            stmt.executeUpdate();
            message = "Joined server successfully.";
        } catch (SQLException e) {
            message = "Failed to join server: " + e.getMessage();
        }
    }
    // Invite User to Server by server name and username
    public void inviteUserToServer() {
        String serverName = this.inviteServerName;
        String targetUserName = this.inviteTargetUserName;
        if (conn == null || login == null) {
            message = "Not connected to database or user not logged in.";
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
            String permSql = "SELECT 1 FROM server_member_roles smr " +
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
        String getUserSql = "SELECT userID FROM users WHERE username = ?";
        try (PreparedStatement userStmt = conn.prepareStatement(getUserSql)) {
            userStmt.setString(1, targetUserName);
            try (ResultSet rs = userStmt.executeQuery()) {
                if (rs.next()) {
                    targetUserId = rs.getInt("userID");
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
    //  load Invites for User
    public List<Invite> loadInvites() {
        List<Invite> invites = new ArrayList<>();
        if (conn == null) {
            message = "Not connected to database.";
            return invites;
        }
        if (login == null) {
            message = "User not logged in.";
            return invites;
        }
        int userId = login.getUserId();
        String sql = "SELECT i.inviteID, s.name AS serverName, u.username AS inviterName " +
                     "FROM server_invites i " +
                     "JOIN servers s ON i.serverID = s.serverID " +
                     "JOIN users u ON i.invited_by = u.userID " +
                     "WHERE i.invitedID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    Invite invite = new Invite();
                    invite.setInviteID(rs.getInt("inviteID"));
                    invite.setServerName(rs.getString("serverName"));
                    invite.setInviterName(rs.getString("inviterName"));
                    invites.add(invite);
                    count++;
                }
            }
        } catch (SQLException e) {
            message = "Failed to load invites: " + e.getMessage();
        }
        if (invites.isEmpty()) {
            message = "No invites found for userId: " + userId;
        }
        return invites;
    }
    //Accept Invite
    public void acceptInvite(int inviteId) {
        if (conn == null || login == null) {
            message = "Not connected to database or user not logged in.";
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
        } catch (SQLException e) {
            message = "Failed to accept invite: " + e.getMessage();
        }
    }
    // Decline Invite
    public void declineInvite(int inviteId) {   
        if (conn == null || login == null) {
            message = "Not connected to database or user not logged in.";
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
    //Kick User from Server 
    public void kickUserFromServer() {
        if (conn == null || login == null) {
            message = "Not connected.";
            return;
        }

        int serverId = -1;
        int targetUserId = -1;

        try {
            // 1. find server ID
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

            // ❌ block kicking in public server
            if (isPublic) {
                message = "Cannot kick from public server.";
                return;
            }

            // 1.5 check permission
            String permSql = "SELECT 1 FROM server_member_roles smr " +
                            "JOIN server_roles sr ON smr.roleID = sr.roleID " +
                            "WHERE smr.userID = ? AND smr.serverID = ? AND sr.can_kick = TRUE";

            PreparedStatement permStmt = conn.prepareStatement(permSql);
            permStmt.setInt(1, login.getUserId()); // current user
            permStmt.setInt(2, serverId);

            ResultSet permRs = permStmt.executeQuery();

            if (!permRs.next()) {
                message = "You do not have permission to kick users.";
                return;
            }
                        // 2. find user ID
            String userSql = "SELECT userID FROM users WHERE username = ?";
            stmt = conn.prepareStatement(userSql);
            stmt.setString(1, kickTargetUserName);
            rs = stmt.executeQuery();

            if (rs.next()) {
                targetUserId = rs.getInt("userID");
            } else {
                message = "User not found.";
                return;
            }

            // 3. delete membership
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
    //transfer ownership of server 
    public void transferServerOwnership() {
        if (conn == null || login == null) {
            message = "Not connected.";
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
            String userSql = "SELECT userID FROM users WHERE username = ?";
            stmt = conn.prepareStatement(userSql);
            stmt.setString(1, transferTargetUserName);
            rs = stmt.executeQuery();
            if (rs.next()) {
                targetUserId = rs.getInt("userID");
            } else {
                message = "User not found.";
                return;
            }
            if (targetUserId == oldOwnerId) {
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
            String giveAdmin = "INSERT INTO server_member_roles (userID, serverID, roleID) " +
                    "SELECT ?, ?, roleID FROM server_roles WHERE serverID = ? AND role_name = 'Admin'";
            stmt = conn.prepareStatement(giveAdmin);
            stmt.setInt(1, targetUserId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, serverId);
            stmt.executeUpdate();
            stmt = conn.prepareStatement(removeRoles);
            stmt.setInt(1, oldOwnerId);
            stmt.setInt(2, serverId);
            stmt.executeUpdate();
            String giveMember = "INSERT INTO server_member_roles (userID, serverID, roleID) " +
                    "SELECT ?, ?, roleID FROM server_roles WHERE serverID = ? AND is_default_role = TRUE";
            stmt = conn.prepareStatement(giveMember);
            stmt.setInt(1, oldOwnerId);
            stmt.setInt(2, serverId);
            stmt.setInt(3, serverId);
            stmt.executeUpdate();
            message = "Ownership transferred successfully.";
        } catch (SQLException e) {
            message = "Error: " + e.getMessage();
        }
    }
    //leave server
    public void leaveServer() {
        if (conn == null || login == null) {
            message = "Not connected.";
            return;
        }
        try {
            String ownerSql = "SELECT serverID, ownerID FROM servers WHERE LOWER(name) = LOWER(?)";
            PreparedStatement stmt = conn.prepareStatement(ownerSql);
            stmt.setString(1, leaveServerName.trim());
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                message = "Server not found.";
                return;
            }
            int serverId = rs.getInt("serverID");
            int ownerId = rs.getInt("ownerID");
            if (ownerId == login.getUserId()) {
                message = "You are the owner. Transfer ownership before leaving the server.";
                return;
            }
            String checkSql = "SELECT 1 FROM server_members WHERE userID = ? AND serverID = ?";
            stmt = conn.prepareStatement(checkSql);
            stmt.setInt(1, login.getUserId());
            stmt.setInt(2, serverId);
            rs = stmt.executeQuery();

            if (!rs.next()) {
                message = "You are not a member of this server.";
                return;
            }
            String deleteSql = "DELETE FROM server_members WHERE userID = ? AND serverID = ?";
            stmt = conn.prepareStatement(deleteSql);
            stmt.setInt(1, login.getUserId());
            stmt.setInt(2, serverId);
            stmt.executeUpdate();
            message = "Left server successfully.";
        } catch (SQLException e) {
            message = "Error: " + e.getMessage();
        }
    }
    //has permission
    public boolean hasPermission(int serverId, String permissionColumn) {
        if (conn == null || login == null) {
            return false;
        }
        String sql = "SELECT 1 FROM server_member_roles smr " +
                    "JOIN server_roles sr ON smr.roleID = sr.roleID " +
                    "WHERE smr.userID = ? AND smr.serverID = ? AND sr." + permissionColumn + " = TRUE";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, login.getUserId());
            stmt.setInt(2, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }
    //load server members 
    public List<String> loadServerMembers(int serverId) {
        List<String> members = new ArrayList<>();

        if (conn == null || login == null) {
            return members;
        }

        try {
            String checkMemberSql = "SELECT 1 FROM server_members WHERE userID = ? AND serverID = ?";
            PreparedStatement stmt = conn.prepareStatement(checkMemberSql);
            stmt.setInt(1, login.getUserId());
            stmt.setInt(2, serverId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                message = "You are not a member of this server.";
                return members;
            }
            String ownerSql = "SELECT ownerID FROM servers WHERE serverID = ?";
            stmt = conn.prepareStatement(ownerSql);
            stmt.setInt(1, serverId);
             rs = stmt.executeQuery();
            boolean isAdmin = false;
            if (rs.next()) {
                isAdmin = (rs.getInt("ownerID") == login.getUserId());
            }
            if (isAdmin) {
                String sql = "SELECT u.username FROM users u " +
                            "JOIN server_members sm ON u.userID = sm.userID " +
                            "WHERE sm.serverID = ?";

                stmt = conn.prepareStatement(sql);
                stmt.setInt(1, serverId);
                rs = stmt.executeQuery();
                while (rs.next()) {
                    members.add(rs.getString("username"));
                }
                return members;
            }
            String sql =
                "SELECT u.username FROM users u " +
                "JOIN server_members sm ON u.userID = sm.userID " +
                "WHERE sm.serverID = ? " +
                "AND u.userID NOT IN ( " +
                "   SELECT blockedID FROM blocks WHERE userID = ? " +
                ") " +
                "AND u.userID NOT IN ( " +
                "   SELECT userID FROM blocks WHERE blockedID = ? " +
                ")";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, serverId);
            stmt.setInt(2, login.getUserId());
            stmt.setInt(3, login.getUserId());
            rs = stmt.executeQuery();
            while (rs.next()) {
                members.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            message = "Error loading members: " + e.getMessage();
        }
        return members;
    }
    // delete server by name (ONLY OWNER)
    public void deleteServer() {
        if (conn == null || login == null) {
            message = "Not connected.";
            return;
        }
        if (serverName == null || serverName.trim().isEmpty()) {
            message = "Server name cannot be empty.";
            return;
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT serverID, ownerID FROM servers WHERE LOWER(name) = LOWER(?)")) {
            stmt.setString(1, serverName.trim());
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                message = "Server not found.";
                return;
            }
            int serverId = rs.getInt("serverID");
            int ownerId = rs.getInt("ownerID");
            if (ownerId != login.getUserId()) {
                message = "Only the owner can delete this server.";
                return;
            }
            try (PreparedStatement deleteStmt = conn.prepareStatement(
                    "DELETE FROM servers WHERE serverID = ?")) {
                deleteStmt.setInt(1, serverId);
                int rows = deleteStmt.executeUpdate();
                if (rows > 0) {
                    message = "Server deleted successfully.";
                } else {
                    message = "Delete failed.";
                }
            }
        } catch (SQLException e) {
            message = "Error: " + e.getMessage();
        }
    }
    //  Getters and Setters 
    public String getServerName() {
        return serverName;
    }
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }
    public List<Server> getServers() {
        return servers;
    }
    public boolean isPublicServer() {
        return publicServer;
    }
    public void setPublicServer(boolean publicServer) {
        this.publicServer = publicServer;
    }
    public List<Server> getPublicServers() {
        return publicServers;
    }
    public int getUserId() {
        return login != null ? login.getUserId() : -1;
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
    public String getMessage() {
        return message;
    }
    public void loadMyInvites() {
        myInvites = loadInvites();
    }
    public List<Invite> getMyInvites() {
        return myInvites;
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