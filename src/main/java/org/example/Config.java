package org.example;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Config {
    // 🔑 DB connection ma'lumotlari (MariaDB)
    private static final String DB_URL  = "jdbc:mariadb://localhost:3306/your_db_name";
    private static final String DB_USER = "your_username";
    private static final String DB_PASS = "your_password";

    private static Connection conn;

    static {
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            System.out.println("✅ Database connected (MariaDB)!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 👤 User qo‘shish yoki yangilash (UPsert)
    public static void upsertUser(long chatId, String username, String firstname) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (chat_id, username, firstname) " +
                            "VALUES (?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE username=?, firstname=?"
            );
            ps.setLong(1, chatId);
            ps.setString(2, username);
            ps.setString(3, firstname);
            ps.setString(4, username);
            ps.setString(5, firstname);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // 🎁 Promo tekshirish
    public static boolean isPromoUsed(long chatId) {
        try {
            PreparedStatement ps = conn.prepareStatement("SELECT promo_used FROM users WHERE chat_id=?");
            ps.setLong(1, chatId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBoolean(1);
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    // 🎁 Promo status o‘zgartirish
    public static void setPromoUsed(long chatId, boolean used) {
        try {
            PreparedStatement ps = conn.prepareStatement("UPDATE users SET promo_used=? WHERE chat_id=?");
            ps.setBoolean(1, used);
            ps.setLong(2, chatId);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // 📝 Request yaratish
    public static void createRequest(long chatId, String service, String extra) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO requests (chat_id, service, extra) VALUES (?, ?, ?)"
            );
            ps.setLong(1, chatId);
            ps.setString(2, service);
            ps.setString(3, extra);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // 📋 Barcha requestlarni olish
    public static List<String> listRequests() {
        List<String> list = new ArrayList<>();
        try {
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SELECT id, chat_id, service, extra FROM requests ORDER BY id DESC");
            while (rs.next()) {
                list.add("ID=" + rs.getLong(1) + " | ChatId=" + rs.getLong(2) +
                        " | Service=" + rs.getString(3) +
                        " | Extra=" + rs.getString(4));
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    // ❌ Request ID bo‘yicha o‘chirish
    public static boolean deleteRequestById(long id) {
        try {
            PreparedStatement ps = conn.prepareStatement("DELETE FROM requests WHERE id=?");
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    // ❌ ChatId bo‘yicha barcha requestlarni o‘chirish
    public static void deleteRequestsByChatId(long chatId) {
        try {
            PreparedStatement ps = conn.prepareStatement("DELETE FROM requests WHERE chat_id=?");
            ps.setLong(1, chatId);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }
}
