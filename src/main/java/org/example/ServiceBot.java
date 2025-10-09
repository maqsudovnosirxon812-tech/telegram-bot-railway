package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.*;

import java.util.*;

public class ServiceBot extends TelegramLongPollingBot {

    // ⚙️ Token va username to‘g‘ridan-to‘g‘ri kodda yoziladi
    private static final String BOT_TOKEN = "8495297601:AAF-h1qu1XugDGG6pYD_brlbv_FPTIxTZHs";      // <-- bu yerga tokenni yoz
    private static final String BOT_USERNAME = "edu_serve_bot"; // <-- bu yerga bot username’ni yoz

    private static final String DEFAULT_PROMO = "SYNOPSIS_2026";
    private static final String BACK_TO_MAIN = "⬅ Bosh menuga qaytish";

    private static ServiceBot instance;

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
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

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

            // Admin bilan muloqot rejimi
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

            // Xizmat tanlangan bo‘lsa
            if (selectedService.containsKey(chatId)) {
                String svc = selectedService.get(chatId);
                switch (svc) {
                    case "Konspekt yozish" -> {
                        if (!pageCount.containsKey(chatId)) {
                            pageCount.put(chatId, 2);
                            sendKonspektInline(chatId);
                        } else {
                            sendText(chatId, "📘 Betlar sonini inline tugmalar orqali belgilang yoki /start bosing.");
                        }
                        return;
                    }
                    case "Uyga vazifa" -> {
                        if (!tempAnswers.containsKey(chatId)) {
                            tempAnswers.put(chatId, text);
                            sendText(chatId, "✍️ Endi mavzuni kiriting:");
                        } else {
                            String fan = tempAnswers.remove(chatId);
                            String mavzu = text;
                            String msgToAdmin = "📚 Uyga vazifa\n"
                                    + "👤 Ism: " + from.getFirstName() + "\n"
                                    + "🔗 Username: " + username + "\n"
                                    + "💬 ChatId: " + chatId + "\n"
                                    + "📘 Fan: " + fan + "\n"
                                    + "🧾 Mavzu: " + mavzu;
                            AdminBot.notifyAdmin(msgToAdmin);
                            sendText(chatId, "✅ Uyga vazifa yuborildi. Adminga xabar berildi.");
                            selectedService.remove(chatId);
                        }
                        return;
                    }
                    case "Loyha ishlari" -> {
                        String msgToAdmin = "🧩 Loyha ishlari\n"
                                + "👤 Ism: " + from.getFirstName() + "\n"
                                + "🔗 Username: " + username + "\n"
                                + "💬 ChatId: " + chatId + "\n"
                                + "📄 Tavsif: " + text;
                        AdminBot.notifyAdmin(msgToAdmin);
                        sendText(chatId, "✅ Loyha ma’lumoti yuborildi.");
                        selectedService.remove(chatId);
                        return;
                    }
                    case "Slayd yasab berish" -> {
                        if (!tempAnswers.containsKey(chatId)) {
                            tempAnswers.put(chatId, text);
                            pageCount.put(chatId, 2);
                            sendSlidesInline(chatId, text);
                        } else {
                            sendText(chatId, "🎞 Slaydlar sonini inline tugmalar orqali belgilang yoki /start bosing.");
                        }
                        return;
                    }
                }
            }

            // Asosiy menyu
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
                    if (text.equalsIgnoreCase(DEFAULT_PROMO)) {
                        sendText(chatId, "✅ Promo kod qabul qilindi!");
                        AdminBot.notifyAdmin("📩 Promo ishlatildi!\nFoydalanuvchi: " + username);
                    } else if (text.equalsIgnoreCase("Konspekt yozish")) {
                        selectedService.put(chatId, "Konspekt yozish");
                        pageCount.put(chatId, 2);
                        sendText(chatId, "📘 Siz Konspekt yozish xizmatini tanladingiz.");
                        sendKonspektInline(chatId);
                    } else if (text.equalsIgnoreCase("Uyga vazifa")) {
                        selectedService.put(chatId, "Uyga vazifa");
                        tempAnswers.remove(chatId);
                        sendText(chatId, "✍️ Qaysi fan uchun uyga vazifa kerak?");
                    } else if (text.equalsIgnoreCase("Loyha ishlari")) {
                        selectedService.put(chatId, "Loyha ishlari");
                        sendText(chatId, "🧩 Loyha haqida qisqacha yozing.");
                    } else if (text.equalsIgnoreCase("Slayd yasab berish")) {
                        selectedService.put(chatId, "Slayd yasab berish");
                        tempAnswers.remove(chatId);
                        sendText(chatId, "📑 Qaysi mavzu uchun slayd kerak?");
                    } else {
                        sendText(chatId, "❌ Men bu buyruqni tushunmadim. /start bosing.");
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
                int pages = pageCount.getOrDefault(chatId, 2);
                String msgToAdmin = "📘 *Konspekt so‘rovi*\n"
                        + "👤 Ism: " + firstName + "\n"
                        + "🔗 Username: " + username + "\n"
                        + "💬 ChatId: " + chatId + "\n"
                        + "📄 Betlar: " + pages;
                AdminBot.notifyAdmin(msgToAdmin);
                sendText(chatId, "✅ Konspekt uchun so‘rovingiz yuborildi!");
                clearState(chatId);
            } else if (data.equals("confirm_slides")) {
                String topic = tempAnswers.getOrDefault(chatId, "Mavzu");
                int slides = pageCount.getOrDefault(chatId, 2);
                String msgToAdmin = "🎞 *Slayd so‘rovi*\n"
                        + "👤 Ism: " + firstName + "\n"
                        + "🔗 Username: " + username + "\n"
                        + "💬 ChatId: " + chatId + "\n"
                        + "🧾 Mavzu: " + topic + "\n"
                        + "📊 Slaydlar: " + slides;
                AdminBot.notifyAdmin(msgToAdmin);
                sendText(chatId, "✅ Slayd so‘rovingiz yuborildi!");
                clearState(chatId);
            }

