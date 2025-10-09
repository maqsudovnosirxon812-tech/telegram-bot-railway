package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.*;

public class AdminBot extends TelegramLongPollingBot {
    private static final String BOT_TOKEN = "8295381933:AAFgcq71yiksMshiKw11JBc64qE1QAwtOE4";
    private static final String BOT_USERNAME = "answer812_bot";

    // 🔹 Bir nechta adminlarni shu ro‘yxatga qo‘shish mumkin
    private static final List<String> ADMINS = List.of("6448561095", "5150677380");

    private static AdminBot instance;
    private final Map<String, AdminState> adminState = new HashMap<>();
    private final Map<String, String> stateTargets = new HashMap<>();

    private enum AdminState { IDLE, TYPING_TARGET, TYPING_TEXT, ANSWERING }

    public AdminBot() { instance = this; }

    public static void notifyAdmin(String text) {
        if (instance != null) instance.sendTextToAdmins(text);
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

        switch (text) {
            case "/start" -> {
                sendText(chatId, """
                        🛠 Admin buyruqlari:
                        /show — barcha so'rovlarni ko'rish
                        /type — foydalanuvchiga xabar yuborish
                        /answered — foydalanuvchining so‘rovini yakunlash""");
                adminState.put(chatId, AdminState.IDLE);
            }

            case "/show" -> {
                List<String> rows = Config.listRequests();
                if (rows.isEmpty()) sendText(chatId, "📭 Ma'lumot yo‘q.");
                else {
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
            }

            case "/type" -> {
                adminState.put(chatId, AdminState.TYPING_TARGET);
                sendText(chatId, "✍️ ChatId kiriting (masalan: 1234567890)");
            }

            case "/answered" -> {
                adminState.put(chatId, AdminState.ANSWERING);
                sendText(chatId, "✍️ ChatId yoki request ID kiriting:");
            }

            default -> handleText(chatId, text);
        }
    }

    private void handleText(String chatId, String text) {
        AdminState state = adminState.getOrDefault(chatId, AdminState.IDLE);

        switch (state) {
            case TYPING_TARGET -> {
                stateTargets.put(chatId, text);
                adminState.put(chatId, AdminState.TYPING_TEXT);
                sendText(chatId, "✍️ Endi yubormoqchi bo‘lgan matnni kiriting:");
            }

            case TYPING_TEXT -> {
                String target = stateTargets.remove(chatId);
                if (target != null) {
                    try {
                        SendMessage msg = new SendMessage();
                        msg.setChatId(target);
                        msg.setText("📢 *Admindan xabar:*\n" + text);
                        new ServiceBot().execute(msg);
                        sendText(chatId, "✅ Xabar yuborildi: " + target);
                    } catch (Exception e) {
                        sendText(chatId, "❌ Xabar yuborishda xatolik!");
                        e.printStackTrace();
                    }
                }
                adminState.put(chatId, AdminState.IDLE);
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
                } else sendText(chatId, "⚠️ Noto‘g‘ri format! Raqam kiriting.");
                adminState.put(chatId, AdminState.IDLE);
            }

            default -> sendText(chatId, "Noma'lum buyruq. /show, /type yoki /answered ni sinab ko‘ring.");
        }
    }

    private void sendText(String chatId, String text) {
        try { execute(new SendMessage(chatId, text)); } catch (Exception e) { e.printStackTrace(); }
    }

    public void sendTextToAdmins(String text) {
        for (String adminId : ADMINS) {
            sendText(adminId, text);
        }
    }
}
