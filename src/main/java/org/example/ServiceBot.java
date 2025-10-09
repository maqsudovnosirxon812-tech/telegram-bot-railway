package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.*;

public class ServiceBot extends TelegramLongPollingBot {
    private static final String BOT_TOKEN = "8449488730:AAHa5Q9xH7tXckbGLyO6twT1SB-QnCIHrcQ";
    private static final String BOT_USERNAME = "Konspek1_bot";
    private static final String DEFAULT_PROMO = "SYNOPSIS_2026";
    private static final String BACK_TO_MAIN = "⬅ Bosh menuga qaytish";

    private final Map<String, String> selectedService = new HashMap<>();
    private final Map<String, String> tempAnswers = new HashMap<>();
    private final Map<String, Integer> pageCount = new HashMap<>();
    private final Map<String, String> lastInlineMessageKey = new HashMap<>();
    private final Map<String, Boolean> chattingWithAdmin = new HashMap<>();

    @Override
    public String getBotUsername() { return BOT_USERNAME; }

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

            Config.upsertUser(msg.getChatId(), from.getUserName(), from.getFirstName());

            // Admin bilan yozish holati
            if (chattingWithAdmin.getOrDefault(chatId, false)) {
                if (text.equalsIgnoreCase(BACK_TO_MAIN)) {
                    chattingWithAdmin.put(chatId, false);
                    handleStart(chatId, from);
                    return;
                }
                String msgToAdmin = "📩 *Foydalanuvchidan xabar:*\n"
                        + "👤 Ism: " + from.getFirstName() + "\n"
                        + "🔗 Username: " + username + "\n"
                        + "💬 ChatId: " + chatId + "\n"
                        + "📝 Xabar: " + text;
                AdminBot.notifyAdmin(msgToAdmin);
                sendText(chatId, "✅ Xabaringiz adminga yuborildi.");
                return;
            }

            // Menular
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
                default -> handleText(chatId, text, from, username);
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

