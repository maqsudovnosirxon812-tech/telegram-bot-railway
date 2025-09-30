package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class MainAdmin {
    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new AdminBot());
            System.out.println("✅ AdminBot ishga tushdi!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
