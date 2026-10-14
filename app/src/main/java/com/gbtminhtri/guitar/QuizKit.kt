package com.gbtminhtri.guitar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/** Màu 5 mức thuộc: 0 (đỏ, chưa thuộc) -> 4 (xanh, rất thuộc). */
val quizBoxColors = listOf(
    Color(0xFFE5484D), Color(0xFFF5A524), Color(0xFFF2D13C), Color(0xFF8BD450), Color(0xFF3DBE8B)
)

/** Các hợp âm dễ nhầm với nhau: dùng làm đáp án gây nhiễu để bài khó dần. */
val chordLookAlikes = mapOf(
    "Em" to listOf("E", "Am"),
    "Am" to listOf("A", "Em", "Dm"),
    "C" to listOf("Am", "G", "F"),
    "G" to listOf("C", "D"),
    "D" to listOf("A", "G", "Dm"),
    "A" to listOf("Am", "D"),
    "E" to listOf("Em", "A"),
    "Dm" to listOf("D", "Am"),
    "F" to listOf("C", "Dm")
)

/** Đọc danh sách mức thuộc đã lưu; sai kích thước thì bắt đầu lại từ 0. */
fun parseQuizBoxes(saved: String?, size: Int): List<Int> {
    val parsed = saved?.split(",")?.mapNotNull { it.toIntOrNull() }
    return if (parsed != null && parsed.size == size) parsed else List(size) { 0 }
}

/**
 * Chọn một câu trong `candidates`: câu nào mức thuộc càng thấp thì càng dễ được chọn.
 * Tránh lặp lại câu vừa hỏi (nếu còn câu khác).
 */
fun pickWeightedItem(candidates: List<Int>, boxes: List<Int>, previous: Int?): Int {
    val pool = if (candidates.size > 1) candidates.filter { it != previous } else candidates
    val weights = pool.map { 5 - boxes[it].coerceIn(0, 4) }
    var r = Random.nextInt(weights.sum())
    for (i in pool.indices) {
        if (r < weights[i]) return pool[i]
        r -= weights[i]
    }
    return pool.last()
}

/** Thanh thời gian chạy ngược, chuyển đỏ khi sắp hết giờ. */
@Composable
fun QuizTimerBar(fractionProvider: () -> Float, limitMs: Long) {
    val fraction = fractionProvider().coerceIn(0f, 1f)
    val color = if (fraction > 0.3f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Text(
            String.format("%.1fs", fraction * limitMs / 1000f),
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
    }
}

/** Một hàng chấm tròn, màu thể hiện mức thuộc của từng hợp âm. */
@Composable
fun QuizMasteryRow(title: String, labels: List<String>, rowBoxes: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { i, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 44.dp)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(quizBoxColors[rowBoxes[i].coerceIn(0, 4)]),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}