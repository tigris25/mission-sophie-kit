package fr.mission.sophie.audio

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Generates the fallback sound effects as small WAV files (no external asset needed). */
object Synth {
    private const val RATE = 44100

    private fun note(n: Int): Double = 440.0 * 2.0.pow((n - 69) / 12.0) // MIDI note -> Hz

    private class Buf(seconds: Double) {
        val data = DoubleArray((seconds * RATE).toInt())

        /** Bell-like tone: fundamental + soft harmonics, fast attack, exponential decay. */
        fun bell(start: Double, freq: Double, dur: Double, amp: Double = 0.5, decay: Double = 4.0) {
            val s0 = (start * RATE).toInt()
            val n = (dur * RATE).toInt()
            for (i in 0 until n) {
                val idx = s0 + i
                if (idx >= data.size) break
                val t = i.toDouble() / RATE
                val env = min(1.0, t / 0.005) * exp(-decay * t)
                val v = sin(2 * PI * freq * t) + 0.35 * sin(2 * PI * freq * 2 * t) + 0.12 * sin(2 * PI * freq * 3.01 * t)
                data[idx] += amp * env * v / 1.47
            }
        }

        fun sweep(start: Double, f0: Double, f1: Double, dur: Double, amp: Double = 0.5, tremolo: Double = 0.0, square: Boolean = false) {
            val s0 = (start * RATE).toInt()
            val n = (dur * RATE).toInt()
            var phase = 0.0
            for (i in 0 until n) {
                val idx = s0 + i
                if (idx >= data.size) break
                val t = i.toDouble() / RATE
                val f = f0 + (f1 - f0) * (t / dur)
                phase += 2 * PI * f / RATE
                var v = sin(phase)
                if (square) v = if (v >= 0) 0.6 else -0.6
                val env = min(1.0, t / 0.01) * min(1.0, (dur - t) / 0.08)
                val trem = if (tremolo > 0) 0.7 + 0.3 * sin(2 * PI * tremolo * t) else 1.0
                data[idx] += amp * env * trem * v
            }
        }

        fun writeWav(file: File) {
            val pcm = ByteBuffer.allocate(data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            val peak = data.maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(1.0) ?: 1.0
            data.forEach { pcm.putShort((it / peak * 0.9 * Short.MAX_VALUE).toInt().toShort()) }
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + data.size * 2); put("WAVE".toByteArray())
                put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2)
                putShort(2); putShort(16)
                put("data".toByteArray()); putInt(data.size * 2)
            }
            FileOutputStream(file).use { it.write(header.array()); it.write(pcm.array()) }
        }
    }

    /** Pentatonic notes for up to 20 powers (index in the content's power list), over four octaves. */
    val POWER_NOTES = intArrayOf(60, 62, 64, 67, 69, 72, 74, 76, 79, 81, 84, 86, 88, 91, 93, 96, 98, 100, 103, 105)

    /** Writes every fallback sound into [dir] if missing. Returns name -> file. */
    fun generate(dir: File): Map<String, File> {
        dir.mkdirs()
        val out = LinkedHashMap<String, File>()
        fun make(name: String, seconds: Double, draw: Buf.() -> Unit) {
            val f = File(dir, "$name.wav")
            if (!f.exists() || f.length() < 100) Buf(seconds).apply(draw).writeWav(f)
            out[name] = f
        }
        make("scan_ok", 0.45) { bell(0.0, note(88), 0.3, decay = 9.0); bell(0.1, note(93), 0.35, decay = 8.0) }
        make("neutral", 0.4) { bell(0.0, note(72), 0.4, amp = 0.4, decay = 8.0) }
        make("step_ok", 0.5) { bell(0.0, note(84), 0.2, decay = 10.0); bell(0.08, note(91), 0.4, decay = 7.0) }
        make("error", 0.5) { sweep(0.0, 180.0, 110.0, 0.45, amp = 0.35, square = true) }
        make("level_up", 2.4) {
            listOf(60, 64, 67, 72, 76, 79, 84).forEachIndexed { i, n -> bell(i * 0.09, note(n), 0.6, amp = 0.45, decay = 5.0) }
            listOf(72, 76, 79, 84).forEach { bell(0.7, note(it), 1.7, amp = 0.35, decay = 1.8) }
        }
        make("corruption", 2.2) {
            sweep(0.0, 420.0, 70.0, 2.0, amp = 0.5, tremolo = 9.0)
            sweep(0.0, 210.0, 45.0, 2.0, amp = 0.3, tremolo = 6.0)
        }
        make("finale", 4.5) {
            val melody = listOf(67, 72, 76, 79, 76, 79, 84)
            melody.forEachIndexed { i, n -> bell(i * 0.22, note(n), 0.9, amp = 0.45, decay = 3.0) }
            listOf(60, 67, 72, 76, 79, 84).forEach { bell(1.7, note(it), 2.8, amp = 0.3, decay = 1.0) }
        }
        POWER_NOTES.forEachIndexed { i, n ->
            make("power_$i", 0.9) { bell(0.0, note(n), 0.9, amp = 0.55, decay = 3.5); bell(0.0, note(n + 12), 0.5, amp = 0.15, decay = 6.0) }
        }
        return out
    }
}
