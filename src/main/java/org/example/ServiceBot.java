package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendSticker;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.*;

import java.io.InputStream;
import java.util.*;

public class ServiceBot extends TelegramLongPollingBot {

    private static final String BOT_TOKEN = "8495297601:AAF-h1qu1XugDGG6pYD_brlbv_FPTIxTZHs";
    private static final String BOT_USERNAME = "edu_serve_bot";

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

            // 🔹 Agar foydalanuvchi admin bilan yozish rejimida bo‘lsa
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

            // 🔹 Deep-link bilan /start bosilganda ham ishlaydi
            if (text.startsWith("/start")) {
                String[] parts = text.split(" ", 2);
                if (parts.length > 1) {
                    String payload = parts[1];
                    sendText(chatId, "🔑 Sizning promo yoki havola kodi: `" + payload + "`");
                }
                handleStart(chatId, from);
                return;
            }

            // 🔹 Tanlangan xizmatni qayta ishlash
            if (selectedService.containsKey(chatId)) {
                String svc = selectedService.get(chatId);
                handleSelectedService(svc, chatId, text, from, username);
                return;
            }

            // 🔹 Asosiy menyu buyruqlari
            switch (text) {
                case "Promo Code" ->{
                    sendPhoto(chatId,"images/download.png","");
                    sendText(chatId, "🔑 Iltimos, promo " +
                        "kodni " +
                        "kiriting:");
                }
                case "Hizmatlar" -> sendServicesMenu(chatId);
                case "Profile" -> showProfile(chatId, from);
                case "📩 Adminga yozish" -> {
                    chattingWithAdmin.put(chatId, true);
                    sendTextWithKeyboard(chatId, "✍️ Xabaringizni yozing. Adminga yuboriladi.\n\n" + BACK_TO_MAIN, backKeyboard());
                }
                case BACK_TO_MAIN -> handleStart(chatId, from);
                default -> handleCustomText(chatId, text, from, username);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // === FOYDALANUVCHI XIZMATNI TANLAGANDA ===
    private void handleSelectedService(String svc, String chatId, String text, User from, String username) {
        switch (svc) {
            case "Konspekt yozish" -> {
                if (!pageCount.containsKey(chatId)) {
                    pageCount.put(chatId, 2);
                    sendKonspektInline(chatId);
                } else {
                    sendText(chatId, "📘 Betlar sonini inline tugmalar orqali belgilang yoki /start bosing.");
                }
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
            }
            case "Slayd yasab berish" -> {
                if (!tempAnswers.containsKey(chatId)) {
                    tempAnswers.put(chatId, text);
                    pageCount.put(chatId, 2);
                    sendSlidesInline(chatId, text);
                } else {
                    sendText(chatId, "🎞 Slaydlar sonini inline tugmalar orqali belgilang yoki /start bosing.");
                }
            }
        }
    }

    // === CALLBACK (INLINE) BOSILGANDA ===
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
                case "inc" -> current += 2;
                case "dec" -> current = Math.max(2, current - 2);
            }
            pageCount.put(chatId, current);

            if (data.equals("confirm_konspekt")) {
                AdminBot.notifyAdmin("📘 *Konspekt so‘rovi*\n👤 " + firstName +
                        "\n🔗 " + username + "\n💬 ChatId: " + chatId +
                        "\n📄 Betlar: " + current);
                sendText(chatId, "✅ Konspekt uchun so‘rovingiz yuborildi!");
                clearState(chatId);
            } else if (data.equals("confirm_slides")) {
                String topic = tempAnswers.getOrDefault(chatId, "Mavzu");
                AdminBot.notifyAdmin("🎞 *Slayd so‘rovi*\n👤 " + firstName +
                        "\n🔗 " + username + "\n💬 ChatId: " + chatId +
                        "\n🧾 Mavzu: " + topic +
                        "\n📊 Slaydlar: " + current);
                sendText(chatId, "✅ Slayd so‘rovingiz yuborildi!");
                clearState(chatId);
            } else {
                editInlineCount(chatId, messageId, current);
            }

            AnswerCallbackQuery ack = new AnswerCallbackQuery();
            ack.setCallbackQueryId(cq.getId());
            execute(ack);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // === YORDAMCHI METODLAR ===
    private InlineKeyboardMarkup buildInlineMarkup(int current, boolean isSlide) {
        InlineKeyboardButton minus = new InlineKeyboardButton("-2");
        minus.setCallbackData("dec");
        InlineKeyboardButton plus = new InlineKeyboardButton("+2");
        plus.setCallbackData("inc");
        InlineKeyboardButton confirm = new InlineKeyboardButton(isSlide ? "Tasdiqlash (Slayd)" : "Tasdiqlash (Konspekt)");
        confirm.setCallbackData(isSlide ? "confirm_slides" : "confirm_konspekt");
        return new InlineKeyboardMarkup(List.of(
                List.of(minus, plus),
                List.of(confirm)
        ));
    }

