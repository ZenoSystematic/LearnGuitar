package com.gbtminhtri.guitar

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class SessionStep(
    val title: String,
    val minutes: Int,        // buổi 15 phút
    val shortMinutes: Int,   // buổi 10 phút (ngày bận)
    val instruction: String,
    val route: String? = null,
    val routeLabel: String = "",
    val route2: String? = null,
    val route2Label: String = "",
    val emoji: String = "🎵"
)

// Bạn có thể sửa nội dung, số phút và biểu tượng ở đây.
private val sessionSteps = listOf(
    SessionStep(
        "Khởi động và Tuner", 2, 1,
        "Chỉnh dây bằng Tuner, duỗi ngón tay, rồi làm vài câu đố tên dây nếu còn thời gian.",
        "tuner", "Mở Tuner",
        "strings", "Mở bài đố tên dây",
        emoji = "🎚️"
    ),
    SessionStep(
        "Hợp âm sạch", 3, 2,
        "Bấm từng hợp âm bạn đang học, gảy từng dây một và nghe xem dây nào bị tịt hoặc rè.",
        "chords", "Mở Hợp âm",
        "chordquiz", "Quiz hợp âm",
        emoji = "🎼"
    ),
    SessionStep(
        "Chuyển hợp âm", 5, 4,
        "Chạy một lượt 60 giây, thật thà bấm nút khi mắc lỗi. Làm lại nếu còn thời gian.",
        "practice", "Mở Luyện tập",
        emoji = "🔁"
    ),
    SessionStep(
        "Quạt hoặc rải", 3, 2,
        "Chọn một mẫu quạt hoặc rải bạn đang tập và chơi liền, giữ nhịp đều.",
        "pattern", "Mở Quạt/Rải",
        emoji = "🎸"
    ),
    SessionStep(
        "Đệm một vòng hợp âm", 2, 1,
        "Chọn một vòng hợp âm và đệm liên tục, cố gắng đổi hợp âm kịp nhịp.",
        "pattern", "Mở Quạt/Rải",
        emoji = "🎶"
    )
)

/**
 * Trạng thái buổi tập nằm ngoài màn hình, và đồng hồ tính theo mốc thời gian thật,
 * nên bạn mở tab khác để tập rồi quay lại thì thời gian vẫn đúng.
 */
object SessionStore {
    var running by mutableStateOf(false)
    var finished by mutableStateOf(false)
    var short by mutableStateOf(false)
    var stepIndex by mutableIntStateOf(0)
    var stepEndMillis by mutableLongStateOf(0L)
}

private fun minutesOf(i: Int): Int =
    if (SessionStore.short) sessionSteps[i].shortMinutes else sessionSteps[i].minutes

