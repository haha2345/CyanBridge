package com.fersaiyan.cyanbridge.voice

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.jieli.jl_audio_decode.callback.OnDecodeStreamCallback
import com.jieli.jl_audio_decode.opus.OpusManager
import com.jieli.jl_audio_decode.opus.model.OpusOption
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.communication.ILargeDataResponse
import com.oudmon.ble.base.communication.LargeDataHandler
import com.oudmon.ble.base.communication.bigData.resp.AiChatResponse
import com.oudmon.ble.base.communication.bigData.resp.GlassModelControlResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class GlassesVoiceChatMvp(
    private val context: Context,
    private val openAi: OpenAiClient,
    private val speak: (String) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    private var opusManager: OpusManager? = null
    private var idleJob: Job? = null
    private var hardTimeoutJob: Job? = null

    private val pcm = ByteArrayOutputStream(256 * 1024)

    private val opusChunk = ByteArray(120)
    private var opusIndex = 0

    private val decodeCb =
        object : OnDecodeStreamCallback {
            override fun onDecodeStream(pcmData: ByteArray) {
                synchronized(pcm) { pcm.write(pcmData) }
                bumpIdleTimer()
            }

            override fun onStart() = Unit
            override fun onComplete(path: String?) = Unit
            override fun onError(code: Int, message: String?) {
                Log.e(TAG, "Opus decode error: code=$code message=$message")
            }
        }

    private val aiChatListener =
        object : ILargeDataResponse<AiChatResponse> {
            override fun parseData(cmdType: Int, response: AiChatResponse?) {
                if (!running.get()) return
                if (cmdType != 0x59) return // 89: AI audio stream frame in official APK

                val sub = response?.subData ?: return
                if (sub.size <= 6) return

                val payload = sub.copyOfRange(6, sub.size)
                feedOpus(payload)
            }
        }

    private val controlCb =
        object : ILargeDataResponse<GlassModelControlResponse> {
            override fun parseData(cmdType: Int, response: GlassModelControlResponse?) = Unit
        }

    fun isRunning(): Boolean = running.get()

    fun toggle() {
        if (running.get()) {
            stopAndProcess("toggle")
        } else {
            start("toggle")
        }
    }

    fun start(source: String) {
        if (!running.compareAndSet(false, true)) return

        if (!BleOperateManager.getInstance().isConnected) {
            running.set(false)
            onStatus("BLE not connected; can’t start voice chat.")
            return
        }

        onStatus("Voice MVP: starting ($source)…")
        Log.i(TAG, "start: source=$source")

        synchronized(pcm) { pcm.reset() }
        opusIndex = 0

        opusManager =
            try {
                OpusManager().also { mgr ->
                    val opt =
                        OpusOption()
                            .setHasHead(false)
                            .setSampleRate(16_000)
                            .setPacketSize(40)
                            .setChannel(1)
                    mgr.startDecodeStream(opt, decodeCb)
                }
            } catch (t: Throwable) {
                running.set(false)
                onStatus("Opus init failed: ${t.message}")
                Log.e(TAG, "Opus init failed", t)
                return
            }

        LargeDataHandler.getInstance().initPackageNotify(aiChatListener)
        LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x07), controlCb) // start voice stream

        bumpIdleTimer()
        hardTimeoutJob?.cancel()
        hardTimeoutJob =
            scope.launch {
                delay(HARD_TIMEOUT_MS)
                if (running.get()) stopAndProcess("hard-timeout")
            }
    }

    fun stopAndProcess(source: String) {
        if (!running.compareAndSet(true, false)) return

        onStatus("Voice MVP: stopping ($source)…")
        Log.i(TAG, "stopAndProcess: source=$source")

        idleJob?.cancel()
        hardTimeoutJob?.cancel()

        // Stop stream on glasses + stop receiving notify frames
        LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x0B), controlCb) // exit AI voice
        LargeDataHandler.getInstance().removeGptNotify()

        val mgr = opusManager
        opusManager = null
        try {
            mgr?.stopDecodeStream()
        } catch (_: Throwable) {
        }
        try {
            mgr?.release()
        } catch (_: Throwable) {
        }

        val pcmBytes =
            synchronized(pcm) {
                pcm.toByteArray()
            }

        scope.launch {
            if (pcmBytes.isEmpty()) {
                onStatus("No audio captured (PCM empty).")
                return@launch
            }

            val wav = File(context.cacheDir, "glasses_voice_${SystemClock.elapsedRealtime()}.wav")
            try {
                WavWriter.write16BitPcmMonoWav(wav, pcmBytes, sampleRateHz = 16_000)
            } catch (t: Throwable) {
                onStatus("WAV write failed: ${t.message}")
                Log.e(TAG, "WAV write failed", t)
                return@launch
            }

            val text =
                try {
                    openAi.transcribeWav(wav).trim()
                } catch (t: Throwable) {
                    onStatus("Transcribe failed: ${t.message}")
                    Log.e(TAG, "Transcribe failed", t)
                    return@launch
                }

            if (text.isBlank()) {
                onStatus("Transcribe ok but text empty.")
                return@launch
            }

            onStatus("You: $text")

            val reply =
                try {
                    openAi.chat(text).trim()
                } catch (t: Throwable) {
                    onStatus("Chat failed: ${t.message}")
                    Log.e(TAG, "Chat failed", t)
                    return@launch
                }

            if (reply.isNotBlank()) {
                onStatus("AI: $reply")
                try {
                    speak(reply)
                } catch (t: Throwable) {
                    Log.e(TAG, "TTS failed", t)
                }
            }
        }
    }

    fun shutdown() {
        try {
            if (running.get()) stopAndProcess("shutdown")
        } catch (_: Throwable) {
        }
        scope.cancel()
    }

    private fun bumpIdleTimer() {
        idleJob?.cancel()
        idleJob =
            scope.launch {
                delay(IDLE_TIMEOUT_MS)
                if (running.get()) stopAndProcess("idle-timeout")
            }
    }

    private fun feedOpus(bytes: ByteArray) {
        val mgr = opusManager ?: return

        // Vendor app buffers 120 bytes (3 * 40) before feeding the decoder.
        for (b in bytes) {
            opusChunk[opusIndex++] = b
            if (opusIndex == opusChunk.size) {
                try {
                    mgr.writeAudioStream(opusChunk)
                } catch (t: Throwable) {
                    Log.e(TAG, "writeAudioStream failed", t)
                }
                opusIndex = 0
            }
        }
    }

    companion object {
        private const val TAG = "VoiceMVP"
        private const val IDLE_TIMEOUT_MS = 1_200L
        private const val HARD_TIMEOUT_MS = 20_000L
    }
}

