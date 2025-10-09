package org.example;

public class Main {
    public static void main(String[] args) {
        // Health server (Railway uchun zarur)
        HealthServer.start();

        // ServiceBot (User bot)
        Thread userBotThread = new Thread(() -> {
            try {
                MainService.main(args);
            } catch (Exception e) {
                System.err.println("❌ Xatolik ServiceBot’da: " + e.getMessage());
            }
        }, "ServiceBot-Thread");

        // AdminBot
        Thread adminBotThread = new Thread(() -> {
            try {
                MainAdmin.main(args);
            } catch (Exception e) {
                System.err.println("❌ Xatolik AdminBot’da: " + e.getMessage());
            }
        }, "AdminBot-Thread");

        // Threadlarni ishga tushiramiz
        userBotThread.start();
        adminBotThread.start();

        System.out.println("✅ Both bots started successfully!");
    }
}
