package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class MainService {
    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new ServiceBot());
            System.out.println("✅ ServiceBot ishga tushdi!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
