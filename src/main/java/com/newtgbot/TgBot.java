package com.newtgbot;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
@Slf4j
public class TgBot extends TelegramLongPollingBot {

    static final String CHAT_ID = "";
    //id чата, где бот установлен админом

    public static HashMap<Long, User> users;
    private final WeatherStation weatherStation;
    private final NotionSender notionSender;
    private final Listener listener;
    private final Writer writer;

    private final CallbackQueryWorker callbackQueryWorker;
    @Getter
    private final ProductWorker productWorker;

    private final static int INCOMING_LIMIT = 1000;
    @Getter
    @Setter
    private List<Message> messages;

    @Override
    public String getBotUsername() {
        return "tgBot";
    }

    @Override
    public String getBotToken() {
        return "";
//        get telegram bot token
    }

    public TgBot() {
        log.info("tgbot has started");
        users = new HashMap<>();
        weatherStation = new WeatherStation();
        notionSender = new NotionSender();
        listener = new Listener();
        writer = new Writer();
        messages = new ArrayList<>();
        productWorker = new ProductWorker(this);
        callbackQueryWorker = new CallbackQueryWorker(this);

    }

    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasCallbackQuery()) {
            callbackQueryWorker.buttonTap(update);
            return;
        }

        logIncomingMessage(update);
        Message message = update.getMessage();

        if (checkIfNewUser(message)) {
            getStarted(message);
            return;
        }

        if (update.getMessage().hasVoice()) {
            update.getMessage().setText(workWithVoiceMessage(update));
        }

        else if (message.getText().length() > INCOMING_LIMIT) {
            sendText(message.getChatId(), "Слишком длинно.");
            return;
        }

        if (message.isCommand()) {
            if (message.getText().contains("/start")) {
                removeMessages();
                writer.setNewHistory();
                users = new HashMap<>();
                getStarted(update.getMessage());
            }
            return;
        }

        if (message.getText().length() > 2) {
            if (users.get(message.getFrom().getId()).getIsAddingProducts()) {
                productWorker.addGoodsFromReadyText(message.getText());
                deleteIncomingMessage(message);
                return;
            }
            if (users.get(message.getFrom().getId()).getIsAddingTask()) {
                notionSender.sendTask(message.getText());
                deleteIncomingMessage(message);
                return;
            }
            workWithMessage(update);
        }
    }

    private void logIncomingMessage(Update update) {

        var message = update.getMessage();
        var user = message.getFrom();

        log.info(user.getFirstName() + " wrote " + message.getText());

    }

    private boolean checkIfNewUser(Message message) {
        return (users.get(message.getFrom().getId()) == null);
    }

    private String workWithVoiceMessage(Update update) {
        String textFromAudio = "";
        try {
            File file = execute(new GetFile(update.getMessage().getVoice().getFileId()));
            java.io.File downloaded = downloadFile(file);

            textFromAudio = listener.convertAudio(update, downloaded);
            log.info(textFromAudio);
            sendText(update.getMessage().getChatId(), "Я слышу вот так: " + textFromAudio);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return textFromAudio;
    }

    private void workWithMessage(Update update) {

        String text = update.getMessage().getText().toLowerCase();
        Message message = update.getMessage();
        User user = users.get(message.getFrom().getId());

        if (user.getIsAddingProducts()) {
            productWorker.addGoodsFromReadyText(text);
            deleteIncomingMessage(message);
            return;
        }

        if (text.toLowerCase().startsWith("нарисуй")) {
            text = text.substring(text.toLowerCase().indexOf("нарисуй"));
            try {
                execute(new SendPhoto(message.getChatId().toString(), new InputFile(writer.getPicture(text), "temp.jpg")));
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            return;
        }

        if (text.equals("список покупок")) {
            deleteIncomingMessage(message);
            user.setProducts(new ArrayList<>(productWorker.getCurrentProducts()));
            sendMenu(message.getChatId(), "<b>Checklist: </b>", callbackQueryWorker.getKeyboard(user));
            return;
        }
        if (text.equals("добавить")) {
            deleteIncomingMessage(message);
            sendMenu(message.getChatId(), "<b>что добавить: </b>", getKeyboardCancel());
            user.setIsAddingProducts(true);
            return;
        }

        if (text.equals("удалить список")) {
            productWorker.removeAll();
            getStarted(update.getMessage());
            return;
        }

        if (text.equals("поставить задачу")) {
            deleteIncomingMessage(message);
            user.setIsAddingTask(true);
            sendMenu(message.getChatId(), "<b>что отправить в Notion?: </b>", getKeyboardCancel());
            return;
        }

        if (text.equals("погода")) {

            this.getMessages().add(sendText(message.getChatId(), weatherStation.getWeather()));
            getStarted(update.getMessage());
            return;
        }

        if (text.equals("сбросить контекст")) {
            writer.setNewHistory();
            getStarted(update.getMessage());
            return;
        }

        this.getMessages().add(sendText(message.getChatId(), writer.getReply(message.getText())));
        this.getMessages().add(message);
    }

    public void deleteIncomingMessage(Message message) {
        try {
            execute(new DeleteMessage(String.valueOf(message.getChatId()), message.getMessageId()));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public Message sendText(Long who, String what) {
        SendMessage sm = SendMessage.builder()
                .chatId(who.toString())
                .text(what).build();
        try {
            return execute(sm);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendMenu(Long who, String txt, InlineKeyboardMarkup kb) {
        SendMessage sm = SendMessage.builder().chatId(who.toString())
                .parseMode("HTML").text(txt)
                .replyMarkup(kb).build();

        try {
            execute(sm);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void getStarted(Message message) {

        User user = users.get(message.getFrom().getId());
        if (user != null) {
            Message lastMessage = user.getLastMessage();
            if (lastMessage != null) {
                deleteIncomingMessage(lastMessage);
            }
            user.setLastMessage(sendCustomKeyboard(
                    message.getChatId(), " <b>Выбирай, что нужно сделать.</b> ", getStartedKeyboard()));

        } else {
            user = new User();
            user.setLastMessage(sendCustomKeyboard(message.getChatId(), " <b>Привет! \r\n" +
                    "Выбирай, что нужно сделать.</b> ", getStartedKeyboard()));
            users.put(message.getFrom().getId(), user);
        }
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        deleteIncomingMessage(message);

    }

    private void removeMessages() {
        for (User user : users.values()) {
            deleteIncomingMessage(user.getLastMessage());
        }
        for (Message message : messages) {
            deleteIncomingMessage(message);
        }
        this.messages = new ArrayList<>();
    }

    public Message sendCustomKeyboard(Long who, String txt, ReplyKeyboardMarkup kb) {
        SendMessage sm = SendMessage.builder().chatId(who.toString())
                .parseMode("HTML").text(txt)
                .replyMarkup(kb).build();

        try {
            return execute(sm);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    public static ReplyKeyboardMarkup getStartedKeyboard() {

        var checklist = KeyboardButton.builder()
                .text("чек-лист")
                .build();

        var delete = KeyboardButton.builder()
                .text("удалить список")
                .build();

        var task = KeyboardButton.builder()
                .text("поставить задачу")
                .build();

        var weather = KeyboardButton.builder()
                .text("погода")
                .build();

        var add = KeyboardButton.builder()
                .text("добавить")
                .build();

        var restart = KeyboardButton.builder()
                .text("сбросить контекст")
                .build();
        KeyboardRow firstRow = new KeyboardRow(List.of(checklist));
        KeyboardRow secondRow = new KeyboardRow(List.of(add));
        KeyboardRow thirdRow = new KeyboardRow(List.of(delete));
        KeyboardRow fourthRow = new KeyboardRow(List.of(task));
        KeyboardRow sixthRow = new KeyboardRow(List.of(weather));
        KeyboardRow seventhRow = new KeyboardRow(List.of(restart));

        ReplyKeyboardMarkup resultKeyboard = ReplyKeyboardMarkup.builder()
                .keyboardRow(firstRow)
                .keyboardRow(secondRow)
                .keyboardRow(thirdRow)
                .keyboardRow(fourthRow)
                .keyboardRow(sixthRow)
                .keyboardRow(seventhRow)
                .build();
        System.out.println(resultKeyboard);

        return resultKeyboard;
    }

    private InlineKeyboardMarkup getKeyboardCancel() {

        var cancel = InlineKeyboardButton.builder()
                .text("закончить").callbackData("/cancel")
                .build();
        List<InlineKeyboardButton> list = new ArrayList<>(List.of(cancel));
        return InlineKeyboardMarkup.builder()
                .keyboardRow(list)
                .build();
    }

}
