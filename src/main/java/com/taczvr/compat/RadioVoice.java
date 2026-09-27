package com.taczvr.compat;

import com.taczvr.TaczVR;
import com.taczvr.server.RadioState;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple Voice Chat plugin for the radio. Only loaded by Simple Voice Chat, so nothing else in the mod may reference
 * this class: while you talk into a radio, your voice also goes, radio-filtered, to everyone RadioState says hears
 * you, wherever they are.
 */
@ForgeVoicechatPlugin
public final class RadioVoice implements VoicechatPlugin {
    private static final String CATEGORY = "taczvr_radio";

    @Nullable
    private static volatile VoicechatServerApi api;
    private static final Map<UUID, Speaker> SPEAKERS = new ConcurrentHashMap<>();
    public static volatile int clientRadioSounds = 0;

    /**
     * One talking player: their radio channel and the codec to filter their voice.
     */
    private static final class Speaker {
        final StaticAudioChannel channel;
        final OpusDecoder decoder;
        final OpusEncoder encoder;
        final RadioFilter filter = new RadioFilter();

        Speaker(VoicechatServerApi api, UUID id) {
            // its own channel, the proximity voice of the same player must not mix into it
            this.channel = api.createStaticAudioChannel(UUID.nameUUIDFromBytes(("taczvr_radio_" + id).getBytes(StandardCharsets.UTF_8)));
            if (this.channel != null) {
                this.channel.setCategory(CATEGORY);
            }
            this.decoder = api.createDecoder();
            this.encoder = api.createEncoder();
        }

        void close() {
            this.decoder.close();
            this.encoder.close();
        }
    }

    @Override
    public String getPluginId() {
        return TaczVR.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
        registration.registerEvent(PlayerDisconnectedEvent.class, event -> close(event.getPlayerUuid()));
        registration.registerEvent(ClientReceiveSoundEvent.StaticSound.class, event -> clientRadioSounds++);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        VoicechatServerApi server = event.getVoicechat();
        api = server;
        RadioState.voiceChatLoaded = true;
        RadioState.onRelease = RadioVoice::released;
        try {
            VolumeCategory radio = server.volumeCategoryBuilder()
                    .setId(CATEGORY)
                    .setName("Radio")
                    .setNameTranslationKey("taczvr.radio.category")
                    .setDescription("Voices from TaCZ VR radios")
                    .setDescriptionTranslationKey("taczvr.radio.category.description")
                    .setIcon(icon())
                    .build();
            server.registerVolumeCategory(radio);
        } catch (Throwable t) {
            TaczVR.LOGGER.warn("Could not add the radio volume slider to Simple Voice Chat", t);
        }
        TaczVR.LOGGER.info("Radio talks through Simple Voice Chat");
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null || event.getPacket().isWhispering()) {
            return;
        }
        UUID id = sender.getPlayer().getUuid();
        if (RadioState.isTransmitting(id)) {
            transmit(event.getVoicechat(), id, event.getPacket().getOpusEncodedData());
        }
    }

    /**
     * Sends one frame of a speaker's voice to everyone who hears them on the radio. Public for the self-test.
     *
     * @return how many got it
     */
    public static int transmit(VoicechatServerApi server, UUID sender, byte[] opus) {
        List<UUID> receivers = RadioState.receivers(sender);
        if (receivers.isEmpty() || opus.length == 0) {
            return 0;
        }
        Speaker speaker = SPEAKERS.computeIfAbsent(sender, id -> new Speaker(server, id));
        if (speaker.channel == null) {
            return 0;
        }
        byte[] radio;
        try {
            radio = speaker.encoder.encode(speaker.filter.apply(speaker.decoder.decode(opus)));
        } catch (Throwable t) {
            // the plain voice is better than none
            radio = opus;
        }
        speaker.channel.clearTargets();
        int sent = 0;
        for (UUID receiver : receivers) {
            VoicechatConnection connection = server.getConnectionOf(receiver);
            if (connection != null && connection.isConnected() && !connection.isDisabled()) {
                speaker.channel.addTarget(connection);
                sent++;
            }
        }
        if (sent > 0) {
            speaker.channel.send(radio);
            RadioState.framesSent++;
        }
        return sent;
    }

    @Nullable
    public static VoicechatServerApi api() {
        return api;
    }

    private static void released(UUID id) {
        Speaker speaker = SPEAKERS.get(id);
        if (speaker != null) {
            if (speaker.channel != null) {
                speaker.channel.flush();
            }
            speaker.decoder.resetState();
            speaker.encoder.resetState();
            speaker.filter.reset();
        }
    }

    private static void close(UUID id) {
        Speaker speaker = SPEAKERS.remove(id);
        if (speaker != null) {
            speaker.close();
        }
    }

    /**
     * A little radio for the volume slider: grey body, antenna, green screen.
     */
    private static int[][] icon() {
        int[][] icon = new int[16][16];
        for (int x = 4; x < 12; x++) {
            for (int y = 5; y < 15; y++) {
                icon[x][y] = 0xFF3A3F45;
            }
        }
        for (int y = 1; y < 5; y++) {
            icon[10][y] = 0xFF1B1D20;
        }
        for (int x = 5; x < 11; x++) {
            icon[x][6] = 0xFF6BE36B;
            icon[x][7] = 0xFF6BE36B;
        }
        for (int x = 5; x < 11; x += 2) {
            for (int y = 9; y < 14; y += 2) {
                icon[x][y] = 0xFF1B1D20;
            }
        }
        return icon;
    }
}
