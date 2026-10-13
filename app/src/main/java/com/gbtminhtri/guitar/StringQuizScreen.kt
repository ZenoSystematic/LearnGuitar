package com.gbtminhtri.guitar

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

// ===== Cấu hình học tập: chỉnh các số này theo ý bạn =====
private const val FAST_MS = 3000L         // trả lời đúng trong thời gian này mới tính là "nhanh" và được tăng mức thuộc
private const val MASTERED_BOX = 3        // mức thuộc từ đây trở lên thì tính là đã thuộc câu đó (tối đa 4)
private const val GOAL_STREAK = 10        // đúng liên tiếp bấy nhiêu câu thì gợi ý tăng tốc độ
private const val TIMEOUT = "⏱"           // giá trị đặc biệt: hết giờ

private data class Level(val name: String, val limitMs: Long)

private val levels = listOf(
    Level("Chậm", 10_000L),
    Level("Vừa", 6_000L),
    Level("Nhanh", 4_000L),
    Level("Phản xạ", 2_500L)
)

private val stringNames = mapOf(6 to "E", 5 to "A", 4 to "D", 3 to "G", 2 to "B", 1 to "E")
private val nameLabels = listOf("E", "A", "D", "G", "B", "E")

// Màu từng mức thuộc: 0 (chưa thuộc) -> 4 (rất thuộc)
private val boxColors = listOf(
    Color(0xFFE5484D), Color(0xFFF5A524), Color(0xFFF2D13C), Color(0xFF8BD450), Color(0xFF3DBE8B)
)

/**
 * 12 câu hỏi cố định, mỗi câu có một "mức thuộc" riêng:
 *  - Câu 0..5:  số dây -> tên (dây 6, 5, 4, 3, 2, 1)
 *  - Câu 6..11: tên dây -> số dây (E trầm, A, D, G, B, E thanh)
 */
private data class Question(
    val item: Int,
    val text: String,
    val stringNumber: Int,
    val options: List<String>,
    val answer: String
)

private fun buildQuestion(item: Int): Question {
    return if (item < 6) {
        val n = 6 - item
        Question(item, "Dây số $n tên là gì?", n, listOf("E", "A", "D", "G", "B"), stringNames.getValue(n))
    } else {
        val n = 12 - item
        val name = stringNames.getValue(n)
        val text = when (n) {
            6 -> "Dây E to nhất (trầm nhất) là dây số mấy?"
            1 -> "Dây E nhỏ nhất (thanh nhất) là dây số mấy?"
            else -> "Dây $name là dây số mấy?"
        }
        Question(item, text, n, (1..6).map { it.toString() }, n.toString())
    }
}

/** Chọn câu tiếp theo: câu nào chưa thuộc thì có nhiều khả năng được chọn hơn. */
private fun pickItem(boxes: List<Int>, previous: Int?): Int {
    val weights = boxes.map { 5 - it }
    while (true) {
        var r = Random.nextInt(weights.sum())
        var index = 0
        for (i in weights.indices) {
            if (r < weights[i]) {
                index = i
                break
            }
            r -= weights[i]
        }
        if (index != previous) return index
    }
}

