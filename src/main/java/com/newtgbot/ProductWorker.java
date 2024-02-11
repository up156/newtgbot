package com.newtgbot;

import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static com.newtgbot.TgBot.CHAT_ID;

public class ProductWorker {

    private final TgBot tgBot;

    public ProductWorker(TgBot tgBot) {
        this.tgBot = tgBot;
    }

    public HashSet<String> getCurrentProducts() {

        HashSet<String> result = new HashSet<>();
        try {
            Chat chat = tgBot.execute(new GetChat(CHAT_ID));
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

    void updateList(String text) {

        if (text.length() < 2) {
            return;
        }
        try {
            Chat chat = tgBot.execute(new GetChat(CHAT_ID));
            Message editMessage = chat.getPinnedMessage();
            String currentMessage = editMessage.getText();
            EditMessageText editMessageText = EditMessageText.builder()
                    .messageId(editMessage.getMessageId())
                    .chatId(String.valueOf(editMessage.getChatId()))
                    .text(currentMessage + "\r\n" + text)
                    .build();
            tgBot.execute(editMessageText);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    void addGoodsFromReadyText(String temp) {
        StringBuilder stringBuilder = new StringBuilder();
        List<String> goods = new ArrayList<>(List.of(temp.split(temp.contains("\n") ? "\n" : ",")));
        goods.forEach(g -> {
            if (!g.isEmpty()) {
                stringBuilder.append(g.trim()).append("\r\n");
            }
        });
        updateList(stringBuilder.toString());
    }

    public void removeAll() {
        try {
            Chat chat = tgBot.execute(new GetChat(CHAT_ID));
            Message editMessage = chat.getPinnedMessage();
            if (editMessage.getText().equals("Список в магазин:")) {
                return;
            }
            EditMessageText editMessageText = EditMessageText.builder()
                    .messageId(editMessage.getMessageId())
                    .chatId(String.valueOf(editMessage.getChatId()))
                    .text("Список в магазин:")
                    .build();
            tgBot.execute(editMessageText);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
}
