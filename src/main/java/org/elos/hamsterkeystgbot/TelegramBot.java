package org.elos.hamsterkeystgbot;

import org.elos.hamsterkeystgbot.model.Keys;
import org.elos.hamsterkeystgbot.model.User;
import org.elos.hamsterkeystgbot.model.UserReferrals;
import org.elos.hamsterkeystgbot.repository.UserReferralsRepository;
import org.elos.hamsterkeystgbot.service.KeysService;
import org.elos.hamsterkeystgbot.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class TelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    public static final String referralURL = "https://t.me/hamster_komb_keys_bot?start=";
    public static long adminId = 975340794;
    public static boolean broadcastMessageSended = false;
    public static boolean receivedNewKeys = false;

    private final MessageSource messageSource;
    private final UserService userService;
    private final KeysService keysService;
    private final UserReferralsRepository userReferralsRepository;




    @Autowired
    public TelegramBot(MessageSource messageSource,
                       UserService userService,
                       KeysService keysService,
                       UserReferralsRepository userReferralsRepository) {
        this.messageSource = messageSource;
        this.userService = userService;
        this.keysService = keysService;
        this.userReferralsRepository = userReferralsRepository;
    }

    @Value("${telegram.bot.token}")
    private String botToken;


    @Value("${telegram.channel.id}")
    private String channelId;

    @Override
    public String getBotToken() {
        return botToken;
    }

    private final TelegramClient telegramClient = new OkHttpTelegramClient("7262202111:AAGGuvh6ltYnXkXU7PWHsQSp-AS_r9-Zn4E");


    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }


    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String command = update.getMessage().getText();
            Long userId = update.getMessage().getFrom().getId();
            Long chatId = update.getMessage().getChatId();

            User user = userService.findByUserId(userId);
            if (user != null) {
                String language = user.getLanguage();
                if (language == null || language.isEmpty()) {
                    promptLanguageSelection(chatId);
                    return;
                }
            }
            if (command.startsWith("/start")) {
                handleStartCommand(update);
            }
            switch (command) {
                case "/get_keys":
                    handleGetKeysCommand(userId, chatId);
                    break;
                case "/change_language":
                    if (userService.existsByUserId(userId)) {
                        handleChangeLanguageCommand(userId, chatId);
                    } else {
                        handleStartCommand(update);
                    }
                    break;
                case "/referrals":
                    if (userService.existsByUserId(userId)) {
                        handleReferrals(userId, chatId);
                    } else {
                        handleStartCommand(update);
                    }
                    break;

            }
            if (userId == adminId) {
                switch(command) {
                    case "/broadcast":
                        if (broadcastMessageSended) {
                            sendMessageByText(userId, "<b>\uD83D\uDEA8 Broadcast message have already sent.</b>\nWrite a command /again_broadcast for broadcast recovery.");
                        } else {
                            handleBroadcastMessageAsync(userId);
                        }
                        break;
                    case "/again_broadcast":
                        broadcastMessageSended = false;
                        sendMessageByText(userId, "<b>✅ Broadcast message restored.");
                        break;
                    case "/amount_of_keys":
                        handleAmountOfKeys(userId);
                        break;
                    case "/set_received_new_keys":
                        handleReceivedNewKeys(userId);
                        break;
                }
            }


        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

    private void handleReceivedNewKeys(Long userId) {
        String text;
        if (receivedNewKeys) {
            text = "<b>❗\uFE0FYou have already set received new keys!</b>";
        } else {
            text = "✅ Received new keys is set to <b>false</b>!";
            receivedNewKeys = true;
            userService.setReceivedNewKeys();
        }
        sendMessageByText(userId, text);
    }

    private void handleAmountOfKeys(Long userId) {
       String amount = keysService.getKeysAmount();
       sendMessageByText(userId, amount);
    }

    private void handleReferrals(Long userId, Long chatId) {
        int invitedFriends = userReferralsRepository.findByReferrerId(userId).size();
        sendMessage(chatId, "referral", invitedFriends, referralURL + userId);
    }

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public void handleBroadcastMessageAsync(Long userId) {
        executorService.submit(() -> handleBroadcastMessage(userId));
    }


    private void handleBroadcastMessage(Long userId) {
        List<User> all = userService.findAll();
        int blockedUsers = 0;
        for (User user : all) {
            InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                    .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder()
                                            .text(getTextByLanguage(user.getUserId(), "get.bonus.keys"))
                                            .callbackData("get.keys").build(),
                                    InlineKeyboardButton.builder()
                                            .text(getTextByLanguage(user.getUserId(), "invite.friend"))
                                            .callbackData("invite.friend").build()
                            )
                    ).build();
            try {
                sendMessageByMessageKey(user.getChatId(), "broadcast.message", markup);
                System.out.println("Success! " + user.getChatId());
            } catch (TelegramApiException e) {
                // Если бот заблокирован пользователем, логируем и продолжаем выполнение цикла
                if (e.getMessage().contains("bot was blocked by the user")) {
                    blockedUsers++;
//                    String text = "❌ <b>User <i>"+user.getChatId()+"</i> blocked the bot</b>";
//                    System.out.println(text);
//                    sendMessageByText(userId, text);
                } else {
                    System.out.println("error: " + e.getMessage());
                }
            }

        }
        broadcastMessageSended = true;
        sendMessageByText(userId, "✅ <b>Broadcast message sent to all users.</b>\n❌ Users who blocked the bot: <i>" + blockedUsers+"</i>");
    }

    private void handleChangeLanguageCommand(Long userId, Long chatId) {
        selectNewLanguage(userId, chatId);
    }

    private void selectNewLanguage(Long userId, Long chatId) {
        String text;
        if (userService.findByUserId(userId).getLanguage().equals("ru")) {
            text = "Пожалуйста, выберите новый язык";
        } else {
            text = "Please, select new language";
        }

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("English")
                                        .callbackData("new_lang_en").build(),
                                InlineKeyboardButton.builder()
                                        .text("Русский")
                                        .callbackData("new_lang_ru").build()
                        )
                ).build();
        sendMessageByText(chatId, text, markup);
    }

    private void promptLanguageSelection(Long chatId) {
        String text = "Пожалуйста, выберите язык / Please select your language";
        SendMessage sendMessage = new SendMessage(String.valueOf(chatId), text);

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("English")
                                        .callbackData("lang_en").build(),
                                InlineKeyboardButton.builder()
                                        .text("Русский")
                                        .callbackData("lang_ru").build()
                        )
                ).build();
        sendMessage.setReplyMarkup(markup);

        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            System.out.println(e.getMessage());
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long userId = callbackQuery.getFrom().getId();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        if (data.startsWith("invite.friend")) {
            handleReferrals(userId,chatId);
        } else if (data.startsWith("get.keys")) {
            handleGetKeysCommand(userId, chatId);
        } else if (data.startsWith("new_lang_")) {
            String selectedLanguage = data.split("_")[2];
            User user = userService.findByUserId(userId);
            if (user == null) {
                user = new User();
                user.setUserId(userId);
                user.setChatId(chatId);
            }

            if (user.getLanguage().equals(selectedLanguage)) {
                Message message = sendMessage(chatId, "already.selected.language", selectedLanguage);
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        DeleteMessage deleteMessage = DeleteMessage.builder()
                                .messageId(message.getMessageId()).chatId(message.getChatId()).build();
                        try {
                            telegramClient.execute(deleteMessage);
                        } catch (TelegramApiException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }, 6500);
                return;
            }

            user.setLanguage(selectedLanguage);
            userService.save(user);

            String editText = "";
            if (selectedLanguage.equals("ru")) {
                editText = "<b>Язык \uD83C\uDDF7\uD83C\uDDFA RU выбран!</b>";
            } else if (selectedLanguage.equals("en")) {
                editText = "<b>Language \uD83C\uDDEC\uD83C\uDDE7 EN selected!</b>";
            }

            EditMessageText editMessageText = new EditMessageText(editText);
            editMessageText.setChatId(chatId);
            editMessageText.setMessageId(messageId);
            editMessageText.setParseMode("HTML");

            try {
                telegramClient.execute(editMessageText);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        } else if (data.startsWith("lang_")) {
            String selectedLanguage = data.split("_")[1];
            User user = userService.findByUserId(userId);
            if (user == null) {
                user = new User();
                user.setUserId(userId);
                user.setChatId(chatId);
            }
            user.setLanguage(selectedLanguage);
            userService.save(user);
            String text = "";
            EditMessageText editMessageText = new EditMessageText(text);
            editMessageText.setChatId(chatId);
            if (selectedLanguage.equals("ru")) {
                text = String.format("<b>Язык \uD83C\uDDF7\uD83C\uDDFA%s выбран.</b> Теперь вы можете использовать бота.\n/get_keys - чтобы получить ключи в Хамстер Комбат", selectedLanguage);
            } else if (selectedLanguage.equals("en")) {
                text = String.format("<b>Language \uD83C\uDDEC\uD83C\uDDE7%s selected.</b> Now you can use the bot.\n/get_keys - to get keys for Hamster Kombat", selectedLanguage);
            }
            editMessageText.setText(text);
            editMessageText.setMessageId(messageId);
            editMessageText.setParseMode("HTML");
            try {
                telegramClient.execute(editMessageText);
                welcomeMessage(chatId);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        } else if (data.startsWith("get_bonus_keys")) {
            Long referrerId = Long.valueOf(data.split(":")[1]);
            String referralName = data.split(":")[2];
            User referrer = userService.findByUserId(referrerId);
            int bonusCount = referrer.getBonusCount();
            if (bonusCount < 1) {
                sendMessage(referrer.getChatId(), "already.received.keys", "<b>" + referralName + "</b>!");
            } else {
                referrer.setBonusCount(bonusCount - 1);
                EditMessageText editMessageText = new EditMessageText(getTextByLanguage(userId, "received.keys", "<b>" + referralName + "</b>!"));
                editMessageText.setChatId(String.valueOf(chatId));
                editMessageText.setMessageId(messageId);
                editMessageText.setParseMode("HTML");

                try {
                    userService.save(referrer);
                    sendKeys(callbackQuery.getFrom().getId(), chatId);
                    telegramClient.execute(editMessageText);
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void handleStartCommand(Update update) {
        Message message = update.getMessage();
        Long userId = message.getFrom().getId();
        Long chatId = message.getChatId();
        String[] args = message.getText().split(" ");
        if (userService.existsByUserId(userId)) {
            sendMessage(chatId, "already.registered");
        } else {
            //Referral system
            if (args.length > 1) {
                Long referrerId = Long.parseLong(args[1]);
                if (!referrerId.equals(userId)) {
                    // Handle referral logic
                    UserReferrals userReferrals = new UserReferrals();
                    userReferrals.setUserId(userId);
                    userReferrals.setReferrerId(referrerId);
                    userReferralsRepository.save(userReferrals);
                    User referrer = userService.findByUserId(referrerId);
                    referrer.setBonusCount(referrer.getBonusCount() + 1);
                    userService.save(referrer);
                    notify_referrer(referrer.getChatId(), message.getFrom().getUserName() != null
                            ? message.getFrom().getUserName()
                            : message.getFrom().getFirstName() + (message.getFrom().getLastName() != null ? " " + message.getFrom().getLastName() : "")); //notify referrer about new referral, second parameter mean if username of user is null, we put a first and last names of referral
                }
            }

            // Register new user
            User user = new User();
            user.setUserId(userId);
            user.setChatId(chatId);
            userService.save(user);
            promptLanguageSelection(chatId);
        }
    }

    private void welcomeMessage(Long chatId) throws TelegramApiException {
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text(getTextByLanguage(chatId, "get.bonus.keys"))
                                        .callbackData("get.keys").build()
                        )
                ).build();
        sendMessageByMessageKey(chatId, "welcome.message", markup);
    }


    private void notify_referrer(Long referrerId, String referralName) {
        SendMessage sendMessage = new SendMessage(String.valueOf(referrerId), getTextByLanguage(referrerId, "invited.friend", referralName));
        sendMessage.setParseMode("HTML");


        InlineKeyboardMarkup referralMarkup = InlineKeyboardMarkup
                .builder()
                .keyboardRow(
                        new InlineKeyboardRow(InlineKeyboardButton
                                .builder()
                                .text(getTextByLanguage(referrerId, "get.bonus.keys"))
                                .callbackData("get_bonus_keys:" + referrerId + ":" + referralName)
                                .build()
                        )
                )
                .build();
        sendMessage.setReplyMarkup(referralMarkup);

        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }

    }

    private void handleGetKeysCommand(Long userId, Long chatId) {
        if (!isUserSubscribed(userId)) {
            InlineKeyboardMarkup linkMarkup = InlineKeyboardMarkup.builder()
                    .keyboardRow(new InlineKeyboardRow(InlineKeyboardButton
                            .builder()
                            .text(getTextByLanguage(userId, "go.to.channel"))
                            .url("https://t.me/KeysHamsterKombatChannel")
                            .build()
                    )).build();
            try {
                sendMessageByMessageKey(chatId, "not.subscribed", linkMarkup);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        if (!canUserGetKeys(userId, chatId)) {
            User user = userService.findByUserId(userId);
            if (user.getReceivedNewKeys() == null || !user.getReceivedNewKeys()) {
                sendKeysByPrefix(userId, chatId, "HIDE");
                user.setReceivedNewKeys(true);
                userService.save(user);
            } else {
                sendMessage(chatId, "remaining.time", userService.getRemainingTimeToNextKeys(user));
            }
            return;
        }

        sendKeys(userId, chatId);
        User user = userService.findByUserId(userId);
        user.setReceivedNewKeys(true);
        userService.save(user);

        long randomMilliseconds = 35000 + new Random().nextLong(75000 - 35000);
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                sendReferralLink(userId, chatId);
            }
        }, randomMilliseconds); // 30 seconds
    }

    private void sendKeysByPrefix(Long userId, Long chatId, String... prefix) {
        StringBuilder keyBatch = new StringBuilder(getTextByLanguage(userId, "your.keys"));
        List<Keys> keys = keysService.findTop4ByPrefixes(prefix);
        if (keys.size() < 4) {
            sendMessage(chatId, "not.enough.keys.prefix", "<b>" + Arrays.toString(prefix) + "</b>.");
            return;
        } else {
            String lastPrefix = "";
            for (Keys key : keys) {
                if (!lastPrefix.isEmpty() && !lastPrefix.equals(key.getPrefix())) {
                    keyBatch.append("\n"); // Добавляем пустую строку при смене префикса
                }
                keyBatch.append("<code>").append(key.getPrefix()).append("-").append(key.getKeyValue()).append("</code>").append("\n");
                lastPrefix = key.getPrefix();
            }
        }
        keyBatch.append("\n");
        keysService.deleteAll(keys);
        sendMessageByText(chatId, keyBatch.toString());
    }

    private boolean isUserSubscribed(Long userId) {
        try {
            ChatMember chatMember = telegramClient.execute(new GetChatMember(channelId, userId));
            return chatMember.getStatus().equals("member") || chatMember.getStatus().equals("administrator") || chatMember.getStatus().equals("creator");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean canUserGetKeys(Long userId, Long chatId) {
        ZoneId zone = ZoneId.of("Europe/Moscow");
        // Check if keys are available
        if (!keysService.areKeysAvailable()) {
            sendMessage(chatId, "not.enough.keys");
            return false;
        }

        User user = userService.findByUserId(userId);
        LocalDateTime lastRequest = user.getLastRequest();
        if (lastRequest != null) {
            ZonedDateTime lastRequestZone = lastRequest.atZone(zone);
            ZonedDateTime nextAvailableRequest = lastRequestZone.toLocalDate().plusDays(1).atStartOfDay(zone);
            ZonedDateTime now = ZonedDateTime.now(zone);
            if (now.isBefore(nextAvailableRequest)) {
                return false;
            } else {
                user.setLastRequest(ZonedDateTime.now(zone).toLocalDateTime());
                userService.save(user);
            }
        }

        // Update last request time
        user.setLastRequest(ZonedDateTime.now(zone).toLocalDateTime());
        userService.save(user);
        return true;
    }


    private void sendKeys(Long userId, Long chatId) {
        sendMessageByText(chatId, keysService.getKeys(userService.findByUserId(userId)));
    }

    private void sendReferralLink(Long userId, Long chatId) {
        String referralLink = referralURL + userId;
        sendMessage(chatId, "referral.link", referralLink);
    }

    private String getTextByLanguage(Long userId, String messageKey, Object... args) {
        String language = userService.findByUserId(userId).getLanguage();
        Locale locale = new Locale(language == null ? "ru" : language);
        return messageSource.getMessage(messageKey, args, locale);
    }

    private Message sendMessageByText(Long chatId, String text, InlineKeyboardMarkup... markup) {
        SendMessage sendMessage = new SendMessage(String.valueOf(chatId), text);
        sendMessage.setParseMode("HTML");
        if (markup.length > 0) {
            sendMessage.setReplyMarkup(markup[0]);
        }
        try {
            return telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private Message sendMessageByMessageKey(Long chatId, String messageKey, InlineKeyboardMarkup markup, Object... args) throws TelegramApiException {
        SendMessage sendMessage = new SendMessage(String.valueOf(chatId), getTextByLanguage(chatId, messageKey, args));
        sendMessage.setParseMode("HTML");
        sendMessage.setReplyMarkup(markup);

        return telegramClient.execute(sendMessage);

    }

    private Message sendMessage(Long chatId, String messageKey, Object... args) {

        String languageCode = userService.findByUserId(chatId).getLanguage();
        Locale locale = new Locale(languageCode);
        String text = messageSource.getMessage(messageKey, args, locale);
        SendMessage message = new SendMessage(String.valueOf(chatId), text);
        message.setText(text);
        message.setParseMode("HTML");
        try {
            return telegramClient.execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }

    }

}