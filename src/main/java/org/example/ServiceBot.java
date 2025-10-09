package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.*;

public class ServiceBot extends TelegramLongPollingBot {
    // Token va usernameni environment variable orqali oling
    private static final String BOT_TOKEN = System.getenv("SERVICE_BOT_TOKEN");
    private static final String BOT_USERNAME = System.getenv("SERVICE_BOT_USERNAME");

    private static final String DEFAULT_PROMO = "SYNOPSIS_2026";
    private static final String BACK_TO_MAIN = "⬅ Bosh menuga qaytish";

    // singleton instance (AdminBot kabi)
    private static ServiceBot instance;

    // state maps
    private final Map<String, String> selectedService = new HashMap<>();
    private final Map<String, String> tempAnswers = new HashMap<>();
    private final Map<String, Integer> pageCount = new HashMap<>();
    private final Map<String, Boolean> chattingWithAdmin = new HashMap<>();

    public ServiceBot() {
        instance = this;
    }

    public static ServiceBot getInstance() {
        return instance;
    }

    @Override
    public String getBotUsername() { return BOT_USERNAME != null ? BOT_USERNAME : "UNKNOWN_SERVICE_BOT"; }

    @Override
    public String getBotToken() { return BOT_TOKEN; }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
                return;
            }

            if (!update.hasMessage()) return;
            Message msg = update.getMessage();
            if (!msg.hasText()) return;

            String text = msg.getText().trim();
            String chatId = String.valueOf(msg.getChatId());
            User from = msg.getFrom();
            String username = (from.getUserName() != null ? "@" + from.getUserName() : from.getFirstName());

            // ensure user recorded in DB (if you have such logic)
            try { Config.upsertUser(msg.getChatId(), from.getUserName(), from.getFirstName()); } catch (Exception ignored) {}

            // Admin-chat mode
            if (chattingWithAdmin.getOrDefault(chatId, false)) {
                if (text.equalsIgnoreCase(BACK_TO_MAIN)) {
                    chattingWithAdmin.put(chatId, false);
                    handleStart(chatId, from);
                    return;
                }
                // forward message to admin
                String msgToAdmin = "📩 *Foydalanuvchidan xabar:*\n"
                        + "👤 Ism: " + from.getFirstName() + "\n"
                        + "🔗 Username: " + username + "\n"
                        + "💬 ChatId: " + chatId + "\n"
                        + "📝 Xabar: " + text;
                AdminBot.notifyAdmin(msgToAdmin);
                sendText(chatId, "✅ Xabaringiz adminga yuborildi.");
                return;
            }

            // if user currently selected a service and we expect further text input
            if (selectedService.containsKey(chatId)) {
                String svc = selectedService.get(chatId);
                switch (svc) {
                    case "Konspekt yozish" -> {
                        // when user selected konspekt, we handle entirely with inline buttons.
                        if (!pageCount.containsKey(chatId)) {
                            pageCount.put(chatId, 2);
                            sendKonspektInline(chatId);
                            return;
                        } else {
                            sendText(chatId, "📘 Betlar sonini inline tugmalar orqali belgilang yoki /start bosing.");
                            return;
                        }
                    }
                    case "Uyga vazifa" -> {
                        if (!tempAnswers.containsKey(chatId)) {
                            tempAnswers.put(chatId, text); // fan
                            sendText(chatId, "✍️ Endi mavzuni kiriting:");
                            return;
                        } else {
                            String fan = tempAnswers.remove(chatId);
                            String mavzu = text;
                            // create request and notify admin
                            try { Config.createRequest(Long.parseLong(chatId), "Uyga vazifa", fan + " | " + mavzu); } catch (Exception ignored) {}
                            String msgToAdmin = "📚 Uyga vazifa\n"
                                    + "👤 Ism: " + from.getFirstName() + "\n"
                                    + "🔗 Username: " + username + "\n"
                                    + "💬 ChatId: " + chatId + "\n"
                                    + "📘 Fan: " + fan + "\n"
                                    + "🧾 Mavzu: " + mavzu;
                            AdminBot.notifyAdmin(msgToAdmin);
                            sendText(chatId, "✅ Uyga vazifa yuborildi.\nAdminga xabar berildi.\n👤 Username: " + username);
                            selectedService.remove(chatId);
                            return;
                        }
                    }
                    case "Loyha ishlari" -> {
                        // single message is the project description
                        try { Config.createRequest(Long.parseLong(chatId), "Loyha ishlari", text); } catch (Exception ignored) {}
                        String msgToAdmin = "🧩 Loyha ishlari\n"
                                + "👤 Ism: " + from.getFirstName() + "\n"
                                + "🔗 Username: " + username + "\n"
                                + "💬 ChatId: " + chatId + "\n"
                                + "📄 Tavsif: " + text;
                        AdminBot.notifyAdmin(msgToAdmin);
                        sendText(chatId, "✅ Loyha ma’lumoti yuborildi.\n👤 Username: " + username);
                        selectedService.remove(chatId);
                        return;
                    }
                    case "Slayd yasab berish" -> {
                        if (!tempAnswers.containsKey(chatId)) {
                            tempAnswers.put(chatId, text); // topic
                            pageCount.put(chatId, 2);
                            sendSlidesInline(chatId, text);
                            return;
                        } else {
                            sendText(chatId, "🎞 Slayd holati: inline tugmalar orqali slayd sonini belgilang yoki /start bosing.");
                            return;
                        }
                    }
                }
            }

            // Main command handling
            switch (text) {
                case "/start" -> handleStart(chatId, from);
                case "Promo Code" -> sendText(chatId, "🔑 Iltimos, promo kodni kiriting:");
                case "Hizmatlar" -> sendServicesMenu(chatId);
                case "Profile" -> showProfile(chatId, from);
                case "📩 Adminga yozish" -> {
                    chattingWithAdmin.put(chatId, true);
                    sendTextWithKeyboard(chatId, "✍️ Xabaringizni yozing. Adminga yuboriladi.\n\n" + BACK_TO_MAIN, backKeyboard());
                }
                case BACK_TO_MAIN -> handleStart(chatId, from);
                default -> {
                    // promo code match or unknown
                    if (text.equalsIgnoreCase(DEFAULT_PROMO)) {
                        try { Config.setPromoUsed(Long.parseLong(chatId), true); } catch (Exception ignored) {}
                        sendText(chatId, "✅ Promo kod qabul qilindi! Adminga habar yuborildi.");
                        AdminBot.notifyAdmin("📩 Promo ishlatildi!\nFoydalanuvchi: " + from.getFirstName() + " id=" + chatId);
                    } else if (text.equalsIgnoreCase("Konspekt yozish")) {
                        selectedService.put(chatId, "Konspekt yozish");
                        pageCount.put(chatId, 2);
                        sendText(chatId, "📘 Siz Konspekt yozish xizmatini tanladingiz.\n👤 Username: " + username);
                        sendKonspektInline(chatId);
                    } else if (text.equalsIgnoreCase("Uyga vazifa")) {
                        selectedService.put(chatId, "Uyga vazifa");
                        tempAnswers.remove(chatId);
                        sendText(chatId, "✍️ Qaysi fan uchun uyga vazifa kerak?\n👤 Username: " + username);
                    } else if (text.equalsIgnoreCase("Loyha ishlari")) {
                        selectedService.put(chatId, "Loyha ishlari");
                        sendText(chatId, "🧩 Loyha haqida qisqacha yozing.\n👤 Username: " + username);
                    } else if (text.equalsIgnoreCase("Slayd yasab berish")) {
                        selectedService.put(chatId, "Slayd yasab berish");
                        tempAnswers.remove(chatId);
                        sendText(chatId, "📑 Qaysi mavzu uchun slayd kerak?\n👤 Username: " + username);
                    } else {
                        sendText(chatId, "❌ Men bu buyruqni tushunmadim. Menudan tanlang yoki /start bosing.");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==== CALLBACK HANDLER ====
    private void handleCallback(CallbackQuery cq) {
        try {
            String data = cq.getData();
            String chatId = String.valueOf(cq.getMessage().getChatId());
            Integer messageId = cq.getMessage().getMessageId();
            int current = pageCount.getOrDefault(chatId, 2);
            User user = cq.getFrom();
            String firstName = user.getFirstName();
            String username = (user.getUserName() != null ? "@" + user.getUserName() : "—");

            if (data.equals("inc")) {
                current += 2;
                pageCount.put(chatId, current);
                editInlineCount(chatId, messageId, current);
            } else if (data.equals("dec")) {
                if (current > 2) current -= 2;
                pageCount.put(chatId, current);
                editInlineCount(chatId, messageId, current);
            } else if (data.equals("confirm_konspekt")) {
                String service = "Konspekt yozish";
                int pages = pageCount.getOrDefault(chatId, 2);
                try { Config.createRequest(Long.parseLong(chatId), service, "Betlar: " + pages); } catch (Exception ignored) {}

                String msgToAdmin = "📘 *Konspekt so‘rovi*\n"
                        + "👤 Ism: " + firstName + "\n"
                        + "🔗 Username: " + username + "\n"
                        + "💬 ChatId: " + chatId + "\n"
                        + "📄 Betlar: " + pages;
                AdminBot.notifyAdmin(msgToAdmin);

                sendText(chatId, "✅ Konspekt uchun so‘rovingiz adminga yuborildi!");
                clearState(chatId);
            } else if (data.equals("confirm_slides")) {
                String topic = tempAnswers.getOrDefault(chatId, "Mavzu");
                int slides = pageCount.getOrDefault(chatId, 2);
                try { Config.createRequest(Long.parseLong(chatId), "Slayd yasab berish", topic + " | Slaydlar: " + slides); } catch (Exception ignored) {}

                String msgToAdmin = "🎞 *Slayd so‘rovi*\n"
                        + "👤 Ism: " + firstName + "\n"
                        + "🔗 Username: " + username + "\n"
                        + "💬 ChatId: " + chatId + "\n"
                        + "🧾 Mavzu: " + topic + "\n"
                        + "📊 Slaydlar: " + slides;
                AdminBot.notifyAdmin(msgToAdmin);

                sendText(chatId, "✅ Slaydlar bo‘yicha so‘rovingiz yuborildi.");
                clearState(chatId);
            }

            AnswerCallbackQuery ack = new AnswerCallbackQuery();
            ack.setCallbackQueryId(cq.getId());
            execute(ack);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==== INLINE MARKUP / SEND / EDIT ====
    private InlineKeyboardMarkup buildInlineMarkup(int current) {
        InlineKeyboardButton minus = new InlineKeyboardButton();
        minus.setText("-2");
        minus.setCallbackData("dec");

        InlineKeyboardButton plus = new InlineKeyboardButton();
        plus.setText("+2");
        plus.setCallbackData("inc");

        InlineKeyboardButton confirmK = new InlineKeyboardButton();
        confirmK.setText("Tasdiqlash (Konspekt)");
        confirmK.setCallbackData("confirm_konspekt");

        InlineKeyboardButton confirmS = new InlineKeyboardButton();
        confirmS.setText("Tasdiqlash (Slayd)");
        confirmS.setCallbackData("confirm_slides");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(Arrays.asList(minus, plus));
        rows.add(Arrays.asList(confirmK, confirmS));
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    private void editInlineCount(String chatId, int messageId, int current) {
        try {
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId);
            edit.setMessageId(messageId);
            edit.setText("📄 Betlar soni: " + current);
            edit.setReplyMarkup(buildInlineMarkup(current));
            execute(edit);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendKonspektInline(String chatId) {
        int current = pageCount.getOrDefault(chatId, 2);
        SendMessage sm = new SendMessage(chatId, "📘 Nechta bet kerak? (juft sonlarda ishlaydi)");
        sm.setReplyMarkup(buildInlineMarkup(current));
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendSlidesInline(String chatId, String topic) {
        int current = pageCount.getOrDefault(chatId, 2);
        SendMessage sm = new SendMessage(chatId, "🎞 Mavzu: " + topic + "\nNechta slayd kerak?");
        sm.setReplyMarkup(buildInlineMarkup(current));
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    // ==== HELPERS ====
    public static void ishtugadiStatic(String chatId) {
        ServiceBot sb = ServiceBot.getInstance();
        if (sb != null) {
            try {
                sb.execute(new SendMessage(chatId, "✅ So‘rovingiz yakunlandi. Ish tayyor!"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Agar service bot ro'yxatdan o'tmagan bo'lsa, AdminBot orqali xabar yuborish mumkin
            AdminBot ab = AdminBot.getInstance();
            if (ab != null) {
                try {
                    ab.execute(new SendMessage(chatId, "✅ So‘rovingiz yakunlandi. Ish tayyor!"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void clearState(String chatId) {
        selectedService.remove(chatId);
        tempAnswers.remove(chatId);
        pageCount.remove(chatId);
        chattingWithAdmin.remove(chatId);
    }

    // ==== MENULAR ====
    private void handleStart(String chatId, User from) {
        String uname = (from.getUserName() != null) ? "@" + from.getUserName() : from.getFirstName();
        String greeting = String.format("Assalomu alaykum %s!\nQanday yordam kerak? Menudan tanlang.", uname);
        sendTextWithKeyboard(chatId, greeting, mainKeyboard());
    }

    private ReplyKeyboardMarkup mainKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Promo Code"));
        row1.add(new KeyboardButton("Hizmatlar"));
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("Profile"));
        row2.add(new KeyboardButton("📩 Adminga yozish"));
        keyboard.setKeyboard(List.of(row1, row2));
        return keyboard;
    }

    private ReplyKeyboardMarkup backKeyboard() {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton(BACK_TO_MAIN));
        kb.setKeyboard(Collections.singletonList(row));
        return kb;
    }

    private void sendServicesMenu(String chatId) {
        String text = "📋 Hizmatlar — tanlang:";
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        KeyboardRow r1 = new KeyboardRow();
        r1.add(new KeyboardButton("Konspekt yozish"));
        r1.add(new KeyboardButton("Uyga vazifa"));
        KeyboardRow r2 = new KeyboardRow();
        r2.add(new KeyboardButton("Loyha ishlari"));
        r2.add(new KeyboardButton("Slayd yasab berish"));
        KeyboardRow r3 = new KeyboardRow();
        r3.add(new KeyboardButton(BACK_TO_MAIN));
        kb.setKeyboard(Arrays.asList(r1, r2, r3));
        sendTextWithKeyboard(chatId, text, kb);
    }

    private void showProfile(String chatId, User from) {
        boolean used = false;
        try { used = Config.isPromoUsed(Long.parseLong(chatId)); } catch (Exception ignored) {}
        String text = String.format("👤 Profil\nIsm: %s\nUsername: %s\nPromo: %s",
                from.getFirstName(),
                (from.getUserName() == null ? "-" : "@" + from.getUserName()),
                used ? "Bor" : "Yo'q");
        sendText(chatId, text);
    }

    // ==== SEND HELPERS ====
    protected void sendText(String chatId, String text) {
        try { execute(new SendMessage(chatId, text)); } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendTextWithKeyboard(String chatId, String text, ReplyKeyboardMarkup keyboard) {
        SendMessage sm = new SendMessage(chatId, text);
        sm.setReplyMarkup(keyboard);
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }
}
