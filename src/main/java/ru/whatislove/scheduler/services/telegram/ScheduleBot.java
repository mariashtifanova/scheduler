package ru.whatislove.scheduler.services.telegram;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.whatislove.scheduler.services.NotificationsService;
import ru.whatislove.scheduler.services.telegram.util.ManageEntity;

@Component
public class ScheduleBot extends TelegramLongPollingBot {

    private final String BOT_NAME;
    private final String BOT_TOKEN;
    private final CommandService commandService;
    private final NotificationsService notificationsService;

    @Autowired
    public ScheduleBot(@Value("${bot.name}") String botName, @Value("${bot.token}") String botToken,
                       CommandService commandService, NotificationsService notificationsService) {
        super();
        this.BOT_NAME = botName;
        this.BOT_TOKEN = botToken;
        this.notificationsService = notificationsService;
        TelegramBotsApi botsApi;
        try {
            botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(this);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        this.commandService = commandService;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    @Override
    public String getBotUsername() {
        return BOT_NAME;
    }

    @Override
    public void onUpdateReceived(Update update) {
        List<ManageEntity> next;
        long chatId;
        if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
            next = commandService.executeCommand(update.getCallbackQuery().getData(), chatId);
        } else {
            var msg = update.getMessage();
            chatId = msg.getChatId();
            next = commandService.executeCommand(msg.getText(), chatId);
        }

        for (ManageEntity manageEntity : next) {
            sendMessage(manageEntity.getChatId(), manageEntity.getMessage(), manageEntity.getKeyboardMarkup());
        }
    }

    public void sendMessage(Long who, String txt, ReplyKeyboard kb) {
        SendMessage sm = SendMessage.builder().chatId(who.toString())
                .parseMode("HTML").text(txt)
                .replyMarkup(kb).build();

        try {
            execute(sm);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    @Async
    @Scheduled(cron = "0 * * * * *")
    void sendSchedule() {
        Map<Long, ManageEntity> messages = notificationsService.sendSchedule();

        messages.forEach((student, msg) -> {
            sendMessage(student, msg.getMessage(), msg.getKeyboardMarkup());
        });
    }
}
