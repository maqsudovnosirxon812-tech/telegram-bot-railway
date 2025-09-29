package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) throws Exception {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

        // ikkala botni ro‘yxatdan o‘tkazamiz
        botsApi.registerBot(new ServiceBot());
        botsApi.registerBot(new AdminBot());

        System.out.println("✅ Ikkita bot parallel ishlayapti!");
    }
}
