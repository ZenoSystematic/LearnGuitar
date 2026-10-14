package com.gbtminhtri.guitar

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Biểu đồ các lượt tập chuyển hợp âm: mỗi chấm là một lượt (chấm đặc = đạt, vòng rỗng = chưa đạt),
 * đường liền là BPM sạch cao nhất đã đạt tính đến lượt đó.
 */
@Composable
fun ProgressChart(results: List<DrillResult>, modifier: Modifier = Modifier) {
    val axisColor = MaterialTheme.colorScheme.outline
    val lineColor = MaterialTheme.colorScheme.primary
    val failColor = MaterialTheme.colorScheme.error
    val textColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier) {
        if (results.isEmpty()) return@Canvas

        val left = 90f
        val right = size.width - 20f
        val top = 20f
        val bottom = size.height - 20f

        val minBpm = results.minOf { it.bpm }
        val maxBpm = results.maxOf { it.bpm }
        val yMin = ((minBpm - 5) / 5) * 5
        var yMax = ((maxBpm + 5 + 4) / 5) * 5
        if (yMax <= yMin) yMax = yMin + 10

        fun yOf(bpm: Int): Float = bottom - (bpm - yMin).toFloat() / (yMax - yMin) * (bottom - top)
        fun xOf(i: Int): Float =
            if (results.size == 1) (left + right) / 2f
            else left + i.toFloat() / (results.size - 1) * (right - left)

        // Đường lưới và nhãn BPM bên trái
        val labelPaint = Paint().apply {
            color = textColor.toArgb()
            textSize = 12.sp.toPx()
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        listOf(yMin, (yMin + yMax) / 2, yMax).forEach { bpm ->
            val y = yOf(bpm)
            drawLine(axisColor, Offset(left, y), Offset(right, y), strokeWidth = 1.5f)
            drawContext.canvas.nativeCanvas.drawText(
                bpm.toString(), left - 12f, y + labelPaint.textSize / 3f, labelPaint
            )
        }

        // Đường BPM sạch cao nhất tính đến từng lượt
        val path = Path()
        var best: Int? = null
        var started = false
        results.forEachIndexed { i, r ->
            if (r.passed) best = maxOf(best ?: 0, r.bpm)
            val b = best
            if (b != null) {
                val x = xOf(i)
                val y = yOf(b)
                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }
        }
        if (started) drawPath(path, lineColor, style = Stroke(width = 4f))

        // Từng lượt tập
        results.forEachIndexed { i, r ->
            val center = Offset(xOf(i), yOf(r.bpm))
            if (r.passed) {
                drawCircle(lineColor, radius = 9f, center = center)
            } else {
                drawCircle(failColor, radius = 9f, center = center, style = Stroke(width = 4f))
            }
        }
    }
}

private fun pairLabel(pair: String) = pair.replace("-", " ↔ ")

/** Ô số liệu nhỏ dưới biểu đồ. */
@Composable
private fun StatPill(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$emoji $value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressSection() {
    val context = LocalContext.current
    val repo = remember { DrillRepository(context.applicationContext) }
    val drillAll = remember { repo.loadAll() }
    val patternAll = remember { repo.loadPatternResults() }
    val drillPairs = remember { drillAll.map { it.pair }.distinct() }
    val patternKeys = remember { patternAll.map { it.pair }.distinct() }

    // Mặc định chọn chuỗi dữ liệu mới nhất. Khóa: "c:" + cặp hợp âm, hoặc "p:" + tên mẫu quạt/rải
    val lastDrill = drillAll.lastOrNull()
    val lastPattern = patternAll.lastOrNull()
    val initial: String? = when {
        lastDrill != null && (lastPattern == null || lastDrill.timeMillis >= lastPattern.timeMillis) ->
            "c:" + lastDrill.pair
        lastPattern != null -> "p:" + lastPattern.pair
        else -> null
    }
    var selectedKey by remember { mutableStateOf(initial) }
    val sel = selectedKey

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("📈 Biểu đồ BPM", style = MaterialTheme.typography.titleMedium)

        if (sel == null) {
            Text(
                "Chưa có dữ liệu. Tập một lượt ở tab Luyện tập hoặc Quạt/Rải để biểu đồ xuất hiện.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Một hàng chọn duy nhất: 🔁 = chuyển hợp âm, 🎸 = quạt/rải
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                drillPairs.forEach { p ->
                    FilterChip(
                        selected = sel == "c:$p",
                        onClick = { selectedKey = "c:$p" },
                        label = { Text("🔁 " + pairLabel(p)) }
                    )
                }
                patternKeys.forEach { k ->
                    FilterChip(
                        selected = sel == "p:$k",
                        onClick = { selectedKey = "p:$k" },
                        label = { Text("🎸 $k") }
                    )
                }
            }

            val results = if (sel.startsWith("c:")) {
                drillAll.filter { it.pair == sel.removePrefix("c:") }
            } else {
                patternAll.filter { it.pair == sel.removePrefix("p:") }
            }
            ProgressChart(
                results = results.takeLast(20),
                modifier = Modifier.fillMaxWidth().height(180.dp)
            )

            val passedList = results.filter { it.passed }
            val firstPassed = passedList.firstOrNull()?.bpm
            val bestPassed = passedList.maxOfOrNull { it.bpm }
            val bpmGain = if (firstPassed != null && bestPassed != null) {
                val diff = bestPassed - firstPassed
                if (diff >= 0) "+$diff" else "$diff"
            } else "—"

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("🎯", "${results.size}", "lượt tập", Modifier.weight(1f))
                StatPill("✅", "${passedList.size}", "lượt đạt", Modifier.weight(1f))
                StatPill("⚡", bpmGain, "BPM tăng", Modifier.weight(1f))
            }
            Text(
                "● đạt   ○ chưa đạt   — BPM sạch cao nhất",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}