            switch (data) {
                case "inc" -> {
                    current += 2;
                    pageCount.put(chatId, current);
                    editInlineCount(chatId, messageId, current);
                }
                case "dec" -> {
                    if (current > 2) current -= 2;
                    pageCount.put(chatId, current);
                    editInlineCount(chatId, messageId, current);
                }
                case "confirm_konspekt" -> {
                    String service = "Konspekt yozish";
                    int pages = pageCount.getOrDefault(chatId, 2);
                    Config.createRequest(Long.parseLong(chatId), service, "Betlar: " + pages);

                    String msgToAdmin = "📘 *Konspekt so‘rovi*\n"
                            + "👤 Ism: " + firstName + "\n"
                            + "🔗 Username: " + username + "\n"
                            + "💬 ChatId: " + chatId + "\n"
                            + "📄 Betlar: " + pages;
                    AdminBot.notifyAdmin(msgToAdmin);

                    sendText(chatId, "✅ Konspekt uchun so‘rovingiz adminga yuborildi!");
                    clearState(chatId);
                }
                case "confirm_slides" -> {
                    String topic = tempAnswers.getOrDefault(chatId, "Mavzu");
                    int slides = pageCount.getOrDefault(chatId, 2);
                    Config.createRequest(Long.parseLong(chatId), "Slayd yasab berish", topic + " | Slaydlar: " + slides);

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
            }

            AnswerCallbackQuery ack = new AnswerCallbackQuery();
            ack.setCallbackQueryId(cq.getId());
            execute(ack);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==== FOYDALANUVCHI YOZGAN MATN ====
    private void handleText(String chatId, String text, User from, String username) {
        try {
            if (selectedService.containsKey(chatId)) {
                String svc = selectedService.get(chatId);
                switch (svc) {
                    case "Konspekt yozish" -> {
                        if (!pageCount.containsKey(chatId)) {
                            pageCount.put(chatId, 2);
                            sendKonspektInline(chatId);
                        } else sendText(chatId, "📘 Betlar sonini inline tugmalar orqali belgilang yoki /start bosing.");
                    }
                    case "Uyga vazifa" -> {
                        if (!tempAnswers.containsKey(chatId)) {
                            tempAnswers.put(chatId, text);
                            sendText(chatId, "✍️ Endi mavzuni kiriting:");
                        } else {
                            String fan = tempAnswers.remove(chatId);
                            String mavzu = text;
                            Config.createRequest(Long.parseLong(chatId), "Uyga vazifa", fan + " | " + mavzu);
                            AdminBot.notifyAdmin("📚 *Uyga vazifa*\n"
                                    + "👤 Ism: " + from.getFirstName() + "\n"
                                    + "🔗 Username: " + username + "\n"
                                    + "💬 ChatId: " + chatId + "\n"
                                    + "📘 Fan: " + fan + "\n"
                                    + "🧾 Mavzu: " + mavzu);
                            sendText(chatId, "✅ Uyga vazifa yuborildi.");
                            selectedService.remove(chatId);
                        }
                    }
                    case "Loyha ishlari" -> {
                        Config.createRequest(Long.parseLong(chatId), "Loyha ishlari", text);
                        AdminBot.notifyAdmin("🧩 *Loyha ishlari*\n"
                                + "👤 Ism: " + from.getFirstName() + "\n"
                                + "🔗 Username: " + username + "\n"
                                + "💬 ChatId: " + chatId + "\n"
                                + "📄 Tavsif: " + text);
                        sendText(chatId, "✅ Loyha ma’lumoti yuborildi.");
                        selectedService.remove(chatId);
                    }
                    case "Slayd yasab berish" -> {
                        if (!tempAnswers.containsKey(chatId)) {
                            tempAnswers.put(chatId, text);
                            pageCount.put(chatId, 2);
                            sendSlidesInline(chatId, text);
                        } else sendText(chatId, "🎞 Slayd holati: inline tugmalar orqali slayd sonini belgilang yoki /start bosing.");
                    }
                }
                return;
            }

            switch (text) {
                case "Konspekt yozish" -> {
                    selectedService.put(chatId, "Konspekt yozish");
                    pageCount.put(chatId, 2);
                    sendText(chatId, "📘 Siz Konspekt yozish xizmatini tanladingiz.");
                    sendKonspektInline(chatId);
                }
                case "Uyga vazifa" -> {
                    selectedService.put(chatId, "Uyga vazifa");
                    tempAnswers.remove(chatId);
                    sendText(chatId, "✍️ Qaysi fan uchun uyga vazifa kerak?");
                }
                case "Loyha ishlari" -> {
                    selectedService.put(chatId, "Loyha ishlari");
                    sendText(chatId, "🧩 Loyha haqida qisqacha yozing.");
                }
                case "Slayd yasab berish" -> {
                    selectedService.put(chatId, "Slayd yasab berish");
                    tempAnswers.remove(chatId);
                    sendText(chatId, "📑 Qaysi mavzu uchun slayd kerak?");
                }
                default -> {
                    if (text.equalsIgnoreCase(DEFAULT_PROMO)) {
                        Config.setPromoUsed(Long.parseLong(chatId), true);
                        sendText(chatId, "✅ Promo kod qabul qilindi! Adminga habar yuborildi.");
                        AdminBot.notifyAdmin("📩 Promo ishlatildi!\nFoydalanuvchi: " + from.getFirstName() + " id=" + chatId);
                    } else {
                        sendText(chatId, "❌ Men bu buyruqni tushunmadim. Menudan tanlang yoki /start bosing.");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==== INLINE BUTTONLAR ====
    private InlineKeyboardMarkup buildInlineMarkup(int current, boolean isEdit) {
        InlineKeyboardButton minus = new InlineKeyboardButton("-2");
        minus.setCallbackData("dec");

        InlineKeyboardButton plus = new InlineKeyboardButton("+2");
        plus.setCallbackData("inc");

        InlineKeyboardButton confirmK = new InlineKeyboardButton("Tasdiqlash (Konspekt)");
        confirmK.setCallbackData("confirm_konspekt");

        InlineKeyboardButton confirmS = new InlineKeyboardButton("Tasdiqlash (Slayd)");
        confirmS.setCallbackData("confirm_slides");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(Arrays.asList(minus, plus));
        rows.add(Arrays.asList(confirmK, confirmS));
        return new InlineKeyboardMarkup(rows);
    }

    private void editInlineCount(String chatId, int messageId, int current) {
        try {
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId);
            edit.setMessageId(messageId);
            edit.setText("📄 Betlar soni: " + current);
            edit.setReplyMarkup(buildInlineMarkup(current, true));
            execute(edit);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendKonspektInline(String chatId) {
        int current = pageCount.getOrDefault(chatId, 2);
        SendMessage sm = new SendMessage(chatId, "📘 Nechta bet kerak?");
        sm.setReplyMarkup(buildInlineMarkup(current, false));
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendSlidesInline(String chatId, String topic) {
        int current = pageCount.getOrDefault(chatId, 2);
        SendMessage sm = new SendMessage(chatId, "🎞 Mavzu: " + topic + "\nNechta slayd kerak?");
        sm.setReplyMarkup(buildInlineMarkup(current, false));
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    // ==== BU YERDA ISHTUGADI STATIC ====
    public static void ishtugadiStatic(String chatId) {
        try {
            ServiceBot bot = new ServiceBot();
            bot.execute(new SendMessage(chatId, "✅ So‘rovingiz yakunlandi. Ish tayyor!"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==== MENU VA PROFIL ====
    private void handleStart(String chatId, User from) {
        String uname = (from.getUserName() != null) ? "@" + from.getUserName() : from.getFirstName();
        String greeting = String.format("Assalomu alaykum %s!\nQuyidagi menulardan tanlang 👇", uname);
        sendTextWithKeyboard(chatId, greeting, mainKeyboard());
    }

    private ReplyKeyboardMarkup mainKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        KeyboardRow r1 = new KeyboardRow();
        r1.add(new KeyboardButton("Promo Code"));
        r1.add(new KeyboardButton("Hizmatlar"));
        KeyboardRow r2 = new KeyboardRow();
        r2.add(new KeyboardButton("Profile"));
        r2.add(new KeyboardButton("📩 Adminga yozish"));
        keyboard.setKeyboard(Arrays.asList(r1, r2));
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
        boolean used = Config.isPromoUsed(Long.parseLong(chatId));
        String text = String.format("👤 Profil\nIsm: %s\nUsername: %s\nPromo: %s",
                from.getFirstName(),
                (from.getUserName() == null ? "-" : "@" + from.getUserName()),
                used ? "Bor" : "Yo‘q");
        sendText(chatId, text);
    }

    // ==== YORDAMCHI ====
    protected void sendText(String chatId, String text) {
        try { execute(new SendMessage(chatId, text)); } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendTextWithKeyboard(String chatId, String text, ReplyKeyboardMarkup keyboard) {
        SendMessage sm = new SendMessage(chatId, text);
        sm.setReplyMarkup(keyboard);
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    private void clearState(String chatId) {
        selectedService.remove(chatId);
        tempAnswers.remove(chatId);
        pageCount.remove(chatId);
        lastInlineMessageKey.remove(chatId);
    }
}
