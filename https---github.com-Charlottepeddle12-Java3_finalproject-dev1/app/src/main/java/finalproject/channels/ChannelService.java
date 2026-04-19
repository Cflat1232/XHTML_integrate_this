package finalproject.channels;

import finalproject.Users.UserLogin;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import javax.naming.*;
import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.*;
import java.util.*;

@Named("channelBean")
@SessionScoped
public class ChannelService implements Serializable {

    @Inject
    private UserLogin login;

    private Connection conn;
    private int serverID;
    private String channelName;
    private String message = "";
    private List<Channel> channels = new ArrayList<>();

    @PostConstruct
    public void open() {
        try {
            Context ctx = new InitialContext();
            conn = ((DataSource) ctx.lookup("java:/comp/env/jdbc/javaproject")).getConnection();
        } catch (Exception e) { message = e.getMessage(); }
    }

    @PreDestroy
    public void close() {
        try { if (conn != null) conn.close(); } catch (Exception ignored) {}
    }

    // ---------- PERMISSION ----------
    private boolean canCreate() throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(
            "SELECT 1 FROM server_member_roles smr JOIN server_roles sr ON smr.roleID=sr.roleID " +
            "WHERE smr.userID=? AND smr.serverID=? AND sr.can_create_channel=TRUE"
        );
        stmt.setInt(1, login.getUserId());
        stmt.setInt(2, serverID);
        return stmt.executeQuery().next();
    }

    // ---------- CREATE ----------
    public void createChannel() {
        try {
            if (!canCreate()) {
                message = "No permission.";
                return;
            }

            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO channels(serverID,name,created_by) VALUES (?,?,?)"
            );
            stmt.setInt(1, serverID);
            stmt.setString(2, channelName);
            stmt.setInt(3, login.getUserId());
            stmt.executeUpdate();

            message = "Channel created.";
            loadChannels();

        } catch (Exception e) {
            message = e.getMessage();
        }
    }

    // ---------- LOAD ----------
    public void loadChannels() {
        channels.clear();
        try {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM channels WHERE serverID=?"
            );
            stmt.setInt(1, serverID);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Channel c = new Channel();
                c.setChannelID(rs.getInt("channelID"));
                c.setName(rs.getString("name"));
                channels.add(c);
            }
        } catch (Exception e) {
            message = e.getMessage();
        }
    }

    // ---------- DELETE ----------
    public void deleteChannel(int id) {
        try {
            if (!canCreate()) {
                message = "No permission.";
                return;
            }

            PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM channels WHERE channelID=?"
            );
            stmt.setInt(1, id);
            stmt.executeUpdate();

            message = "Channel deleted.";
            loadChannels();

        } catch (Exception e) {
            message = e.getMessage();
        }
    }

    // ---------- GETTERS ----------
    public List<Channel> getChannels() { return channels; }
    public String getMessage() { return message; }
    public void setServerID(int id) { this.serverID = id; }
    public void setChannelName(String name) { this.channelName = name; }
}