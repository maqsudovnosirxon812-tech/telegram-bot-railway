package org.example;

import io.github.cdimascio.dotenv.Dotenv;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Config {
    private static final Dotenv dotenv = Dotenv.load();
    private static Connection connection;

    // 🔑 .env dan token va sozlamalar olish
    public static String get(String key) {
        return dotenv.get(key);
    }

    // 📦 Database ulanish
    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            String url = get("DB_URL");
            String user = get("DB_USER");
            String pass = get("DB_PASSWORD");
            connection = DriverManager.getConnection(url, user, pass);
        }
        return connection;
    }

    // 👤 USER funksiyalari
    public static void upsertUser(long chatId, String username, String firstName) {
        try (Connection c = getConnection()) {
            String sql = """
                INSERT INTO users (id, username, first_name)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE username = VALUES(username),
                                        first_name = VALUES(first_name)
            """;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, chatId);
                ps.setString(2, username);
                ps.setString(3, firstName);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean isPromoUsed(long chatId) {
        try (Connection c = getConnection()) {
            PreparedStatement ps = c.prepareStatement("SELECT promo_used FROM users WHERE id = ?");
            ps.setLong(1, chatId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBoolean("promo_used");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void setPromoUsed(long chatId, boolean used) {
        try (Connection c = getConnection()) {
            PreparedStatement ps = c.prepareStatement("UPDATE users SET promo_used = ? WHERE id = ?");
            ps.setBoolean(1, used);
            ps.setLong(2, chatId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 📋 REQUEST funksiyalari
    public static void createRequest(long chatId, String service, String details) {
        try (Connection c = getConnection()) {
            PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO requests (chat_id, service, details) VALUES (?, ?, ?)"
            );
            ps.setLong(1, chatId);
            ps.setString(2, service);
            ps.setString(3, details);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<String> listRequests() {
        List<String> result = new ArrayList<>();
        try (Connection c = getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, chat_id, service, details, created_at FROM requests ORDER BY created_at DESC")) {
            while (rs.next()) {
                result.add(String.format(
                        "id=%d | chatId=%d | service=%s | at=%s\n%s",
                        rs.getLong("id"),
                        rs.getLong("chat_id"),
                        rs.getString("service"),
                        rs.getTimestamp("created_at"),
                        rs.getString("details") == null ? "" : rs.getString("details")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static boolean deleteRequestById(long id) {
        try (Connection c = getConnection()) {
            PreparedStatement ps = c.prepareStatement("DELETE FROM requests WHERE id = ?");
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void deleteRequestsByChatId(long chatId) {
        try (Connection c = getConnection()) {
            PreparedStatement ps = c.prepareStatement("DELETE FROM requests WHERE chat_id = ?");
            ps.setLong(1, chatId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
