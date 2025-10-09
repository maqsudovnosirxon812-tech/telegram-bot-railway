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
    private static final String BOT_TOKEN = "YOUR_SERVICEBOT_TOKEN";
    private static final String BOT_USERNAME = "YOUR_SERVICEBOT_USERNAME";
    private static final String DEFAULT_PROMO = "SYNOPSIS_2026";
    private static final String BACK_TO_MAIN = "⬅ Bosh menuga qaytish";

    private final Map<String, String> selectedService = new HashMap<>();
    private final Map<String, String> tempAnswers = new HashMap<>();
    private final Map<String, Integer> pageCount = new HashMap<>();
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

            // Admin bilan yozish holati
            if (chattingWithAdmin.getOrDefault(chatId, false)) {
                if (text.equalsIgnoreCase(BACK_TO_MAIN)) {
                    chattingWithAdmin.put(chatId, false);
                    handleStart(chatId, from);
                    return;
                }
                String msgToAdmin = "📩 *Foydalanuvchidan xabar:*\n"
                        + "👤 Ism: " + from.getFirstName() + "\n"
                        + "💬 ChatId: r" + chatId + "\n"
                        + "📝 Xabar: " + text;
                AdminBot.notifyAdmin(msgToAdmin);
                sendText(chatId, "✅ Xabaringiz adminga yuborildi.");
                return;
            }

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
                default -> sendText(chatId, "❌ Men bu buyruqni tushunmadim. Menudan tanlang yoki /start bosing.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==== CALLBACKLAR ====
    private void handleCallback(CallbackQuery cq) {
        try {
            String data = cq.getData();
            String chatId = String.valueOf(cq.getMessage().getChatId());
            Integer messageId = cq.getMessage().getMessageId();
            int current = pageCount.getOrDefault(chatId, 2);
            User user = cq.getFrom();
            String firstName = user.getFirstName();

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
            }
            AnswerCallbackQuery ack = new AnswerCallbackQuery();
            ack.setCallbackQueryId(cq.getId());
            execute(ack);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==== INLINE TUGMALAR ====
    private InlineKeyboardMarkup buildInlineMarkup(int current) {
        InlineKeyboardButton minus = new InlineKeyboardButton("-2");
        minus.setCallbackData("dec");
        InlineKeyboardButton plus = new InlineKeyboardButton("+2");
        plus.setCallbackData("inc");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(Arrays.asList(minus, plus));
        return new InlineKeyboardMarkup(rows);
    }

    private void editInlineCount(String chatId, int messageId, int current) {
        try {
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId);
            edit.setMessageId(messageId);
            edit.setText("📄 Betlar soni: " + current);
            edit.setReplyMarkup(buildInlineMarkup(current));
            execute(edit);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ==== ISH TUGADI ====
    public static void ishtugadiStatic(String chatId) {
        try {
            ServiceBot bot = new ServiceBot();
            bot.execute(new SendMessage(chatId, "✅ So‘rovingiz yakunlandi. Ish tayyor!"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==== MENULAR ====
    private void handleStart(String chatId, User from) {
        String greeting = "Assalomu alaykum, " + from.getFirstName() + "!\nQuyidagi menulardan tanlang 👇";
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
        String text = String.format("👤 Profil\nIsm: %s\nUsername: %s",
                from.getFirstName(),
                from.getUserName() == null ? "-" : "@" + from.getUserName());
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
}
