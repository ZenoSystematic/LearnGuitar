package com.gbtminhtri.guitar

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Metronome phát tiếng "tích" bằng AudioTrack (stream PCM) nên nhịp rất đều.
 *
 * - onBeat(beatIndex): gọi mỗi nhịp chính (0..beatsPerBar-1)
 * - onStep(stepIndex): gọi mỗi bước nhỏ (0..beatsPerBar*stepsPerBeat-1), dùng cho Pattern Trainer
 * - stepsPerBeat: 1 = mỗi nhịp một tiếng, 2 = chia đôi nhịp (đếm "1 & 2 & 3 & 4 &")
 */
class Metronome(
    private val onStep: ((Int) -> Unit)? = null,
    private val onBeat: (beatIndex: Int) -> Unit
) {

    private val sampleRate = 44100

    @Volatile var bpm: Int = 80
    @Volatile var beatsPerBar: Int = 4
    @Volatile var stepsPerBeat: Int = 1
    @Volatile private var running = false
    private var thread: Thread? = null

    val isRunning: Boolean get() = running

    fun start() {
        if (running) return
        running = true
        thread = Thread {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()
            var step = 0
            while (running) {
                val spb = stepsPerBeat
                val total = beatsPerBar * spb
                step %= total
                val samplesPerStep = sampleRate * 60 / (bpm * spb)
                val isBeat = step % spb == 0
                val buffer = makeClick(samplesPerStep, accent = step == 0, strong = isBeat)
                if (isBeat) onBeat(step / spb)
                onStep?.invoke(step)
                track.write(buffer, 0, buffer.size) // blocks tới khi đủ chỗ -> nhịp đều
                step += 1
            }
            track.stop()
            track.release()
        }.also { it.start() }
    }

    fun stop() {
        running = false
        thread = null
    }

    /** Tiếng click ngắn + im lặng cho đủ độ dài bước. Nhịp chính to hơn, nhịp phụ nhỏ hơn. */
    private fun makeClick(totalSamples: Int, accent: Boolean, strong: Boolean): ShortArray {
        val out = ShortArray(totalSamples)
        val freq = when {
            accent -> 1800.0
            strong -> 1200.0
            else -> 800.0
        }
        val amplitude = if (strong) 0.8 else 0.35
        val clickLen = (sampleRate * 0.03).toInt().coerceAtMost(totalSamples)
        for (i in 0 until clickLen) {
            val envelope = 1.0 - i.toDouble() / clickLen
            val sample = sin(2 * PI * freq * i / sampleRate) * envelope
            out[i] = (sample * Short.MAX_VALUE * amplitude).toInt().toShort()
        }
        return out
    }
}