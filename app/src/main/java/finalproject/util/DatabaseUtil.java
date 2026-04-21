package finalproject.util;

import java.sql.Connection;
import java.sql.SQLException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

public class DatabaseUtil {

    public static Connection getConnection() throws SQLException {
        try {
            Context ctx = new InitialContext();
            DataSource ds = (DataSource) ctx.lookup("java:comp/env/jdbc/java_project");
            return ds.getConnection();
        } catch (NamingException e) {
            throw new SQLException("Datasource lookup failed", e);
        }
    }
}