private fun loadBoxes(saved: String?): List<Int> {
    val parsed = saved?.split(",")?.mapNotNull { it.toIntOrNull() }
    return if (parsed != null && parsed.size == 12) parsed else List(12) { 0 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringQuizScreen() {
    val context = LocalContext.current
    val prefs = remember {
        context.applicationContext.getSharedPreferences("strings", Context.MODE_PRIVATE)
    }

    val boxes = remember {
        mutableStateListOf<Int>().apply { addAll(loadBoxes(prefs.getString("boxes", null))) }
    }
    var level by remember { mutableIntStateOf(prefs.getInt("level", 1).coerceIn(0, levels.size - 1)) }
    var soundOn by remember { mutableStateOf(prefs.getBoolean("sound", true)) }

    var question by remember { mutableStateOf(buildQuestion(pickItem(boxes, null))) }
    var selected by remember { mutableStateOf<String?>(null) }
    var streak by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(prefs.getInt("best_streak", 0)) }
    var correctCount by remember { mutableIntStateOf(0) }
    var answered by remember { mutableIntStateOf(0) }
    var showTable by remember { mutableStateOf(false) }

    var startedAtMs by remember { mutableLongStateOf(0L) }
    var lastTimeMs by remember { mutableLongStateOf(0L) }
    var timeFraction by remember { mutableFloatStateOf(1f) }
    val recentTimes = remember { mutableStateListOf<Long>() }

    fun saveBoxes() {
        prefs.edit()
            .putString("boxes", boxes.joinToString(","))
            .putBoolean("mastered", boxes.all { it >= MASTERED_BOX })
            .apply()
    }

    fun nextQuestion() {
        question = buildQuestion(pickItem(boxes, question.item))
        selected = null
    }

    fun answer(option: String) {
        if (selected != null) return
        val timeMs = System.currentTimeMillis() - startedAtMs
        lastTimeMs = timeMs
        selected = option
        answered += 1
        val item = question.item

        if (option == question.answer) {
            correctCount += 1
            streak += 1
            if (streak > best) {
                best = streak
                prefs.edit().putInt("best_streak", best).apply()
            }
            recentTimes.add(timeMs)
            if (recentTimes.size > 10) recentTimes.removeAt(0)
            // Chỉ tăng mức thuộc khi trả lời đúng VÀ nhanh
            if (timeMs <= FAST_MS) boxes[item] = minOf(4, boxes[item] + 1)
            if (soundOn) SoundFx.playCorrect()
        } else {
            streak = 0
            boxes[item] = maxOf(0, boxes[item] - 2)
            if (soundOn) SoundFx.playWrong()
        }
        saveBoxes()
    }

    fun timeUp() {
        if (selected != null) return
        selected = TIMEOUT
        lastTimeMs = levels[level].limitMs
        answered += 1
        streak = 0
        val item = question.item
        boxes[item] = maxOf(0, boxes[item] - 2)
        if (soundOn) SoundFx.playWrong()
        saveBoxes()
    }

    // Đếm ngược cho từng câu hỏi
    LaunchedEffect(question, level) {
        val limit = levels[level].limitMs
        startedAtMs = System.currentTimeMillis()
        timeFraction = 1f
        while (selected == null) {
            val elapsed = System.currentTimeMillis() - startedAtMs
            val fraction = 1f - elapsed.toFloat() / limit
            if (fraction <= 0f) {
                timeFraction = 0f
                timeUp()
                break
            }
            timeFraction = fraction
            delay(50)
        }
    }

    // Trả lời đúng thì tự sang câu tiếp sau một chút
    LaunchedEffect(selected) {
        if (selected != null && selected == question.answer) {
            delay(700)
            nextQuestion()
        }
    }

    val masteryPercent = boxes.sum() * 100 / (12 * 4)
    val mastered = boxes.all { it >= MASTERED_BOX }
    val chosen = selected

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Đố tên dây",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                soundOn = !soundOn
                prefs.edit().putBoolean("sound", soundOn).apply()
            }) { Text(if (soundOn) "🔊 Bật tiếng" else "🔇 Tắt tiếng") }
        }

        // Chọn mức tốc độ
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            levels.forEachIndexed { index, lv ->
                FilterChip(
                    selected = level == index,
                    onClick = {
                        level = index
                        prefs.edit().putInt("level", index).apply()
                    },
                    label = { Text("${lv.name} · ${lv.limitMs / 1000.0}s".replace(".0s", "s")) }
                )
            }
        }

        // Thanh thời gian
        TimerBar(fractionProvider = { timeFraction }, limitMs = levels[level].limitMs)

        // Câu hỏi và sơ đồ 6 dây
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                question.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            val isNumberToName = question.item < 6
            StringsDiagram(
                highlight = if (isNumberToName || chosen != null) question.stringNumber else null,
                showNames = chosen != null,
                modifier = Modifier.fillMaxWidth().height(110.dp)
            )
        }

        // Các đáp án, xếp 3 nút mỗi hàng
        question.options.chunked(3).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowOptions.forEach { option ->
                    val container = when {
                        chosen == null -> MaterialTheme.colorScheme.primary
                        option == question.answer -> MaterialTheme.colorScheme.tertiary
                        option == chosen -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    Button(
                        onClick = { answer(option) },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(64.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = container,
                            contentColor = contentColorFor(container)
                        )
                    ) { Text(option, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                }
                repeat(3 - rowOptions.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        // Phản hồi sau khi trả lời
        if (chosen != null) {
            val seconds = String.format("%.1f", lastTimeMs / 1000.0)
            when {
                chosen == TIMEOUT -> Text(
                    "Hết giờ! Đáp án là ${question.answer}.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                chosen == question.answer && lastTimeMs <= FAST_MS -> Text(
                    "Đúng rồi! ⚡ $seconds giây",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
                chosen == question.answer -> Text(
                    "Đúng, nhưng hơi chậm ($seconds giây). Dưới ${FAST_MS / 1000} giây mới tính là thuộc.",
                    style = MaterialTheme.typography.titleMedium
                )
                else -> Text(
                    "Chưa đúng. Đáp án là ${question.answer}.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (chosen != question.answer) {
                Button(
                    onClick = { nextQuestion() },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Câu tiếp") }
            }
        }

        if (streak >= GOAL_STREAK && level < levels.size - 1) {
            Text(
                "Đúng $streak câu liên tiếp! Thử mức nhanh hơn nhé.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Bảng độ thuộc
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Độ thuộc: $masteryPercent%", style = MaterialTheme.typography.titleMedium)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(masteryPercent / 100f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }
            MasteryRow("Số dây → tên", listOf("6", "5", "4", "3", "2", "1"), boxes.subList(0, 6))
            MasteryRow("Tên → số dây", nameLabels, boxes.subList(6, 12))
            Text(
                if (mastered) "🏆 Bạn đã thuộc cả 12 câu! Mục \"Biết tên 6 dây\" trong tab Tiến bộ đã tự tick."
                else "Mục tiêu: cả 12 chấm xanh lá. Mỗi lần trả lời đúng trong ${FAST_MS / 1000} giây, chấm đó tăng một mức; trả lời sai thì giảm.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val average = if (recentTimes.isEmpty()) null else recentTimes.average() / 1000.0
            Text(
                "Chuỗi đúng: $streak · Cao nhất: $best · Đúng $correctCount/$answered" +
                        (if (average != null) " · Tốc độ TB: ${String.format("%.1f", average)}s" else ""),
                style = MaterialTheme.typography.bodySmall
            )
        }

        TextButton(onClick = { showTable = !showTable }) {
            Text(if (showTable) "Ẩn bảng tên dây" else "Xem bảng tên dây")
        }
        if (showTable) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp)
            ) {
                Text("Dây 6: E (to nhất)")
                Text("Dây 5: A")
                Text("Dây 4: D")
                Text("Dây 3: G")
                Text("Dây 2: B")
                Text("Dây 1: E (nhỏ nhất)")
            }
        }
    }
}

/** Thanh thời gian chạy ngược, chuyển đỏ khi sắp hết giờ. Chỉ phần này vẽ lại khi đếm. */
@Composable
private fun TimerBar(fractionProvider: () -> Float, limitMs: Long) {
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

/** Sơ đồ 6 dây giống sơ đồ hợp âm: dây 6 (to nhất) bên trái, dây 1 (nhỏ nhất) bên phải. */
@Composable
private fun StringsDiagram(highlight: Int?, showNames: Boolean, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.onSurface
    val highlightColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val left = w * 0.14f
        val right = w * 0.86f
        val top = h * 0.28f
        val bottom = h * 0.72f
        val gap = (right - left) / 5f

        val normalPaint = Paint().apply {
            color = textColor.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = 15.sp.toPx()
            isAntiAlias = true
            isFakeBoldText = true
        }
        val highlightPaint = Paint().apply {
            color = highlightColor.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = 19.sp.toPx()
            isAntiAlias = true
            isFakeBoldText = true
        }

        for (index in 0..5) {
            val number = 6 - index
            val x = left + index * gap
            val isHighlighted = highlight == number
            val thickness = (4.5f - index * 0.6f).dp.toPx()

            if (isHighlighted) {
                drawLine(
                    color = highlightColor.copy(alpha = 0.25f),
                    start = Offset(x, top - 6.dp.toPx()),
                    end = Offset(x, bottom + 6.dp.toPx()),
                    strokeWidth = thickness + 12.dp.toPx()
                )
            }
            drawLine(
                color = if (isHighlighted) highlightColor else lineColor,
                start = Offset(x, top),
                end = Offset(x, bottom),
                strokeWidth = thickness
            )
            drawContext.canvas.nativeCanvas.drawText(
                number.toString(), x, top - 14.dp.toPx(),
                if (isHighlighted) highlightPaint else normalPaint
            )
            if (showNames) {
                drawContext.canvas.nativeCanvas.drawText(
                    nameLabels[index], x, bottom + 24.dp.toPx(),
                    if (isHighlighted) highlightPaint else normalPaint
                )
            }
        }
    }
}

/** Một hàng chấm tròn, màu thể hiện mức thuộc của từng câu hỏi. */
@Composable
private fun MasteryRow(title: String, labels: List<String>, rowBoxes: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, label ->
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(boxColors[rowBoxes[i].coerceIn(0, 4)]),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}