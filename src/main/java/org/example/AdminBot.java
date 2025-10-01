package org.example;

import io.github.cdimascio.dotenv.Dotenv;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.HashMap;
import java.util.Map;

public class AdminBot extends TelegramLongPollingBot {

    private static final Dotenv dotenv = Dotenv.load();
    private static AdminBot instance;

    private static final String BOT_TOKEN = dotenv.get("ADMIN_BOT_TOKEN");
    private static final String BOT_USERNAME = dotenv.get("ADMIN_BOT_USERNAME");
    private static final String ADMIN_CHAT_ID = dotenv.get("ADMIN_CHAT_ID");

    private final Map<String, Boolean> waitingForType = new HashMap<>();
    private final Map<String, String> typeTargetChat = new HashMap<>();
    private final Map<String, Boolean> waitingForAnswered = new HashMap<>();

    public AdminBot() {
        instance = this;
    }

    public static void notifyAdmin(String text) {
        if (instance != null) {
            instance.sendTextToAdmin(text);
        }
    }

    @Override
    public String getBotUsername() { return BOT_USERNAME; }

    @Override
    public String getBotToken() { return BOT_TOKEN; }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        String chatId = String.valueOf(update.getMessage().getChatId());
        String text = update.getMessage().getText();

        if (!chatId.equals(ADMIN_CHAT_ID)) {
            sendText(chatId, "❌ Siz admin emassiz.");
            return;
        }

        // --- /type logikasi ---
        if (text.equalsIgnoreCase("/type")) {
            waitingForType.put(chatId, true);
            sendText(chatId, "✍️ ChatId yozing (masalan: 1234567890):");
            return;
        }

        if (waitingForType.getOrDefault(chatId, false) && !typeTargetChat.containsKey(chatId)) {
            typeTargetChat.put(chatId, text);
            sendText(chatId, "✍️ Endi yuboriladigan matnni kiriting:");
            return;
        }

        if (typeTargetChat.containsKey(chatId)) {
            String targetChatId = typeTargetChat.get(chatId);
            ServiceBot.ishtugadiStatic(targetChatId, text);
            sendText(chatId, "☑️ Xabar yuborildi: " + targetChatId);
            typeTargetChat.remove(chatId);
            waitingForType.put(chatId, false);
            return;
        }

        // --- /answered logikasi ---
        if (text.equalsIgnoreCase("/answered")) {
            waitingForAnswered.put(chatId, true);
            sendText(chatId, "✍️ ChatId yozing (masalan: 1234567890):");
            return;
        }

        if (waitingForAnswered.getOrDefault(chatId, false)) {
            String targetChatId = text;
            ServiceBot.ishtugadiStatic(targetChatId);
            sendText(chatId, "☑️ Xabar yuborildi. Endi bemalol uxlashingiz mumkin 😅");
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
}
