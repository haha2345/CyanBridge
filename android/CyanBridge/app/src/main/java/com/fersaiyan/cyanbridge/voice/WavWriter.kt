package com.fersaiyan.cyanbridge.voice

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavWriter {
    fun write16BitPcmMonoWav(
        outFile: File,
        pcm: ByteArray,
        sampleRateHz: Int = 16_000,
    ) {
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRateHz * numChannels * bitsPerSample / 8
        val blockAlign = (numChannels * bitsPerSample / 8).toShort()

        val dataSize = pcm.size
        val riffSize = 36 + dataSize

        FileOutputStream(outFile).use { fos ->
            fos.write("RIFF".toByteArray(Charsets.US_ASCII))
            fos.write(leInt(riffSize))
            fos.write("WAVE".toByteArray(Charsets.US_ASCII))

            fos.write("fmt ".toByteArray(Charsets.US_ASCII))
            fos.write(leInt(16)) // PCM chunk size
            fos.write(leShort(1)) // audio format = PCM
            fos.write(leShort(numChannels.toShort()))
            fos.write(leInt(sampleRateHz))
            fos.write(leInt(byteRate))
            fos.write(leShort(blockAlign))
            fos.write(leShort(bitsPerSample.toShort()))

            fos.write("data".toByteArray(Charsets.US_ASCII))
            fos.write(leInt(dataSize))
            fos.write(pcm)
        }
    }

    private fun leInt(v: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    private fun leShort(v: Short): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array()
}

