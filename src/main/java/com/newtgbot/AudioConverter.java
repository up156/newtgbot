package com.newtgbot;
import java.io.IOException;

public class AudioConverter {
    public AudioConverter() {
    }
    private static final String VOICE_PATH = "/app/com/newtgbot/temp.ogg";
    public static final String WAV_PATH = "/app/com/newtgbot/temp.wav";
    private static final String OPUS_PATH = "opusdec ";

    public boolean getWavVoice() {
        try {
            Process p = Runtime.getRuntime().exec(OPUS_PATH + "--rate 16000 " + VOICE_PATH + " " + WAV_PATH);
            p.waitFor();
            p.destroy();
            return true;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }
}
