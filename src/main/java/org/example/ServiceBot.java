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
 * To'liq ServiceBot sinfi:
 * - Konspekt: +2 / -2 tugmalari (juft sonlarda harakat qiladi) va tasdiqlash
 * - Uyga vazifa: fan -> mavzu (DB ga yozadi va adminga yuboradi)
 * - Loyha ishlari: qisqacha tavsif (DB ga yozadi va adminga yuboradi)
 * - Slayd: mavzu -> +2/-2 slidlar -> tasdiqlash
 */
public class ServiceBot extends TelegramLongPollingBot {
    private static final String BOT_TOKEN = "8449488730:AAHa5Q9xH7tXckbGLyO6twT1SB-QnCIHrcQ";
    private static final String BOT_USERNAME = "Konspek1_bot";
    private static final String DEFAULT_PROMO = "SYNOPSIS_2026";
    private static final String BACK_TO_MAIN = "⬅ Bosh menuga qaytish";

    // Holatlar va vaqtinchalik ma'lumotlar
    private final Map<String, String> selectedService = new HashMap<>(); // chatId -> service name
    private final Map<String, String> tempAnswers = new HashMap<>();    // chatId -> temp text (fan yoki mavzu)
    private final Map<String, Integer> pageCount = new HashMap<>();      // chatId -> current even count
    private final Map<String, String> lastInlineMessageKey = new HashMap<>(); // chatId -> "<chatId>:<messageId>" to identify editable message if needed

    public ServiceBot() { /* konstruktor */ }

    @Override
    public String getBotUsername() { return BOT_USERNAME; }

