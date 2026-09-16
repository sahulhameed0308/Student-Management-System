import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

public class StudentDatabaseWebServer {

    private static final int PORT = 8080;
    private static final Path WEB_ROOT = Path.of("web").toAbsolutePath().normalize();
    private static final int MAX_PHOTO_BYTES = 2 * 1024 * 1024; // 2 MB

    public static void main(String[] args) throws IOException {
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
            try {
                st.executeUpdate("ALTER TABLE students ADD COLUMN photo VARCHAR(255)");
            } catch (SQLException ignored) {
                // Column may already exist; ignore
            }
        } catch (SQLException e) {
            System.err.println("Failed to initialize database table: " + e.getMessage());
        }
    }

    private static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            if ("/".equals(path)) {
                path = "/index.html";
            }

            // If the requested page is the main dashboard, require a simple auth cookie.
            boolean requiresAuth = "/index.html".equals(path) || "/".equals(path);
            if (requiresAuth) {
                String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
                if (cookieHeader == null || !cookieHeader.contains("Auth=1")) {
                    exchange.getResponseHeaders().set("Location", "/login.html");
                    exchange.sendResponseHeaders(302, -1);
                    return;
                }
            }

            Path resolved = WEB_ROOT.resolve(path.substring(1)).normalize();
            if (!resolved.startsWith(WEB_ROOT)) {
                sendText(exchange, 403, "Forbidden");
                return;
            }

            if (Files.exists(resolved) && Files.isRegularFile(resolved)) {
                byte[] content = Files.readAllBytes(resolved);
                String mimeType = mimeTypeFor(resolved);
                exchange.getResponseHeaders().set("Content-Type", mimeType);
                exchange.sendResponseHeaders(200, content.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content);
                }
            } else {
                sendText(exchange, 404, "File not found");
            }
        }
    }

    private static class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            try {
                if ("POST".equals(method) && "/api/login".equals(path)) {
                    handleLogin(exchange);
                    return;
                }

                if ("POST".equals(method) && "/api/logout".equals(path)) {
                    handleLogout(exchange);
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

                if ("POST".equals(method) && path.matches("/api/students/\\d+/photo")) {
                    handlePhotoUpload(exchange, path);
                    return;
                }

                if ("GET".equals(method) && path.startsWith("/api/students/")) {
                    getStudentById(exchange, path);
                    return;
                }

                if ("PUT".equals(method) && path.startsWith("/api/students/")) {
                    updateStudent(exchange, path);
                    return;
                }

                if ("DELETE".equals(method) && path.startsWith("/api/students/")) {
                    deleteStudent(exchange, path);
                    return;
                }

                if ("GET".equals(method) && "/api/health".equals(path)) {
                    sendJson(exchange, 200, "{\"status\":\"ok\"}");
                    return;
                }

                sendText(exchange, 404, "Not found");
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }

        private void listStudents(HttpExchange exchange) throws SQLException, IOException {
            String queryString = exchange.getRequestURI().getQuery();
            String q = null;
            if (queryString != null) {
                for (String part : queryString.split("&")) {
                    if (part.startsWith("q=") || part.startsWith("search=")) {
                        String[] kv = part.split("=", 2);
                        if (kv.length == 2) {
                            q = URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name());
                        }
                    }
                }
            }

                List<Map<String, String>> rows = new ArrayList<>();
            if (q == null || q.isEmpty()) {
                String sql = "SELECT id, st_name, email_id, course FROM students ORDER BY id ASC";
                try (Connection con = DBConnection.getConnection();
                     PreparedStatement ps = con.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {

                    while (rs.next()) {
                        Map<String, String> student = new HashMap<>();
                        student.put("id", String.valueOf(rs.getInt("id")));
                        student.put("name", rs.getString("st_name"));
                        student.put("email", rs.getString("email_id"));
                        student.put("course", rs.getString("course"));
                        String photo = rs.getString("photo");
                        student.put("photo", photo);
                        if (photo != null && !photo.isEmpty()) {
                            int s = photo.lastIndexOf('/');
                            String file = s >= 0 ? photo.substring(s+1) : photo;
                            student.put("photoThumb", "uploads/thumb_" + file);
                        } else {
                            student.put("photoThumb", "");
                        }
                        rows.add(student);
                    }
                }
            } else {
                String sql = "SELECT id, st_name, email_id, course FROM students WHERE st_name LIKE ? OR email_id LIKE ? OR course LIKE ? ORDER BY id ASC";
                String like = "%" + q + "%";
                try (Connection con = DBConnection.getConnection();
                     PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, like);
                    ps.setString(2, like);
                    ps.setString(3, like);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, String> student = new HashMap<>();
                            student.put("id", String.valueOf(rs.getInt("id")));
                            student.put("name", rs.getString("st_name"));
                            student.put("email", rs.getString("email_id"));
                            student.put("course", rs.getString("course"));
                            String photo = rs.getString("photo");
                            student.put("photo", photo);
                            if (photo != null && !photo.isEmpty()) {
                                int s = photo.lastIndexOf('/');
                                String file = s >= 0 ? photo.substring(s+1) : photo;
                                student.put("photoThumb", "uploads/thumb_" + file);
                            } else {
                                student.put("photoThumb", "");
                            }
                            rows.add(student);
                        }
                    }
                }
            }

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < rows.size(); i++) {
                if (i > 0) {
                    json.append(",");
                }
                Map<String, String> student = rows.get(i);
                json.append("{\"id\":\"")
                        .append(student.get("id"))
                        .append("\",\"name\":\"")
                        .append(escapeJson(student.get("name")))
                        .append("\",\"email\":\"")
                        .append(escapeJson(student.get("email")))
                    .append("\",\"course\":\"")
                    .append(escapeJson(student.get("course")))
                    .append("\",\"photo\":\"")
                    .append(escapeJson(student.get("photo")))
                        .append("\"}");
            }
            json.append("]");
            sendJson(exchange, 200, json.toString());
        }

        private void addStudent(HttpExchange exchange) throws IOException, SQLException {
            Map<String, String> payload = readJsonBody(exchange);
            String name = payload.getOrDefault("name", "").trim();
            String email = payload.getOrDefault("email", "").trim();
            String course = payload.getOrDefault("course", "").trim();

            if (name.isEmpty() || email.isEmpty() || course.isEmpty()) {
                sendJson(exchange, 400, "{\"error\":\"Name, email, and course are required.\"}");
                return;
            }

(This backup file continues; omitted for brevity)