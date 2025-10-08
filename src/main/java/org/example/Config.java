package org.example;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Config {

    // ✅ MySQL Railway konfiguratsiyasi
    private static final String DB_URL  = "jdbc:mysql://shinkansen.proxy.rlwy.net:44343/railway?useSSL=true&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "YGYGxcMgwgAtbeQvzHGORzdigwoOIPiM";

    private static Connection conn;

    static {
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            System.out.println("✅ Database connected to Railway (MySQL)!");

            // Jadval mavjudligini tekshirish va kerak bo‘lsa yaratish
            createTablesIfNotExists();

        } catch (SQLException e) {
            System.err.println("❌ Database ulanishda xatolik: " + e.getMessage());
        }
    }

    // 🔹 Agar "users" va "requests" jadvallari yo‘q bo‘lsa — yaratadi
    private static void createTablesIfNotExists() {
        String createUsersTable = """
                CREATE TABLE IF NOT EXISTS users (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    chat_id BIGINT UNIQUE NOT NULL,
                    username VARCHAR(255),
                    firstname VARCHAR(255),
                    promo_used BOOLEAN DEFAULT FALSE
                )
                """;

        String createRequestsTable = """
                CREATE TABLE IF NOT EXISTS requests (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    chat_id BIGINT NOT NULL,
                    service VARCHAR(255) NOT NULL,
                    extra TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;

        try (Statement st = conn.createStatement()) {
            st.executeUpdate(createUsersTable);
            st.executeUpdate(createRequestsTable);
            System.out.println("📦 Jadval(lar) tekshirildi va kerak bo‘lsa yaratildi!");
        } catch (SQLException e) {
            System.err.println("⚠️ createTablesIfNotExists() xatolik: " + e.getMessage());
        }
    }

    // 🔹 Foydalanuvchini qo‘shish yoki yangilash
    public static void upsertUser(long chatId, String username, String firstname) {
        String sql = """
                INSERT INTO users (chat_id, username, firstname)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE username=?, firstname=?""";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            ps.setString(2, username);
            ps.setString(3, firstname);
            ps.setString(4, username);
            ps.setString(5, firstname);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("⚠️ upsertUser() xatolik: " + e.getMessage());
        }
    }

    // 🔹 Promo kod ishlatilganmi?
    public static boolean isPromoUsed(long chatId) {
        String sql = "SELECT promo_used FROM users WHERE chat_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBoolean(1);
            }
        } catch (SQLException e) {
            System.err.println("⚠️ isPromoUsed() xatolik: " + e.getMessage());
        }
        return false;
    }

    // 🔹 Promo kodni o‘rnatish
    public static void setPromoUsed(long chatId, boolean used) {
        String sql = "UPDATE users SET promo_used=? WHERE chat_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, used);
            ps.setLong(2, chatId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("⚠️ setPromoUsed() xatolik: " + e.getMessage());
        }
    }

    // 🔹 So‘rov yaratish
    public static void createRequest(long chatId, String service, String extra) {
        String sql = "INSERT INTO requests (chat_id, service, extra) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            ps.setString(2, service);
            ps.setString(3, extra);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("⚠️ createRequest() xatolik: " + e.getMessage());
        }
    }

    // 🔹 Barcha so‘rovlarni olish
    public static List<String> listRequests() {
        List<String> list = new ArrayList<>();
        String sql = "SELECT id, chat_id, service, extra FROM requests ORDER BY id DESC";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(String.format(
                        "ID=%d | ChatId=%d | Service=%s | Extra=%s",
                        rs.getLong("id"),
                        rs.getLong("chat_id"),
                        rs.getString("service"),
                        rs.getString("extra")
                ));
            }
        } catch (SQLException e) {
            System.err.println("⚠️ listRequests() xatolik: " + e.getMessage());
        }
        return list;
    }

    // 🔹 ID orqali so‘rovni o‘chirish
    public static boolean deleteRequestById(long id) {
        String sql = "DELETE FROM requests WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("⚠️ deleteRequestById() xatolik: " + e.getMessage());
        }
        return false;
    }

    // 🔹 ChatId bo‘yicha barcha so‘rovlarni o‘chirish
    public static void deleteRequestsByChatId(long chatId) {
        String sql = "DELETE FROM requests WHERE chat_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, chatId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("⚠️ deleteRequestsByChatId() xatolik: " + e.getMessage());
        }
    }
}
