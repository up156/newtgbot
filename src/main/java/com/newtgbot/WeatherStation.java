package com.newtgbot;

import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
@Slf4j
public class WeatherStation {

    private final static String TOKEN = "";
//    token Open Weather api
    private final static String WEATHER_NOW = "https://api.openweathermap.org/data/2.5/weather?q=%D1%81%D0%B0%D0%BD%D0%BA%D1%82-%D0%BF%D0%B5%D1%82%D0%B5%D1%80%D0%B1%D1%83%D1%80%D0%B3&lang=ru&units=metric&appid=" + TOKEN;
    private final static String WEATHER_FORECAST = "https://api.openweathermap.org/data/2.5/forecast?q=%D1%81%D0%B0%D0%BD%D0%BA%D1%82-%D0%BF%D0%B5%D1%82%D0%B5%D1%80%D0%B1%D1%83%D1%80%D0%B3&lang=ru&units=metric&appid=" + TOKEN;

    public WeatherStation() {
    }

    public String getWeather() {
        try {
            if (getWeatherResponse().isEmpty()) {
                return "Сервер недоступен. Попробуйте позже.";
            }

            String result = prepareCurrentWeatherReply(getWeatherResponse().get(0)) +
                    prepareForecastReply(getWeatherResponse().get(1));

            log.info("WeatherStation in getWeather: " + result);
            return result;
        } catch (ParseException e) {
            e.printStackTrace();
            return "что-то сломалось";
        }
    }

    private List<String> getWeatherResponse() {

        List<String> replies = new ArrayList<>();
        try {
            List<URL> list = List.of((new URL(WEATHER_NOW)), new URL(WEATHER_FORECAST));
            list.forEach(url -> {
                try {
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();

                    con.setRequestMethod("GET");
                    con.setConnectTimeout(5000);
                    con.setReadTimeout(5000);
                    con.setDoOutput(true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
                    String inputLine;
                    StringBuilder reply = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        reply.append(inputLine);
                    }
                    in.close();
                    replies.add(reply.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return replies;
    }

    private String prepareCurrentWeatherReply(String current) throws ParseException {
        JSONParser parser = new JSONParser();
        JSONObject object = (JSONObject) parser.parse(current);
        JSONObject main = (JSONObject) object.get("main");
        JSONObject sys = (JSONObject) object.get("sys");
        JSONObject weather = (JSONObject) ((JSONArray) object.get("weather")).get(0);
        return ("\uD83E\uDD99 Температура  сейчас: "
                + (Math.round(Double.parseDouble(main.get("temp").toString()) * 10) / 10.0) + "°" + "\r\n"
                + getEmoji(weather.get("icon").toString()) + " На улице " + weather.get("description") + "\r\n"
                + "☀" + "☀" + "☀" + " Восход солнца: " + convertEpochToLocalTime(sys.get("sunrise").toString()) + "\r\n"
                + "\uD83C\uDF1D\uD83C\uDF1D\uD83C\uDF1D "
                + "Заход солнца: " + convertEpochToLocalTime(sys.get("sunset").toString()))
                + "\n" + "\n" + "⚡⚡" + "\n" + "\n" + "Прогноз на ближайшие 24 часа:" + "\n";
    }

    private String prepareForecastReply(String forecast) throws ParseException {

        JSONParser parser = new JSONParser();
        JSONObject objectForecast = (JSONObject) parser.parse(forecast);
        JSONArray list = (JSONArray) objectForecast.get("list");
        StringBuilder result = new StringBuilder();

        for (int i = 1; i <= 9; i++) {

            JSONObject weatherForecast = (JSONObject) ((JSONArray) ((JSONObject) list.get(i)).get("weather")).get(0);
            Long dt = (Long) ((JSONObject) list.get(i)).get("dt");
            JSONObject mainForecast = (JSONObject) ((JSONObject) list.get(i)).get("main");

            result.append(convertEpochToLocalTime(dt.toString())).append(" - ")
                    .append(getEmoji(weatherForecast.get("icon").toString())).append(" ")
                    .append((Math.round(Float.parseFloat(mainForecast.get("temp").toString()) * 10)) / 10.0)
                    .append("°").append(" ").append(weatherForecast.get("description")).append("\r\n");
            i++;
        }
        return result.toString();
    }

    private String convertEpochToLocalTime(String epoch) {

        return Instant.ofEpochSecond(Long.parseLong(epoch))
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private String getEmoji(String icon) {
        return switch (icon) {
            case "01d" -> "☀";
            case "01n" -> "\uD83C\uDF1A";
            case "02d" -> "\uD83C\uDF24";
            case "02n", "03d", "03n", "04d", "04n" -> "☁";
            case "09d" -> "\uD83C\uDF26";
            case "09n", "10d", "10n" -> "\uD83C\uDF28";
            case "11d", "11n" -> "\uD83C\uDF29";
            case "13d", "13n" -> "❄";
            case "50d", "50n" -> "\uD83C\uDF2B";
            default -> "";
        };
    }

}
