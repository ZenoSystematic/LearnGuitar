package com.gbtminhtri.guitar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sin

/**
 * Test cho phần logic thuần (không cần điện thoại hay giả lập).
 * Đặt file này vào: app/src/test/java/com/gbtminhtri/guitar/AppLogicTest.kt
 * Chạy: nhấp chuột phải vào file, chọn Run 'AppLogicTest' (hoặc ./gradlew test).
 */
class AppLogicTest {

    // ----- Dữ liệu hợp âm: mỗi chỗ tham chiếu tên hợp âm đều phải trỏ tới hợp âm có thật -----

    @Test
    fun everyChordHasSixFretsAndSixFingers() {
        basicChords.forEach {
            assertEquals("${it.name}: số phím", 6, it.frets.size)
            assertEquals("${it.name}: số ngón", 6, it.fingers.size)
        }
    }

    @Test
    fun progressionsUseOnlyKnownChords() {
        val names = basicChords.map { it.name }.toSet()
        progressions.forEach { p ->
            p.chords.forEach { assertTrue("${p.name} dùng hợp âm lạ: $it", it in names) }
        }
    }

    @Test
    fun everyChordHasABassString() {
        basicChords.forEach { assertTrue("${it.name} thiếu trong bassStrings", bassStrings.containsKey(it.name)) }
    }

    @Test
    fun lookAlikesPointToExistingChords() {
        val names = basicChords.map { it.name }.toSet()
        chordLookAlikes.forEach { (chord, others) ->
            assertTrue("$chord không có trong basicChords", chord in names)
            others.forEach { assertTrue("$chord nhầm với hợp âm lạ: $it", it in names) }
        }
    }

    // ----- Chọn câu hỏi theo độ thuộc -----

    @Test
    fun weightedPickNeverRepeatsPreviousWhenOthersExist() {
        val boxes = listOf(0, 0, 0)
        repeat(300) {
            assertTrue(pickWeightedItem(listOf(0, 1, 2), boxes, previous = 1) != 1)
        }
    }

    @Test
    fun weightedPickFavorsWeakItems() {
        val boxes = listOf(4, 4, 0) // câu 2 chưa thuộc, hai câu kia đã rất thuộc
        val picks = List(2000) { pickWeightedItem(listOf(0, 1, 2), boxes, previous = null) }
        assertTrue("câu yếu nên được hỏi nhiều nhất", picks.count { it == 2 } > 1000)
    }

    // ----- Chuỗi ngày -----

    private val fmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun keyDaysAgo(now: Long, n: Int): String {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.add(Calendar.DAY_OF_YEAR, -n)
        return fmt.format(cal.time)
    }

    private val now: Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 15, 12, 0, 0)
    }.timeInMillis

    @Test
    fun streakCountsConsecutiveDaysIncludingToday() {
        val days = setOf(keyDaysAgo(now, 0), keyDaysAgo(now, 1), keyDaysAgo(now, 2))
        assertEquals(3, DayStreak.currentStreak(days, now))
    }

    @Test
    fun streakSurvivesUntilEndOfTodayIfYesterdayWasPracticed() {
        val days = setOf(keyDaysAgo(now, 1), keyDaysAgo(now, 2))
        assertEquals(2, DayStreak.currentStreak(days, now))
    }

    @Test
    fun streakBreaksAfterAMissedDay() {
        val days = setOf(keyDaysAgo(now, 2), keyDaysAgo(now, 3))
        assertEquals(0, DayStreak.currentStreak(days, now))
    }

    @Test
    fun streakIsZeroWithNoDays() {
        assertEquals(0, DayStreak.currentStreak(emptySet(), now))
    }

    // ----- Dò cao độ: sinh sóng giống tiếng đàn rồi kiểm tra tần số tìm được -----

    @Test
    fun pitchDetectorFindsEveryOpenString() {
        val sampleRate = 44100
        val strings = listOf(82.41, 110.00, 146.83, 196.00, 246.94, 329.63)
        for (f in strings) {
            val buffer = FloatArray(8192) { i ->
                val t = i.toDouble() / sampleRate
                // hoạ âm bậc 1, 2, 3 như dây đàn thật
                (sin(2 * PI * f * t) + 0.5 * sin(2 * PI * 2 * f * t) + 0.25 * sin(2 * PI * 3 * f * t)).toFloat() * 0.3f
            }
            val detected = PitchDetector.detect(buffer, sampleRate)
            assertNotNull("không nhận ra dây $f Hz", detected)
            val centsOff = 1200.0 * ln(detected!! / f) / ln(2.0)
            assertTrue("dây $f Hz lệch ${"%.1f".format(centsOff)} cent", abs(centsOff) < 5.0)
        }
    }
}