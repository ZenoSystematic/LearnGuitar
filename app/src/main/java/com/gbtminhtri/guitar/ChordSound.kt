package com.gbtminhtri.guitar

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Âm thanh hợp âm. Có hai nguồn, tự chọn theo thứ tự:
 *
 * 1. FILE GHI ÂM THẬT (khuyên dùng): nếu thư mục res/raw có file chord_em, chord_am, chord_c,
 *    chord_g, chord_d, chord_a, chord_e (đuôi .mp3, .ogg hoặc .wav) thì phát file đó.
 *    Hợp âm nào chưa có file thì dùng nguồn số 2.
 * 2. TỰ TỔNG HỢP: mô phỏng 6 dây đàn (Karplus-Strong) cộng thêm vị trí gảy, tiếng "tạch" của phím gảy,
 *    cộng hưởng thùng đàn và chút vang phòng nhỏ. Nghe ổn nhưng không thể bằng đàn thật.
 *
 * Cần gọi ChordSound.init(context) một lần (đã gọi trong GuitarApp).
 */
object ChordSound {
    private const val SAMPLE_RATE = 44100
    private const val DURATION_SEC = 3.2

    // Tần số 6 dây buông: E2 A2 D3 G3 B3 E4 (dây 6 -> dây 1)
    private val openStrings = doubleArrayOf(82.41, 110.00, 146.83, 196.00, 246.94, 329.63)

    private val cache = HashMap<String, ShortArray>()
    private val sampleIds = HashMap<String, Int>()
    private var current: AudioTrack? = null
    private var player: MediaPlayer? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Tạo trước âm thanh tổng hợp (gọi ở luồng nền) để lần bấm đầu tiên không bị trễ. */
    fun prepare(chord: Chord) {
        if (sampleResId(chord) != 0) return // có file ghi âm thì không cần tổng hợp
        synchronized(cache) {
            if (!cache.containsKey(chord.name)) cache[chord.name] = synthesize(chord)
        }
    }

    fun play(chord: Chord) {
        val resId = sampleResId(chord)
        if (resId != 0 && playRecording(resId)) return
        Thread {
            val samples = synchronized(cache) {
                cache.getOrPut(chord.name) { synthesize(chord) }
            }
            playSamples(samples)
        }.start()
    }

    /** Dừng âm đang vang (khi rời màn hình). */
    fun stop() {
        synchronized(this) { stopCurrent() }
    }

    // ---------- File ghi âm thật (res/raw) ----------

    private fun sampleResId(chord: Chord): Int {
        val ctx = appContext ?: return 0
        return synchronized(sampleIds) {
            sampleIds.getOrPut(chord.name) {
                ctx.resources.getIdentifier("chord_" + chord.name.lowercase(), "raw", ctx.packageName)
            }
        }
    }

