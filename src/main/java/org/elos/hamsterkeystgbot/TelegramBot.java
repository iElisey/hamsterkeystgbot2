package org.elos.hamsterkeystgbot;

import org.elos.hamsterkeystgbot.model.Keys;
import org.elos.hamsterkeystgbot.model.UserReferrals;
import org.elos.hamsterkeystgbot.model.UserSessions;
import org.elos.hamsterkeystgbot.repository.KeysRepository;
import org.elos.hamsterkeystgbot.repository.UserReferralsRepository;
import org.elos.hamsterkeystgbot.repository.UserSessionsRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

@Component
public class TelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final MessageSource messageSource;
    private final UserSessionsRepository userSessionsRepository;
    private final KeysRepository keysRepository;
    private final UserReferralsRepository userReferralsRepository;

    private final TelegramClient telegramClient = new OkHttpTelegramClient("7262202111:AAGGuvh6ltYnXkXU7PWHsQSp-AS_r9-Zn4E");

    @Autowired
    public TelegramBot(MessageSource messageSource, UserSessionsRepository userSessionsRepository, KeysRepository keysRepository, UserReferralsRepository userReferralsRepository) {
        this.messageSource = messageSource;
        this.userSessionsRepository = userSessionsRepository;
        this.keysRepository = keysRepository;
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


    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }


    @Override
    public void consume(Update update) {
        System.out.println("HI EVERYONE");
        if (update.hasMessage() && update.getMessage().hasText()) {
            String command = update.getMessage().getText();
            Long userId = update.getMessage().getFrom().getId();
            Long chatId = update.getMessage().getChatId();

            if (userSessionsRepository.findByUserId(userId).isPresent()) {
                String language = userSessionsRepository.findByUserId(userId).get().getLanguage();
                if (language == null || language.isEmpty()) {
                    promptLanguageSelection(chatId);
                    return;
                }
            }

            if (command.startsWith("/start")) {
                handleStartCommand(update);
            } else if (command.startsWith("/get_keys")) {
                handleGetKeysCommand(userId, chatId);
            } else if (command.startsWith("/change_language")) {
                if (userSessionsRepository.existsByUserId(userId)) {
                    handleChangeLanguageCommand(userId, chatId);
                } else {
                    handleStartCommand(update);
                }
            } else if (command.startsWith("/broadcast")) {
                if (userId == 975340794) {
                    handleBroadcastMessage();
                }
            }
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

    private void handleBroadcastMessage() {
        List<UserSessions> all = userSessionsRepository.findAll();
        for (UserSessions userSessions : all) {
            SendMessage sendMessage = new SendMessage(String.valueOf(userSessions.getChatId()),
                    getTextByLanguage(userSessions.getUserId(), "broadcast.message"));
            sendMessage.setParseMode("HTML");
            sendMessage.setReplyMarkup(InlineKeyboardMarkup.builder()
                    .keyboardRow(new InlineKeyboardRow(
                                    InlineKeyboardButton.builder()
                                            .text(getTextByLanguage(userSessions.getUserId(), "get.bonus.keys"))
                                            .callbackData("get.keys").build()
                            )
                    ).build());
        }
    }

    private void handleChangeLanguageCommand(Long userId, Long chatId) {
        selectNewLanguage(userId, chatId);
    }

    private void selectNewLanguage(Long userId, Long chatId) {
        String text;
        if (userSessionsRepository.findByUserId(userId).orElseThrow().getLanguage().equals("ru")) {
            text = "Пожалуйста, выберите новый язык";
        } else if (userSessionsRepository.findByUserId(userId).orElseThrow().getLanguage().equals("en")) {
            text = "Please, select new language";
        } else {
            text = "Please, select new language";
        }

        SendMessage sendMessage = new SendMessage(String.valueOf(chatId), text);

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
        sendMessage.setReplyMarkup(markup);

        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
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
        if (data.startsWith("get.keys")) {
            handleGetKeysCommand(userId, chatId);
        } else if (data.startsWith("new_lang_")) {
            String selectedLanguage = data.split("_")[2];
            UserSessions userSession = userSessionsRepository.findByUserId(userId).orElseGet(() -> {
                UserSessions userSessions = new UserSessions();
                userSessions.setUserId(userId);
                userSessions.setChatId(chatId);
                return userSessions;
            });

            if (userSession.getLanguage().equals(selectedLanguage)) {
                Message message = sendMessage(chatId, "already.selected.language");
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
                }, 10000);
                return;
            }

            userSession.setLanguage(selectedLanguage);
            userSessionsRepository.save(userSession);

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
            UserSessions userSession = userSessionsRepository.findByUserId(userId).orElseGet(() -> {
                UserSessions userSessions = new UserSessions();
                userSessions.setUserId(userId);
                userSessions.setChatId(chatId);
                return userSessions;
            });
            userSession.setLanguage(selectedLanguage);
            userSessionsRepository.save(userSession);
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
            UserSessions referrer = userSessionsRepository.findByUserId(referrerId).orElseThrow();
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
                    userSessionsRepository.save(referrer);
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
        System.out.println(userId);
        String[] args = message.getText().split(" ");
        if (userSessionsRepository.existsByUserId(userId)) {
            sendMessage(message.getChatId(), "already.registered");
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

                    UserSessions referrer = userSessionsRepository.findByUserId(referrerId).orElseThrow();
                    referrer.setBonusCount(referrer.getBonusCount() + 1);
                    userSessionsRepository.save(referrer);
                    notify_referrer(referrer.getChatId(), message.getFrom().getUserName() != null
                            ? message.getFrom().getUserName()
                            : message.getFrom().getFirstName() + (message.getFrom().getLastName() != null ? " " + message.getFrom().getLastName() : "")); //notify referrer about new referral, second parameter mean if username of user is null, we put a first and last names of referral
                }
            }

            // Register new user
            UserSessions userSession = new UserSessions();
            userSession.setUserId(userId);
            userSession.setChatId(message.getChatId());
            userSessionsRepository.save(userSession);
            promptLanguageSelection(message.getChatId());
        }
    }

    private void welcomeMessage(Long chatId) {
        sendMessage(chatId, "welcome.message");
        SendMessage sendMessage = new SendMessage(String.valueOf(chatId), getTextByLanguage(chatId, "welcome.message"));
        sendMessage.setParseMode("HTML");
        sendMessage.setReplyMarkup(InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text(getTextByLanguage(chatId, "get.bonus.keys"))
                                        .callbackData("get.keys").build()
                        )
                ).build());
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
            String text = getTextByLanguage(userId, "not.subscribed");
            SendMessage sendMessage = new SendMessage(String.valueOf(chatId), text);
            sendMessage.setText(text);
            sendMessage.setParseMode("HTML");

            InlineKeyboardMarkup linkMarkup = InlineKeyboardMarkup.builder()
                    .keyboardRow(new InlineKeyboardRow(InlineKeyboardButton
                            .builder()
                            .text(getTextByLanguage(userId, "go.to.channel"))
                            .url("https://t.me/KeysHamsterKombatChannel")
                            .build()
                    )).build();
            sendMessage.setReplyMarkup(linkMarkup);
            try {
                telegramClient.execute(sendMessage);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        if (!canUserGetKeys(userId, chatId)) {
            return;
        }

        sendKeys(userId, chatId);

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                sendReferralLink(userId, chatId);
            }
        }, 30000); // 30 seconds
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
        if (!areKeysAvailable()) {
            sendMessage(chatId, "not.enough.keys");
            return false;
        }

        UserSessions userSession = userSessionsRepository.findByUserId(userId).orElseGet(() -> {
            // New user
            UserSessions newUserSession = new UserSessions();
            newUserSession.setUserId(userId);
            newUserSession.setChatId(chatId);
            newUserSession.setLastRequest(ZonedDateTime.now(zone).toLocalDateTime());
            return userSessionsRepository.save(newUserSession);
        });
        LocalDateTime lastRequest = userSession.getLastRequest();
        if (lastRequest != null) {
            ZonedDateTime lastRequestZone = lastRequest.atZone(zone);
            ZonedDateTime nextAvailableRequest = lastRequestZone.toLocalDate().plusDays(1).atStartOfDay(zone);
            ZonedDateTime now = ZonedDateTime.now(zone);
            if (now.isBefore(nextAvailableRequest)) {
                long hoursUntilNextRequest = ChronoUnit.HOURS.between(now, nextAvailableRequest);
                long minutesUntilNextRequest = ChronoUnit.MINUTES.between(now, nextAvailableRequest) % 60;

                String remainingTime;
                String strhours = "";
                String strmins = "";
                if (userSession.getLanguage().equals("ru")) {
                    strhours = "часов";
                    strmins = "минут";
                } else if (userSession.getLanguage().equals("en")) {
                    strhours = "hours";
                    strmins = "minutes";
                }
                if (hoursUntilNextRequest > 0) {
                    remainingTime = String.format("%d %s %d %s", hoursUntilNextRequest, strhours, minutesUntilNextRequest, strmins);
                } else {
                    remainingTime = String.format("%d %s", minutesUntilNextRequest, strmins);
                }

                sendMessage(chatId, "remaining.time", remainingTime);
                return false;
            } else {
                userSession.setLastRequest(ZonedDateTime.now(zone).toLocalDateTime());
                userSessionsRepository.save(userSession);
            }
        }

        // Update last request time
        userSession.setLastRequest(ZonedDateTime.now(zone).toLocalDateTime());
        userSessionsRepository.save(userSession);
        return true;
    }

    private boolean areKeysAvailable() {
        String[] prefixes = {"BIKE", "CUBE", "TRAIN", "CLONE"};
        for (String prefix : prefixes) {
            long count = keysRepository.countByPrefix(prefix);
            if (count < 4) {
                return false;
            }
        }
        return true;
    }

    private void sendKeys(Long userId, Long chatId) {
        String[] prefixes = {"BIKE", "CUBE", "TRAIN", "CLONE", "MERGE"};
        StringBuilder keyBatch = new StringBuilder(getTextByLanguage(userId, "your.keys"));
        for (String prefix : prefixes) {
            List<Keys> keys = keysRepository.findTop4ByPrefix(prefix);
            if (keys.size() < 4) {
                sendMessage(chatId, "not.enough.keys.prefix", "<b>" + prefix + "</b>.");
                return;
            }
            for (Keys key : keys) {
                keyBatch.append("<code>").append(prefix).append("-").append(key.getKeyValue()).append("</code>").append("\n");
            }
            keyBatch.append("\n");
            keysRepository.deleteAll(keys);
        }
        SendMessage sendMessage = new SendMessage(String.valueOf(chatId), keyBatch.toString());
        sendMessage.setParseMode("HTML");
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendReferralLink(Long userId, Long chatId) {
        String referralLink = "https://t.me/hamster_komb_keys_bot?start=" + userId;
        sendMessage(chatId, "referral.link", referralLink);
    }

    private String getTextByLanguage(Long userId, String messageKey, Object... args) {
        String language = userSessionsRepository.findByUserId(userId).orElseThrow().getLanguage();
        Locale locale = new Locale(language);
        return messageSource.getMessage(messageKey, args, locale);
    }

    private Message sendMessage(Long chatId, String messageKey, Object... args) {
        String languageCode = userSessionsRepository.findByUserId(chatId).orElseThrow().getLanguage();
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