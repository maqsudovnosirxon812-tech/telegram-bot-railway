package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.HashMap;
import java.util.Map;

public class AdminBot extends TelegramLongPollingBot {
    private final ServiceBot serviceBot = new ServiceBot();
    private static final String BOT_TOKEN = "8295381933:AAFgcq71yiksMshiKw11JBc64qE1QAwtOE4";
    private static final String BOT_USERNAME = "answer812_bot";
    private static final String ADMIN_CHAT_ID = "6448561095";

    // admin qaysi userga yozmoqchi ekanini vaqtincha saqlash
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
        Message msg = update.getMessage();
        String chatId = String.valueOf(msg.getChatId());
        String text = msg.getText();

        if (text.equals("/start")) {
            shartlar(chatId);
            return;
        }

        // faqat admin ishlata oladi
        if (!chatId.equals(ADMIN_CHAT_ID)) {
            sendTextt(chatId, "❌ Siz admin emassiz.");
            return;
        }

        // /type → foydalanuvchiga yozish
        if (text.equalsIgnoreCase("/type")) {
            waitingForType.put(chatId, true);
            sendTextt(chatId, "✍️ ChatId yozing (masalan: 1234567890):");
        }
        else if (waitingForType.getOrDefault(chatId, false)) {
            String targetChatId = text; // admin kiritgan chatId
            sendTextt(targetChatId, "✅ Siz tanlagan hizmat tugallandi. Rahmat!");
            sendTextt(chatId, "☑️ Habar yuborildi: " + targetChatId);
            waitingForType.put(chatId, false);
        }

        // /answered → xizmat tugaganini yuborish
        if (text.equalsIgnoreCase("/answered")) {
            waitingForAnswered.put(chatId, true);
            sendTextt(chatId, "✍️ ChatId yozing (masalan: 1234567890):");
        }
        else if (waitingForAnswered.getOrDefault(chatId, false)) {
            String targetChatId = text; // admin kiritgan chatId
            serviceBot.ishtugadi(targetChatId); // endi chatId yuborilyapti
            sendTextt(chatId, "☑️ Habar yuborildi. Endi bemalol uxlashingiz mumkin 😅");
            waitingForAnswered.put(chatId, false);
        }
    }

    private void shartlar(String chatId) {
        sendTextt(chatId, """
                Shartlar:
                /answered → foydalanuvchiga "ishga tayyor" habarini yuboradi
                /type → foydalanuvchiga ixtiyoriy habar yuborish""");
    }

    private void sendTextt(String chatId, String text) {
        try {
            execute(new SendMessage(chatId, text));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendTextToAdmin(String text) {
        try {
            execute(new SendMessage(ADMIN_CHAT_ID, text));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(new AdminBot());
        System.out.println("✅ AdminBot ishga tushdi!");
    }
}