    private void editInlineCount(String chatId, int messageId, int current) {
        try {
            boolean isSlide = "Slayd yasab berish".equals(selectedService.getOrDefault(chatId, ""));
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId);
            edit.setMessageId(messageId);
            edit.setText((isSlide ? "🎞 Slaydlar soni: " : "📄 Betlar soni: ") + current);
            edit.setReplyMarkup(buildInlineMarkup(current, isSlide));
            execute(edit);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendKonspektInline(String chatId) {
        sendPhoto(chatId,"images/botga1.jpeg","");
        int current = pageCount.getOrDefault(chatId, 2);
        SendMessage sm = new SendMessage(chatId, "📘 Nechta bet kerak?");
        sm.setReplyMarkup(buildInlineMarkup(current, false));
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendSlidesInline(String chatId, String topic) {
        sendPhoto(chatId,"images/slaydYasash.jpeg","");
        int current = pageCount.getOrDefault(chatId, 2);
        SendMessage sm = new SendMessage(chatId, "🎞 Mavzu: " + topic + "\nNechta slayd kerak?");
        sm.setReplyMarkup(buildInlineMarkup(current, true));
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    private void handleCustomText(String chatId, String text, User from, String username) {
        if (text.equalsIgnoreCase(DEFAULT_PROMO)) {
            sendText(chatId, "✅ Promo kod qabul qilindi!");
            AdminBot.notifyAdmin("📩 Promo ishlatildi!\nFoydalanuvchi: " + username);
        } else if (text.equalsIgnoreCase("Konspekt yozish")) {
            selectedService.put(chatId, "Konspekt yozish");
            pageCount.put(chatId, 2);
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

    private void handleStart(String chatId, User from) {
        sendSticker(chatId,"CAACAgIAAxkBAlv3vGjuZboO35195_WX6d3kKXC-zxCnAAKrIQACkvIYS1pamgABwWPVUDYE");
        String uname = (from.getUserName() != null) ? "@" + from.getUserName() : from.getFirstName();
        String greeting = "Assalomu alaykum " + uname + "!\nQanday yordam kerak?";
        sendTextWithKeyboard(chatId, greeting, mainKeyboard());
    }

    private void sendSticker(String chatId, String stickerId) {
        try {
            SendSticker sticker = new SendSticker();
            sticker.setChatId(chatId);
            sticker.setSticker(new InputFile(stickerId));
            execute(sticker);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private ReplyKeyboardMarkup mainKeyboard() {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        KeyboardRow r1 = new KeyboardRow();
        r1.add("Promo Code");
        r1.add("Hizmatlar");
        KeyboardRow r2 = new KeyboardRow();
        r2.add("Profile");
        r2.add("📩 Adminga yozish");
        kb.setKeyboard(List.of(r1, r2));
        return kb;
    }

    private ReplyKeyboardMarkup backKeyboard() {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        kb.setKeyboard(List.of(new KeyboardRow(List.of(new KeyboardButton(BACK_TO_MAIN)))));
        return kb;
    }

    private void sendServicesMenu(String chatId) {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        kb.setKeyboard(List.of(
                new KeyboardRow(List.of(new KeyboardButton("Konspekt yozish"), new KeyboardButton("Uyga vazifa"))),
                new KeyboardRow(List.of(new KeyboardButton("Loyha ishlari"), new KeyboardButton("Slayd yasab berish"))),
                new KeyboardRow(List.of(new KeyboardButton(BACK_TO_MAIN)))
        ));
        sendTextWithKeyboard(chatId, "📋 Hizmatlar — tanlang:", kb);
    }

    private void showProfile(String chatId, User from) {
        String text = "👤 Profil\nIsm: " + from.getFirstName()
                + "\nUsername: " + (from.getUserName() == null ? "-" : "@" + from.getUserName())
                + "\nPromo: Yo‘q";
        sendText(chatId, text);
    }

    private void clearState(String chatId) {
        selectedService.remove(chatId);
        tempAnswers.remove(chatId);
        pageCount.remove(chatId);
        chattingWithAdmin.remove(chatId);
    }


    protected void sendText(String chatId, String text) {
        try { execute(new SendMessage(chatId, text)); } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendTextWithKeyboard(String chatId, String text, ReplyKeyboardMarkup kb) {
        SendMessage sm = new SendMessage(chatId, text);
        sm.setReplyMarkup(kb);
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendPhoto(String chatId, String resourcePath, String caption) {
        try {
            // resources ichidagi faylni oqish
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (inputStream == null) {
                sendText(chatId, "❌ Rasm topilmadi: " + resourcePath);
                return;
            }

            InputFile photo = new InputFile(inputStream, "image.jpg");

            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId);
            sendPhoto.setPhoto(photo);

            if (caption != null && !caption.isEmpty()) {
                sendPhoto.setCaption(caption);
            }

            execute(sendPhoto);
        } catch (Exception e) {
            e.printStackTrace();
            sendText(chatId, "⚠️ Rasm yuborishda xatolik yuz berdi!");
        }
    }

    public static void ishtugadiStatic(String chatId) {
        try {
            ServiceBot bot = ServiceBot.getInstance();
            bot.execute(new SendMessage(chatId, "✅ So‘rovingiz yakunlandi. Ish tayyor!"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
