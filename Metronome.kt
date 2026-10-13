package com.gbtminhtri.guitar

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Metronome phát tiếng "tích" bằng AudioTrack (stream PCM) nên nhịp rất đều,
 * không bị lệch như khi dùng Handler/delay.
 */
class Metronome(private val onBeat: (beatIndex: Int) -> Unit) {

    private val sampleRate = 44100

    @Volatile var bpm: Int = 80
    @Volatile var beatsPerBar: Int = 4
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
            var beat = 0
            while (running) {
                val samplesPerBeat = sampleRate * 60 / bpm
                val buffer = makeBeat(samplesPerBeat, accent = beat == 0)
                onBeat(beat)
                track.write(buffer, 0, buffer.size) // blocks tới khi đủ chỗ -> nhịp đều
                beat = (beat + 1) % beatsPerBar
            }
            track.stop()
            track.release()
        }.also { it.start() }
    }

    fun stop() {
        running = false
        thread = null
    }

    /** Một nhịp = tiếng click ngắn (30ms) + im lặng cho đủ độ dài nhịp. */
    private fun makeBeat(totalSamples: Int, accent: Boolean): ShortArray {
        val out = ShortArray(totalSamples)
        val freq = if (accent) 1800.0 else 1200.0
        val clickLen = (sampleRate * 0.03).toInt().coerceAtMost(totalSamples)
        for (i in 0 until clickLen) {
            val envelope = 1.0 - i.toDouble() / clickLen
            val sample = sin(2 * PI * freq * i / sampleRate) * envelope
            out[i] = (sample * Short.MAX_VALUE * 0.8).toInt().toShort()
        }
        return out
    }
}
