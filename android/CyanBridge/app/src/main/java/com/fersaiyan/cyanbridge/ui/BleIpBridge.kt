package com.fersaiyan.cyanbridge.ui

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

/**
 * Small helper that watches BLE payloads and tries to extract an IPv4
 * address. We feed it from the Bluetooth callbacks and then read the
 * last-seen IP from the data download flow.
 */
class BleIpBridge {
    private val _ip = MutableStateFlow<String?>(null)
    val ip = _ip.asStateFlow()

    private fun isMostlyPrintableAscii(bytes: ByteArray, len: Int = bytes.size): Boolean {
        if (bytes.isEmpty() || len <= 0) return true
        var printable = 0
        val n = minOf(bytes.size, len)
        for (i in 0 until n) {
            val b = bytes[i]
            val v = b.toInt() and 0xFF
            val ok = v == 0x09 || v == 0x0A || v == 0x0D || (v in 0x20..0x7E)
            if (ok) printable++
        }
        return printable.toDouble() / n.toDouble() >= 0.85
    }

    private fun toHexPreview(bytes: ByteArray, maxBytes: Int = 24): String {
        if (bytes.isEmpty()) return ""
        val n = minOf(bytes.size, maxBytes)
        val sb = StringBuilder(n * 2)
        for (i in 0 until n) {
            val v = bytes[i].toInt() and 0xFF
            sb.append("0123456789abcdef"[v ushr 4])
            sb.append("0123456789abcdef"[v and 0x0F])
        }
        if (bytes.size > n) sb.append("…")
        return sb.toString()
    }

    fun onCharacteristicChanged(source: String, value: ByteArray) {
        // Only decode a bounded prefix to avoid large allocations when we accidentally
        // receive binary streams (e.g., JPEG thumbnail chunks).
        val scanLen = minOf(value.size, 256)
        val msg = try {
            String(value, 0, scanLen, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }

        // Avoid spamming logcat with binary payloads (e.g., JPEG thumbnail chunks).
        // We only need text for the IPv4 regex extraction.
        if (isMostlyPrintableAscii(value, scanLen)) {
            val preview = msg
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .take(160)
            val suffix = if (msg.length > 160) "…" else ""
            val trunc = if (value.size > scanLen) " (trunc)" else ""
            Log.d("BleIpBridge", "[$source] raw='$preview$suffix'$trunc")
        } else {
            Log.d(
                "BleIpBridge",
                "[$source] binary len=${value.size} head=${toHexPreview(value)}"
            )
        }

        // Regex to find an IPv4 address in the payload
        val regex = Regex("""(\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b)""")
        val match = regex.find(msg)
        if (match != null) {
            val foundIp = match.value
            Log.i("BleIpBridge", "Detected device IP in BLE payload: $foundIp")
            _ip.value = foundIp
        }
    }
}

// Single shared instance used across the app
val bleIpBridge: BleIpBridge = BleIpBridge()
