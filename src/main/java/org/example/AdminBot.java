package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.*;

public class AdminBot extends TelegramLongPollingBot {
    // Token va usernameni environment variable orqali oling — hech qachon kodga yozmang.
    private static final String BOT_TOKEN = System.getenv("ADMIN_BOT_TOKEN");
    private static final String BOT_USERNAME = System.getenv("ADMIN_BOT_USERNAME");

    // Adminlar chatId-lari (String formatda). Agar kerak bo'lsa long[] ga o'zgartiring.
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
    public String getBotUsername() { return BOT_USERNAME != null ? BOT_USERNAME : "UNKNOWN_ADMIN_BOT"; }

    @Override
    public String getBotToken() { return BOT_TOKEN; }

    private boolean isAdmin(String chatId) {
        return ADMINS.contains(chatId);
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (!update.hasMessage() || !update.getMessage().hasText()) return;

            String chatId = String.valueOf(update.getMessage().getChatId());
            String text = update.getMessage().getText().trim();

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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleText(String chatId, String text, AdminSession session) {
        try {
            switch (session.state) {
                case TYPING_TARGET -> {
                    session.target = text;
                    session.state = AdminState.TYPING_TEXT;
                    sendText(chatId, "✍️ Endi yuboriladigan matnni kiriting:");
                }

                case TYPING_TEXT -> {
                    if (session.target != null) {
                        try {
                            // Avvalgi yondashuv: new ServiceBot().execute(...) — bu ro'yxatdan o'tmagan bot bo'lishi mumkin.
                            // Bu yerda AdminBot o'zi xabar yuboradi (har qanday chatId ga xabar yuborish mumkin).
                            SendMessage msg = new SendMessage(session.target, "📢 <b>Admin javobi:</b>\n" + text);
                            msg.enableHtml(true);
                            execute(msg);
                            sendText(chatId, "✅ Xabar yuborildi foydalanuvchiga: " + session.target);
                        } catch (Exception e) {
                            sendText(chatId, "❌ Xabar yuborishda xatolik: " + e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        sendText(chatId, "⚠️ Target aniqlanmagan. /type ni qaytadan bajaring.");
                    }
                    session.state = AdminState.IDLE;
                    session.target = null;
                }

                case ANSWERING -> {
                    String trimmed = text.trim();
                    // Kiritilgan raqammi tekshiramiz
                    if (!trimmed.matches("\\d+")) {
                        sendText(chatId, "⚠️ Iltimos, faqat raqam kiriting (so‘rov ID yoki chatId).");
                        session.state = AdminState.IDLE;
                        return;
                    }
                    long id = Long.parseLong(trimmed);

                    boolean deletedByRequestId = false;
                    try {
                        deletedByRequestId = Config.deleteRequestById(id);
                    } catch (Exception ignored) {}

                    if (deletedByRequestId) {
                        sendText(chatId, "☑️ Request ID=" + id + " o‘chirildi.");
                        // Agar servis bot ishlayotgan bo'lsa xabarnoma yuboramiz
                        ServiceBot sb = ServiceBot.getInstance();
                        if (sb != null) {
                            try {
                                sb.execute(new SendMessage(String.valueOf(id), "✅ So‘rovingiz yakunlandi. Ish tayyor!"));
                            } catch (Exception ignored) {}
                        }
                    } else {
                        // agar request id topilmasa, sinab ko'ramiz: bu chatId bo'lishi mumkin
                        try {
                            Config.deleteRequestsByChatId(id);
                            sendText(chatId, "☑️ ChatId=" + id + " bo‘yicha so‘rovlar o‘chirildi.");
                            ServiceBot sb = ServiceBot.getInstance();
                            if (sb != null) {
                                try {
                                    sb.execute(new SendMessage(String.valueOf(id), "✅ So‘rovingiz yakunlandi. Ish tayyor!"));
                                } catch (Exception ignored) {}
                            }
                        } catch (Exception e) {
                            sendText(chatId, "❌ O‘chirishda xatolik: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    session.state = AdminState.IDLE;
                }

                default -> sendText(chatId, "ℹ️ Noma’lum buyruq. /show, /type yoki /answered dan foydalaning.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendText(chatId, "❌ Ichki xato yuz berdi: " + e.getMessage());
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

    protected void sendText(String chatId, String text) {
        try {
            SendMessage msg = new SendMessage(chatId, text);
            msg.enableHtml(true);
            execute(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
