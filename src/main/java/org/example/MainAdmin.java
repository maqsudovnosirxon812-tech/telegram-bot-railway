package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;

public class MainAdmin {
    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

            // Webhookni xavfsiz o‘chirish (404 chiqsa jim)
            try {
                new AdminBot().execute(new DeleteWebhook());
            } catch (Exception ignored) {}

            botsApi.registerBot(new AdminBot());
            System.out.println("✅ AdminBot ishga tushdi!");
        } catch (TelegramApiException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("404")) {
                System.out.println("⚠️ Webhook topilmadi, o'tkazib yuborildi (AdminBot)");
            } else {
                e.printStackTrace();
            }
        }
    }
}
