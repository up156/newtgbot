package com.newtgbot;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class NotionSender {
    private static final String TOKEN = "";
//  token из Notion
    private static final String DATABASE_ID = "";
//  id базы данных для отправки уведомлений

    public NotionSender() {
    }

    public String sendTask(String taskMessage) {
        try {

            String requestBody = "{\"parent\": {\"type\": \"database_id\",\"database_id\": \"" + DATABASE_ID
                    + "\"},\"properties\": {" + "\"Name\": {\"title\": [{\"text\": {\"content\": \" " +
                    taskMessage + "\"}}]},\"Status\": {\"status\": {\"id\" : \"not-started\"}}}}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .header("Authorization", TOKEN)
                    .header("Notion-Version", "2022-06-28")
                    .header("Content-Type", "application/json")
                    .uri(URI.create("https://api.notion.com/v1/pages"))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            System.out.println(response.body());
            return response.body();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }
}
