package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;

public class MainService {
    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

            // Webhookni xavfsiz o‘chirish (agar yo‘q bo‘lsa, jim o‘tadi)
            try {
                new ServiceBot().execute(new DeleteWebhook());
            } catch (Exception ignored) {}

            botsApi.registerBot(new ServiceBot());
            System.out.println("✅ ServiceBot ishga tushdi!");
        } catch (TelegramApiException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("404")) {
                System.out.println("⚠️ Webhook topilmadi, o'tkazib yuborildi (ServiceBot)");
            } else {
                e.printStackTrace();
            }
        }
    }
}
