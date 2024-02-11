package com.newtgbot;

import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

import static com.newtgbot.TgBot.*;

public class CallbackQueryWorker {

    private final TgBot tgBot;
    public CallbackQueryWorker(TgBot tgBot) {
        this.tgBot = tgBot;

    }

    void buttonTap(Update update) {
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
                    if (!(tgBot.getProductWorker().getCurrentProducts().contains(s) ||
                            tgBot.getProductWorker().getCurrentProducts().contains(s.toLowerCase()))) {
                        stringBuilder.append(s).append("\r\n");
                    }
                }
                tgBot.getProductWorker().updateList(stringBuilder.toString());
                tgBot.sendText(chatId, "Отлично");
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
            tgBot.execute(close);
            tgBot.execute(newKb);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void onQueryFinal(Update update) {

        User user = users.get(update.getCallbackQuery().getFrom().getId());
        Message lastMessage = update.getCallbackQuery().getMessage();
        tgBot.deleteIncomingMessage(user.getLastMessage());
        user.setLastMessage(tgBot.sendCustomKeyboard(
                lastMessage.getChatId(), " <b>Выбирай, что нужно сделать.</b> ", getStartedKeyboard()));
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        tgBot.deleteIncomingMessage(lastMessage);
    }

    InlineKeyboardMarkup getKeyboard(User user) {

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
}
