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

/**
 * ServiceBot:
 * - Konspekt: +2 / -2 tugmalari va tasdiqlash
 * - Uyga vazifa: fan -> mavzu (DB ga yozadi va adminga yuboradi)
 * - Loyha ishlari: tavsif (DB ga yozadi va adminga yuboradi)
 * - Slayd: mavzu -> +2/-2 slayd -> tasdiqlash
 */
public class ServiceBot extends TelegramLongPollingBot {
    private static final String BOT_TOKEN = "8449488730:AAHa5Q9xH7tXckbGLyO6twT1SB-QnCIHrcQ";
    private static final String BOT_USERNAME = "Konspek1_bot";
    private static final String DEFAULT_PROMO = "SYNOPSIS_2026";
    private static final String BACK_TO_MAIN = "⬅ Bosh menuga qaytish";

    private final Map<String, String> selectedService = new HashMap<>();
    private final Map<String, String> tempAnswers = new HashMap<>();
    private final Map<String, Integer> pageCount = new HashMap<>();
    private final Map<String, String> lastInlineMessageKey = new HashMap<>();

    public ServiceBot() { }

    @Override
    public String getBotUsername() { return BOT_USERNAME; }

    @Override
    public String getBotToken() { return BOT_TOKEN; }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                CallbackQuery cq = update.getCallbackQuery();
                String data = cq.getData();
                String chatId = String.valueOf(cq.getMessage().getChatId());
                Integer messageId = cq.getMessage().getMessageId();
                int current = pageCount.getOrDefault(chatId, 2);

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
                    Config.createRequest(Long.parseLong(chatId), service, "Betlar: " + pages);
                    AdminBot.notifyAdmin("📘 Konspekt\nChatId: " + chatId + "\nBetlar: " + pages);
                    sendText(chatId, "✅ Konspekt uchun so‘rovingiz adminga yuborildi!");
                    clearState(chatId);
                } else if (data.equals("confirm_slides")) {
                    String topic = tempAnswers.getOrDefault(chatId, "Mavzu");
                    int slides = pageCount.getOrDefault(chatId, 2);
                    Config.createRequest(Long.parseLong(chatId), "Slayd yasab berish", topic + " | Slaydlar: " + slides);
                    AdminBot.notifyAdmin("🎞 Slayd\nChatId: " + chatId + "\nMavzu: " + topic + "\nSlaydlar: " + slides);
                    sendText(chatId, "✅ Slaydlar bo‘yicha so‘rovingiz yuborildi.");
                    clearState(chatId);
                }

                AnswerCallbackQuery ack = new AnswerCallbackQuery();
                ack.setCallbackQueryId(cq.getId());
                execute(ack);
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

            if (text.equals("/start")) {
                handleStart(chatId, from);
                return;
            }

            if (text.equalsIgnoreCase("Promo Code")) {
                sendText(chatId, "🔑 Iltimos, promo kodni kiriting:");
                return;
            }

            if (text.equalsIgnoreCase("Hizmatlar")) {
                sendServicesMenu(chatId);
                return;
            }

            if (text.equalsIgnoreCase("Profile")) {
                showProfile(chatId, from);
                return;
            }

            if (text.equalsIgnoreCase(BACK_TO_MAIN)) {
                handleStart(chatId, from);
                return;
            }

            if (selectedService.containsKey(chatId)) {
                String svc = selectedService.get(chatId);

                switch (svc) {
                    case "Konspekt yozish" -> {
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
                            tempAnswers.put(chatId, text);
                            sendText(chatId, "✍️ Endi mavzuni kiriting:");
                            return;
                        } else {
                            String fan = tempAnswers.remove(chatId);
                            String mavzu = text;
                            Config.createRequest(Long.parseLong(chatId), "Uyga vazifa", fan + " | " + mavzu);
                            AdminBot.notifyAdmin("📚 Uyga vazifa\nChatId: " + chatId + "\nUsername: " + username +
                                    "\nFan: " + fan + "\nMavzu: " + mavzu);
                            sendText(chatId, "✅ Uyga vazifa yuborildi.\nAdminga xabar berildi.\n👤 Username: " + username);
                            selectedService.remove(chatId);
                            return;
                        }
                    }

                    case "Loyha ishlari" -> {
                        Config.createRequest(Long.parseLong(chatId), "Loyha ishlari", text);
                        AdminBot.notifyAdmin("🧩 Loyha ishlari\nChatId: " + chatId + "\nUsername: " + username +
                                "\nTavsif: " + text);
                        sendText(chatId, "✅ Loyha ma’lumoti yuborildi.\n👤 Username: " + username);
                        selectedService.remove(chatId);
                        return;
                    }

                    case "Slayd yasab berish" -> {
                        if (!tempAnswers.containsKey(chatId)) {
                            tempAnswers.put(chatId, text);
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

            switch (text) {
                case "Konspekt yozish" -> {
                    selectedService.put(chatId, "Konspekt yozish");
                    pageCount.put(chatId, 2);
                    sendText(chatId, "📘 Siz Konspekt yozish xizmatini tanladingiz.\n👤 Username: " + username + "\nID: " + chatId);
                    sendKonspektInline(chatId);
                }
                case "Uyga vazifa" -> {
                    selectedService.put(chatId, "Uyga vazifa");
                    tempAnswers.remove(chatId);
                    sendText(chatId, "✍️ Qaysi fan uchun uyga vazifa kerak?\n👤 Username: " + username + "\nID: " + chatId);
                }
                case "Loyha ishlari" -> {
                    selectedService.put(chatId, "Loyha ishlari");
                    sendText(chatId, "🧩 Loyha haqida qisqacha yozing.\n👤 Username: " + username + "\nID: " + chatId);
                }
                case "Slayd yasab berish" -> {
                    selectedService.put(chatId, "Slayd yasab berish");
                    tempAnswers.remove(chatId);
                    sendText(chatId, "📑 Qaysi mavzu uchun slayd kerak?\n👤 Username: " + username + "\nID: " + chatId);
                }
                default -> {
                    if (text.equalsIgnoreCase(DEFAULT_PROMO)) {
                        Config.setPromoUsed(msg.getChatId(), true);
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

    private void editInlineCount(String chatId, int messageId, int current) {
        try {
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId);
            edit.setMessageId(messageId);
            edit.setText("📄 Betlar soni: " + current);
            edit.setReplyMarkup(buildInlineMarkup(current, true));
            execute(edit);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendKonspektInline(String chatId) {
        int current = pageCount.getOrDefault(chatId, 2);
        SendMessage sm = new SendMessage(chatId, "📘 Nechta bet kerak? (juft sonlarda ishlaydi)");
        sm.setReplyMarkup(buildInlineMarkup(current, false));
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendSlidesInline(String chatId, String topic) {
        int current = pageCount.getOrDefault(chatId, 2);
        SendMessage sm = new SendMessage(chatId, "🎞 Mavzu: " + topic + "\nNechta slayd kerak?");
        sm.setReplyMarkup(buildInlineMarkup(current, false));
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    public static void ishtugadiStatic(String chatId) {
        try {
            ServiceBot bot = new ServiceBot();
            bot.execute(new SendMessage(chatId, "✅ So‘rovingiz yakunlandi. Ish tayyor!"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private InlineKeyboardMarkup buildInlineMarkup(int current, boolean includeConfirmOnly) {
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
        rows.add(Collections.singletonList(confirmK));
        return new InlineKeyboardMarkup(rows);
    }

    private void clearState(String chatId) {
        selectedService.remove(chatId);
        tempAnswers.remove(chatId);
        pageCount.remove(chatId);
        lastInlineMessageKey.remove(chatId);
    }

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
        keyboard.setKeyboard(List.of(row1, row2));
        return keyboard;
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
                used ? "Bor" : "Yo'q");
        sendText(chatId, text);
    }

    protected void sendText(String chatId, String text) {
        try { execute(new SendMessage(chatId, text)); } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendTextWithKeyboard(String chatId, String text, ReplyKeyboardMarkup keyboard) {
        SendMessage sm = new SendMessage(chatId, text);
        sm.setReplyMarkup(keyboard);
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }
}
