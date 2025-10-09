package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.*;

public class AdminBot extends TelegramLongPollingBot {
    private static final String BOT_TOKEN = "8295381933:AAFgcq71yiksMshiKw11JBc64qE1QAwtOE4";
    private static final String BOT_USERNAME = "answer812_bot";
    private static final List<String> ADMINS = Arrays.asList("6448561095", "5150677380");

    private static final Map<String, AdminState> adminState = new HashMap<>();
    private static final Map<String, String> stateTargets = new HashMap<>();

    enum AdminState { IDLE, TYPING_TEXT }

    @Override
    public String getBotUsername() { return BOT_USERNAME; }

    @Override
    public String getBotToken() { return BOT_TOKEN; }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        String chatId = String.valueOf(update.getMessage().getChatId());
        String text = update.getMessage().getText().trim();

        if (!ADMINS.contains(chatId)) return;

        AdminState state = adminState.getOrDefault(chatId, AdminState.IDLE);

        if (state == AdminState.TYPING_TEXT) {
            String target = stateTargets.remove(chatId);
            if (target != null) {
                try {
                    SendMessage msg = new SendMessage();
                    msg.setChatId(target);
                    msg.setText("📢 *Admindan xabar:*\n" + text);
                    new ServiceBot().execute(msg);
                    sendText(chatId, "✅ Xabar yuborildi foydalanuvchiga: " + target);
                } catch (Exception e) {
                    sendText(chatId, "❌ Xabar yuborishda xatolik!");
                }
            }
            adminState.put(chatId, AdminState.IDLE);
            return;
        }

        if (text.startsWith("r")) {
            String target = text.substring(1);
            stateTargets.put(chatId, target);
            adminState.put(chatId, AdminState.TYPING_TEXT);
            sendText(chatId, "✍️ Endi foydalanuvchiga yuboriladigan xabarni yozing:");
            return;
        }

        sendText(chatId, "⚙ Buyruq mavjud emas. Foydalanuvchi `ChatId` ni kiriting (masalan: r12345678).");
    }

    public static void notifyAdmin(String text) {
        for (String adminId : ADMINS) {
            try {
                new AdminBot().execute(new SendMessage(adminId, text));
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void sendText(String chatId, String text) {
        try { execute(new SendMessage(chatId, text)); } catch (Exception e) { e.printStackTrace(); }
    }
}
