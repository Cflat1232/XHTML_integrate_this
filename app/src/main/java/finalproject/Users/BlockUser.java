package finalproject.Users;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import finalproject.util.DatabaseUtil;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("blockUserBean")
@SessionScoped
public class BlockUser implements Serializable {
    private Connection conn;
    private String blockedUserName;
    private String message;
    private final List<String> blockedUserNames = new ArrayList<>();

    @Inject
    private UserLogin login;

    @PostConstruct
    public void openConnection() {
        try {
            conn = DatabaseUtil.getConnection();
        } catch (SQLException e) {
            message = e.getMessage();
        }
    }

    @PreDestroy
    public void closeConnection() {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                message = e.getMessage();
            }
        }
    }

    public void blockUser() {
        if (conn == null) {
            message = "Database connection is not available.";
            return;
        }

        if (login == null || login.getUserId() <= 0) {
            message = "You must be logged in to block a user.";
            return;
        }

        if (blockedUserName == null || blockedUserName.isBlank()) {
            message = "Enter a username.";
            return;
        }

        try (PreparedStatement findUser = conn.prepareStatement("SELECT userID FROM users WHERE username = ?")) {
            findUser.setString(1, blockedUserName);
            try (ResultSet userResult = findUser.executeQuery()) {
                if (!userResult.next()) {
                    message = "Enter correct username.";
                    return;
                }
                int blockedId = userResult.getInt("userID");
                if (blockedId == login.getUserId()) {
                    message = "You cannot block yourself.";
                    return;
                }
                try (PreparedStatement checkExisting = conn.prepareStatement(
                        "SELECT 1 FROM blocks WHERE userID = ? AND blockedID = ?")) {
                    checkExisting.setInt(1, login.getUserId());
                    checkExisting.setInt(2, blockedId);
                    try (ResultSet existing = checkExisting.executeQuery()) {
                        if (existing.next()) {
                            message = "User is already blocked.";
                            return;
                        }
                    }
                }
                try (PreparedStatement checkReverseBlock = conn.prepareStatement(
                        "SELECT 1 FROM blocks WHERE userID = ? AND blockedID = ?")) {
                    checkReverseBlock.setInt(1, blockedId);
                    checkReverseBlock.setInt(2, login.getUserId());
                    try (ResultSet reverse = checkReverseBlock.executeQuery()) {
                        if (reverse.next()) {
                            message = "Invalid username.";
                            return;
                        }
                    }
                }
                try (PreparedStatement insertBlock = conn.prepareStatement(
                        "INSERT INTO blocks (userID, blockedID) VALUES (?, ?)")) {
                    insertBlock.setInt(1, login.getUserId());
                    insertBlock.setInt(2, blockedId);
                    int rowsAffected = insertBlock.executeUpdate();
                    if (rowsAffected == 1) {
                        message = "Blocked user " + blockedUserName + ".";
                        loadBlockedUsers();
                    } else {
                        message = "Block request failed.";
                    }
                }
            }
        } catch (SQLException e) {
            message = e.getMessage();
        }
    }

    public void unblockUser() {
        if (conn == null) {
            message = "Database connection is not available.";
            return;
        }

        if (login == null || login.getUserId() <= 0) {
            message = "You must be logged in to unblock a user.";
            return;
        }

        if (blockedUserName == null || blockedUserName.isBlank()) {
            message = "Enter a username.";
            return;
        }

        try (PreparedStatement findUser = conn.prepareStatement("SELECT userID FROM users WHERE username = ?")) {
            findUser.setString(1, blockedUserName);
            try (ResultSet userResult = findUser.executeQuery()) {
                if (!userResult.next()) {
                    message = "Enter correct username.";
                    return;
                }

                int blockedId = userResult.getInt("userID");
                if (blockedId == login.getUserId()) {
                    message = "You cannot unblock yourself.";
                    return;
                }

                try (PreparedStatement removeBlock = conn
                        .prepareStatement("DELETE FROM blocks WHERE userID = ? AND blockedID = ?")) {
                    removeBlock.setInt(1, login.getUserId());
                    removeBlock.setInt(2, blockedId);
                    int rowsAffected = removeBlock.executeUpdate();
                    if (rowsAffected == 1) {
                        message = "Unblocked user " + blockedUserName + ".";
                        loadBlockedUsers();
                    } else {
                        message = "User is not currently blocked.";
                    }
                }
            }
        } catch (SQLException e) {
            message = e.getMessage();
        }
    }

    public void loadBlockedUsers() {
        blockedUserNames.clear();

        if (conn == null || login == null || login.getUserId() <= 0) {
            return;
        }

        try (PreparedStatement findBlockedUsers = conn.prepareStatement(
                "SELECT u.username FROM blocks b JOIN users u ON u.userID = b.blockedID "
                        + "WHERE b.userID = ? ORDER BY u.username")) {
            findBlockedUsers.setInt(1, login.getUserId());
            try (ResultSet result = findBlockedUsers.executeQuery()) {
                while (result.next()) {
                    blockedUserNames.add(result.getString("username"));
                }
            }
        } catch (SQLException e) {
            message = e.getMessage();
        }
    }

    public String getBlockedUserName() {
        return blockedUserName;
    }

    public void setBlockedUserName(String blockedUserName) {
        this.blockedUserName = blockedUserName;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getBlockedUserNames() {
        return blockedUserNames;
    }
}
