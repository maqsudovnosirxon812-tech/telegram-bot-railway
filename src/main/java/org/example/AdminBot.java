package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.HashMap;
import java.util.Map;

public class AdminBot extends TelegramLongPollingBot {
    private static final String BOT_TOKEN = System.getenv("ADMIN_BOT_TOKEN");
    private static final String BOT_USERNAME = System.getenv("ADMIN_BOT_USERNAME");
    private static final String ADMIN_CHAT_ID = System.getenv("ADMIN_CHAT_ID");

    private static AdminBot instance; // singleton

    public AdminBot() {
        instance = this;
    }

    public static void notifyAdmin(String text) {
        if (instance != null) {
            instance.sendTextToAdmin(text);
        }
    }

    private final Map<String, Boolean> waitingForType = new HashMap<>();
    private final Map<String, Boolean> waitingForAnswered = new HashMap<>();

    @Override
    public String getBotUsername() { return BOT_USERNAME; }

    @Override
    public String getBotToken() { return BOT_TOKEN; }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) return;
        String chatId = String.valueOf(update.getMessage().getChatId());
        String text = update.getMessage().getText();

        if (text.equals("/start")) {
            sendText(chatId, """
                    Shartlar:
                    /answered → foydalanuvchiga 'ishga tayyor' habarini yuboradi
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
            waitingForType.put(chatId, false);
            waitingForAnswered.put(chatId, false);
            sendText(chatId, "✍️ Endi yubormoqchi bo‘lgan matnni kiriting:");
            waitingForType.put(targetChatId, true); // targetga xabar tayyor
        } else if (waitingForType.containsKey(chatId) && waitingForType.get(chatId)) {
            // admin matn kirityapti
            String targetChatId = waitingForType.keySet().stream()
                    .filter(id -> !id.equals(chatId))
                    .findFirst().orElse(null);
            if (targetChatId != null) {
                ServiceBot.ishtugadiStatic(targetChatId); // service bot orqali yuborish
                sendText(chatId, "☑️ Xabar yuborildi: " + targetChatId);
                waitingForType.put(chatId, false);
            }
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendTextToAdmin(String text) {
        sendText(ADMIN_CHAT_ID, text);
    }
}
