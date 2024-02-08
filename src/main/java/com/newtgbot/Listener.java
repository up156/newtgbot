package com.newtgbot;

import org.apache.commons.io.FileUtils;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class Listener {
    private final AudioConverter audioConverter;

    private final static String VOSK_PATH = "/app/com/example/vosk-model-small";
    private static final String WAV_PATH = "/app/com/example/temp.wav";

    private static final String DOWNLOAD_PATH = "/app/com/example/temp.ogg";

    public Listener() {
        audioConverter = new AudioConverter();
    }

    public String convertAudio(Update update, File downloaded) {

        try {
            FileUtils.copyFile(downloaded, new File(DOWNLOAD_PATH));
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (audioConverter.getWavVoice()) {
            int duration = update.getMessage().getVoice().getDuration();
            try (Model model = new Model(VOSK_PATH);
                 InputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(new FileInputStream(WAV_PATH)));
                 Recognizer recognizer = new Recognizer(model, 16000)) {
                int nbytes;
                byte[] b = new byte[4096 * duration];
                StringBuilder stringBuilder = new StringBuilder();
                while ((nbytes = ais.read(b)) >= 0) {
                    if (recognizer.acceptWaveForm(b, nbytes)) {
                        String tempResult = new String(recognizer.getResult().getBytes(StandardCharsets.UTF_8));
                        stringBuilder.append(" ").append(getTextFromJSON(tempResult));
                    }
                }
                String result = getTextFromJSON(recognizer.getFinalResult());
                stringBuilder.append(" ").append(result);
                return new String(stringBuilder.toString().trim().getBytes(), StandardCharsets.UTF_8);
            } catch (IOException | UnsupportedAudioFileException e) {
                throw new RuntimeException(e);
            }
        }
        return "";
    }

    private String getTextFromJSON(String text) {
        try {
            JSONParser parser = new JSONParser();
            org.json.simple.JSONObject object = (org.json.simple.JSONObject) parser.parse(text);
            return object.get("text").toString();
        } catch (ParseException e) {
            e.printStackTrace();
            return "";
        }
    }
}