            AnswerCallbackQuery ack = new AnswerCallbackQuery();
            ack.setCallbackQueryId(cq.getId());
            execute(ack);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private InlineKeyboardMarkup buildInlineMarkup(int current) {
        InlineKeyboardButton minus = new InlineKeyboardButton("-2");
        minus.setCallbackData("dec");
        InlineKeyboardButton plus = new InlineKeyboardButton("+2");
        plus.setCallbackData("inc");
        InlineKeyboardButton confirmK = new InlineKeyboardButton("Tasdiqlash (Konspekt)");
        confirmK.setCallbackData("confirm_konspekt");
        InlineKeyboardButton confirmS = new InlineKeyboardButton("Tasdiqlash (Slayd)");
        confirmS.setCallbackData("confirm_slides");
        return new InlineKeyboardMarkup(List.of(List.of(minus, plus), List.of(confirmK, confirmS)));
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
        SendMessage sm = new SendMessage(chatId, "📘 Nechta bet kerak?");
        sm.setReplyMarkup(buildInlineMarkup(current));
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendSlidesInline(String chatId, String topic) {
        int current = pageCount.getOrDefault(chatId, 2);
        SendMessage sm = new SendMessage(chatId, "🎞 Mavzu: " + topic + "\nNechta slayd kerak?");
        sm.setReplyMarkup(buildInlineMarkup(current));
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    private void clearState(String chatId) {
        selectedService.remove(chatId);
        tempAnswers.remove(chatId);
        pageCount.remove(chatId);
        chattingWithAdmin.remove(chatId);
    }

    private void handleStart(String chatId, User from) {
        String uname = (from.getUserName() != null) ? "@" + from.getUserName() : from.getFirstName();
        String greeting = "Assalomu alaykum " + uname + "!\nQanday yordam kerak?";
        sendTextWithKeyboard(chatId, greeting, mainKeyboard());
    }

    private ReplyKeyboardMarkup mainKeyboard() {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        KeyboardRow r1 = new KeyboardRow();
        r1.add(new KeyboardButton("Promo Code"));
        r1.add(new KeyboardButton("Hizmatlar"));
        KeyboardRow r2 = new KeyboardRow();
        r2.add(new KeyboardButton("Profile"));
        r2.add(new KeyboardButton("📩 Adminga yozish"));
        kb.setKeyboard(List.of(r1, r2));
        return kb;
    }

    private ReplyKeyboardMarkup backKeyboard() {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        kb.setKeyboard(List.of(List.of(new KeyboardButton(BACK_TO_MAIN))));
        return kb;
    }

    private void sendServicesMenu(String chatId) {
        String text = "📋 Hizmatlar — tanlang:";
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        kb.setKeyboard(List.of(
                List.of(new KeyboardButton("Konspekt yozish"), new KeyboardButton("Uyga vazifa")),
                List.of(new KeyboardButton("Loyha ishlari"), new KeyboardButton("Slayd yasab berish")),
                List.of(new KeyboardButton(BACK_TO_MAIN))
        ));
        sendTextWithKeyboard(chatId, text, kb);
    }

    private void showProfile(String chatId, User from) {
        String text = "👤 Profil\nIsm: " + from.getFirstName()
                + "\nUsername: " + (from.getUserName() == null ? "-" : "@" + from.getUserName())
                + "\nPromo: " + (false ? "Bor" : "Yo‘q");
        sendText(chatId, text);
    }

    protected void sendText(String chatId, String text) {
        try { execute(new SendMessage(chatId, text)); } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendTextWithKeyboard(String chatId, String text, ReplyKeyboardMarkup kb) {
        SendMessage sm = new SendMessage(chatId, text);
        sm.setReplyMarkup(kb);
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }
}
