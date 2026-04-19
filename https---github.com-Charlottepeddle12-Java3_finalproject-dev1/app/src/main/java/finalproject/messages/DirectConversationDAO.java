package finalproject.messages;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.sql.*;

public class DirectConversationDAO {

    private DataSource dataSource;

    public DirectConversationDAO() {
        try {
            Context ctx = new InitialContext();
            dataSource = (DataSource) ctx.lookup("java:/comp/env/jdbc/javaproject");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // GET OR CREATE CONVERSATION
    public int getOrCreateConversation(int user1, int user2) {
        if (user1 > user2) {
            int temp = user1;
            user1 = user2;
            user2 = temp;
        }

        try (Connection conn = dataSource.getConnection()) {

            // check existing
            PreparedStatement check = conn.prepareStatement(
                "SELECT conversationID FROM direct_conversations WHERE userOneID = ? AND userTwoID = ?"
            );
            check.setInt(1, user1);
            check.setInt(2, user2);

            ResultSet rs = check.executeQuery();

            if (rs.next()) {
                return rs.getInt("conversationID");
            }

            // create new
            PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO direct_conversations (userOneID, userTwoID) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            insert.setInt(1, user1);
            insert.setInt(2, user2);
            insert.executeUpdate();

            ResultSet keys = insert.getGeneratedKeys();
            if (keys.next()) {
                return keys.getInt(1);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return -1;
    }
}