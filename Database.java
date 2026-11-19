import java.sql.*;
import java.util.*;

/**
 * Database Management Class for GlucoTrack.
 * Handles all SQLite relational database operations using standard SQL.
 * Database file: tracker.db
 */
public class Database {
    private static final String DB_URL = "jdbc:sqlite:tracker.db";

    /**
     * Initializes the SQLite database and creates the required tables if not already present.
     */
    public static void init() {
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                // Table 1: users (stores registered usernames)
                String createUsersSql = "CREATE TABLE IF NOT EXISTS users (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "username TEXT UNIQUE NOT NULL COLLATE NOCASE" +
                        ");";
                stmt.execute(createUsersSql);

                // Table 2: sugar_logs (stores glucose readings linked to user)
                String createLogsSql = "CREATE TABLE IF NOT EXISTS sugar_logs (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "username TEXT NOT NULL COLLATE NOCASE, " +
                        "sugar_level REAL NOT NULL, " +
                        "reading_type TEXT NOT NULL, " +
                        "log_date TEXT NOT NULL, " +
                        "notes TEXT, " +
                        "FOREIGN KEY (username) REFERENCES users(username)" +
                        ");";
                stmt.execute(createLogsSql);

                System.out.println("[Database] SQLite database initialized successfully (tracker.db).");
            }
        } catch (Exception e) {
            System.err.println("[Database] Initialization error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    /**
     * Registers a new user.
     * Returns true if created successfully, false if username already exists.
     */
    public static boolean registerUser(String username) {
        if (username == null || username.trim().isEmpty()) return false;
        String sql = "INSERT INTO users(username) VALUES(?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username.trim());
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Checks if a user exists in the database.
     */
    public static boolean userExists(String username) {
        if (username == null || username.trim().isEmpty()) return false;
        String sql = "SELECT id FROM users WHERE username = ? COLLATE NOCASE";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Adds a new blood glucose reading log.
     */
    public static boolean addLog(String username, double sugarLevel, String readingType, String logDate, String notes) {
        String sql = "INSERT INTO sugar_logs(username, sugar_level, reading_type, log_date, notes) VALUES(?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username.trim());
            pstmt.setDouble(2, sugarLevel);
            pstmt.setString(3, readingType != null ? readingType : "Random");
            pstmt.setString(4, logDate != null ? logDate : "");
            pstmt.setString(5, notes != null ? notes : "");
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Deletes a reading by ID and username.
     */
    public static boolean deleteLog(int id, String username) {
        String sql = "DELETE FROM sugar_logs WHERE id = ? AND username = ? COLLATE NOCASE";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.setString(2, username.trim());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Retrieves all sugar logs for a specific user ordered chronologically.
     */
    public static List<Map<String, Object>> getLogs(String username) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT id, username, sugar_level, reading_type, log_date, notes " +
                     "FROM sugar_logs WHERE username = ? COLLATE NOCASE ORDER BY log_date ASC, id ASC";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getInt("id"));
                    row.put("username", rs.getString("username"));
                    row.put("sugar_level", rs.getDouble("sugar_level"));
                    row.put("reading_type", rs.getString("reading_type"));
                    row.put("log_date", rs.getString("log_date"));
                    row.put("notes", rs.getString("notes") == null ? "" : rs.getString("notes"));
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Retrieves all records from users table.
     */
    public static List<Map<String, Object>> getAllUsers() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT id, username FROM users ORDER BY id ASC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("username", rs.getString("username"));
                list.add(row);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Retrieves all records from sugar_logs table.
     */
    public static List<Map<String, Object>> getAllLogs() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT id, username, sugar_level, reading_type, log_date, notes FROM sugar_logs ORDER BY log_date ASC, id ASC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("username", rs.getString("username"));
                row.put("sugar_level", rs.getDouble("sugar_level"));
                row.put("reading_type", rs.getString("reading_type"));
                row.put("log_date", rs.getString("log_date"));
                row.put("notes", rs.getString("notes") == null ? "" : rs.getString("notes"));
                list.add(row);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Calculates statistics: Total Readings, Average Glucose, and Estimated HbA1c
     * Formula: Estimated HbA1c = (Average Glucose + 46.7) / 28.7
     */
    public static Map<String, Object> getStats(String username) {
        Map<String, Object> stats = new HashMap<>();
        String sql = "SELECT COUNT(*) as total, AVG(sugar_level) as avg_level FROM sugar_logs WHERE username = ? COLLATE NOCASE";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("total");
                    double avg = rs.getDouble("avg_level");
                    stats.put("totalReadings", count);

                    if (count > 0) {
                        double roundedAvg = Math.round(avg * 10.0) / 10.0;
                        stats.put("averageGlucose", roundedAvg);

                        // American Diabetes Association formula
                        double hba1c = (avg + 46.7) / 28.7;
                        double roundedHbA1c = Math.round(hba1c * 10.0) / 10.0;
                        stats.put("estimatedHbA1c", roundedHbA1c);

                        if (roundedHbA1c < 5.7) {
                            stats.put("status", "Normal");
                        } else if (roundedHbA1c <= 6.4) {
                            stats.put("status", "Prediabetes");
                        } else {
                            stats.put("status", "Diabetes Range");
                        }
                    } else {
                        stats.put("averageGlucose", 0);
                        stats.put("estimatedHbA1c", 0);
                        stats.put("status", "No Data Yet");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return stats;
    }

    /**
     * Executes custom SQL queries from the Database Inspector console.
     */
    public static Map<String, Object> executeCustomQuery(String sql) {
        Map<String, Object> response = new HashMap<>();
        String trimmed = sql.trim();
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            if (trimmed.toUpperCase().startsWith("SELECT")) {
                try (ResultSet rs = stmt.executeQuery(trimmed)) {
                    ResultSetMetaData md = rs.getMetaData();
                    int columns = md.getColumnCount();
                    List<String> columnNames = new ArrayList<>();
                    for (int i = 1; i <= columns; i++) {
                        columnNames.add(md.getColumnLabel(i));
                    }
                    List<Map<String, Object>> rows = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columns; i++) {
                            row.put(md.getColumnLabel(i), rs.getObject(i));
                        }
                        rows.add(row);
                    }
                    response.put("success", true);
                    response.put("columns", columnNames);
                    response.put("rows", rows);
                }
            } else {
                int affected = stmt.executeUpdate(trimmed);
                response.put("success", true);
                response.put("message", "Statement executed successfully. " + affected + " row(s) affected.");
            }
        } catch (SQLException e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return response;
    }

    /**
     * Exports full SQL dump (DDL and DML) of tracker.db.
     */
    public static String exportSqlDump() {
        StringBuilder sb = new StringBuilder();
        sb.append("-- ========================================================\n");
        sb.append("-- GlucoTrack SQLite Database Export\n");
        sb.append("-- Generated on: ").append(new java.util.Date()).append("\n");
        sb.append("-- ========================================================\n\n");

        sb.append("-- Table: users\n");
        sb.append("CREATE TABLE IF NOT EXISTS users (\n");
        sb.append("    id INTEGER PRIMARY KEY AUTOINCREMENT,\n");
        sb.append("    username TEXT UNIQUE NOT NULL\n");
        sb.append(");\n\n");

        for (Map<String, Object> u : getAllUsers()) {
            String uname = String.valueOf(u.get("username")).replace("'", "''");
            sb.append("INSERT INTO users (id, username) VALUES (")
              .append(u.get("id")).append(", '").append(uname).append("');\n");
        }

        sb.append("\n-- Table: sugar_logs\n");
        sb.append("CREATE TABLE IF NOT EXISTS sugar_logs (\n");
        sb.append("    id INTEGER PRIMARY KEY AUTOINCREMENT,\n");
        sb.append("    username TEXT NOT NULL,\n");
        sb.append("    sugar_level REAL NOT NULL,\n");
        sb.append("    reading_type TEXT NOT NULL,\n");
        sb.append("    log_date TEXT NOT NULL,\n");
        sb.append("    notes TEXT\n");
        sb.append(");\n\n");

        for (Map<String, Object> l : getAllLogs()) {
            String uname = String.valueOf(l.get("username")).replace("'", "''");
            String type = String.valueOf(l.get("reading_type")).replace("'", "''");
            String date = String.valueOf(l.get("log_date")).replace("'", "''");
            String notes = String.valueOf(l.get("notes")).replace("'", "''");
            sb.append("INSERT INTO sugar_logs (id, username, sugar_level, reading_type, log_date, notes) VALUES (")
              .append(l.get("id")).append(", '")
              .append(uname).append("', ")
              .append(l.get("sugar_level")).append(", '")
              .append(type).append("', '")
              .append(date).append("', '")
              .append(notes).append("');\n");
        }

        return sb.toString();
    }
}
