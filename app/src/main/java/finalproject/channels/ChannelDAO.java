package finalproject.channels;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ChannelDAO {
    private DataSource dataSource;

    public ChannelDAO() {
        try {
            Context ctx = new InitialContext();
            dataSource = (DataSource) ctx.lookup("java:/comp/env/jdbc/javaproject");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // CREATE CHANNEL
    public boolean createChannel(int serverID, String name, int createdBy) {
        String sql = "INSERT INTO channels (serverID, name, created_by) VALUES (?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, serverID);
            stmt.setString(2, name);
            stmt.setInt(3, createdBy);

            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }

    // GET CHANNELS BY SERVER
    public List<Channel> getChannelsByServer(int serverID) {
        List<Channel> channels = new ArrayList<>();

        String sql = "SELECT * FROM channels WHERE serverID = ? ORDER BY name ASC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, serverID);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Channel c = new Channel();
                    c.setChannelID(rs.getInt("channelID"));
                    c.setServerID(rs.getInt("serverID"));
                    c.setName(rs.getString("name"));
                    c.setCreatedBy(rs.getInt("created_by"));
                    c.setCreatedAt(rs.getTimestamp("created_at"));
                    channels.add(c);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return channels;
    }

    // DELETE CHANNEL
    public boolean deleteChannel(int channelID) {
        String sql = "DELETE FROM channels WHERE channelID = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, channelID);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }
}