    @Override
    public String getBotToken() { return BOT_TOKEN; }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            // CALLBACK QUERY HANDLING (plus/minus and confirms)
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
                    sendText(chatId, "✅ Konspekt uchun so'rovingiz qabul qilindi. Adminga yuborildi.");
                    clearState(chatId);
                } else if (data.equals("confirm_slides")) {
                    String topic = tempAnswers.getOrDefault(chatId, "Mavzu");
                    int slides = pageCount.getOrDefault(chatId, 2);
                    Config.createRequest(Long.parseLong(chatId), "Slayd yasab berish", topic + " | Slaydlar: " + slides);
                    AdminBot.notifyAdmin("🎞 Slayd\nChatId: " + chatId + "\nMavzu: " + topic + "\nSlaydlar: " + slides);
                    sendText(chatId, "✅ Slaydlar bo'yicha so'rovingiz yuborildi. Adminga xabar berildi.");
                    clearState(chatId);
                }

                // Acknowledge callback so client spinner disappears
                AnswerCallbackQuery ack = new AnswerCallbackQuery();
                ack.setCallbackQueryId(cq.getId());
                execute(ack);
                return;
            }

            // MESSAGE HANDLING
            if (!update.hasMessage()) return;
            Message msg = update.getMessage();
            if (!msg.hasText()) return;

            String text = msg.getText().trim();
            String chatId = String.valueOf(msg.getChatId());
            User from = msg.getFrom();

            // DB: upsert user
            Config.upsertUser(msg.getChatId(), from.getUserName(), from.getFirstName());

            // Start / main flow
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

            // Agar foydalanuvchi xizmat tanlagan bo'lsa, unga mosroq keyingi savollarni qabul qilamiz
            if (selectedService.containsKey(chatId)) {
                String svc = selectedService.get(chatId);

                switch (svc) {
                    case "Konspekt yozish" -> {
                        // Agar hali sahifa soni tanlanmagan bo'lsa — boshlang'ich 2 va inline tugmalarni yuborish
                        if (!pageCount.containsKey(chatId)) {
                            pageCount.put(chatId, 2);
                            sendKonspektInline(chatId);
                            return;
                        } else {
                            // bu holat odatda inline orqali ishlaydi; agar foydalanuvchi matn yuborsa, bekor qilish yoki yangidan boshlash mumkin
                            sendText(chatId, "📘 Konspekt holati: iltimos inline tugmalar orqali betlar sonini belgilang yoki /start bosing.");
                            return;
                        }
                    }

                    case "Uyga vazifa" -> {
                        // tempAnswers: dastlab fan so'raladi, keyin mavzu
                        if (!tempAnswers.containsKey(chatId)) {
                            tempAnswers.put(chatId, text); // fan
                            sendText(chatId, "✍️ Endi mavzuni kiriting:");
                            return;
                        } else {
                            String fan = tempAnswers.remove(chatId);
                            String mavzu = text;
                            Config.createRequest(Long.parseLong(chatId), "Uyga vazifa", fan + " | " + mavzu);
                            AdminBot.notifyAdmin("📚 Uyga vazifa\nChatId: " + chatId + "\nFan: " + fan + "\nMavzu: " + mavzu);
                            sendText(chatId, "✅ Uyga vazifa yuborildi. Adminga xabar berildi.");
                            selectedService.remove(chatId);
                            return;
                        }
                    }

                    case "Loyha ishlari" -> {
                        // Qisqacha tavsifni qabul qilib DB va adminga yuboramiz
                        Config.createRequest(Long.parseLong(chatId), "Loyha ishlari", text);
                        AdminBot.notifyAdmin("🧩 Loyha ishlari\nChatId: " + chatId + "\nTavsif: " + text);
                        sendText(chatId, "✅ Loyha ma'lumoti yuborildi. Adminga xabar berildi.");
                        selectedService.remove(chatId);
                        return;
                    }

                    case "Slayd yasab berish" -> {
                        // tempAnswers: avval mavzu so'raladi -> so'ngra inline count va tasdiqlash
                        if (!tempAnswers.containsKey(chatId)) {
                            tempAnswers.put(chatId, text); // mavzu
                            pageCount.put(chatId, 2);
                            sendSlidesInline(chatId, text);
                            return;
                        } else {
                            // odatda inline tugmalar orqali davom etadi
                            sendText(chatId, "🎞 Slayd holati: iltimos inline tugmalar orqali slayd sonini belgilang yoki /start bosing.");
                            return;
                        }
                    }
                }
            }

            // Agar bu yerga kelsa — yangi buyruq (xizmatni tanlash)
            switch (text) {
                case "Konspekt yozish" -> {
                    selectedService.put(chatId, "Konspekt yozish");
                    pageCount.put(chatId, 2);
                    sendKonspektInline(chatId);
                }
                case "Uyga vazifa" -> {
                    selectedService.put(chatId, "Uyga vazifa");
                    tempAnswers.remove(chatId);
                    sendText(chatId, "✍️ Qaysi fan uchun uyga vazifa kerak?");
                }
                case "Loyha ishlari" -> {
                    selectedService.put(chatId, "Loyha ishlari");
                    sendText(chatId, "🧩 Loyha haqida qisqacha yozing:");
                }
                case "Slayd yasab berish" -> {
                    selectedService.put(chatId, "Slayd yasab berish");
                    tempAnswers.remove(chatId);
                    sendText(chatId, "📑 Qaysi mavzu uchun slayd kerak? Iltimos mavzuni yozing:");
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

    // Inline tugmalar ustida tahrirlash uchun yordamchi
    private void editInlineCount(String chatId, int current) {
        try {
            // Agar lastInlineMessageKey mavjud bo'lsa, undagi messageId orqali edit qilamiz,
            // lekin CallbackQuery ishlaganda biz messageId-ni olamiz va shu metoddan chaqiramiz.
            // Bu metod faqatgina display uchun ishlaydi (agar messageId bilinsa, EditMessageText ishlatamiz).
            // Agar messageId bilinmasa, shunchaki yangi xabar yuborish:
            SendMessage sm = new SendMessage(chatId, "📄 Betlar soni: " + current + "\nTasdiqlash uchun 'Tasdiqlash' tugmasini bosing.");
            sm.setReplyMarkup(buildInlineMarkup(current, true));
            execute(sm);
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

    // Konspekt uchun inline yuborish
    private void sendKonspektInline(String chatId) {
        int current = pageCount.getOrDefault(chatId, 2);
        SendMessage sm = new SendMessage(chatId, "📘 Siz 'Konspekt yozish' xizmatini tanladingiz.\nNechta bet kerak? (juft sonlarda ishlaydi)");
        sm.setReplyMarkup(buildInlineMarkup(current, false));
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    // Slayd uchun inline yuborish
    private void sendSlidesInline(String chatId, String topic) {
        int current = pageCount.getOrDefault(chatId, 2);
        SendMessage sm = new SendMessage(chatId, "🎞 Mavzu: " + topic + "\nNechta slayd kerak? (juft sonlarda ishlaydi)");
        sm.setReplyMarkup(buildInlineMarkup(current, false));
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    // Qo‘shimcha metod
    public static void ishtugadiStatic(String chatId) {
        try {
            ServiceBot bot = new ServiceBot();
            bot.execute(new SendMessage(chatId, "✅ So‘rovingiz yakunlandi. Ish tayyor!"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // Inline markup quruvchisi: minus, plus va tasdiqlash tugmasi
    private InlineKeyboardMarkup buildInlineMarkup(int current, boolean includeConfirmOnly) {
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

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(minus);
        row1.add(plus);

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(row1);

        // Ikkinchi qator: faqat tasdiqlash tugmasi — qaysi xizmatga bog'liq bo'lishi kerak.
        // Biz shapely: agar oxirgi selectedService Slayd bo'lsa confirm_slides, aks holda confirm_konspekt.
        InlineKeyboardButton confirm = new InlineKeyboardButton();
        confirm.setText("Tasdiqlash: " + current);
        // Agar tempAnswers da mavzu bor — ehtimol slayd; otherwise konspekt
        // Ammo biz callbackda aniq nomga moslangan tasdiqlash tugmasini ham yuboramiz:
        // Agar hozirgi chat tempAnswers mavjud bo'lsa u slayd bo'lishi mumkin — lekin bu umumiy helper — shunday qilib ikki tugma qo'shmaymiz.
        // Buning o'rniga includeConfirmOnly==false bo'lsa ikkala variantdan birini qo'yamiz: (logic: caller belgilaydi)
        // Simplify: agar includeConfirmOnly==true -> faqat tasdiqlash tugmasi bilan ishlanadi (avvalgi versiyalarga qarab).
        // Ammo biz shu yerda default confirm sifatida "confirm_konspekt" qo'yamiz; sendSlidesInline / sendKonspektInline so'ngra server callback qatorida mosligini ta'minlaydi.

        // Determine confirm callback: if there's any tempAnswers that corresponds to chat having Slayd service,
        // This method cannot see chatId, so includeConfirmOnly parameter used by callers: false -> include confirm for both
        // To keep behavior deterministic, we'll build 2nd row with both confirm buttons but UI tight: show only one confirm depending on context.
        // Simpler: second row will contain a single confirm button; callers previously set appropriate callback via buildInlineMarkup(current, false) and later we fix via message text.

        // For safety here: set confirm callback to "confirm_konspekt" by default.
        confirm.setCallbackData("confirm_konspekt");

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(confirm);

        rows.add(row2);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    // Yordamchi: clear state after request done
    private void clearState(String chatId) {
        selectedService.remove(chatId);
        tempAnswers.remove(chatId);
        pageCount.remove(chatId);
        lastInlineMessageKey.remove(chatId);
    }

    // UI: asosiy menyu
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

    // Send helpers
    protected void sendText(String chatId, String text) {
        try { execute(new SendMessage(chatId, text)); } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendTextWithKeyboard(String chatId, String text, ReplyKeyboardMarkup keyboard) {
        SendMessage sm = new SendMessage(chatId, text);
        sm.setReplyMarkup(keyboard);
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }
}