private val heroGradient = Brush.linearGradient(listOf(Color(0xFFFF9A3C), Color(0xFFD9541E)))
private val successGradient = Brush.linearGradient(listOf(Color(0xFF3DBE8B), Color(0xFF1F8A5B)))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.applicationContext.getSharedPreferences("today", Context.MODE_PRIVATE)
    }
    val repo = remember { DrillRepository(context.applicationContext) }

    // Làm mới đồng hồ mỗi nửa giây
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            now = System.currentTimeMillis()
        }
    }

    // Những ngày đã tập
    val dayFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    var days by remember { mutableStateOf(prefs.getStringSet("days", emptySet())!!.toSet()) }
    val daysThisWeek = (0..6).count { d ->
        dayFormat.format(Date(now - d * 86_400_000L)) in days
    }
    val practicedToday = dayFormat.format(Date(now)) in days

    fun startStep(i: Int) {
        SessionStore.stepIndex = i
        SessionStore.stepEndMillis = System.currentTimeMillis() + minutesOf(i) * 60_000L
    }

    fun finishSession() {
        SessionStore.running = false
        SessionStore.finished = true
        days = days + dayFormat.format(Date())
        prefs.edit().putStringSet("days", days.toSet()).apply()
    }

    fun nextStep() {
        val next = SessionStore.stepIndex + 1
        if (next >= sessionSteps.size) finishSession() else startStep(next)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when {
            // ===== Đang tập =====
            SessionStore.running -> {
                val i = SessionStore.stepIndex
                val step = sessionSteps[i]
                val totalSeconds = (minutesOf(i) * 60).toFloat()
                val remaining = ((SessionStore.stepEndMillis - now) / 1000).coerceAtLeast(0)
                val tip = remember(i) {
                    if (i == 2) {
                        val pair = repo.loadAll().lastOrNull()?.pair ?: "C-G"
                        "Gợi ý: chuyển ${pair.replace("-", " ↔ ")} ở ${repo.getBpm(pair)} BPM."
                    } else null
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Bước ${i + 1}/${sessionSteps.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    // Thanh tiến độ các bước
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sessionSteps.indices.forEach { k ->
                            Box(
                                modifier = Modifier
                                    .width(if (k == i) 28.dp else 12.dp)
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (k <= i) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                            )
                        }
                    }

                    TimerRing(
                        fraction = (remaining / totalSeconds).coerceIn(0f, 1f),
                        emoji = step.emoji,
                        timeText = String.format("%d:%02d", remaining / 60, remaining % 60)
                    )

                    Text(
                        step.title,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    if (remaining == 0L) {
                        Text(
                            "Hết giờ bước này! Sang bước tiếp nhé.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        step.instruction,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (tip != null) {
                        Text(
                            tip,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (step.route != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { onOpen(step.route) }) { Text(step.routeLabel) }
                            if (step.route2 != null) {
                                OutlinedButton(onClick = { onOpen(step.route2) }) { Text(step.route2Label) }
                            }
                        }
                    }
                }

                Button(
                    onClick = { nextStep() },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Text(
                        if (i == sessionSteps.size - 1) "Hoàn thành 🎉" else "Xong, sang bước tiếp",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(
                    onClick = { SessionStore.running = false },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("Dừng buổi tập") }
            }

            // ===== Vừa hoàn thành =====
            SessionStore.finished -> {
                HeroCard(now, daysThisWeek, practicedToday, days, dayFormat)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(successGradient)
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🎉", fontSize = 56.sp)
                        Text(
                            "Hoàn thành buổi tập!",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Text(
                            "Bạn vừa tập có chủ đích. Nghe lại tiếng đàn của mình một chút rồi nghỉ tay nhé.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.92f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Button(
                    onClick = { SessionStore.finished = false },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("Xong", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            }

            // ===== Màn hình chính =====
            else -> {
                HeroCard(now, daysThisWeek, practicedToday, days, dayFormat)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !SessionStore.short,
                        onClick = { SessionStore.short = false },
                        label = { Text("15 phút") }
                    )
                    FilterChip(
                        selected = SessionStore.short,
                        onClick = { SessionStore.short = true },
                        label = { Text("10 phút (ngày bận)") }
                    )
                }

                val totalMinutes = sessionSteps.indices.sumOf { minutesOf(it) }
                Text(
                    "Buổi tập hôm nay · $totalMinutes phút",
                    style = MaterialTheme.typography.titleMedium
                )
                sessionSteps.forEachIndexed { index, step ->
                    StepRow(step, minutesOf(index))
                }

                Button(
                    onClick = {
                        SessionStore.finished = false
                        SessionStore.running = true
                        startStep(0)
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) {
                    Text("▶  Bắt đầu buổi tập", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Text("Công cụ", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolCard("🎚️", "Tuner", Modifier.weight(1f)) { onOpen("tuner") }
                    ToolCard("🧠", "Đố tên dây", Modifier.weight(1f)) { onOpen("strings") }
                    ToolCard("⏱️", "Metronome", Modifier.weight(1f)) { onOpen("metronome") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolCard("📝", "Quiz hợp âm", Modifier.weight(1f)) { onOpen("chordquiz") }
                    ToolCard("👂", "Luyện tai", Modifier.weight(1f)) { onOpen("ear") }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Thẻ đầu trang: lời chào, 7 ngày gần nhất, số ngày đã tập trong tuần. */
@Composable
private fun HeroCard(
    now: Long,
    daysThisWeek: Int,
    practicedToday: Boolean,
    days: Set<String>,
    dayFormat: SimpleDateFormat
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 11 -> "Chào buổi sáng"
        hour < 14 -> "Chào buổi trưa"
        hour < 18 -> "Chào buổi chiều"
        else -> "Chào buổi tối"
    }
    val dayNames = arrayOf("CN", "T2", "T3", "T4", "T5", "T6", "T7")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(heroGradient)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "$greeting!",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Text(
                        if (practicedToday) "Hôm nay bạn đã tập rồi" else "Tập guitar hôm nay nhé",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                }
                Text("🎸", fontSize = 56.sp)
            }

            // 7 ngày gần nhất, hôm nay nằm ngoài cùng bên phải
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (offset in 6 downTo 0) {
                    val time = now - offset * 86_400_000L
                    val done = dayFormat.format(Date(time)) in days
                    val cal = Calendar.getInstance().apply { timeInMillis = time }
                    val label = dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (done) Color.White else Color.White.copy(alpha = 0.25f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (done) {
                                Text("✓", color = Color(0xFFD9541E), fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = if (offset == 0) 1f else 0.75f),
                            fontWeight = if (offset == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Text(
                "Tuần này: $daysThisWeek/7 ngày. Nghỉ một ngày cũng không sao, quay lại là được.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

/** Một bước của buổi tập: biểu tượng, tên và số phút. */
@Composable
private fun StepRow(step: SessionStep, minutes: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(step.emoji, fontSize = 22.sp)
        }
        Text(
            step.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$minutes phút",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** Thẻ công cụ nhanh: biểu tượng lớn và tên. */
@Composable
private fun ToolCard(emoji: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(emoji, fontSize = 30.sp)
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** Vòng đồng hồ đếm ngược, vòng cung ngắn dần theo thời gian còn lại. */
@Composable
private fun TimerRing(fraction: Float, emoji: String, timeText: String) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val arcColor = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 36.sp)
            Text(timeText, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        }
    }
}