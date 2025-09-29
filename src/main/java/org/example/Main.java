package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

            ServiceBot serviceBot = new ServiceBot();
            AdminBot adminBot = new AdminBot();

            botsApi.registerBot(serviceBot);
            botsApi.registerBot(adminBot);

            System.out.println("✅ Ikkita bot parallel ishlayapti!");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ Botlarni ro'yxatga olishda xatolik yuz berdi.");
        }
    }
}
