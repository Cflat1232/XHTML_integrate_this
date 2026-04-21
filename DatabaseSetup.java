import java.io.*;
import java.sql.*;

public class DatabaseSetup {
    public static void main(String[] args) {
        // Try different combinations
        int[][] configs = {
            {3308, 0}, // port 3308, Bokagi89.
            {3308, 1}, // port 3308, Bokagi89
            {3308, 2}, // port 3308, empty password
            {3306, 0}, // port 3306, Bokagi89.
            {3306, 1}, // port 3306, Bokagi89
            {3306, 2}, // port 3306, empty password
        };
        
        String[] passwords = {"Bokagi89.", "Bokagi89", ""};
        
        for (int[] config : configs) {
            int port = config[0];
            String password = passwords[config[1]];
            
            String url = "jdbc:mariadb://localhost:" + port + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&authenticationPlugins=org.mariadb.jdbc.authentication.NativePasswordPluginFactory";
            System.out.println("\nAttempting connection to port " + port + " with password: " + (password.isEmpty() ? "(empty)" : "***"));
            
            try {
                setupDatabase(url, "root", password);
                return;
            } catch (Exception e) {
                System.out.println("Failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        }
        
        System.err.println("\nAll connection attempts failed!");
    }
    
    static void setupDatabase(String url, String user, String password) throws Exception {
        Connection conn;
        try {
            conn = DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            // Try with empty password if that failed
            if (password != null && !password.isEmpty()) {
                try {
                    conn = DriverManager.getConnection(url, user, "");
                } catch (SQLException e2) {
                    throw e;
                }
            } else {
                throw e;
            }
        }
        
        try (Statement stmt = conn.createStatement()) {
            // Read and execute the SQL file
            File sqlFile = new File("db.sql");
            StringBuilder sql = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new FileReader(sqlFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sql.append(line).append("\n");
                }
            }

            System.out.println("Connected successfully! Setting up database...");
            
            // Split by semicolon and execute each statement
            String[] statements = sql.toString().split(";");
            int count = 0;

            for (String statement : statements) {
                statement = statement.trim();
                if (!statement.isEmpty() && !statement.startsWith("--")) {
                    try {
                        stmt.execute(statement);
                        count++;
                    } catch (SQLException e) {
                        System.out.println("⚠ SQL Error: " + e.getMessage());
                    }
                }
            }

            System.out.println("✓ Database setup completed! Executed " + count + " statements.");
            conn.close();

        } catch (Exception e) {
            conn.close();
            throw e;
        }
    }
}