package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.*;

public class AdminBot extends TelegramLongPollingBot {
    // ⚙️ Token va username to‘g‘ridan-to‘g‘ri kod ichida
    private static final String BOT_TOKEN = "8295381933:AAFgcq71yiksMshiKw11JBc64qE1QAwtOE4";
    private static final String BOT_USERNAME = "answer812_bot";

    // 👑 Adminlar ro‘yxati
    private static final List<String> ADMINS = List.of("6448561095", "5150677380");

    private static AdminBot instance;
    private final Map<String, AdminSession> sessions = new HashMap<>();

    private enum AdminState { IDLE, TYPING_TARGET, TYPING_TEXT, ANSWERING }

    private static class AdminSession {
        AdminState state = AdminState.IDLE;
        String target = null;
    }

    public AdminBot() {
        instance = this;
    }

    public static AdminBot getInstance() {
        return instance;
    }

    @Override
    public String getBotUsername() { return BOT_USERNAME; }

    @Override
    public String getBotToken() { return BOT_TOKEN; }

    private boolean isAdmin(String chatId) {
        return ADMINS.contains(chatId);
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        String chatId = String.valueOf(update.getMessage().getChatId());
        String text = update.getMessage().getText();

        if (!isAdmin(chatId)) {
            sendText(chatId, "❌ Siz admin emassiz.");
            return;
        }

        AdminSession session = sessions.computeIfAbsent(chatId, k -> new AdminSession());

        switch (text) {
            case "/start" -> {
                sendText(chatId, """
                        🛠 <b>Admin menyusi:</b>
                        /show — barcha so‘rovlarni ko‘rish
                        /type — foydalanuvchiga xabar yuborish
                        /answered — so‘rovni yakunlash
                        """);
                session.state = AdminState.IDLE;
            }

            case "/show" -> {
                List<String> rows = Config.listRequests();
                if (rows.isEmpty()) {
                    sendText(chatId, "📭 So‘rovlar mavjud emas.");
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (String r : rows) {
                        if (sb.length() + r.length() > 3500) {
                            sendText(chatId, sb.toString());
                            sb.setLength(0);
                        }
                        sb.append(r).append("\n\n");
                    }
                    if (sb.length() > 0) sendText(chatId, sb.toString());
                }
                session.state = AdminState.IDLE;
            }

            case "/type" -> {
                session.state = AdminState.TYPING_TARGET;
                sendText(chatId, "✍️ Foydalanuvchi chatId’sini kiriting (masalan: <code>123456789</code>)");
            }

            case "/answered" -> {
                session.state = AdminState.ANSWERING;
                sendText(chatId, "✍️ ChatId yoki so‘rov ID kiriting:");
            }

            default -> handleText(chatId, text, session);
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
                if (text.matches("\\d+")) {
                    long id = Long.parseLong(text);
                    boolean deleted = Config.deleteRequestById(id);
                    if (deleted) {
                        sendText(chatId, "☑️ Request ID=" + id + " o‘chirildi.");
                        ServiceBot.ishtugadiStatic(String.valueOf(id));
                    } else {
                        Config.deleteRequestsByChatId(id);
                        ServiceBot.ishtugadiStatic(String.valueOf(id));
                        sendText(chatId, "☑️ ChatId=" + id + " bo‘yicha so‘rovlar o‘chirildi.");
                    }
                } else {
                    sendText(chatId, "⚠️ Faqat raqam kiriting!");
                }
                session.state = AdminState.IDLE;
            }

            default -> sendText(chatId, "ℹ️ Noma’lum buyruq. /show, /type yoki /answered dan foydalaning.");
        }
    }

    public static void notifyAdmin(String text) {
        if (instance != null) instance.sendTextToAdmins(text);
    }

    public void sendTextToAdmins(String text) {
        for (String adminId : ADMINS) {
            sendText(adminId, text);
        }
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
