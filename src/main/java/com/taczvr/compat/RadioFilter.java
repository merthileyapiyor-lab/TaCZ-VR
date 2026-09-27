package com.taczvr.compat;

import java.util.Random;

/**
 * Makes a voice sound like it comes out of a radio: only the middle of the voice (about 350 to 2800 Hz), driven a bit
 * into distortion, with a little hiss. One per speaker, it keeps its state between the 20 ms frames.
 */
public final class RadioFilter {
    private static final float SAMPLE_RATE = 48000.0F;
    private static final float HIGH_PASS_HZ = 350.0F;
    private static final float LOW_PASS_HZ = 2800.0F;
    private static final float DRIVE = 2.4F;
    private static final float HISS = 0.012F;

    private final float highPass;
    private final float lowPass;
    // two stages each way, so the rumble and the highs really go
    private float hpIn1;
    private float hpOut1;
    private float hpIn2;
    private float hpOut2;
    private float lp1;
    private float lp2;
    private final Random random = new Random();

    public RadioFilter() {
        float dt = 1.0F / SAMPLE_RATE;
        float hpRc = 1.0F / (2.0F * (float) Math.PI * HIGH_PASS_HZ);
        float lpRc = 1.0F / (2.0F * (float) Math.PI * LOW_PASS_HZ);
        this.highPass = hpRc / (hpRc + dt);
        this.lowPass = dt / (lpRc + dt);
    }

    public void reset() {
        this.hpIn1 = 0.0F;
        this.hpOut1 = 0.0F;
        this.hpIn2 = 0.0F;
        this.hpOut2 = 0.0F;
        this.lp1 = 0.0F;
        this.lp2 = 0.0F;
    }

    public short[] apply(short[] pcm) {
        short[] out = new short[pcm.length];
        for (int i = 0; i < pcm.length; i++) {
            float x = pcm[i] / 32768.0F;
            float hp1 = this.highPass * (this.hpOut1 + x - this.hpIn1);
            this.hpIn1 = x;
            this.hpOut1 = hp1;
            float hp = this.highPass * (this.hpOut2 + hp1 - this.hpIn2);
            this.hpIn2 = hp1;
            this.hpOut2 = hp;
            this.lp1 += this.lowPass * (hp - this.lp1);
            this.lp2 += this.lowPass * (this.lp1 - this.lp2);
            float y = (float) Math.tanh(this.lp2 * DRIVE) * 0.8F + (this.random.nextFloat() - 0.5F) * HISS;
            out[i] = (short) Math.max(-32768, Math.min(32767, Math.round(y * 32767.0F)));
        }
        return out;
    }

    public static double rms(short[] pcm) {
        double sum = 0.0;
        for (short s : pcm) {
            sum += (double) s * s;
        }
        return Math.sqrt(sum / Math.max(1, pcm.length));
    }
}
