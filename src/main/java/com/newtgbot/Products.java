package com.newtgbot;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

public enum Products {

    HOUSEHOLD (List.of("Бумага", "Салфетки", "Влажные салфетки", "Для посуды", "Для унитаза", "Для сантехники")),
    GROCERY (List.of("Сладкое", "Кофе", "Чай", "Паста", "Греча", "Рис", "Мука", "Каша", "Масло оливковое",
             "Соус азиатский")),
    CANNED (List.of("Кукуруза", "Горошек", "Фасоль", "Тунец", "Шпроты", "Паштет")),
    BREAD (List.of("Хлеб обычный", "Хлеб зерновой")),
    VEGETABLES (List.of("Салат", "Чеснок", "Помидоры", "Черри", "Авокадо", "Баклажан", "Перец", "Огурцы",
            "Картофель", "Шампиньоны", "Вешенки", "Зелень", "Шпинат", "Руккола")),
    FRUITS (List.of("Бананы","Сливы","Мандарины","Груши","Помелло","Сезонные фрукты")),
    MEAT (List.of("Мясо", "Говядина", "Рыба", "Фарш", "Свинина", "Кура")),
    DAIRY (List.of("Немолоко", "Яйца", "Масло сливочное","Сыр творожный","Пармезан","Просто сыр","Сыр бри","Сметана"));

    private final List<String> list;

    Products(List<String> list) {
        this.list = list;
    }

    public List<String> getList() {
        return list;
    }

    public static List<InlineKeyboardButton> getFirstHalf() {

        List<InlineKeyboardButton> result = new ArrayList<>();
        result.addAll(Products.HOUSEHOLD.getButtons());
        result.addAll(Products.GROCERY.getButtons());
        result.addAll(Products.CANNED.getButtons());
        result.addAll(Products.BREAD.getButtons());
        return result;

    }

    public static List<InlineKeyboardButton> getSecondHalf() {

        List<InlineKeyboardButton> result = new ArrayList<>();
        result.addAll(Products.VEGETABLES.getButtons());
        result.addAll(Products.FRUITS.getButtons());
        result.addAll(Products.MEAT.getButtons());
        result.addAll(Products.DAIRY.getButtons());
        return result;

    }

    private List<InlineKeyboardButton> getButtons() {

        List<InlineKeyboardButton> result = new ArrayList<>();
        for (String product : list) {
            result.add(InlineKeyboardButton.builder()
                    .text(product).callbackData(product).build());
        }

        return result;

    }
}
