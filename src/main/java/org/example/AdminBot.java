package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.HashMap;
import java.util.Map;

public class AdminBot extends TelegramLongPollingBot {

    private static AdminBot instance; // singleton

    public AdminBot() {
        instance = this;
    }

    public static void notifyAdmin(String text) {
        if (instance != null) {
            instance.sendTextToAdmin(text);
        }
    }

    private static final String BOT_TOKEN = "8295381933:AAFgcq71yiksMshiKw11JBc64qE1QAwtOE4";
    private static final String BOT_USERNAME = "answer812_bot";
    private static final String ADMIN_CHAT_ID = "6448561095";

    private final Map<String, Boolean> waitingForType = new HashMap<>();
    private final Map<String, Boolean> waitingForAnswered = new HashMap<>();

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) return;
        String chatId = String.valueOf(update.getMessage().getChatId());
        String text = update.getMessage().getText();

        if (text.equals("/start")) {
            sendText(chatId, """
                    Shartlar:
                    /answered → foydalanuvchiga "ishga tayyor" habarini yuboradi
                    /type → foydalanuvchiga ixtiyoriy habar yuborish""");
            return;
        }

        if (!chatId.equals(ADMIN_CHAT_ID)) {
            sendText(chatId, "❌ Siz admin emassiz.");
            return;
        }

        if (text.equalsIgnoreCase("/type")) {
            waitingForType.put(chatId, true);
            sendText(chatId, "✍️ ChatId yozing (masalan: 1234567890):");
        } else if (waitingForType.getOrDefault(chatId, false)) {
            String targetChatId = text;
            sendText(targetChatId, "✅ Sizga xabar yuborildi!");
            sendText(chatId, "☑️ Habar yuborildi: " + targetChatId);
            waitingForType.put(chatId, false);
        }

        if (text.equalsIgnoreCase("/answered")) {
            waitingForAnswered.put(chatId, true);
            sendText(chatId, "✍️ ChatId yozing (masalan: 1234567890):");
        } else if (waitingForAnswered.getOrDefault(chatId, false)) {
            String targetChatId = text;
            ServiceBot.ishtugadiStatic(targetChatId); // static orqali yuborish
            sendText(chatId, "☑️ Habar yuborildi. Endi bemalol uxlashingiz mumkin 😅");
            waitingForAnswered.put(chatId, false);
        }
    }

    public void sendText(String chatId, String text) {
        try {
            execute(new SendMessage(chatId, text));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void sendTextToAdmin(String text) {
        sendText(ADMIN_CHAT_ID, text);
    }

    public static void main(String[] args) throws Exception {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(new AdminBot());
        System.out.println("✅ AdminBot ishga tushdi!");
    }
}
