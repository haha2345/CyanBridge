package com.jieli.jl_audio_decode.callback;

public interface OnStateCallback {
    void onStart();

    void onComplete(String path);

    void onError(int code, String message);
}

