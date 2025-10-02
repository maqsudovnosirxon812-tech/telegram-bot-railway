package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminBot extends TelegramLongPollingBot {
    private static final String BOT_TOKEN = Config.get("ADMIN_BOT_TOKEN");
    private static final String BOT_USERNAME = Config.get("ADMIN_BOT_USERNAME");
    private static final String ADMIN_CHAT_ID = Config.get("ADMIN_CHAT_ID");

    private static AdminBot instance;
    private final Map<String, AdminState> adminState = new HashMap<>();
    private final Map<String, String> stateTargets = new HashMap<>();

    private enum AdminState { IDLE, TYPING_TARGET, TYPING_TEXT, ANSWERING }

    public AdminBot() { instance = this; }

    public static void notifyAdmin(String text) {
        if (instance != null) instance.sendTextToAdmin(text);
    }

    @Override public String getBotUsername() { return BOT_USERNAME; }
    @Override public String getBotToken() { return BOT_TOKEN; }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) return;
        String chatId = String.valueOf(update.getMessage().getChatId());
        String text = update.getMessage().getText();

        if (text.equals("/start")) {
            sendText(chatId, """
                    Shartlar:
                    /answered → foydalanuvchiga 'ishga tayyor' habarini yuboradi va DB dan o'chiradi
                    /type → foydalanuvchiga ixtiyoriy habar yuborish
                    /show → DB dagi barcha so'rovlarni ko'rsatish""");
            adminState.put(chatId, AdminState.IDLE);
            return;
        }

        if (!chatId.equals(ADMIN_CHAT_ID)) {
            sendText(chatId, "❌ Siz admin emassiz.");
            return;
        }

        switch (text.split(" ")[0].toLowerCase()) {
            case "/type" -> {
                adminState.put(chatId, AdminState.TYPING_TARGET);
                sendText(chatId, "✍️ ChatId yozing (masalan: 1234567890) — keyin yubormoqchi bo‘lgan matnni kiriting.");
            }
            case "/answered" -> {
                adminState.put(chatId, AdminState.ANSWERING);
                sendText(chatId, "✍️ ChatId yoki request id yozing:");
            }
            case "/show" -> {
                List<String> rows = Config.listRequests();
                if (rows.isEmpty()) sendText(chatId, "Ma'lumot yo'q.");
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
            default -> {
                AdminState state = adminState.getOrDefault(chatId, AdminState.IDLE);
                if (state == AdminState.TYPING_TARGET) {
                    stateTargets.put(chatId, text);
                    adminState.put(chatId, AdminState.TYPING_TEXT);
                    sendText(chatId, "✍️ Endi yubormoqchi bo‘lgan matnni kiriting:");
                } else if (state == AdminState.TYPING_TEXT) {
                    String targetChat = stateTargets.remove(chatId);
                    if (targetChat != null) {
                        try {
                            new ServiceBot().execute(new SendMessage(targetChat, text));
                            sendText(chatId, "☑️ Xabar yuborildi: " + targetChat);
                        } catch (Exception e) { e.printStackTrace(); }
                    }
                    adminState.put(chatId, AdminState.IDLE);
                } else if (state == AdminState.ANSWERING) {
                    String param = text.trim();
                    if (param.matches("\\d+")) {
                        long id = Long.parseLong(param);
                        boolean deleted = Config.deleteRequestById(id);
                        if (deleted) {
                            ServiceBot.ishtugadiStatic(String.valueOf(id));
                            sendText(chatId, "☑️ Request id=" + id + " o'chirildi va foydalanuvchiga habar yuborildi.");
                        } else {
                            Config.deleteRequestsByChatId(id);
                            ServiceBot.ishtugadiStatic(String.valueOf(id));
                            sendText(chatId, "☑️ ChatId=" + id + " uchun barcha so‘rovlar o‘chirildi va habar yuborildi.");
                        }
                    } else {
                        sendText(chatId, "Xato: faqat raqamli chatId yoki request id kiriting.");
                    }
                    adminState.put(chatId, AdminState.IDLE);
                } else {
                    sendText(chatId, "Noma'lum buyruq. /show, /type yoki /answered bering.");
                }
            }
        }
    }

    public void sendText(String chatId, String text) {
        try { execute(new SendMessage(chatId, text)); }
        catch (Exception e) { e.printStackTrace(); }
    }

    public void sendTextToAdmin(String text) {
        sendText(ADMIN_CHAT_ID, text);
    }
}
