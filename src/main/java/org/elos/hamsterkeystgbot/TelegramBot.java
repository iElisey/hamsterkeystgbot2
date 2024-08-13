package org.elos.hamsterkeystgbot;

import org.elos.hamsterkeystgbot.model.Keys;
import org.elos.hamsterkeystgbot.model.UserReferrals;
import org.elos.hamsterkeystgbot.model.UserSessions;
import org.elos.hamsterkeystgbot.repository.KeysRepository;
import org.elos.hamsterkeystgbot.repository.UserReferralsRepository;
import org.elos.hamsterkeystgbot.repository.UserSessionsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

@Component
public class TelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {


    private final UserSessionsRepository userSessionsRepository;
    private final KeysRepository keysRepository;
    private final UserReferralsRepository userReferralsRepository;

    private final TelegramClient telegramClient = new OkHttpTelegramClient("7262202111:AAGGuvh6ltYnXkXU7PWHsQSp-AS_r9-Zn4E");


    public TelegramBot(@Autowired UserSessionsRepository userSessionsRepository,@Autowired KeysRepository keysRepository, @Autowired UserReferralsRepository userReferralsRepository) {
        this.userSessionsRepository = userSessionsRepository;
        this.keysRepository = keysRepository;
        this.userReferralsRepository = userReferralsRepository;
    }

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

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

            if (command.startsWith("/start")) {
                handleStartCommand(update);
            } else if (command.startsWith("/get_keys")) {
                handleGetKeysCommand(userId, chatId);
            }
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        if(data.startsWith("get_bonus_keys")) {
            Long referrerId = Long.valueOf(data.split(":")[1]);
            String referralUsername = data.split(":")[2];
            UserSessions referrer = userSessionsRepository.findByUserId(referrerId).orElseThrow();
            int bonusCount = referrer.getBonusCount();
            if (bonusCount < 1) {
                sendMessage(referrer.getChatId(), "⚠\uFE0F You have already received the keys for the invited friend <b>" + referralUsername + "</b>!");
                return;
            } else {
                referrer.setBonusCount(bonusCount - 1);

                Integer messageId = callbackQuery.getMessage().getMessageId();
                Long chatId = callbackQuery.getMessage().getChatId();
                EditMessageText editMessageText = new EditMessageText("⬇\uFE0F✅ You have successfully received keys for your invited friend <b>"+referralUsername+"</b>!");
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
            sendMessage(message.getChatId(), "<b>You are already registered.</b>");
        } else {
            userSessionsRepository.findByUserId(userId).orElseGet(() -> {

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
                                : message.getFrom().getFirstName() + " " + message.getFrom().getLastName()); //notify referrer about new referral, second parameter mean if username of user is null, we put a first and last names of referral
                    }
                }

                // Register new user
                UserSessions userSession = new UserSessions();
                userSession.setUserId(userId);
                userSession.setChatId(message.getChatId());
                return userSessionsRepository.save(userSession);

            });
            sendMessage(message.getChatId(), "<b>Hello \uD83D\uDC4B \nI can send you keys for game Hamster Kombat \uD83D\uDC39</b>\nSend a command /get_keys to check it");
        }


    }

    private void notify_referrer(Long referrerId, String referralName) {
        SendMessage sendMessage = new SendMessage(String.valueOf(referrerId), "\uD83C\uDF89 <b>You invited a friend: "+referralName+"</b>!\nClick the button below to get your keys.");
        sendMessage.setParseMode("HTML");

        InlineKeyboardMarkup referralMarkup = InlineKeyboardMarkup
                .builder()
                .keyboardRow(
                        new InlineKeyboardRow(InlineKeyboardButton
                                .builder()
                                .text("\uD83D\uDD11 Get keys")
                                .callbackData("get_bonus_keys:"+referrerId+":"+referralName)
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
            String text = "<b>❌You are not subscribed to our channel. Please subscribe to receive keys.</b>" +
                    "\nChannel link - https://t.me/KeysHamsterKombatChannel";
            SendMessage sendMessage = new SendMessage(String.valueOf(chatId),text);
            sendMessage.setText(text);
            sendMessage.setParseMode("HTML");

            InlineKeyboardMarkup linkMarkup = InlineKeyboardMarkup.builder()
                    .keyboardRow(new InlineKeyboardRow(InlineKeyboardButton
                            .builder()
                            .text("Go to channel")
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
        // Check if keys are available
        if (!areKeysAvailable()) {
            sendMessage(chatId, "<b>Not enough keys available. Try again later.</b>");
            return false;
        }

        UserSessions userSession = userSessionsRepository.findByUserId(userId).orElseGet(() -> {
            // New user
            UserSessions newUserSession = new UserSessions();
            newUserSession.setUserId(userId);
            newUserSession.setChatId(chatId);
            newUserSession.setLastRequest(LocalDateTime.now());
            return userSessionsRepository.save(newUserSession);
        });
        LocalDateTime lastRequest = userSession.getLastRequest();
        if (lastRequest != null) {
            ZonedDateTime lastRequestZone = lastRequest.atZone(ZoneId.of("Europe/Moscow"));
            ZonedDateTime nextAvailableRequest = lastRequestZone.toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("Europe/Moscow"));
            if (ZonedDateTime.now().isBefore(nextAvailableRequest)) {
                Duration duration = Duration.between(ZonedDateTime.now(), nextAvailableRequest);
                long hours = duration.toHours();
                long minutes = duration.toMinutes() % 60;

                String remainingTime;
                if (hours > 0) {
                    remainingTime = String.format("%d hours %d minutes", hours, minutes);
                } else {
                    remainingTime = String.format("%d minutes", minutes);
                }

                sendMessage(chatId, String.format("You have already received keys today.\nPlease try again in <b>%s</b>.", remainingTime));
                return false;
            } else {
                userSession.setLastRequest(LocalDateTime.now());
                userSessionsRepository.save(userSession);
            }
        }

        // Update last request time
        userSession.setLastRequest(LocalDateTime.now());
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
        String[] prefixes = {"BIKE", "CUBE", "TRAIN", "CLONE"};
        StringBuilder keyBatch = new StringBuilder("<b>🔑 Your keys:</b>\n\n");
        for (String prefix : prefixes) {
            List<Keys> keys = keysRepository.findTop4ByPrefix(prefix);
            if (keys.size() < 4) {
                sendMessage(chatId, "Not enough keys for prefix <b>" + prefix + "</b>.");
                return;
            }
            for (Keys key : keys) {
                keyBatch.append("<code>").append(prefix).append("-").append(key.getKeyValue()).append("</code>").append("\n");
            }
            keyBatch.append("\n");
            keysRepository.deleteAll(keys);
        }
        sendMessage(chatId, keyBatch.toString(), "HTML");
    }

    private void sendReferralLink(Long userId, Long chatId) {
        String referralLink = "https://t.me/hamster_komb_keys_bot?start=" + userId;
        String messageText = "<b>🔑Invite your friends and get additional keys!</b>\n\nYour referral link:\n" + referralLink;
        sendMessage(chatId, messageText, "HTML");
    }

    private void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, "HTML");
    }

    private void sendMessage(Long chatId, String text, String parseMode) {
        SendMessage message = new SendMessage(String.valueOf(chatId),text);
        message.setText(text);
        message.setParseMode(parseMode);
        try {
            telegramClient.execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}