package com.newtgbot;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Writer {
    private final static String AUTH_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";
    private final static String CHAT_URL = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions";
    private final static String CREDENTIALS = "";
//    gigachat token

    private final static int HISTORY_LIMIT = 2500;
    private String accessToken;
    private Long expiresAt;
    @Getter
    @Setter
    private List<JSONObject> history;

    public Writer() {
        setNewHistory();
    }

    public void getAuthorisation() {

        HttpsURLConnection connection = getSSLVerifiedConnection();

        HttpRequest request = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + CREDENTIALS)
                .header("RqUID", "")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .uri(URI.create(AUTH_URL))
                .POST(HttpRequest.BodyPublishers.ofString("scope=GIGACHAT_API_PERS"))
                .build();

        JSONObject object = getObject(request);
        setAccessToken(object.get("access_token").toString());
        setExpiresAt(Long.valueOf(object.get("expires_at").toString()));
        log.info("Set accessToken: " + accessToken);
        log.info("Set expiresAt: " + expiresAt);
        assert connection != null;
        connection.disconnect();
    }

    public String getReply(String message) {

        JSONObject object = (JSONObject) ((org.json.simple.JSONArray) getObject(message).get("choices")).get(0);
        object = (JSONObject) object.get("message");
        history.add(object);
        while (history.toString().length() > HISTORY_LIMIT) {
            history.remove(1);
            history.remove(1);
        }
        String reply = object.get("content").toString();
        log.info(reply);
        return reply;
    }

    public InputStream getPicture(String message) {

        String reply = getObject(message).get("content").toString();
        reply = reply.substring(reply.indexOf("<img src=\"") + 10);
        reply = reply.substring(0, reply.indexOf("\""));
        log.info(reply);
        InputStream stream = getPictureRequest(reply);

        return stream;
    }

    private JSONObject getObject(String message) {
        if (!isAccessTokenValid()) {
            getAuthorisation();
        }
        HttpsURLConnection connection = getSSLVerifiedConnection();

        HttpRequest request = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .uri(URI.create(CHAT_URL))
                .POST(HttpRequest.BodyPublishers.ofString(getJSONMessageForPicture(message)))
                .build();

        JSONObject object = getObject(request);
        object = (JSONObject) ((org.json.simple.JSONArray) object.get("choices")).get(0);
        object = (JSONObject) object.get("message");
        assert connection != null;
        connection.disconnect();
        return object;
    }

    private InputStream getPictureRequest(String id) {
        try {
        HttpRequest request = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .uri(URI.create("https://gigachat.devices.sberbank.ru/api/v1/files/" + id + "/content"))
                .GET()
                .build();

        log.info(request.toString());

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private JSONObject getObject(HttpRequest request) {
        log.info(request.toString());
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info(String.valueOf(response));
            log.info(response.body());
            JSONParser parser = new JSONParser();
            return (JSONObject) parser.parse(response.body());
        } catch (IOException | InterruptedException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private String getJSONMessageForPicture(String message) {

        JSONObject obj = new JSONObject();
        obj.put("model", "GigaChat:latest");
        obj.put("temperature", 0.7);
        List<JSONObject> messages = new ArrayList<>();
        JSONObject currentMessage = new JSONObject();
        currentMessage.put("role", "user");
        currentMessage.put("content", message);
        messages.add(currentMessage);
        obj.put("messages", messages);
        log.info(obj.toJSONString());
        return obj.toJSONString();
    }

    private Boolean isAccessTokenValid() {

        if (expiresAt == null || expiresAt < System.currentTimeMillis() - 10000L) {
            return false;
        }
        return true;
    }

    private String getJSONMessage(String message) {

        JSONObject obj = new JSONObject();
        obj.put("model", "GigaChat:latest");
        obj.put("temperature", 0.87);
        obj.put("top_p", 0.47);
        obj.put("max_tokens", 512);
        obj.put("repetition_penalty", 1.07);
        obj.put("stream", false);
        obj.put("update_interval", 0);
        List<JSONObject> messages = history;
        JSONObject currentMessage = new JSONObject();
        currentMessage.put("role", "user");
        currentMessage.put("content", message);
        messages.add(currentMessage);
        setHistory(messages);
        obj.put("messages", messages);
        System.out.println(obj.toJSONString());
        return obj.toJSONString();
    }

    public void setNewHistory() {
        List<JSONObject> list = new ArrayList<>();
        JSONObject object = new JSONObject();
        object.put("role", "system");
        object.put("content", "Отвечай как ученый.");
        list.add(object);
        this.history = list;
    }

    private HttpsURLConnection getSSLVerifiedConnection() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(new KeyManager[0], new TrustManager[]{new DefaultTrustManager()}, new SecureRandom());
            SSLContext.setDefault(ctx);
            URL url = new URL(AUTH_URL);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setHostnameVerifier((arg0, arg1) -> true);
            return conn;
        } catch (IOException | NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
}


