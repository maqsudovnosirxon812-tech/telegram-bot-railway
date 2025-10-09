package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ServiceBot extends TelegramLongPollingBot {
    private static final String BOT_TOKEN = "8449488730:AAHa5Q9xH7tXckbGLyO6twT1SB-QnCIHrcQ";
    private static final String BOT_USERNAME = "Konspek1_bot";
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

            Config.upsertUser(msg.getChatId(), from.getUserName(), from.getFirstName());

            // Adminga yozish rejimi
            if (chattingWithAdmin.getOrDefault(chatId, false)) {
                if (text.equalsIgnoreCase(BACK_TO_MAIN)) {
                    chattingWithAdmin.put(chatId, false);
                    handleStart(chatId, from);
                    return;
                }
                String msgToAdmin = "📩 *Foydalanuvchidan xabar:*\n"
                        + "👤 Ism: " + from.getFirstName() + "\n"
                        + "💬 ChatId: " + chatId + "\n"
                        + "📝 Xabar: " + text;
                AdminBot.notifyAdmin(msgToAdmin);
                sendText(chatId, "✅ Xabaringiz adminga yuborildi.");
                return;
            }

            switch (text) {
                case "/start" -> handleStart(chatId, from);
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

    // === CALLBACK HANDLER ===
    private void handleCallback(CallbackQuery cq) {
        try {
            AnswerCallbackQuery ack = new AnswerCallbackQuery();
            ack.setCallbackQueryId(cq.getId());
            execute(ack);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // === STATIK YAKUNLASH ===
    public static void ishtugadiStatic(String chatId) {
        try {
            ServiceBot bot = new ServiceBot();
            bot.execute(new SendMessage(chatId, "✅ So‘rovingiz yakunlandi. Ish tayyor!"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // === BOSHLANG‘ICH MENYU ===
    private void handleStart(String chatId, User from) {
        String uname = (from.getUserName() != null) ? "@" + from.getUserName() : from.getFirstName();
        String greeting = String.format("Assalomu alaykum %s!\nQuyidagi menulardan tanlang 👇", uname);
        sendTextWithKeyboard(chatId, greeting, mainKeyboard());
    }

    private ReplyKeyboardMarkup mainKeyboard() {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        KeyboardRow r1 = new KeyboardRow();
        r1.add(new KeyboardButton("📩 Adminga yozish"));
        kb.setKeyboard(Collections.singletonList(r1));
        return kb;
    }

    private ReplyKeyboardMarkup backKeyboard() {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton(BACK_TO_MAIN));
        kb.setKeyboard(Collections.singletonList(row));
        return kb;
    }

    // === YORDAMCHI ===
    protected void sendText(String chatId, String text) {
        try { execute(new SendMessage(chatId, text)); } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendTextWithKeyboard(String chatId, String text, ReplyKeyboardMarkup kb) {
        SendMessage sm = new SendMessage(chatId, text);
        sm.setReplyMarkup(kb);
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }
}
