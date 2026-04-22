package finalproject.messages;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;

import finalproject.Users.UserLogin;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("dmBean")
@RequestScoped
public class DirectMessageService {

    @Inject
    private UserLogin login;

    private Connection conn;
    private int senderID;
    private int receiverID;
    private String receiverUserName;
    private String messageText;
    private String message;

    @PostConstruct
    public void init() {
        try {
            Context ctx = new InitialContext();
            DataSource ds = (DataSource) ctx.lookup("java:/comp/env/jdbc/java_project");
            conn = ds.getConnection();
        } catch (Exception e) {
            message = e.getMessage();
        }
    }

    @PreDestroy
    public void close() {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException ignored) {
        }
    }

    public void sendDM() {
        if (login == null || login.getUserId() <= 0) {
            message = "Please login to send a direct message.";
            return;
        }
        senderID = login.getUserId();

        if (receiverUserName == null || receiverUserName.isBlank()) {
            message = "Enter a recipient username.";
            return;
        }
        if (messageText == null || messageText.isBlank()) {
            message = "Enter message text.";
            return;
        }

        try {
            PreparedStatement userCheck = conn.prepareStatement(
                "SELECT id FROM users WHERE username = ?"
            );
            userCheck.setString(1, receiverUserName.trim());
            try (ResultSet rs = userCheck.executeQuery()) {
                if (!rs.next()) {
                    message = "Recipient not found.";
                    return;
                }
                receiverID = rs.getInt("userID");
            }

            if (receiverID == senderID) {
                message = "You cannot send a direct message to yourself.";
                return;
            }

            PreparedStatement blockCheck = conn.prepareStatement(
                "SELECT * FROM blocks WHERE (userID = ? AND blockedID = ?) OR (userID = ? AND blockedID = ?)"
            );
            blockCheck.setInt(1, senderID);
            blockCheck.setInt(2, receiverID);
            blockCheck.setInt(3, receiverID);
            blockCheck.setInt(4, senderID);

            if (blockCheck.executeQuery().next()) {
                message = "Cannot send message because a block exists.";
                return;
            }

            DirectConversationDAO dao = new DirectConversationDAO();
            int convoID = dao.getOrCreateConversation(senderID, receiverID);

            MessageDAO messageDAO = new MessageDAO();
            boolean success = messageDAO.sendDirectMessage(convoID, senderID, messageText);
            message = success ? "DM sent!" : "Failed to send DM.";
            if (success) {
                messageText = "";
            }
        } catch (SQLException e) {
            message = e.getMessage();
        }
    }

    public String getReceiverUserName() {
        return receiverUserName;
    }

    public void setReceiverUserName(String receiverUserName) {
        this.receiverUserName = receiverUserName;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public String getMessage() {
        return message;
    }
}