package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.*;

public class AdminBot extends TelegramLongPollingBot {
    // Token va username — siz berganini saqladim
    private static final String BOT_TOKEN = "8295381933:AAFgcq71yiksMshiKw11JBc64qE1QAwtOE4";
    private static final String BOT_USERNAME = "answer812_bot";

    // Adminlar ro'yxati (chatId string formatida)
    private static final List<String> ADMINS = List.of("6448561095", "5150677380");

    private static AdminBot instance;

    // oddiy admin sessiya (agar kerak bo'lsa kengaytirish mumkin)
    private final Map<String, AdminSession> sessions = new HashMap<>();

    private enum AdminState { IDLE, TYPING_TARGET, TYPING_TEXT, ANSWERING }

    private static class AdminSession {
        AdminState state = AdminState.IDLE;
        String target = null;
    }

    public AdminBot() { instance = this; }

    public static AdminBot getInstance() { return instance; }

    public static List<String> getAdmins() { return ADMINS; }

    @Override
    public String getBotUsername() { return BOT_USERNAME; }

    @Override
    public String getBotToken() { return BOT_TOKEN; }

    private boolean isAdmin(String chatId) { return ADMINS.contains(chatId); }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (!update.hasMessage()) return;
            Message message = update.getMessage();
            String chatId = String.valueOf(message.getChatId());

            // Agar admin bo'lmasa — xabar qaytarish
            if (!isAdmin(chatId)) {
                sendText(chatId, "❌ Siz admin emassiz.");
                return;
            }

            // Adminga foydalanuvchilardan forward qilingan fayllar ham yetib keladi
            if (message.hasDocument() || message.hasPhoto() || message.hasVideo() || message.hasAudio() || message.hasVoice() || message.hasSticker()) {
                // bu yerda admin faylni ko'rib, kerak bo'lsa foydalanuvchiga javob yozishi mumkin
                sendText(chatId, "📎 Fayl qabul qilindi. Agar kerak bo'lsa /type orqali foydalanuvchiga javob yuboring yoki /answered bilan so'rovni yakunlang.");
                return;
            }

            if (!message.hasText()) return;
            String text = message.getText();
            AdminSession session = sessions.computeIfAbsent(chatId, k -> new AdminSession());

            switch (text) {
                case "/start" -> {
                    sendText(chatId, """
                            🛠 <b>Admin menyusi:</b>
                            /show — (NOT IMPLEMENTED) barcha so‘rovlar ro'yxati
                            /type — foydalanuvchiga xabar yuborish
                            /answered — so‘rovni yakunlash (ChatId ni kiriting)
                            """);
                    session.state = AdminState.IDLE;
                }
                case "/type" -> {
                    session.state = AdminState.TYPING_TARGET;
                    sendText(chatId, "✍️ Foydalanuvchi chatId’sini kiriting (masalan: 123456789):");
                }
                case "/answered" -> {
                    session.state = AdminState.ANSWERING;
                    sendText(chatId, "✍️ ChatId kiriting (so'rov tugatish uchun):");
                }
                default -> handleText(chatId, text, session);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleText(String chatId, String text, AdminSession session) {
        switch (session.state) {
            case TYPING_TARGET -> {
                session.target = text;
                session.state = AdminState.TYPING_TEXT;
                sendText(chatId, "✍️ Endi yuboriladigan matnni kiriting:");
            }
            case TYPING_TEXT -> {
                if (session.target != null) {
                    try {
                        // Admindan kelgan xabarni foydalanuvchiga yuboramiz (ServiceBot yordamida)
                        SendMessage msg = new SendMessage(session.target, "📢 <b>Admin javobi:</b>\n" + text);
                        msg.enableHtml(true);
                        new ServiceBot().execute(msg);
                        sendText(chatId, "✅ Xabar yuborildi foydalanuvchiga: " + session.target);
                    } catch (Exception e) {
                        sendText(chatId, "❌ Xabar yuborishda xatolik: " + e.getMessage());
                    }
                }
                session.state = AdminState.IDLE;
                session.target = null;
            }
            case ANSWERING -> {
                // /answered keyin admin ChatId kiritsa — bot foydalanuvchiga yakunlangan xabar yuboradi
                try {
                    long id = Long.parseLong(text);
                    ServiceBot.ishtugadiStatic(String.valueOf(id));
                    sendText(chatId, "☑️ So‘rov yakunlandi (ChatId=" + id + ")");
                } catch (NumberFormatException e) {
                    sendText(chatId, "⚠️ Faqat raqam kiriting!");
                }
                session.state = AdminState.IDLE;
            }
            default -> sendText(chatId, "ℹ️ Noma’lum buyruq. /show, /type yoki /answered dan foydalaning.");
        }
    }

    // Adminlarga xabar yuborish uchun statik yordamchi
    public static void notifyAdmin(String text) {
        if (instance != null) instance.sendTextToAdmins(text);
    }

    public void sendTextToAdmins(String text) {
        for (String adminId : ADMINS) sendText(adminId, text);
    }

    private void sendText(String chatId, String text) {
        try {
            SendMessage msg = new SendMessage(chatId, text);
            msg.enableHtml(true);
            execute(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
