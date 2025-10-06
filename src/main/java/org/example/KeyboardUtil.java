package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyboardUtil extends TelegramLongPollingBot {

    // 🧠 Har bir foydalanuvchining hozirgi bet sonini saqlash
    private final Map<String, Integer> pageCount = new HashMap<>();

    @Override
    public String getBotUsername() {
        return "Konspek1_bot"; // 🔹 bu yerga o'z bot username'ingni yoz
    }

    @Override
    public String getBotToken() {
        return "8449488730:AAHa5Q9xH7tXckbGLyO6twT1SB-QnCIHrcQ"; // 🔹 bu yerga tokeningni yoz
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            // ✅ Callback bosilganda ishlaydi
            if (update.hasCallbackQuery()) {
                CallbackQuery cq = update.getCallbackQuery();
                String data = cq.getData();
                String chatId = String.valueOf(cq.getMessage().getChatId());
                Integer messageId = cq.getMessage().getMessageId();

                int current = pageCount.getOrDefault(chatId, 2);
                if ("inc".equals(data)) {
                    current += 2;
                } else if ("dec".equals(data)) {
                    if (current > 2) current -= 2;
                }
                pageCount.put(chatId, current);

                // 📝 Xabarni yangilash
                EditMessageText edit = new EditMessageText();
                edit.setChatId(chatId);
                edit.setMessageId(messageId);
                edit.setText("📄 Betlar soni: " + current);
                edit.setReplyMarkup(plusMinusKeyboard());
                execute(edit);

                // ☑️ Callback javobini yuborish (spinner yo‘qoladi)
                AnswerCallbackQuery ack = new AnswerCallbackQuery();
                ack.setCallbackQueryId(cq.getId());
                execute(ack);

                return;
            }

            // ✅ Oddiy /start buyrug‘i bosilganda
            if (update.hasMessage()) {
                Message message = update.getMessage();
                if (!message.hasText()) return;

                String text = message.getText();
                String chatId = String.valueOf(message.getChatId());

                if (text.equals("/start")) {
                    pageCount.put(chatId, 2);

                    SendMessage sendMessage = new SendMessage();
                    sendMessage.setChatId(chatId);
                    sendMessage.setText("📘 Konspekt botga xush kelibsiz!\n\n📄 Betlar soni: 2");
                    sendMessage.setReplyMarkup(plusMinusKeyboard());
                    execute(sendMessage);
                }
            }

        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // ✅ Plus/Minus tugmalarini yaratish
    private InlineKeyboardMarkup plusMinusKeyboard() {
        InlineKeyboardButton minus = new InlineKeyboardButton();
        minus.setText("-2");
        minus.setCallbackData("dec");

        InlineKeyboardButton plus = new InlineKeyboardButton();
        plus.setText("+2");
        plus.setCallbackData("inc");

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(minus);
        row.add(plus);

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(row);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }
}
