package com.gbtminhtri.guitar

/**
 * Dò cao độ bằng thuật toán YIN.
 * Trả về tần số (Hz) hoặc null nếu không nghe rõ một nốt nào.
 * Chỉ tìm trong khoảng 70-400 Hz, vừa đủ cho 6 dây đàn guitar chuẩn (E2 = 82 Hz đến E4 = 330 Hz).
 */
object PitchDetector {

    fun detect(
        buffer: FloatArray,
        sampleRate: Int,
        minFreq: Double = 70.0,
        maxFreq: Double = 400.0,
        threshold: Double = 0.15
    ): Double? {
        val tauMax = (sampleRate / minFreq).toInt()
        val tauMin = (sampleRate / maxFreq).toInt()
        val window = buffer.size - tauMax
        if (window <= 0) return null

        // Bước 1: độ chênh giữa tín hiệu và chính nó khi trễ tau mẫu
        val d = DoubleArray(tauMax + 1)
        for (tau in 1..tauMax) {
            var sum = 0.0
            for (i in 0 until window) {
                val diff = (buffer[i] - buffer[i + tau]).toDouble()
                sum += diff * diff
            }
            d[tau] = sum
        }

        // Bước 2: chuẩn hóa theo trung bình tích lũy
        val cmnd = DoubleArray(tauMax + 1)
        cmnd[0] = 1.0
        var running = 0.0
        for (tau in 1..tauMax) {
            running += d[tau]
            cmnd[tau] = if (running == 0.0) 1.0 else d[tau] * tau / running
        }

        // Bước 3: tìm chu kỳ đầu tiên có độ chênh đủ nhỏ, rồi đi tới đáy của chỗ trũng đó
        var tauEstimate = -1
        var tau = tauMin
        while (tau <= tauMax) {
            if (cmnd[tau] < threshold) {
                while (tau + 1 <= tauMax && cmnd[tau + 1] < cmnd[tau]) tau++
                tauEstimate = tau
                break
            }
            tau++
        }
        if (tauEstimate == -1) return null

        // Bước 4: nội suy parabol cho chính xác dưới mức 1 mẫu
        var betterTau = tauEstimate.toDouble()
        if (tauEstimate > 1 && tauEstimate < tauMax) {
            val s0 = cmnd[tauEstimate - 1]
            val s1 = cmnd[tauEstimate]
            val s2 = cmnd[tauEstimate + 1]
            val denominator = 2 * (2 * s1 - s2 - s0)
            if (denominator != 0.0) betterTau = tauEstimate + (s2 - s0) / denominator
        }
        return sampleRate / betterTau
    }
}