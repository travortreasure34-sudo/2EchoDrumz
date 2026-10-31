package com.echodrumz.data

import com.echodrumz.audio.WavDecoder
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Synthesized starter kit so the app is playable on first launch with no bundled assets. */
object DefaultKit {
    private const val SR = 44100
    private val names = listOf("Kick", "Snare", "Closed Hat", "Open Hat", "Clap", "Tom Low", "Tom High", "Rim")

    private fun fileFor(n: String) = n.lowercase().replace(' ', '_') + ".wav"

    private fun synth(i: Int): FloatArray {
        val rnd = Random(i + 7)
        fun noise() = rnd.nextFloat() * 2f - 1f
        val dur = when (i) { 0 -> 0.45; 1 -> 0.3; 2 -> 0.09; 3 -> 0.45; 4 -> 0.28; 5, 6 -> 0.45; else -> 0.07 }
        val n = (dur * SR).toInt()
        val out = FloatArray(n)
        var phase = 0.0
        var prev = 0f
        for (k in 0 until n) {
            val t = k.toDouble() / SR
            out[k] = when (i) {
                0 -> { phase += 2 * PI * (45 + 150 * exp(-t * 18)) / SR; (sin(phase) * exp(-t * 7)).toFloat() }
                1 -> (noise() * exp(-t * 16) * 0.7 + sin(2 * PI * 190 * t) * exp(-t * 24) * 0.6).toFloat()
                2, 3 -> { val x = noise(); val hp = x - prev; prev = x; (hp * exp(-t * (if (i == 2) 60.0 else 9.0))).toFloat() }
                4 -> {
                    var a = exp(-t * 18) * 0.4
                    for (b in doubleArrayOf(0.0, 0.012, 0.024)) if (t >= b) a += exp(-(t - b) * 220)
                    (noise() * a).toFloat()
                }
                5, 6 -> { val base = if (i == 5) 110.0 else 165.0; phase += 2 * PI * base * (1 + 0.6 * exp(-t * 20)) / SR; (sin(phase) * exp(-t * 8)).toFloat() }
                else -> (sin(2 * PI * 1700 * t) * 0.6 + noise() * 0.3).toFloat() * exp(-t * 70).toFloat()
            }
        }
        val peak = out.maxOf { abs(it) }.coerceAtLeast(1e-6f)
        for (k in out.indices) out[k] = out[k] / peak * 0.9f
        return out
    }

    fun ensureFiles(dir: File) {
        names.forEachIndexed { i, n ->
            val f = File(dir, fileFor(n))
            if (!f.exists()) f.writeBytes(WavDecoder.encode16(synth(i), SR))
        }
    }

    fun defaultProject(): Project {
        val pads = List(PAD_COUNT) { i -> if (i < names.size) PadConfig(names[i], fileFor(names[i])) else PadConfig() }
        val pattern = List(PAD_COUNT) { p ->
            List(STEP_COUNT) { s ->
                when (p) {
                    0 -> if (s % 4 == 0) 127 else 0
                    1 -> if (s == 4 || s == 12) 110 else 0
                    2 -> if (s % 2 == 0) (if (s % 4 == 0) 100 else 60) else 0
                    else -> 0
                }
            }
        }
        return Project("Untitled", 110f, 0f, pads, pattern)
    }
}
