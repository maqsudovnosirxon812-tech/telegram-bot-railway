package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.methods.send.*;
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
    private final Map<String, String> tempAnswers = new HashMap<>(); // umumiy: mavzu yoki boshqa vaqtinchalik matn
    private final Map<String, Integer> pageCount = new HashMap<>(); // slayd/bet soni
    private final Map<String, Boolean> chattingWithAdmin = new HashMap<>();

    // Slayd maxsus maplar
    private final Map<String, String> slideLang = new HashMap<>(); // chatId -> til
    private final Map<String, String> pendingSlideSummary = new HashMap<>(); // chatId -> tayyor summary yuborilgunga qadar saqlansin
    private final Map<String, Boolean> expectingPrintFile = new HashMap<>(); // chatId -> true agar slayd uchun fayl yuborilishi kutilsa

    public ServiceBot() { instance = this; }

    public static ServiceBot getInstance() { return instance; }

    @Override
    public String getBotUsername() { return BOT_USERNAME; }

    @Override
    public String getBotToken() { return BOT_TOKEN; }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            // CALLBACK (inline) tugmalar
            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
                return;
            }

            if (!update.hasMessage()) return;
            Message msg = update.getMessage();
            String chatId = String.valueOf(msg.getChatId());
            User from = msg.getFrom();
            String username = (from.getUserName() != null ? "@" + from.getUserName() : from.getFirstName());

            // Agar kutilayotgan fayl bo'lsa (printer uchun yoki slayd chop etish uchun)
            if (msg.hasDocument() || msg.hasPhoto() || msg.hasVideo() || msg.hasAudio() || msg.hasVoice() || msg.hasVideoNote() || msg.hasSticker()) {
                // Agar chat print uchun kutilyapti (slayd print) yoki selectedService Printer xizmati bo'lsa
                if (Boolean.TRUE.equals(expectingPrintFile.get(chatId)) || "Printer xizmati".equals(selectedService.get(chatId))) {
                    handlePrinterFile(chatId, msg, from, username);
                    return;
                }
            }

            if (!msg.hasText()) return;
            String text = msg.getText().trim();

            // Agar admin bilan chat rejimida bo'lsa
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

            // /start bilan kelgan deep-link
            if (text.startsWith("/start")) {
                String[] parts = text.split(" ", 2);
                if (parts.length > 1) {
                    String payload = parts[1];
                    sendText(chatId, "🔑 Sizning promo yoki havola kodi: `" + payload + "`");
                }
                handleStart(chatId, from);
                return;
            }

            // Agar avvaldan tanlangan xizmat mavjud bo'lsa uni qayta ishlash
            if (selectedService.containsKey(chatId)) {
                String svc = selectedService.get(chatId);
                // Maxsus holatlar: Slayd jarayoni uchun matnlar va printer uchun fayl boshqaruvi
                if ("Slayd yasab berish".equals(svc)) {
                    handleSlideTextFlow(chatId, text, from, username);
                    return;
                } else if ("Printer xizmati".equals(svc)) {
                    // Agar foydalanuvchi Printer xizmati tanlab matn yuborsa: so'rov haqida izoh
                    sendText(chatId, "🖨 Iltimos, fayl yuboring (har qanday format qabul qilinadi). Agar matn yubormoqchi bo'lsangiz — faylingiz yo‘q degan holda batafsil yozing, adminga yuboriladi.");
                    return;
                } else {
                    handleSelectedService(svc, chatId, text, from, username);
                    return;
                }
            }

            // Asosiy menyu buyruqlari
            switch (text) {
                case "Promo Code" -> {
                    sendPhoto(chatId,"images/download.png","");
                    sendText(chatId, "🔑 Iltimos, promo kodni kiriting:");
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

    // === SLIDE (matn) jarayoni uchun handler ===
    private void handleSlideTextFlow(String chatId, String text, User from, String username) {
        try {
            // 1) til o'rnatilmagan bo'lsa - til tanlansin
            if (!slideLang.containsKey(chatId)) {
                // kutyapmiz faqat ikkita til variantidan biri bo'lishi kerak
                if (text.equalsIgnoreCase("O'zbekcha") || text.equalsIgnoreCase("Uzbek") || text.equalsIgnoreCase("Uz")) {
                    slideLang.put(chatId, "O'zbekcha");
                    sendText(chatId, "📑 Yaxshi. Mavzuni kiriting:");
                    return;
                } else if (text.equalsIgnoreCase("Русский") || text.equalsIgnoreCase("Рус") || text.equalsIgnoreCase("Russian")) {
                    slideLang.put(chatId, "Русский");
                    sendText(chatId, "📑 Отлично. Введите тему:");
                    return;
                } else {
                    sendTextWithKeyboard(chatId, "Tilni tanlang:", slideLanguageKeyboard());
                    return;
                }
            }

            // 2) agar mavzu hali kiritilmagan bo'lsa - tempAnswers ga saqlaymiz va slayd sonini so'raymiz
            if (!tempAnswers.containsKey(chatId)) {
                tempAnswers.put(chatId, text); // mavzu
                pageCount.put(chatId, 2); // boshlang'ich 2 ta
                sendSlidesInline(chatId, text);
                return;
            }

            // 3) agar mavzu bor va user matn yuborsa — ular inline tugmalardan foydalanishlari kerak
            sendText(chatId, "🎞 Slaydlar sonini tanlash uchun pastdagi inline tugmalardan foydalaning (yoki /start bosing).");
        } catch (Exception e) {
            e.printStackTrace();
            sendText(chatId, "⚠️ Xatolik yuz berdi. /start bilan qaytadan boshlang.");
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
                case "inc" -> current += 1; // slaydlarni 1 taga oshiramiz (foydalanuvchi xohlaganicha)
                case "dec" -> current = Math.max(1, current - 1);
            }
            pageCount.put(chatId, current);

            if (data.equals("confirm_slides")) {
                // tayyor summary va narxni hisoblaymiz
                String lang = slideLang.getOrDefault(chatId, "—");
                String topic = tempAnswers.getOrDefault(chatId, "—");
                int count = pageCount.getOrDefault(chatId, 1);
                int pricePerSlide = 500;
                int total = count * pricePerSlide;

                String summary = "🎞 *Slayd so‘rovi*\n"
                        + "👤 Ism: " + firstName + "\n"
                        + "🔗 Username: " + username + "\n"
                        + "💬 ChatId: " + chatId + "\n"
                        + "🌐 Til: " + lang + "\n"
                        + "🧾 Mavzu: " + topic + "\n"
                        + "📊 Slaydlar: " + count + "\n"
                        + "💰 Narx: " + total + " so'm (" + pricePerSlide + " so'm / 1 slayd)";

                // saqlaymiz: agar foydalanuvchi chop etishni xohlasa keyin fayl yuboradi va biz forward qilamiz
                pendingSlideSummary.put(chatId, summary);

                // foydalanuvchiga umumiy ma'lumot va chop etish kerakmi degan savol
                sendText(chatId, summary);
                sendTextWithKeyboard(chatId, "🖨 Printerdan chiqarilsinmi?", yesNoKeyboard());

                // endi kutyapmiz Ha / Yo'q javobini
            } else {
                // agar faqat inc/dec bo'lsa — yangilangan countni edit qilib ko'rsatamiz
                editInlineCount(chatId, messageId, current, true);
            }

            AnswerCallbackQuery ack = new AnswerCallbackQuery();
            ack.setCallbackQueryId(cq.getId());
            execute(ack);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Inline markup ediit qilish uchun yordamchi
    private InlineKeyboardMarkup buildInlineMarkup(int current, boolean isSlide) {
        InlineKeyboardButton minus = new InlineKeyboardButton("-1");
        minus.setCallbackData("dec");
        InlineKeyboardButton plus = new InlineKeyboardButton("+1");
        plus.setCallbackData("inc");
        InlineKeyboardButton confirm = new InlineKeyboardButton(isSlide ? "Tasdiqlash (Slayd)" : "Tasdiqlash");
        confirm.setCallbackData(isSlide ? "confirm_slides" : "confirm_konspekt");
        return new InlineKeyboardMarkup(List.of(
                List.of(minus, plus),
                List.of(confirm)
        ));
    }

    private void editInlineCount(String chatId, int messageId, int current, boolean isSlide) {
        try {
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

    private void sendSlidesInline(String chatId, String topic) {
        sendPhoto(chatId,"images/slaydYasash.jpeg","");
        int current = pageCount.getOrDefault(chatId, 2);
        SendMessage sm = new SendMessage(chatId, "🎞 Mavzu: " + topic + "\nNechta slayd kerak?");
        sm.setReplyMarkup(buildInlineMarkup(current, true));
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    // === Printer (har qanday fayl turini qabul qiladi) ===
    private void handlePrinterFile(String chatId, Message msg, User from, String username) {
        try {
            String fileType = "Noma'lum";
            // aniqlash
            if (msg.hasDocument()) {
                Document doc = msg.getDocument();
                fileType = (doc.getFileName() != null ? doc.getFileName() : "Hujjat");
            } else if (msg.hasPhoto()) {
                fileType = "Rasm";
            } else if (msg.hasVideo()) {
                fileType = "Video";
            } else if (msg.hasAudio()) {
                fileType = "Audio";
            } else if (msg.hasVoice()) {
                fileType = "Voice";
            } else if (msg.hasVideoNote()) {
                fileType = "VideoNote";
            } else if (msg.hasSticker()) {
                fileType = "Sticker";
            }

            // Agar bu slayd uchun yuborilayotgan fayl bo'lsa, pendingSlideSummary ni ham yuboramiz
            String baseMsgToAdmin = "🖨 *Printer xizmati so‘rovi*\n"
                    + "👤 Ism: " + from.getFirstName() + "\n"
                    + "🔗 Username: " + username + "\n"
                    + "💬 ChatId: " + chatId + "\n"
                    + "📎 Fayl turi/nomi: " + fileType;

            AdminBot.notifyAdmin(baseMsgToAdmin);

            // Agar oldindan slayd so'rovi mavjud bo'lsa - adminga summary ham yuborilsin va fayl shu so'rov bilan biriktirilsin
            if (pendingSlideSummary.containsKey(chatId)) {
                String summ = pendingSlideSummary.get(chatId);
                AdminBot.notifyAdmin(summ);
            }

            // Forward the received message to all admins
            for (String adminId : AdminBot.getAdmins()) {
                ForwardMessage fwd = new ForwardMessage(adminId, chatId, msg.getMessageId());
                execute(fwd);
            }

            sendText(chatId, "✅ Faylingiz adminga yuborildi. Tez orada ko‘rib chiqiladi.");
            // tozalash: fayl yuborilgandan so'ng slayd kutishi va selectedService tozalanadi
            expectingPrintFile.remove(chatId);
            pendingSlideSummary.remove(chatId);
            selectedService.remove(chatId);
            tempAnswers.remove(chatId);
            slideLang.remove(chatId);
            pageCount.remove(chatId);

        } catch (Exception e) {
            e.printStackTrace();
            sendText(chatId, "⚠️ Fayl yuborishda xatolik yuz berdi!");
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
                sendPhoto(chatId,"images/uygaVazifa.jpeg","");
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
                sendPhoto(chatId,"images/loyhaIshi.jpeg","");
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
                // boshlang'ich: tilni so'rash
                slideLang.remove(chatId);
                tempAnswers.remove(chatId);
                pageCount.remove(chatId);
                pendingSlideSummary.remove(chatId);
                expectingPrintFile.remove(chatId);

                selectedService.put(chatId, "Slayd yasab berish");
                sendTextWithKeyboard(chatId, "📚 Slayd uchun tilni tanlang:", slideLanguageKeyboard());
            }
            case "Printer xizmati" -> {
                selectedService.put(chatId, "Printer xizmati");
                sendPhoto(chatId,"images/printer.jpeg","");
                sendText(chatId, "🖨 Iltimos, printerdan chiqariladigan faylni yuboring (har qanday format qabul qilinadi).");
            }
        }
    }

    // === CUSTOM TEXT (promo va hizmat tanlash) ===
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
            pageCount.put(chatId, 2);
            slideLang.remove(chatId);
            sendTextWithKeyboard(chatId, "📚 Slayd uchun tilni tanlang:", slideLanguageKeyboard());
        } else if (text.equalsIgnoreCase("Printer xizmati")) {
            selectedService.put(chatId, "Printer xizmati");
            sendText(chatId, "🖨 Iltimos, printerdan chiqariladigan faylni yuboring (har qanday format qabul qilinadi).");
        }
        // Slayd: Ha/Yo'q javoblarini qayta ishlash
        else if (text.equalsIgnoreCase("Ha") || text.equalsIgnoreCase("ha") || text.equalsIgnoreCase("Yes") || text.equalsIgnoreCase("yes")) {
            // printni xohlash
            if (pendingSlideSummary.containsKey(chatId)) {
                // foydalanuvchidan print uchun fayl yuborishi so'raladi
                expectingPrintFile.put(chatId, true);
                sendText(chatId, "🖨 Fayl yuboring (har qanday format). Fayl yuborilgach adminga slayd va fayl birgalikda yuboriladi.");
                // selectedService ni Printer xizmati qilib qo'yamiz, lekin biz expectingPrintFile ham tekshiramiz
                selectedService.put(chatId, "Printer xizmati");
            } else {
                sendText(chatId, "ℹ️ Sizda chap so‘rov topilmadi. /start bilan qayta boshlang.");
            }
        } else if (text.equalsIgnoreCase("Yo'q") || text.equalsIgnoreCase("yo'q") || text.equalsIgnoreCase("Yoq") || text.equalsIgnoreCase("yoq") || text.equalsIgnoreCase("No") || text.equalsIgnoreCase("no")) {
            // print kerak emas -> adminga summary yuborish
            if (pendingSlideSummary.containsKey(chatId)) {
                String summ = pendingSlideSummary.get(chatId);
                AdminBot.notifyAdmin(summ);
                sendText(chatId, "✅ So‘rovingiz adminlarga yuborildi. Tez orada javob keladi.");
                // tozalash
                pendingSlideSummary.remove(chatId);
                selectedService.remove(chatId);
                tempAnswers.remove(chatId);
                slideLang.remove(chatId);
                pageCount.remove(chatId);
            } else {
                sendText(chatId, "ℹ️ Sizda faollashtirilgan so‘rov topilmadi. /start bilan qayta boshlang.");
            }
        } else {
            sendText(chatId, "❌ Men bu buyruqni tushunmadim. /start bosing yoki Hizmatlar dan tanlang.");
        }
    }

    // === BOT START va yordamhci metodlar ===
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
                new KeyboardRow(List.of(new KeyboardButton("Printer xizmati"))),
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
        slideLang.remove(chatId);
        pendingSlideSummary.remove(chatId);
        expectingPrintFile.remove(chatId);
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
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (inputStream == null) {
                sendText(chatId, "❌ Rasm topilmadi: " + resourcePath);
                return;
            }
            InputFile photo = new InputFile(inputStream, "image.jpg");
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId);
            sendPhoto.setPhoto(photo);
            if (caption != null && !caption.isEmpty()) sendPhoto.setCaption(caption);
            execute(sendPhoto);
        } catch (Exception e) {
            e.printStackTrace();
            sendText(chatId, "⚠️ Rasm yuborishda xatolik yuz berdi!");
        }
    }

    // Konspekt inline (o'zgarmagan)
    private void sendKonspektInline(String chatId) {
        sendPhoto(chatId,"images/botga1.jpeg","");
        int current = pageCount.getOrDefault(chatId, 2);
        SendMessage sm = new SendMessage(chatId, "📘 Nechta bet kerak?");
        sm.setReplyMarkup(buildInlineMarkup(current, false));
        try { execute(sm); } catch (Exception e) { e.printStackTrace(); }
    }

    // Til tanlash klaviaturasi (ikki til birinchi o'rinda)
    private ReplyKeyboardMarkup slideLanguageKeyboard() {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        KeyboardRow r1 = new KeyboardRow();
        r1.add("O'zbekcha");
        r1.add("Русский");
        KeyboardRow r2 = new KeyboardRow();
        r2.add(BACK_TO_MAIN);
        kb.setKeyboard(List.of(r1, r2));
        return kb;
    }

    // Ha/Yo'q klaviaturasi
    private ReplyKeyboardMarkup yesNoKeyboard() {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        KeyboardRow r1 = new KeyboardRow();
        r1.add("Ha");
        r1.add("Yo'q");
        kb.setKeyboard(List.of(r1, new KeyboardRow(List.of(new KeyboardButton(BACK_TO_MAIN)))));
        return kb;
    }

    // Admin chaqirganda ishlaydigan statik metod (oldingi kodi saqlanib qoladi)
    public static void ishtugadiStatic(String chatId) {
        try {
            ServiceBot bot = ServiceBot.getInstance();
            bot.execute(new SendMessage(chatId, "✅ So‘rovingiz yakunlandi. Ish tayyor!"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
