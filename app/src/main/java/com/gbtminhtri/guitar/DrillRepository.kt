package com.gbtminhtri.guitar

import android.content.Context

data class DrillResult(
    val timeMillis: Long,
    val pair: String,   // chuyển hợp âm: "C-G" (hai hợp âm xếp theo abc); quạt/rải: "Quạt 1 · G"
    val bpm: Int,
    val errors: Int,
    val passed: Boolean
)

/**
 * Lưu kết quả luyện tập và tiến độ BPM.
 * Dùng SharedPreferences cho đơn giản (không cần cấu hình thêm thư viện).
 * Có hai lịch sử riêng: bài tập chuyển hợp âm và bài tập quạt/rải.
 */
class DrillRepository(context: Context) {

    private val prefs = context.getSharedPreferences("drill", Context.MODE_PRIVATE)

    fun getBpm(pair: String): Int = prefs.getInt("bpm_$pair", 50)
    fun setBpm(pair: String, bpm: Int) = prefs.edit().putInt("bpm_$pair", bpm).apply()

    /** Số lượt đạt liên tiếp ở BPM hiện tại. */
    fun getPasses(pair: String): Int = prefs.getInt("passes_$pair", 0)
    fun setPasses(pair: String, n: Int) = prefs.edit().putInt("passes_$pair", n).apply()

    // ----- Bài tập chuyển hợp âm -----
    fun save(result: DrillResult) = append("history", result)
    fun loadAll(): List<DrillResult> = read("history")

    /** BPM cao nhất đã đạt (đạt tiêu chí) cho cặp hợp âm này. */
    fun bestBpm(pair: String): Int? =
        loadAll().filter { it.pair == pair && it.passed }.maxOfOrNull { it.bpm }

    // ----- Bài tập quạt/rải -----
    fun savePatternResult(result: DrillResult) = append("pattern_history", result)
    fun loadPatternResults(): List<DrillResult> = read("pattern_history")

    // ----- Hàm dùng chung -----
    private fun append(key: String, result: DrillResult) {
        val line = listOf(
            result.timeMillis, result.pair, result.bpm, result.errors,
            if (result.passed) 1 else 0
        ).joinToString("|")
        val old = prefs.getString(key, "") ?: ""
        prefs.edit().putString(key, if (old.isEmpty()) line else "$old\n$line").apply()
    }

    private fun read(key: String): List<DrillResult> {
        val raw = prefs.getString(key, "") ?: ""
        return raw.lines().mapNotNull { line ->
            val p = line.split("|")
            if (p.size != 5) return@mapNotNull null
            try {
                DrillResult(p[0].toLong(), p[1], p[2].toInt(), p[3].toInt(), p[4] == "1")
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}