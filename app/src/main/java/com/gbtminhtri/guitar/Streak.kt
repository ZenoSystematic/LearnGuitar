package com.gbtminhtri.guitar

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Các ngày đã tập, lưu chung một chỗ (SharedPreferences "today", khóa "days", định dạng yyyy-MM-dd)
 * cho cả buổi tập ở màn hình Hôm nay lẫn Thử thách hàng ngày.
 */
object DayStreak {
    private fun format() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun todayKey(): String = format().format(Date())

    fun days(context: Context): Set<String> =
        context.applicationContext.getSharedPreferences("today", Context.MODE_PRIVATE)
            .getStringSet("days", emptySet())!!.toSet()

    /** Ghi nhận hôm nay là một ngày đã tập. */
    fun markToday(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences("today", Context.MODE_PRIVATE)
        val updated = prefs.getStringSet("days", emptySet())!!.toSet() + todayKey()
        prefs.edit().putStringSet("days", updated).apply()
    }

    /** Thời điểm cách `now` đúng n ngày lịch (đúng cả khi đổi giờ hoặc múi giờ, khác với trừ 86_400_000ms). */
    fun daysAgo(now: Long, n: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.add(Calendar.DAY_OF_YEAR, -n)
        return cal.timeInMillis
    }

    /**
     * Số ngày liên tiếp đã tập tính đến hôm nay.
     * Nếu hôm nay chưa tập mà hôm qua có tập thì chuỗi vẫn còn nguyên (chỉ đứt khi qua hết hôm nay mà vẫn chưa tập).
     */
    fun currentStreak(days: Set<String>, now: Long = System.currentTimeMillis()): Int {
        val fmt = format()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        if (fmt.format(cal.time) !in days) cal.add(Calendar.DAY_OF_YEAR, -1)
        var count = 0
        while (fmt.format(cal.time) in days) {
            count++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return count
    }
}