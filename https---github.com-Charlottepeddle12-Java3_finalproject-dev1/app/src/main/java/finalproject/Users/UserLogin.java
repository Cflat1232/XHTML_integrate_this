package finalproject.Users;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;
import java.io.Serializable;

@Named("userLoginBean")
@SessionScoped
public class UserLogin implements Serializable {

    private String userName;
    private String userPassword;
    private String message;

    // 🔥 FIXED: must be int (NOT String)
    private int userId;

    // ======================
    // NAVIGATION METHODS
    // ======================

    public String login() {
        // You can later add DB validation here
        if (userName == null || userName.isBlank()) {
            message = "Enter username";
            return null;
        }

        if (userPassword == null || userPassword.isBlank()) {
            message = "Enter password";
            return null;
        }

        // TEMP: simulate login success
        userId = 1; // ⚠️ replace with DB lookup later
        message = null;

        return "dashboard.xhtml?faces-redirect=true";
    }

    public String signup() {
        // You can add DB insert here later
        message = "Account created. Please login.";
        return "login.xhtml?faces-redirect=true";
    }

    public String logout() {
        // 🔥 clear session data
        userName = null;
        userPassword = null;
        userId = 0;
        message = null;

        return "login.xhtml?faces-redirect=true";
    }

    // ======================
    // GETTERS & SETTERS
    // ======================

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