package finalproject.messages;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;

import finalproject.Users.UserLogin;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("messageBean")
@RequestScoped
public class MessageService {
    @Inject
    private UserLogin login;
    private Connection conn;
    private int channelID = 1;
    private int senderID;
    private int messageID;
    private String messageText;
    private String message = "";
    private List<Message> messages = new ArrayList<>();

    public String getMessage() {
        return message;
    }

    public String getMessageText() {
        return messageText;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setChannelID(int channelID) {
        this.channelID = channelID;
    }

    public void setSenderID(int senderID) {
        this.senderID = senderID;
    }

    public void setMessageID(int messageID) {
        this.messageID = messageID;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    @PostConstruct
    public void openConnection() {
        try {
            Context ctx = new InitialContext();
            DataSource ds = (DataSource) ctx.lookup("java:/comp/env/jdbc/java_project");
            conn = ds.getConnection();
            loadMessages();
        } catch (Exception e) {
            message = e.getMessage();
        }
    }

    @PreDestroy
    public void closeConnection() {
        try {
            if (conn != null) conn.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void loadMessages() {
        if (login == null || login.getUserId() <= 0) {
            message = "Login to load channel messages.";
            return;
        }
        if (channelID <= 0) {
            channelID = 1;
        }
        MessageDAO dao = new MessageDAO();
        messages = dao.getMessagesByChannel(channelID);
        if (messages.isEmpty()) {
            message = "No messages found for channel " + channelID + ".";
        }
    }

    // SEND MESSAGE
    public void sendMessage() {
        if (login == null || login.getUserId() <= 0) {
            message = "Please login to send messages.";
            return;
        }
        senderID = login.getUserId();
        if (channelID <= 0) {
            channelID = 1;
        }
        if (messageText == null || messageText.isBlank()) {
            message = "Enter a message before sending.";
            return;
        }

        try {
            PreparedStatement check = conn.prepareStatement(
                "SELECT * FROM server_members sm " +
                "JOIN channels c ON sm.serverID = c.serverID " +
                "WHERE sm.userID = ? AND c.channelID = ?"
            );
            check.setInt(1, senderID);
            check.setInt(2, channelID);

            ResultSet rs = check.executeQuery();
            if (!rs.next()) {
                message = "You are not allowed to send messages in this channel.";
                return;
            }

            MessageDAO dao = new MessageDAO();
            boolean success = dao.sendMessage(channelID, senderID, messageText);
            if (success) {
                message = "Message sent!";
                messageText = "";
            } else {
                message = "Failed to send message.";
            }
            loadMessages();
        } catch (SQLException e) {
            message = e.getMessage();
        }
    }

    // DELETE MESSAGE
    public void deleteMessage() {
        MessageDAO dao = new MessageDAO();
        boolean success = dao.deleteMessage(messageID, senderID);

        if (success) {
            message = "Message deleted.";
        } else {
            message = "Failed to delete message.";
        }
    }
}