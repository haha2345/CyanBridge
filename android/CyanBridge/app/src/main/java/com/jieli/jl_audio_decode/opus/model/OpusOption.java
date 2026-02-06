package com.jieli.jl_audio_decode.opus.model;

// Minimal API-compatible subset of the vendor OpusOption used by libjl_opus.so.
public class OpusOption {
    private int channel = 1;
    private boolean hasHead = false;
    private int packetSize = 40;
    private int sampleRate = 16000;

    public int getChannel() {
        return channel;
    }

    public boolean isHasHead() {
        return hasHead;
    }

    public int getPacketSize() {
        return packetSize;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public OpusOption setChannel(int channel) {
        if (channel == 1 || channel == 2) {
            this.channel = channel;
        }
        return this;
    }

    public OpusOption setHasHead(boolean hasHead) {
        this.hasHead = hasHead;
        return this;
    }

    public OpusOption setPacketSize(int packetSize) {
        this.packetSize = packetSize;
        return this;
    }

    public OpusOption setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
        return this;
    }
}

