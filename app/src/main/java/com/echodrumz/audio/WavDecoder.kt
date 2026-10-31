package com.echodrumz.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

class DecodedAudio(val data: FloatArray, val channels: Int, val sampleRate: Int)

/** Decodes PCM 8/16/24/32-bit and 32-bit float WAV into interleaved float (mono or stereo). */
object WavDecoder {
    private fun tag(b: ByteArray, o: Int) = String(b, o, 4, Charsets.ISO_8859_1)

    fun decode(b: ByteArray): DecodedAudio {
        require(b.size > 44 && tag(b, 0) == "RIFF" && tag(b, 8) == "WAVE") { "Not a WAV file" }
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        var fmt = 0; var ch = 0; var rate = 0; var bits = 0
        var dataOff = -1; var dataLen = 0
        var pos = 12L
        while (pos + 8 <= b.size) {
            val p = pos.toInt()
            val id = tag(b, p)
            val size = bb.getInt(p + 4).toLong() and 0xFFFFFFFFL
            val body = p + 8
            if (id == "fmt " && size >= 16) {
                fmt = bb.getShort(body).toInt() and 0xFFFF
                ch = bb.getShort(body + 2).toInt() and 0xFFFF
                rate = bb.getInt(body + 4)
                bits = bb.getShort(body + 14).toInt() and 0xFFFF
                if (fmt == 0xFFFE && size >= 26) fmt = bb.getShort(body + 24).toInt() and 0xFFFF
            } else if (id == "data") {
                dataOff = body
                dataLen = minOf(size, (b.size - body).toLong()).toInt()
                break
            }
            pos = body.toLong() + size + (size and 1L)
        }
        require(dataOff >= 0 && ch in 1..8 && rate > 0) { "Invalid WAV header" }
        require((fmt == 1 && bits in intArrayOf(8, 16, 24, 32)) || (fmt == 3 && bits == 32)) {
            "Unsupported WAV format (PCM 8/16/24/32-bit or 32-bit float only)"
        }
        val bytes = bits / 8
        val frames = dataLen / (bytes * ch)
        require(frames > 0) { "WAV contains no audio" }
        val outCh = minOf(ch, 2)
        val out = FloatArray(frames * outCh)
        for (f in 0 until frames) for (c in 0 until outCh) {
            val o = dataOff + (f * ch + c) * bytes
            out[f * outCh + c] = when {
                fmt == 3 -> bb.getFloat(o).coerceIn(-1f, 1f)
                bits == 8 -> ((b[o].toInt() and 0xFF) - 128) / 128f
                bits == 16 -> bb.getShort(o) / 32768f
                bits == 24 -> (((b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or (b[o + 2].toInt() shl 16))) / 8388608f
                else -> bb.getInt(o) / 2147483648f
            }
        }
        return DecodedAudio(out, outCh, rate)
    }

    fun encode16(data: FloatArray, sampleRate: Int): ByteArray {
        val bb = ByteBuffer.allocate(44 + data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + data.size * 2).put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        bb.put("data".toByteArray()).putInt(data.size * 2)
        for (x in data) bb.putShort((x.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        return bb.array()
    }
}
