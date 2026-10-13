package com.gbtminhtri.guitar

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

/**
 * Thu âm từ micro và dò cao độ liên tục (khoảng 20 lần mỗi giây).
 * onPitch được gọi trên luồng nền: tần số (Hz), hoặc null nếu im lặng hoặc không rõ nốt.
 * Chỉ gọi start() sau khi đã được cấp quyền micro.
 */
class TunerEngine(private val onPitch: (Double?) -> Unit) {

    private val sampleRate = 44100
    private val frameSize = 4096      // cửa sổ phân tích, khoảng 93 ms
    private val hopSize = 2048        // mỗi lần đọc thêm khoảng 46 ms
    private val silenceRms = 0.01f    // quá nhỏ hơn mức này thì coi như không có tiếng đàn

    @Volatile private var running = false
    private var thread: Thread? = null

    val isRunning: Boolean get() = running

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        thread = Thread {
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, frameSize * 4)
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                running = false
                onPitch(null)
                return@Thread
            }

            record.startRecording()
            val chunk = ShortArray(hopSize)
            val window = FloatArray(frameSize)
            var filled = 0

            while (running) {
                val read = record.read(chunk, 0, hopSize)
                if (read <= 0) continue

                // Trượt cửa sổ sang trái và thêm mẫu mới vào cuối
                System.arraycopy(window, read, window, 0, frameSize - read)
                for (i in 0 until read) {
                    window[frameSize - read + i] = chunk[i] / 32768f
                }
                filled = minOf(frameSize, filled + read)
                if (filled < frameSize) continue

                var sum = 0.0
                for (v in window) sum += v * v
                val rms = sqrt(sum / frameSize).toFloat()

                if (rms < silenceRms) {
                    onPitch(null)
                } else {
                    onPitch(PitchDetector.detect(window, sampleRate))
                }
            }

            record.stop()
            record.release()
        }.also { it.start() }
    }

    fun stop() {
        running = false
        thread = null
    }
}