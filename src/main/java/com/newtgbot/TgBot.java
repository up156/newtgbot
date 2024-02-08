package com.newtgbot;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
@Slf4j
public class TgBot extends TelegramLongPollingBot {

    private static final String CHAT_ID = "";
    //id чата, где бот установлен админом

    private static HashMap<Long, User> users;
    private final WeatherStation weatherStation;
    private final NotionSender notionSender;
    private final Listener listener;
    private final Writer writer;
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
    }

    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasCallbackQuery()) {
            buttonTap(update);
            return;
        }

        var message = update.getMessage();
        var user = message.getFrom();
        Long id = user.getId();
        String textFromAudio = "";
        log.info(user.getFirstName() + " wrote " + message.getText());

        if (users.get(id) == null) {
            getStarted(message);
            return;
        }

        if (update.getMessage().hasVoice()) {
            try {
                File file = execute(new GetFile(update.getMessage().getVoice().getFileId()));
                java.io.File downloaded = downloadFile(file);

                textFromAudio = listener.convertAudio(update, downloaded);
                System.out.println(textFromAudio);
                sendText(message.getChatId(), "Я слышу вот так: " + textFromAudio);

            } catch (Exception e) {
                e.printStackTrace();
            }

        } else if (message.getText().length() > INCOMING_LIMIT) {
            sendText(message.getChatId(), "Слишком длинно.");
            return;
        }

        if (message.getText() == null) {
            if (!textFromAudio.isEmpty()) {
                update.getMessage().setText(textFromAudio);
                workWithMessage(update);
            }
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
            if (users.get(id).getIsAddingProducts()) {
                addGoodsFromReadyText(message.getText());
                deleteIncomingMessage(message);
                return;
            }
            if (users.get(id).getIsAddingTask()) {
                notionSender.sendTask(message.getText());
                deleteIncomingMessage(message);
                return;
            }
            workWithMessage(update);
        }
    }

    private void workWithMessage(Update update) {

        String text = update.getMessage().getText().toLowerCase();
        Message message = update.getMessage();
        User user = users.get(message.getFrom().getId());

        if (user.getIsAddingProducts()) {
            addGoodsFromReadyText(text);
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
            user.setProducts(new ArrayList<>(getCurrentProducts()));
            sendMenu(message.getChatId(), "<b>Checklist: </b>", getKeyboard(user));
            return;
        }
        if (text.equals("добавить")) {
            deleteIncomingMessage(message);
            sendMenu(message.getChatId(), "<b>что добавить: </b>", getKeyboardCancel());
            user.setIsAddingProducts(true);
            return;
        }

        if (text.equals("удалить список")) {
            removeAll();
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

    private HashSet<String> getCurrentProducts() {

        HashSet<String> result = new HashSet<>();
        try {
            Chat chat = execute(new GetChat(CHAT_ID));
            Message editMessage = chat.getPinnedMessage();
            String currentMessage = editMessage.getText();
            if (currentMessage.length() > 17) {
                currentMessage = currentMessage.substring(currentMessage.indexOf("Список в магазин") + 19);
                result.addAll(List.of(currentMessage.split(currentMessage.contains("\n") ? "\n" : ",")));
            }
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
        return result;
    }


    private void updateList(String text) {

        if (text.length() < 2) {
            return;
        }
        try {
            Chat chat = execute(new GetChat(CHAT_ID));
            Message editMessage = chat.getPinnedMessage();
            String currentMessage = editMessage.getText();
            EditMessageText editMessageText = EditMessageText.builder()
                    .messageId(editMessage.getMessageId())
                    .chatId(String.valueOf(editMessage.getChatId()))
                    .text(currentMessage + "\r\n" + text)
                    .build();
            execute(editMessageText);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void addGoodsFromReadyText(String temp) {
        StringBuilder stringBuilder = new StringBuilder();
        List<String> goods = new ArrayList<>(List.of(temp.split(temp.contains("\n") ? "\n" : ",")));
        goods.forEach(g -> {
            if (!g.isEmpty()) {
                stringBuilder.append(g.trim()).append("\r\n");
            }
        });
        updateList(stringBuilder.toString());
    }

    private void removeAll() {
        try {
            Chat chat = execute(new GetChat(CHAT_ID));
            Message editMessage = chat.getPinnedMessage();
            if (editMessage.getText().equals("Cписок в ленту:")) {
                return;
            }
            EditMessageText editMessageText = EditMessageText.builder()
                    .messageId(editMessage.getMessageId())
                    .chatId(String.valueOf(editMessage.getChatId()))
                    .text("Cписок в ленту:")
                    .build();
            execute(editMessageText);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteIncomingMessage(Message message) {
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

    private ReplyKeyboardMarkup getStartedKeyboard() {

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

    private InlineKeyboardMarkup getKeyboard(User user) {

        var isDone = InlineKeyboardButton.builder()
                .text("готово").callbackData("готово")
                .build();

        InlineKeyboardMarkup resultKeyboard = InlineKeyboardMarkup.builder()
                .keyboard(getButtons(user))
                .keyboardRow(List.of(isDone)).build();
        return resultKeyboard;
    }

    private List<List<InlineKeyboardButton>> getButtons(User user) {
        List<InlineKeyboardButton> listForCheck = user.getIsWorkingWithFirstPart() ?
                Products.getFirstHalf() : Products.getSecondHalf();
        var arrowButton = InlineKeyboardButton.builder()
                .text(user.getIsWorkingWithFirstPart() ? "->" : "<-")
                .callbackData(user.getIsWorkingWithFirstPart() ? "->" : "<-")
                .build();

        List<InlineKeyboardButton> result = new ArrayList<>(List.of());
        List<String> goods = user.getProducts();

        for (InlineKeyboardButton button : listForCheck) {
            if (!goods.contains(button.getText())) {
                result.add(button);
            }
        }

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> temp = new ArrayList<>();
        for (int i = 0; i < result.size(); i++) {
            if (i % 3 == 2) {
                keyboard.add(temp);
                temp = new ArrayList<>();
            }
            temp.add(result.get(i));
        }
        keyboard.add(temp);
        keyboard.add(List.of(arrowButton));
        return keyboard;
    }

    private void onQueryFinal(Update update) {

        User user = users.get(update.getCallbackQuery().getFrom().getId());
        Message lastMessage = update.getCallbackQuery().getMessage();
        deleteIncomingMessage(user.getLastMessage());
        user.setLastMessage(sendCustomKeyboard(
                lastMessage.getChatId(), " <b>Выбирай, что нужно сделать.</b> ", getStartedKeyboard()));
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        deleteIncomingMessage(lastMessage);
    }

    private void buttonTap(Update update) {
        try {
            Message message = update.getCallbackQuery().getMessage();
            Long chatId = message.getChatId();
            int messageId = message.getMessageId();
            String data = update.getCallbackQuery().getData();
            User user = users.get(update.getCallbackQuery().getFrom().getId());

            EditMessageReplyMarkup newKb = EditMessageReplyMarkup.builder()
                    .chatId(String.valueOf(chatId)).messageId(messageId).build();
            if (data.equals("/cancel")) {
                if (user.getIsAddingProducts()) {
                    user.setIsAddingProducts(false);
                    onQueryFinal(update);
                    return;
                }
                user.setIsAddingTask(false);
                onQueryFinal(update);
                return;
            }

            if (data.equals("готово")) {

                StringBuilder stringBuilder = new StringBuilder();
                for (String s : user.getProducts()) {
                    if (!(getCurrentProducts().contains(s) || getCurrentProducts().contains(s.toLowerCase()))) {
                        stringBuilder.append(s).append("\r\n");
                    }
                }
                updateList(stringBuilder.toString());
                sendText(chatId, "Отлично");
                onQueryFinal(update);
                return;
            }
            if (data.equals("->") || data.equals("<-")) {
                user.setIsWorkingWithFirstPart(!data.equals("->"));
            } else {
                List<String> products = user.getProducts();
                products.add(data);
                user.setProducts(products);
            }
            newKb.setReplyMarkup(getKeyboard(user));
            AnswerCallbackQuery close = AnswerCallbackQuery.builder()
                    .callbackQueryId(update.getCallbackQuery().getId()).build();
            execute(close);
            execute(newKb);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

}
