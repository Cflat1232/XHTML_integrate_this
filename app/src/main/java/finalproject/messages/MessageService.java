package finalproject.messages;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import finalproject.Users.UserLogin;
import finalproject.util.DatabaseUtil;
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

    public int getChannelID() {
        return channelID;
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
            conn = DatabaseUtil.getConnection();
            loadMessages(); // Load real data if DB works
        } catch (Exception e) {
            message = "Database not available: " + e.getMessage() + ". Using demo data.";
            loadDemoData(); // Load mock data if DB fails
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

    // Load demo data when database is not available
    private void loadDemoData() {
        messages.clear();

        // Add some demo messages
        Message demoMessage1 = new Message();
        demoMessage1.setMessageID(1);
        demoMessage1.setChannelID(channelID);
        demoMessage1.setSenderID(1);
        demoMessage1.setUsername("DemoUser");
        demoMessage1.setMessageText("Welcome to the chat! This is demo data since the database is not available.");
        demoMessage1.setSentOn(new java.sql.Timestamp(System.currentTimeMillis()));
        messages.add(demoMessage1);

        Message demoMessage2 = new Message();
        demoMessage2.setMessageID(2);
        demoMessage2.setChannelID(channelID);
        demoMessage2.setSenderID(2);
        demoMessage2.setUsername("System");
        demoMessage2.setMessageText("Database connection failed. Using demo mode.");
        demoMessage2.setSentOn(new java.sql.Timestamp(System.currentTimeMillis() - 60000));
        messages.add(demoMessage2);
    }
}