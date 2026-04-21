package finalproject.messages;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import finalproject.util.DatabaseUtil;

public class MessageDAO {

    // SEND MESSAGE
    public boolean sendMessage(int channelID, int senderID, String messageText) {
        String sql = "INSERT INTO messages (channelID, senderID, message_text) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, channelID);
            stmt.setInt(2, senderID);
            stmt.setString(3, messageText);

            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }

    // GET MESSAGES BY CHANNEL
    public List<Message> getMessagesByChannel(int channelID) {
        List<Message> messages = new ArrayList<>();

        String sql = """
            SELECT m.messageID, m.channelID, m.senderID, m.message_text, m.sentOn,
                   u.username
            FROM messages m
            LEFT JOIN users u ON m.senderID = u.userID
            WHERE m.channelID = ?
            ORDER BY m.sentOn ASC
        """;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, channelID);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Message msg = new Message();
                    msg.setMessageID(rs.getInt("messageID"));
                    msg.setChannelID(rs.getInt("channelID"));
                    msg.setSenderID(rs.getInt("senderID"));
                    msg.setUsername(rs.getString("username"));
                    msg.setMessageText(rs.getString("message_text"));
                    msg.setSentOn(rs.getTimestamp("sentOn"));
                    messages.add(msg);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return messages;
    }

    // DELETE MESSAGE (ONLY OWNER)
    public boolean deleteMessage(int messageID, int senderID) {
        String sql = "DELETE FROM messages WHERE messageID = ? AND senderID = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, messageID);
            stmt.setInt(2, senderID);

            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }
    // SEND DM
    public boolean sendDirectMessage(int conversationID, int senderID, String text) {
        String sql = "INSERT INTO messages (conversationID, senderID, message_text) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseUtil.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, conversationID);
            stmt.setInt(2, senderID);
            stmt.setString(3, text);

            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }
}