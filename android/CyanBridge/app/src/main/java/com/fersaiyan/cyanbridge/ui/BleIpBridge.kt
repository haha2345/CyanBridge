package com.fersaiyan.cyanbridge.ui

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

/**
 * Small helper that watches BLE payloads and tries to extract an IPv4
 * address. We feed it from the Bluetooth callbacks and then read the
 * last-seen IP from the data download flow.
 *
 * Also tracks bcfd thumbnail page numbers from raw BLE notifications so
 * that [GlassesRepository] can reorder pages that arrive out of sequence.
 */
class BleIpBridge {
    private val _ip = MutableStateFlow<String?>(null)
    val ip = _ip.asStateFlow()

    // ── Thumbnail page tracking ──────────────────────────────
    // The SDK strips the bcfd header before calling getPictureThumbnails,
    // but raw BLE notifications still contain it. We hash the first payload
    // bytes (offset 11+) and map them to page numbers so the callback can
    // look up the correct page for ordered reassembly.
    private val _thumbPageMap = mutableMapOf<Long, Pair<Int, Int>>() // payloadHash → (page#, totalPages)
    val thumbPageMap: Map<Long, Pair<Int, Int>> get() = _thumbPageMap

    fun clearThumbPageMap() {
        _thumbPageMap.clear()
    }

    /** Hash [len] bytes of [data] starting at [offset]. */
    fun payloadHash(data: ByteArray, offset: Int = 0, len: Int = 48): Long {
        var h = 0L
        val end = minOf(offset + len, data.size)
        for (i in offset until end) {
            h = h * 31 + (data[i].toLong() and 0xFF)
        }
        return h
    }

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
        // ── Detect bcfd thumbnail page header in raw BLE notification ──
        // Format: bc fd fa 03 [xx xx] 01 [totalPages] 00 [pageNum] 00 [payload...]
        // The first sub-chunk of each page has this header. Subsequent sub-chunks
        // in the same page do NOT have it.
        if (value.size > 20 &&
            value[0] == 0xBC.toByte() && value[1] == 0xFD.toByte() &&
            value[2] == 0xFA.toByte() && value[3] == 0x03.toByte()
        ) {
            val totalPages = value[7].toInt() and 0xFF
            val pageNum = value[9].toInt() and 0xFF
            // Hash payload bytes starting at offset 11 — this matches the start
            // of the data that getPictureThumbnails callback will receive.
            val key = payloadHash(value, offset = 11)
            _thumbPageMap[key] = Pair(pageNum, totalPages)
        }

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
