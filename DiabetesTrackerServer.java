import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Lightweight Built-in HTTP Server for GlucoTrack.
 * Runs in BlueJ or terminal via standard public static void main(String[] args).
 * Uses Java's built-in com.sun.net.httpserver.HttpServer with SQLite backend.
 */
public class DiabetesTrackerServer {
    private static final int PORT = 8080;
    private static final File ROOT_DIR = new File(".");

    public static void main(String[] args) {
        try {
            // 1. Initialize SQLite Database
            System.out.println("Initializing SQLite Database...");
            Database.init();

            // 2. Start HTTP Server with automatic port fallback
            int activePort = PORT;
            HttpServer server;
            try {
                server = HttpServer.create(new InetSocketAddress(activePort), 0);
            } catch (java.net.BindException e) {
                activePort = 8081;
                System.out.println("[Notice] Port 8080 was in use. Using port 8081 instead.");
                server = HttpServer.create(new InetSocketAddress(activePort), 0);
            }

            // 3. Register API Endpoints
            server.createContext("/api/register", new RegisterHandler());
            server.createContext("/api/login", new LoginHandler());
            server.createContext("/api/logs", new LogsHandler());
            server.createContext("/api/delete-log", new DeleteLogHandler());
            server.createContext("/api/db/tables", new DbTablesHandler());
            server.createContext("/api/db/query", new DbQueryHandler());
            server.createContext("/api/db/export", new DbExportHandler());

            // 4. Static Web File Handler
            server.createContext("/", new StaticFileHandler());

            server.setExecutor(null); // default executor
            server.start();

            System.out.println("==========================================================");
            System.out.println(" GlucoTrack Server with SQL Backend is RUNNING!");
            System.out.println(" Open your browser and navigate to:");
            System.out.println(" http://localhost:" + activePort);
            System.out.println("==========================================================");

        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ----------------- HANDLERS -----------------

    /**
     * Handles User Registration (/api/register)
     */
    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> params = parseRequestBody(exchange);
                String username = params.get("username");

                if (username == null || username.trim().isEmpty()) {
                    sendJsonResponse(exchange, 400, "{\"success\": false, \"message\": \"Username is required.\"}");
                    return;
                }

                boolean registered = Database.registerUser(username.trim());
                if (registered) {
                    sendJsonResponse(exchange, 200, "{\"success\": true, \"message\": \"User registered successfully!\", \"username\": \"" + escapeJson(username.trim()) + "\"}");
                } else {
                    sendJsonResponse(exchange, 400, "{\"success\": false, \"message\": \"Username already taken. Please choose another.\"}");
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    /**
     * Handles User Login (/api/login)
     * No password required - verifies that username exists in SQLite database.
     */
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> params = parseRequestBody(exchange);
                String username = params.get("username");

                if (username == null || username.trim().isEmpty()) {
                    sendJsonResponse(exchange, 400, "{\"success\": false, \"message\": \"Please enter a username.\"}");
                    return;
                }

                boolean exists = Database.userExists(username.trim());
                if (exists) {
                    sendJsonResponse(exchange, 200, "{\"success\": true, \"message\": \"Login successful!\", \"username\": \"" + escapeJson(username.trim()) + "\"}");
                } else {
                    sendJsonResponse(exchange, 404, "{\"success\": false, \"message\": \"User not found in SQL database. Please register first.\"}");
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    /**
     * Handles Sugar Logs (/api/logs)
     * GET: Returns patient readings and computed clinical stats (Average Glucose & Estimated HbA1c)
     * POST: Inserts new reading log into SQLite database
     */
    static class LogsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                Map<String, String> query = parseQueryParams(exchange.getRequestURI().getRawQuery());
                String username = query.get("username");

                if (username == null || username.trim().isEmpty()) {
                    sendJsonResponse(exchange, 400, "{\"success\": false, \"message\": \"Username query parameter is required.\"}");
                    return;
                }

                List<Map<String, Object>> logs = Database.getLogs(username.trim());
                Map<String, Object> stats = Database.getStats(username.trim());

                StringBuilder json = new StringBuilder();
                json.append("{");
                json.append("\"success\": true,");
                json.append("\"username\": \"").append(escapeJson(username.trim())).append("\",");

                // Clinical Statistics
                json.append("\"stats\": {");
                json.append("\"totalReadings\": ").append(stats.getOrDefault("totalReadings", 0)).append(",");
                json.append("\"averageGlucose\": ").append(stats.getOrDefault("averageGlucose", 0)).append(",");
                json.append("\"estimatedHbA1c\": ").append(stats.getOrDefault("estimatedHbA1c", 0)).append(",");
                json.append("\"status\": \"").append(escapeJson(String.valueOf(stats.getOrDefault("status", "No Data Yet")))).append("\"");
                json.append("},");

                // Logs Array
                json.append("\"logs\": [");
                for (int i = 0; i < logs.size(); i++) {
                    Map<String, Object> log = logs.get(i);
                    if (i > 0) json.append(",");
                    json.append("{");
                    json.append("\"id\": ").append(log.get("id")).append(",");
                    json.append("\"username\": \"").append(escapeJson(String.valueOf(log.get("username")))).append("\",");
                    json.append("\"sugar_level\": ").append(log.get("sugar_level")).append(",");
                    json.append("\"reading_type\": \"").append(escapeJson(String.valueOf(log.get("reading_type")))).append("\",");
                    json.append("\"log_date\": \"").append(escapeJson(String.valueOf(log.get("log_date")))).append("\",");
                    json.append("\"notes\": \"").append(escapeJson(String.valueOf(log.get("notes")))).append("\"");
                    json.append("}");
                }
                json.append("]");
                json.append("}");

                sendJsonResponse(exchange, 200, json.toString());

            } else if ("POST".equalsIgnoreCase(method)) {
                Map<String, String> params = parseRequestBody(exchange);
                String username = params.get("username");
                String sugarStr = params.get("sugar_level");
                if (sugarStr == null) sugarStr = params.get("sugarLevel");
                String type = params.get("reading_type");
                if (type == null) type = params.get("readingType");
                String date = params.get("log_date");
                if (date == null) date = params.get("logDate");
                String notes = params.get("notes");

                if (username == null || sugarStr == null) {
                    sendJsonResponse(exchange, 400, "{\"success\": false, \"message\": \"Username and sugar level are required.\"}");
                    return;
                }

                try {
                    double sugarLevel = Double.parseDouble(sugarStr);
                    boolean added = Database.addLog(username, sugarLevel, type, date, notes);
                    if (added) {
                        sendJsonResponse(exchange, 200, "{\"success\": true, \"message\": \"Reading logged successfully.\"}");
                    } else {
                        sendJsonResponse(exchange, 500, "{\"success\": false, \"message\": \"Failed to save log to SQLite database.\"}");
                    }
                } catch (NumberFormatException e) {
                    sendJsonResponse(exchange, 400, "{\"success\": false, \"message\": \"Sugar level must be a numeric value.\"}");
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    /**
     * Handles Log Deletion (/api/delete-log)
     */
    static class DeleteLogHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> params = parseRequestBody(exchange);
                String idStr = params.get("id");
                String username = params.get("username");

                if (idStr != null && username != null) {
                    try {
                        int id = Integer.parseInt(idStr);
                        boolean deleted = Database.deleteLog(id, username);
                        if (deleted) {
                            sendJsonResponse(exchange, 200, "{\"success\": true, \"message\": \"Log entry deleted.\"}");
                            return;
                        }
                    } catch (NumberFormatException ignored) {}
                }
                sendJsonResponse(exchange, 400, "{\"success\": false, \"message\": \"Invalid delete request.\"}");
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    /**
     * Handles Table Inspection for Database Inspector (/api/db/tables)
     */
    static class DbTablesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            List<Map<String, Object>> users = Database.getAllUsers();
            List<Map<String, Object>> logs = Database.getAllLogs();

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"success\": true,");

            // Users
            json.append("\"users\": [");
            for (int i = 0; i < users.size(); i++) {
                Map<String, Object> u = users.get(i);
                if (i > 0) json.append(",");
                json.append("{\"id\": ").append(u.get("id")).append(", \"username\": \"").append(escapeJson(String.valueOf(u.get("username")))).append("\"}");
            }
            json.append("],");

            // Sugar logs
            json.append("\"sugar_logs\": [");
            for (int i = 0; i < logs.size(); i++) {
                Map<String, Object> l = logs.get(i);
                if (i > 0) json.append(",");
                json.append("{")
                    .append("\"id\": ").append(l.get("id")).append(",")
                    .append("\"username\": \"").append(escapeJson(String.valueOf(l.get("username")))).append("\",")
                    .append("\"sugar_level\": ").append(l.get("sugar_level")).append(",")
                    .append("\"reading_type\": \"").append(escapeJson(String.valueOf(l.get("reading_type")))).append("\",")
                    .append("\"log_date\": \"").append(escapeJson(String.valueOf(l.get("log_date")))).append("\",")
                    .append("\"notes\": \"").append(escapeJson(String.valueOf(l.get("notes")))).append("\"")
                    .append("}");
            }
            json.append("]");
            json.append("}");

            sendJsonResponse(exchange, 200, json.toString());
        }
    }

    /**
     * Handles Custom SQL Execution from Database Inspector (/api/db/query)
     */
    static class DbQueryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> params = parseRequestBody(exchange);
                String sql = params.get("query");
                if (sql == null || sql.trim().isEmpty()) {
                    sendJsonResponse(exchange, 400, "{\"success\": false, \"error\": \"Query string cannot be empty.\"}");
                    return;
                }

                Map<String, Object> result = Database.executeCustomQuery(sql.trim());
                if (Boolean.TRUE.equals(result.get("success"))) {
                    StringBuilder json = new StringBuilder();
                    json.append("{\"success\": true");
                    if (result.containsKey("columns")) {
                        @SuppressWarnings("unchecked")
                        List<String> cols = (List<String>) result.get("columns");
                        json.append(", \"columns\": [");
                        for (int i = 0; i < cols.size(); i++) {
                            if (i > 0) json.append(",");
                            json.append("\"").append(escapeJson(cols.get(i))).append("\"");
                        }
                        json.append("]");
                    }
                    if (result.containsKey("rows")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
                        json.append(", \"rows\": [");
                        for (int i = 0; i < rows.size(); i++) {
                            if (i > 0) json.append(",");
                            Map<String, Object> r = rows.get(i);
                            json.append("{");
                            int cIdx = 0;
                            for (Map.Entry<String, Object> entry : r.entrySet()) {
                                if (cIdx > 0) json.append(",");
                                json.append("\"").append(escapeJson(entry.getKey())).append("\": ");
                                Object val = entry.getValue();
                                if (val == null) {
                                    json.append("null");
                                } else if (val instanceof Number || val instanceof Boolean) {
                                    json.append(val);
                                } else {
                                    json.append("\"").append(escapeJson(val.toString())).append("\"");
                                }
                                cIdx++;
                            }
                            json.append("}");
                        }
                        json.append("]");
                    }
                    if (result.containsKey("message")) {
                        json.append(", \"message\": \"").append(escapeJson((String) result.get("message"))).append("\"");
                    }
                    json.append("}");
                    sendJsonResponse(exchange, 200, json.toString());
                } else {
                    String err = (String) result.getOrDefault("error", "SQL Execution error.");
                    sendJsonResponse(exchange, 400, "{\"success\": false, \"error\": \"" + escapeJson(err) + "\"}");
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    /**
     * Handles SQL Dump Export (/api/db/export)
     */
    static class DbExportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String sqlDump = Database.exportSqlDump();
            byte[] bytes = sqlDump.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"glucotrack_database.sql\"");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /**
     * Serves static frontend files (HTML, CSS, JS, Images).
     */
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            File requestedFile = new File(ROOT_DIR, path.substring(1));

            // Security check against directory traversal outside ROOT_DIR
            if (!requestedFile.getCanonicalPath().startsWith(ROOT_DIR.getCanonicalPath()) || !requestedFile.exists() || requestedFile.isDirectory()) {
                String notFound = "<h1>404 Not Found</h1><p>Requested file " + path + " was not found.</p>";
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(404, notFound.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            String mimeType = getMimeType(requestedFile.getName());
            exchange.getResponseHeaders().set("Content-Type", mimeType);
            exchange.sendResponseHeaders(200, requestedFile.length());

            try (InputStream is = new FileInputStream(requestedFile); OutputStream os = exchange.getResponseBody()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    // ----------------- HELPERS -----------------

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
        byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name());
                    String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name());
                    map.put(key, value);
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    private static Map<String, String> parseRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] b = new byte[1024];
        int n;
        while ((n = is.read(b)) != -1) {
            buffer.write(b, 0, n);
        }
        String body = buffer.toString(StandardCharsets.UTF_8.name()).trim();

        Map<String, String> map = new HashMap<>();
        if (body.startsWith("{") && body.endsWith("}")) {
            String content = body.substring(1, body.length() - 1);
            String[] tokens = content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String token : tokens) {
                String[] kv = token.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replaceAll("^\"|\"$", "");
                    String val = kv[1].trim().replaceAll("^\"|\"$", "");
                    map.put(key, val);
                }
            }
        } else {
            map = parseQueryParams(body);
        }
        return map;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String getMimeType(String filename) {
        if (filename.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filename.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filename.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (filename.endsWith(".json")) return "application/json; charset=UTF-8";
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return "image/jpeg";
        if (filename.endsWith(".svg")) return "image/svg+xml";
        if (filename.endsWith(".sql")) return "text/plain; charset=UTF-8";
        return "text/plain; charset=UTF-8";
    }
}
