package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.*;

public class ServiceBot extends TelegramLongPollingBot {
    private static final String BOT_TOKEN = System.getenv("SERVICE_BOT_TOKEN");
    private static final String BOT_USERNAME = System.getenv("SERVICE_BOT_USERNAME");
    private static final String DEFAULT_PROMO = "SYNOPSIS_2026";

    private final Map<String, Boolean> promoUsed = new HashMap<>();
    private static ServiceBot instance; // static instance for AdminBot

    public ServiceBot() {
        instance = this;
    }

    public static void ishtugadiStatic(String chatId) {
        if (instance != null) {
            instance.ishtugadi(chatId);
        }
    }

    @Override
    public String getBotUsername() { return BOT_USERNAME; }

    @Override
    public String getBotToken() { return BOT_TOKEN; }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) return;

        Message msg = update.getMessage();
        String chatId = String.valueOf(msg.getChatId());
        User from = msg.getFrom();

        if (!msg.hasText()) return;
        String text = msg.getText();

        switch (text) {
            case "/start","⬅\uFE0F Bosh menuga qaytish" -> handleStart(chatId, from);
            case "Promo Code" -> askPromo(chatId);
            case "Hizmatlar" -> sendServicesMenu(chatId);
            case "Konspekt yozish", "Uyga vazifa", "Loyha ishlari", "Slayd yasab berish" ->
                    handleServiceChoice(chatId, from, text);
            case "English course"-> englishCourse(chatId);
            default -> {
                if (text.equalsIgnoreCase(DEFAULT_PROMO)) {
                    promoUsed.put(chatId, true);
                    sendText(chatId, "✅ Promo kod qabul qilindi! Endi hizmatlardan foydalanishingiz mumkin.\nAdminga habar yuborildi.");
                    String notify = String.format("📩 Promo kod ishlatildi!\nFoydalanuvchi: %s (id=%s)\nUsername: @%s",
                            from.getFirstName(), chatId, (from.getUserName() == null ? "-" : from.getUserName()));
                    AdminBot.notifyAdmin(notify);
                } else {
                    sendText(chatId, "❌ Men bu buyruqni tushunmadim. Menudan tanlang yoki /start bosing.");
                }
            }
        }
    }

    private void englishCourse(String chatId) {
        sendText(chatId,"🇬🇧\"English course\"! Siz uchun qiziq bolsa pastdagi linkga kiring\n👇👇👇\n@english_course_uz");
    }

    protected void ishtugadi(String chatId) {
        sendText(chatId,"✅ Siz tanlagan hizmat tugallandi. Rahmat!\nKo'proq ma'lumot uchun @toxirovziyodilla");
    }

    private void handleStart(String chatId, User from) {
        String uname = (from.getUserName() != null) ? "@" + from.getUserName() : from.getFirstName();
        String greeting = String.format(
                "Assalomu alaykum %s! \nYaxshimisiz? Qanday yordam kerak? Menudan tanlang.\n\n" +
                        "📞 Texnik xizmat: @JavaBackend_Boy\n📘 English course: @toxirovziyodilla",
                uname);
        sendTextWithKeyboard(chatId, greeting, mainKeyboard());
    }

    private ReplyKeyboardMarkup mainKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Promo Code"));
        row1.add(new KeyboardButton("Hizmatlar"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("English course"));

        keyboard.setKeyboard(List.of(row1, row2));
        return keyboard;
    }

    private void sendServicesMenu(String chatId) {
        String text = "📋 Hizmatlar — quyidagilardan birini tanlang:";
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);

        KeyboardRow r1 = new KeyboardRow();
        r1.add(new KeyboardButton("Konspekt yozish"));
        r1.add(new KeyboardButton("Uyga vazifa"));

        KeyboardRow r2 = new KeyboardRow();
        r2.add(new KeyboardButton("Loyha ishlari"));
        r2.add(new KeyboardButton("Slayd yasab berish"));

        KeyboardRow r3 = new KeyboardRow();
        r3.add(new KeyboardButton("⬅ Bosh menuga qaytish"));

        kb.setKeyboard(Arrays.asList(r1, r2, r3));
        sendTextWithKeyboard(chatId, text, kb);
    }

    private void askPromo(String chatId) {
        sendText(chatId, "🔑 Iltimos, promo kodni kiriting:");
    }

    private void handleServiceChoice(String chatId, User from, String service) {
        boolean hasPromo = promoUsed.getOrDefault(chatId, false);
        String resp = "✅ Siz '" + service + "' hizmatini tanladingiz.";
        if (hasPromo)
            resp += "\nPromo kodi tasdiqlangan. Adminga habar yuborildi.\nTez orada aloqaga chiqishadi.";
        else
            resp += "\nPromo kod kiritsangiz chegirmaga ega bo'lasiz.";

        sendText(chatId, resp);

        String notify = String.format("📩 Foydalanuvchi: %s\nChatId: %s\nXizmat: %s\nPromo: %s\nUsername: @%s",
                from.getFirstName(), chatId, service, hasPromo ? "Bor" : "Yo‘q",
                (from.getUserName() == null ? "-" : from.getUserName()));

        AdminBot.notifyAdmin(notify);
    }

    protected void sendText(String chatId, String text) {
        try {
            execute(new SendMessage(chatId, text));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendTextWithKeyboard(String chatId, String text, ReplyKeyboardMarkup keyboard) {
        SendMessage sm = new SendMessage(chatId, text);
        sm.setReplyMarkup(keyboard);
        try {
            execute(sm);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
