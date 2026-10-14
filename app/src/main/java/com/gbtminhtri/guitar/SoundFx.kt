package com.gbtminhtri.guitar

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Âm báo đúng/sai, tự tạo bằng code nên không cần thêm file âm thanh nào.
 * - Đúng: hai nốt "ting-ting" đi lên, nghe vui tai.
 * - Sai hoặc hết giờ: hai nốt "bù-bùm" đi xuống (cao rồi thấp), nghe là biết ngay mình sai.
 */
object SoundFx {

    private const val SAMPLE_RATE = 44100

    private val correctSound: ShortArray by lazy {
        makeTone(880.0, 90, 0.45) + makeTone(1318.5, 180, 0.45)
    }

    private val wrongSound: ShortArray by lazy {
        // Hai nốt đi xuống: ~G4 rồi ~C4, mỗi nốt trượt xuống nhẹ cho nghe "xìu"
        makeBuzz(392.0, 370.0, 130, 0.40) + makeBuzz(262.0, 225.0, 300, 0.40)
    }

    fun playCorrect() = play(correctSound)
    fun playWrong() = play(wrongSound)

    /** Một nốt có độ vang tắt dần. */
    private fun makeTone(freq: Double, durationMs: Int, amplitude: Double): ShortArray {
        val n = SAMPLE_RATE * durationMs / 1000
        return ShortArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val decay = 1.0 - i.toDouble() / n
            val attack = minOf(1.0, i / 200.0)
            val s = sin(2 * PI * freq * t) + 0.3 * sin(2 * PI * freq * 2 * t)
            (s / 1.3 * decay * decay * attack * amplitude * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** Tiếng "bzzt": sóng nhiều họa âm, tần số trượt từ cao xuống thấp. */
    private fun makeBuzz(startFreq: Double, endFreq: Double, durationMs: Int, amplitude: Double): ShortArray {
        val n = SAMPLE_RATE * durationMs / 1000
        var phase = 0.0
        return ShortArray(n) { i ->
            val progress = i.toDouble() / n
            val freq = startFreq + (endFreq - startFreq) * progress
            phase += 2 * PI * freq / SAMPLE_RATE
            val s = sin(phase) + 0.5 * sin(3 * phase) + 0.25 * sin(5 * phase)
            val attack = minOf(1.0, i / 150.0)
            val envelope = 1.0 - progress * 0.7
            (s / 1.75 * attack * envelope * amplitude * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun play(samples: ShortArray) {
        Thread {
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                Thread.sleep(samples.size * 1000L / SAMPLE_RATE + 80)
                track.release()
            } catch (e: Exception) {
                // Thiết bị không phát được thì im lặng, không làm app bị lỗi
            }
        }.start()
    }
}