package com.jieli.jl_audio_decode.opus;

import com.jieli.jl_audio_decode.callback.OnDecodeStreamCallback;
import com.jieli.jl_audio_decode.opus.model.OpusOption;

/**
 * Minimal wrapper around the vendor native library {@code libjl_opus.so}.
 *
 * This is intentionally small: just enough for the BLE voice-chat MVP:
 * - startDecodeStream(...)
 * - writeAudioStream(...)
 * - stopDecodeStream()
 *
 * Note: The native library calls back into {@link #onDecodeStreamReceive(int, byte[])}
 * and {@link #onStateCallback(int, int, int, String)} via JNI.
 */
public class OpusManager {
    private static final String LIB = "jl_opus";

    static {
        try {
            // libjl_opus.so depends on libcbuf.so (and may rely on libst_opus.so).
            // Load them explicitly to avoid namespace resolution issues on some devices.
            System.loadLibrary("cbuf");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        try {
            System.loadLibrary("st_opus");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        try {
            System.loadLibrary(LIB);
        } catch (Throwable t) {
            // If this fails, the app can still run but voice decode won't work.
            t.printStackTrace();
        }
    }

    private volatile long managerAddr;
    private volatile OnDecodeStreamCallback decodeStreamCb;

    public OpusManager() {
        managerAddr = initNativeID();
        if (managerAddr == 0L) {
            throw new IllegalStateException("Can not load " + LIB + " lib (initNativeID=0)");
        }
    }

    public void startDecodeStream(OpusOption option, OnDecodeStreamCallback callback) {
        decodeStreamCb = callback;
        if (option == null) option = new OpusOption();
        // state=1 => start (as observed in vendor implementation)
        decodeAudioStream(1, managerAddr, option);
    }

    public void stopDecodeStream() {
        if (managerAddr == 0L) return;
        // state=0 => stop (as observed in vendor implementation)
        decodeAudioStream(0, managerAddr, new OpusOption());
    }

    public void writeAudioStream(byte[] opusBytes) {
        if (managerAddr == 0L) return;
        if (opusBytes == null || opusBytes.length == 0) return;
        saveAudioStream(opusBytes, managerAddr);
    }

    public void release() {
        long addr = managerAddr;
        managerAddr = 0L;
        decodeStreamCb = null;
        if (addr != 0L) {
            nativeDestroy(addr);
        }
    }

    // Called by native
    @SuppressWarnings("unused")
    protected void onDecodeStreamReceive(int dataLen, byte[] pcmData) {
        OnDecodeStreamCallback cb = decodeStreamCb;
        if (cb == null || pcmData == null) return;
        cb.onDecodeStream(pcmData);
    }

    // Called by native
    @SuppressWarnings("unused")
    protected void onStateCallback(int dataType, int state, int code, String message) {
        // For the MVP, treat this callback as optional; the official implementation
        // uses it to route stream start/complete/error callbacks.
        OnDecodeStreamCallback cb = decodeStreamCb;
        if (cb == null) return;
        if (state == 1) {
            cb.onStart();
        } else if (state == 2) {
            cb.onComplete(message);
        } else if (state == 0 && code != 0) {
            cb.onError(code, message);
        }
    }

    private native long initNativeID();

    private native int nativeDestroy(long managerAddr);

    private native void decodeAudioStream(int state, long managerAddr, OpusOption option);

    private native boolean saveAudioStream(byte[] opusBytes, long managerAddr);

    // Present in the vendor JNI registration table (required even if unused by us).
    @SuppressWarnings("unused")
    public native int decodeAudioFile(String inputPath, String outputPath, long managerAddr, OpusOption option);

    // Present in the vendor JNI registration table (required even if unused by us).
    @SuppressWarnings("unused")
    public native int getAudioStreamState(long managerAddr);

    // Present in the vendor JNI registration table (required even if unused by us).
    @SuppressWarnings("unused")
    public native int getEncodeStreamState(long managerAddr);

    // Present in the vendor JNI registration table (required even if unused by us).
    @SuppressWarnings("unused")
    public native int encodeOpusFile(String inputPath, String outputPath, long managerAddr);

    // Present in the vendor JNI registration table (required even if unused by us).
    @SuppressWarnings("unused")
    private native void encodeOpusStream(int state, long managerAddr);

    // Some vendor builds also expose a variant that takes options.
    @SuppressWarnings("unused")
    private native void encodeOpusStream(int state, long managerAddr, OpusOption option);

    // Present in the vendor JNI registration table (required even if unused by us).
    @SuppressWarnings("unused")
    private native boolean savePcmStream(byte[] pcmBytes, long managerAddr);

    // Called by native (not used in the MVP, but must exist for JNI callbacks).
    @SuppressWarnings("unused")
    protected void onEncodeStreamReceive(int dataLen, byte[] opusData) {
        // Intentionally no-op for now.
    }
}
