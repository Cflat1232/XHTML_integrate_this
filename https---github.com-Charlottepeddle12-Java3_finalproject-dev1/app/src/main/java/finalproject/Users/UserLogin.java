package finalproject.Users;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import at.favre.lib.crypto.bcrypt.BCrypt;

@Named("userLoginBean")
@SessionScoped
public class UserLogin implements Serializable {

    private String userName;
    private String userPassword;
    private String message;
    private int userId;
    private DataSource dataSource;

    public UserLogin() {
        try {
            Context ctx = new InitialContext();
            dataSource = (DataSource) ctx.lookup("java:comp/env/jdbc/java_project");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String login() {
        if (userName == null || userName.trim().isEmpty()) {
            message = "Enter username";
            return null;
        }

        if (userPassword == null || userPassword.trim().isEmpty()) {
            message = "Enter password";
            return null;
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, password FROM users WHERE username = ?")) {

            stmt.setString(1, userName);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int dbUserId = rs.getInt("id");
                String storedPassword = rs.getString("password");

                BCrypt.Result result = BCrypt.verifyer()
                        .verify(userPassword.toCharArray(), storedPassword);

                if (result.verified) {
                    this.userId = dbUserId;
                    message = null;
                    return "dashboard.xhtml?faces-redirect=true";
                } else {
                    message = "Invalid username or password";
                    return null;
                }
            } else {
                message = "Invalid username or password";
                return null;
            }

        } catch (Exception e) {
            message = "Login failed: " + e.getMessage();
            e.printStackTrace();
            return null;
        }
    }

    public String signup() {
        if (userName == null || userName.trim().isEmpty()) {
            message = "Enter username";
            return null;
        }

        if (userPassword == null || userPassword.trim().isEmpty()) {
            message = "Enter password";
            return null;
        }

        if (userPassword.length() < 6) {
            message = "Password must be at least 6 characters";
            return null;
        }

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT id FROM users WHERE username = ?")) {
                checkStmt.setString(1, userName);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    message = "Username already exists";
                    return null;
                }
            }

            String hashedPassword = BCrypt.withDefaults()
                    .hashToString(12, userPassword.toCharArray());

            try (PreparedStatement insertStmt = conn.prepareStatement(
                    "INSERT INTO users (username, password) VALUES (?, ?)")) {
                insertStmt.setString(1, userName);
                insertStmt.setString(2, hashedPassword);
                insertStmt.executeUpdate();
            }

            message = "Account created successfully. Please login.";
            return "login.xhtml?faces-redirect=true";

        } catch (Exception e) {
            message = "Registration failed: " + e.getMessage();
            e.printStackTrace();
            return null;
        }
    }

    public String logout() {
        userName = null;
        userPassword = null;
        userId = 0;
        message = null;
        return "login.xhtml?faces-redirect=true";
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserPassword() {
        return userPassword;
    }

    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }
}