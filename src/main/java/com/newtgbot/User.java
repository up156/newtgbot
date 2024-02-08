package com.newtgbot;

import lombok.Getter;
import lombok.Setter;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class User {

    private Boolean isAddingProducts;
    private Boolean isAddingTask;
    private Boolean isWorkingWithFirstPart;
    private List<String> products;
    private Message lastMessage;

    public User() {
        this.isAddingProducts = false;
        this.products = new ArrayList<>();
        this.lastMessage = null;
        this.isWorkingWithFirstPart = true;
        this.isAddingTask = false;
    }

}
