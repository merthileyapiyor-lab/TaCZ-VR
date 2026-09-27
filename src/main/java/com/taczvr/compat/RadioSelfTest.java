package com.taczvr.compat;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;

import java.util.UUID;

/**
 * The self-test's way into the voice chat side, with plain types only so the self-test never loads Simple Voice
 * Chat classes itself.
 */
public final class RadioSelfTest {
    private RadioSelfTest() {
    }

    public static boolean pluginLoaded() {
        return RadioVoice.api() != null;
    }

    public static short[] tone(double hz) {
        short[] pcm = new short[960];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) (Math.sin(2.0 * Math.PI * hz * i / 48000.0) * 12000.0);
        }
        return pcm;
    }

    /**
     * Sends a few frames of a tone as if the sender spoke into their radio.
     *
     * @return how many listeners the last frame went to, -1 without voice chat
     */
    public static int sendTone(UUID sender, int frames) {
        VoicechatServerApi api = RadioVoice.api();
        if (api == null) {
            return -1;
        }
        OpusEncoder encoder = api.createEncoder();
        int sent = 0;
        for (int i = 0; i < frames; i++) {
            sent = RadioVoice.transmit(api, sender, encoder.encode(tone(800.0)));
        }
        encoder.close();
        return sent;
    }

    public static int clientSounds() {
        return RadioVoice.clientRadioSounds;
    }
}
