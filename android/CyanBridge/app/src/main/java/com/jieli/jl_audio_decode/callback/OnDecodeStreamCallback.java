package com.jieli.jl_audio_decode.callback;

public interface OnDecodeStreamCallback extends OnStateCallback {
    void onDecodeStream(byte[] pcmData);
}