    private fun playRecording(resId: Int): Boolean {
        val ctx = appContext ?: return false
        return try {
            synchronized(this) {
                stopCurrent()
                val mp = MediaPlayer.create(ctx, resId) ?: return false
                mp.setOnCompletionListener { finished ->
                    try {
                        finished.release()
                    } catch (e: Exception) {
                    }
                    synchronized(this@ChordSound) {
                        if (player === finished) player = null
                    }
                }
                player = mp
                mp.start()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun stopCurrent() {
        try {
            current?.stop()
        } catch (e: Exception) {
        }
        try {
            current?.release()
        } catch (e: Exception) {
        }
        current = null
        try {
            player?.stop()
        } catch (e: Exception) {
        }
        try {
            player?.release()
        } catch (e: Exception) {
        }
        player = null
    }

    // ---------- Phát âm thanh tự tổng hợp ----------

    private fun playSamples(samples: ShortArray) {
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(samples, 0, samples.size)
            synchronized(this) {
                stopCurrent() // bấm liên tiếp thì ngắt âm trước đó
                current = track
            }
            track.play()
            Thread.sleep((DURATION_SEC * 1000).toLong() + 150)
            synchronized(this) {
                if (current === track) current = null
            }
            track.release()
        } catch (e: Exception) {
            // không phát được thì thôi, không làm app bị lỗi
        }
    }

    // ---------- Tổng hợp ----------

    private fun synthesize(chord: Chord): ShortArray {
        val total = (SAMPLE_RATE * DURATION_SEC).toInt()
        val mix = FloatArray(total)
        val rng = java.util.Random(chord.name.hashCode().toLong())
        var order = 0
        var t = 0.0

        for (s in 0 until 6) {
            val fret = chord.frets[s]
            if (fret < 0) continue // dây không gảy
            val freq = openStrings[s] * 2.0.pow(fret / 12.0)

            // Quạt xuống: các dây lệch nhau khoảng 28ms, hơi ngẫu nhiên cho tự nhiên
            if (order > 0) t += 0.028 + (rng.nextDouble() - 0.5) * 0.012
            val start = (t * SAMPLE_RATE).toInt()
            val gain = (max(0.7, 1.0 - 0.04 * order) * (0.92 + 0.16 * rng.nextDouble())).toFloat()
            val brightness = (0.16 + 0.16 * s / 5.0).toFloat() // dây trầm ấm hơn, dây thanh sáng hơn
            val position = 0.16 + 0.04 * rng.nextDouble()      // vị trí gảy trên dây (tỉ lệ chiều dài)

            val string = pluck(freq, total - start, rng, brightness, position)
            for (i in string.indices) mix[start + i] += gain * string[i]

            // Tiếng "tạch" rất ngắn của phím gảy chạm dây
            val nClick = (0.004 * SAMPLE_RATE).toInt()
            var prevClick = 0f
            for (i in 0 until min(nClick, total - start)) {
                val c = (rng.nextFloat() * 2f - 1f) * exp(-i / (nClick / 4f))
                mix[start + i] += 0.10f * gain * (c - prevClick)
                prevClick = c
            }
            order++
        }

        val shaped = reverb(addBody(mix))

        var peak = 0f
        for (v in shaped) peak = max(peak, abs(v))
        val gain = if (peak > 0f) 0.85f / peak else 1f
        val fadeLen = (SAMPLE_RATE * 0.25).toInt()

        return ShortArray(total) { i ->
            var v = shaped[i] * gain
            val fromEnd = total - i
            if (fromEnd < fadeLen) v *= fromEnd / fadeLen.toFloat() // tắt dần ở cuối
            (v * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** Dây trầm ngân lâu hơn dây thanh: khoảng 7 giây ở dây 6 xuống 3,2 giây ở dây 1 (thời gian giảm 60 dB). */
    private fun t60For(freq: Double): Double {
        val x = ((ln(freq) - ln(82.41)) / (ln(329.63) - ln(82.41))).coerceIn(0.0, 1.0)
        return 7.0 + (3.2 - 7.0) * x
    }

    /**
     * Karplus-Strong: vòng trễ chứa "cú gảy" ban đầu, mỗi vòng lấy trung bình hai mẫu liền nhau
     * (các hoạ âm cao tắt nhanh hơn như dây thật). Có nội suy độ trễ lẻ nên đúng cao độ (lệch < 2 cent).
     * Cú gảy ban đầu là nhiễu đã làm mềm, rồi lọc theo vị trí gảy (gảy gần ngựa đàn thì sáng, xa thì ấm).
     */
    private fun pluck(freq: Double, length: Int, rng: java.util.Random, brightness: Float, position: Double): FloatArray {
        val period = SAMPLE_RATE / freq
        val n0 = floor(period - 0.5).toInt()
        val frac = (period - 0.5 - n0).toFloat()
        val init = n0 + 3

        val e = FloatArray(init)
        var prev = 0f
        for (i in 0 until init) {
            val noise = rng.nextFloat() * 2f - 1f
            prev = brightness * noise + (1f - brightness) * prev
            e[i] = prev
        }
        val k = max(1, (position * period).roundToInt())
        val e2 = FloatArray(init) { i -> if (i >= k) e[i] - e[i - k] else e[i] }
        var mean = 0f
        for (v in e2) mean += v
        mean /= init
        var peak = 0f
        for (i in 0 until init) {
            e2[i] -= mean
            peak = max(peak, abs(e2[i]))
        }
        if (peak > 0f) for (i in 0 until init) e2[i] /= peak

        val y = FloatArray(length)
        for (i in 0 until min(init, length)) y[i] = e2[i]

        val decay = 10.0.pow(-3.0 * (period / SAMPLE_RATE) / t60For(freq)).toFloat()
        for (i in init until length) {
            val a = 0.5f * (y[i - n0] + y[i - n0 - 1])
            val b = 0.5f * (y[i - n0 - 1] + y[i - n0 - 2])
            y[i] = decay * ((1f - frac) * a + frac * b)
        }
        return y
    }

    /** Bộ lọc dải thông (biquad) dùng để mô phỏng cộng hưởng thùng đàn. */
    private fun bandpass(x: FloatArray, f0: Double, q: Double): FloatArray {
        val w0 = 2.0 * PI * f0 / SAMPLE_RATE
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        val b0 = alpha / a0
        val b2 = -alpha / a0
        val a1 = -2.0 * cos(w0) / a0
        val a2 = (1.0 - alpha) / a0
        val y = FloatArray(x.size)
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (i in x.indices) {
            val xi = x[i].toDouble()
            val yi = b0 * xi + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = xi
            y2 = y1
            y1 = yi
            y[i] = yi.toFloat()
        }
        return y
    }

    /** Thùng đàn acoustic cộng hưởng mạnh quanh ~100 Hz, ~200 Hz và ~400 Hz: làm tiếng đầy và ấm hơn. */
    private fun addBody(x: FloatArray): FloatArray {
        val out = x.copyOf()
        val resonances = listOf(Triple(98.0, 4.0, 0.9), Triple(200.0, 5.0, 0.7), Triple(410.0, 6.0, 0.45))
        for ((f0, q, g) in resonances) {
            val band = bandpass(x, f0, q)
            for (i in out.indices) out[i] += (g * band[i]).toFloat()
        }
        return out
    }

    /** Vang phòng nhỏ (Schroeder: 4 bộ lọc lược + 2 bộ lọc thông tất), pha nhẹ để bớt "khô". */
    private fun reverb(x: FloatArray, wet: Float = 0.14f): FloatArray {
        val n = x.size
        val acc = FloatArray(n)
        val combDelays = intArrayOf(1557, 1617, 1491, 1422)
        val combGains = floatArrayOf(0.80f, 0.79f, 0.81f, 0.78f)
        for (c in combDelays.indices) {
            val d = combDelays[c]
            val g = combGains[c]
            val buf = FloatArray(n)
            for (i in 0 until n) buf[i] = x[i] + (if (i >= d) g * buf[i - d] else 0f)
            for (i in 0 until n) acc[i] += buf[i]
        }
        for (i in 0 until n) acc[i] *= 0.25f

        var cur = acc
        for ((d, g) in listOf(225 to 0.5f, 556 to 0.5f)) {
            val out = FloatArray(n)
            for (i in 0 until n) {
                val xd = if (i >= d) cur[i - d] else 0f
                val yd = if (i >= d) out[i - d] else 0f
                out[i] = -g * cur[i] + xd + g * yd
            }
            cur = out
        }

        val result = FloatArray(n)
        for (i in 0 until n) result[i] = (1f - wet) * x[i] + wet * cur[i] * 1.5f
        return result
    }
}