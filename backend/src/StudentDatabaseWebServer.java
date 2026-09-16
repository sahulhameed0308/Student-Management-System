import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StudentDatabaseWebServer {

    private static final int PORT = 8080;
    private static final Path WEB_ROOT = Path.of("frontend").toAbsolutePath().normalize();

    public static void main(String[] args) throws Exception {
        ensureTableExists();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api", new ApiHandler());
        server.createContext("/", new StaticFileHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("Student database website is running at http://localhost:" + PORT);
    }

    private static void ensureTableExists() {
        String sql = "CREATE TABLE IF NOT EXISTS students ("
                + "id INT PRIMARY KEY AUTO_INCREMENT, "
                + "st_name VARCHAR(100) NOT NULL, "
                + "email_id VARCHAR(150) NOT NULL, "
                + "course VARCHAR(100) NOT NULL"
                + ")";

        try (Connection con = DBConnection.getConnection(); Statement st = con.createStatement()) {
            st.executeUpdate(sql);
            System.out.println("Database table is ready.");
        } catch (SQLException e) {
            System.err.println("Failed to initialize database table: " + e.getMessage());
        }
    }

    private static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path) || "".equals(path)) {
                path = "/index.html";
            }

            Path resolved = WEB_ROOT.resolve(path.substring(1)).normalize();
            if (!resolved.startsWith(WEB_ROOT) || !Files.exists(resolved) || !Files.isRegularFile(resolved)) {
                sendText(exchange, 404, "Not found");
                return;
            }

            byte[] content = Files.readAllBytes(resolved);
            String mime = mimeTypeFor(resolved);
            exchange.getResponseHeaders().set("Content-Type", mime);
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        }
    }

    private static class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();

                if ("GET".equals(method) && "/api/health".equals(path)) {
                    sendJson(exchange, 200, "{\"status\":\"ok\"}");
                    return;
                }

                if ("GET".equals(method) && "/api/students".equals(path)) {
                    listStudents(exchange);
                    return;
                }

                if ("POST".equals(method) && "/api/students".equals(path)) {
                    addStudent(exchange);
                    return;
                }

                if (path.startsWith("/api/students/")) {
                    String idPart = path.substring("/api/students/".length());
                    if (idPart.matches("\\d+")) {
                        if ("GET".equals(method)) { getStudentById(exchange, idPart); return; }
                        if ("PUT".equals(method)) { updateStudent(exchange, idPart); return; }
                        if ("DELETE".equals(method)) { deleteStudent(exchange, idPart); return; }
                    }
                }

                sendText(exchange, 404, "Not found");
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }

        private void listStudents(HttpExchange exchange) throws SQLException, IOException {
            String q = null;
            String queryString = exchange.getRequestURI().getQuery();
            if (queryString != null) {
                for (String part : queryString.split("&")) {
                    if (part.startsWith("q=") || part.startsWith("search=")) {
                        String[] kv = part.split("=", 2);
                        if (kv.length == 2) q = URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name());
                    }
                }
            }

            List<Map<String, String>> rows = new ArrayList<>();
            if (q == null || q.isEmpty()) {
                String sql = "SELECT id, st_name, email_id, course FROM students ORDER BY id ASC";
                try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> s = new HashMap<>();
                        s.put("id", String.valueOf(rs.getInt("id")));
                        s.put("name", rs.getString("st_name"));
                        s.put("email", rs.getString("email_id"));
                        s.put("course", rs.getString("course"));
                        rows.add(s);
                    }
                }
            } else {
                String sql = "SELECT id, st_name, email_id, course FROM students WHERE st_name LIKE ? OR email_id LIKE ? OR course LIKE ? ORDER BY id ASC";
                String like = "%" + q + "%";
                try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, like); ps.setString(2, like); ps.setString(3, like);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, String> s = new HashMap<>();
                            s.put("id", String.valueOf(rs.getInt("id")));
                            s.put("name", rs.getString("st_name"));
                            s.put("email", rs.getString("email_id"));
                            s.put("course", rs.getString("course"));
                            rows.add(s);
                        }
                    }
                }
            }

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < rows.size(); i++) {
                if (i > 0) json.append(',');
                Map<String, String> s = rows.get(i);
                json.append("{\"id\":\"").append(s.get("id")).append("\",");
                json.append("\"name\":\"").append(escapeJson(s.get("name"))).append("\",");
                json.append("\"email\":\"").append(escapeJson(s.get("email"))).append("\",");
                json.append("\"course\":\"").append(escapeJson(s.get("course"))).append("\"}");
            }
            json.append("]");
            sendJson(exchange, 200, json.toString());
        }

        private void addStudent(HttpExchange exchange) throws IOException, SQLException {
            String body = readRequestBody(exchange);
            String name = parseJsonField(body, "name");
            String email = parseJsonField(body, "email");
            String course = parseJsonField(body, "course");

            if (name.isEmpty() || email.isEmpty() || course.isEmpty()) {
                sendJson(exchange, 400, "{\"error\":\"Name, email, and course are required.\"}");
                return;
            }

            String sql = "INSERT INTO students (st_name, email_id, course) VALUES (?, ?, ?)";
            try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name); ps.setString(2, email); ps.setString(3, course);
                int affected = ps.executeUpdate();
                if (affected == 0) { sendJson(exchange, 500, "{\"error\":\"Insert failed\"}"); return; }
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    int id = keys.next() ? keys.getInt(1) : -1;
                    sendJson(exchange, 200, "{\"message\":\"Student added\",\"id\":\"" + id + "\"}");
                }
            }
        }

        private void getStudentById(HttpExchange exchange, String idPart) throws SQLException, IOException {
            int id = Integer.parseInt(idPart);
            String sql = "SELECT id, st_name, email_id, course FROM students WHERE id = ?";
            try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { sendJson(exchange, 404, "{\"error\":\"Not found\"}"); return; }
                    StringBuilder json = new StringBuilder("{");
                    json.append("\"id\":\"").append(rs.getInt("id")).append("\",");
                    json.append("\"name\":\"").append(escapeJson(rs.getString("st_name"))).append("\",");
                    json.append("\"email\":\"").append(escapeJson(rs.getString("email_id"))).append("\",");
                    json.append("\"course\":\"").append(escapeJson(rs.getString("course"))).append("\"");
                    json.append("}");
                    sendJson(exchange, 200, json.toString());
                }
            }
        }

        private void updateStudent(HttpExchange exchange, String idPart) throws SQLException, IOException {
            int id = Integer.parseInt(idPart);
            String body = readRequestBody(exchange);
            String name = parseJsonField(body, "name");
            String email = parseJsonField(body, "email");
            String course = parseJsonField(body, "course");

            if (name.isEmpty() || email.isEmpty() || course.isEmpty()) {
                sendJson(exchange, 400, "{\"error\":\"Name, email, and course are required.\"}");
                return;
            }

            String sql = "UPDATE students SET st_name = ?, email_id = ?, course = ? WHERE id = ?";
            try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, name); ps.setString(2, email); ps.setString(3, course); ps.setInt(4, id);
                int affected = ps.executeUpdate();
                if (affected == 0) { sendJson(exchange, 404, "{\"error\":\"Not found\"}"); return; }
                sendJson(exchange, 200, "{\"message\":\"Student updated\"}");
            }
        }

        private void deleteStudent(HttpExchange exchange, String idPart) throws SQLException, IOException {
            int id = Integer.parseInt(idPart);
            String sql = "DELETE FROM students WHERE id = ?";
            try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, id);
                int affected = ps.executeUpdate();
                if (affected == 0) { sendJson(exchange, 404, "{\"error\":\"Not found\"}"); return; }
                sendJson(exchange, 200, "{\"message\":\"Student deleted\"}");
            }
        }
    }

    // --- helpers ---
    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, b.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(b); }
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, b.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(b); }
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream in = exchange.getRequestBody();
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String parseJsonField(String body, String field) {
        if (body == null) return "";
        String pattern = "\"" + field + "\"\\s*:\\s*\\\"([^\\\"]*)\\\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(body);
        return m.find() ? m.group(1) : "";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");
    }

    private static String mimeTypeFor(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        if (name.endsWith(".html") || name.endsWith(".htm")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

}
