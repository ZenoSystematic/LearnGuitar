package com.gbtminhtri.guitar

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * drillPair + drillBpm: nếu có, mục này tự động tick khi bài tập chuyển hợp âm
 * (tab Luyện tập) đã đạt ít nhất drillBpm BPM với cặp hợp âm đó.
 * drillPair là tên hai hợp âm xếp theo abc, nối bằng "-", ví dụ "Am-Em".
 */
data class RoadmapItem(
    val id: String,
    val title: String,
    val detail: String,
    val drillPair: String? = null,
    val drillBpm: Int = 0
)

data class RoadmapLevel(val title: String, val items: List<RoadmapItem>)

// Bạn có thể sửa, thêm, bớt mục tùy ý. Chỉ cần mỗi id là duy nhất.
private val roadmap = listOf(
    RoadmapLevel(
        "Cấp 1: Làm quen",
        listOf(
            RoadmapItem("strings", "Biết tên 6 dây", "Từ dây 6 đến dây 1: E A D G B E. Thuộc cả 12 câu trong bài đố tên dây (đúng trong 3 giây) thì tự tick."),
            RoadmapItem("tune", "Lên dây chuẩn bằng Tuner", "Dùng Tuner trong app (màn hình Hôm nay, bước đầu tiên). Chỉnh dây mỗi lần trước khi tập."),
            RoadmapItem("posture", "Tư thế ngồi và cầm đàn", "Ngồi thoải mái, tập 5 phút không bị mỏi tay hay đau vai.")
        )
    ),
    RoadmapLevel(
        "Cấp 2: Hợp âm đầu tiên",
        listOf(
            RoadmapItem("em", "Em sạch", "Gảy từng dây, cả 6 dây đều kêu rõ, không tịt."),
            RoadmapItem("am", "Am sạch", "Không gảy dây 6, 5 dây còn lại kêu rõ."),
            RoadmapItem(
                "em-am", "Chuyển Em ↔ Am đạt 50 BPM",
                "Tab Luyện tập: không quá 2 lỗi trong 60 giây.", "Am-Em", 50
            ),
            RoadmapItem("strum1", "Quạt 1 đều ở 60 BPM", "Tab Quạt/Rải: quạt 60 giây không lệch nhịp."),
            RoadmapItem("prog1", "Đệm vòng Em – Am bằng Quạt 3 ở 60 BPM", "Tab Quạt/Rải: chọn vòng Em – Am, chơi trọn 2 phút.")
        )
    ),
    RoadmapLevel(
        "Cấp 3: Mở rộng",
        listOf(
            RoadmapItem("cgd", "C, G, D sạch", "Mỗi hợp âm gảy từng dây đều kêu rõ."),
            RoadmapItem("dm", "Dm sạch", "Không gảy dây 6 và 5, 4 dây còn lại kêu rõ."),
            RoadmapItem("f", "F sạch (thế barre)", "Ngón trỏ ấn ngang cả 6 dây ở phím 1, các dây đều kêu. Hợp âm khó, cứ kiên nhẫn nhé."),
            RoadmapItem(
                "chordquiz", "Thuộc hình và tên ${basicChords.size} hợp âm",
                "Mục Quiz hợp âm: đạt mức \"đã thuộc\" ở cả hai chiều (tự tick)."
            ),
            RoadmapItem(
                "ear", "Nghe và nhận ra ${basicChords.size} hợp âm",
                "Mục Luyện tai: đạt mức \"đã thuộc\" cho tất cả hợp âm (tự tick)."
            ),
            RoadmapItem(
                "g-c", "Chuyển G ↔ C đạt 60 BPM",
                "Tab Luyện tập: không quá 2 lỗi trong 60 giây.", "C-G", 60
            ),
            RoadmapItem("strum3", "Quạt 3 ở 70 BPM", "Tab Quạt/Rải: quạt 60 giây đều tay."),
            RoadmapItem("prog2", "Đệm vòng G – D – Em – C ở 60 BPM", "Tab Quạt/Rải: chơi trọn 2 phút, đổi hợp âm kịp nhịp.")
        )
    ),
    RoadmapLevel(
        "Cấp 4: Rải",
        listOf(
            RoadmapItem("pick1", "Rải 1 ở 60 BPM", "Tab Quạt/Rải: mỗi nhịp một dây, 60 giây không vấp."),
            RoadmapItem("pick2", "Rải 2 ở 60 BPM", "Tab Quạt/Rải: 8 nốt mỗi ô nhịp, đều và rõ tiếng."),
            RoadmapItem("pickprog", "Rải theo vòng Em – Am ở 60 BPM", "Tab Quạt/Rải: vừa rải vừa đổi hợp âm.")
        )
    )
)

private val progressHeroGradient = Brush.linearGradient(listOf(Color(0xFFFF9A3C), Color(0xFFD9541E)))
private val levelEmojis = listOf("🌱", "🎼", "🚀", "🎸")

/** Độ thuộc (0..1) của một bài quiz, tính từ các "mức thuộc" đã lưu. */
private fun masteryFraction(context: Context, prefsName: String, size: Int): Float {
    val saved = context.applicationContext
        .getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        .getString("boxes", null)
    return parseQuizBoxes(saved, size).sum() / (size * 4f)
}

/** Giá trị chạy mượt từ 0 lên mục tiêu khi màn hình hiện ra. */
@Composable
private fun rememberAnimatedFraction(target: Float): Float {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val value by animateFloatAsState(
        targetValue = if (started) target.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(900),
        label = "fraction"
    )
    return value
}

@Composable
fun RoadmapScreen() {
    // Mỗi lần đặt lại tiến bộ, dựng lại cả màn hình để số liệu và biểu đồ cập nhật ngay
    var version by remember { mutableIntStateOf(0) }
    key(version) { RoadmapContent(onProgressReset = { version++ }) }
}

@Composable
private fun RoadmapContent(onProgressReset: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.applicationContext.getSharedPreferences("roadmap", Context.MODE_PRIVATE)
    }
    val repo = remember { DrillRepository(context.applicationContext) }

    var ticked by remember { mutableStateOf(prefs.getStringSet("done", emptySet())!!.toSet()) }
    val autoDone = remember {
        val fromDrill = roadmap.flatMap { it.items }
            .filter { it.drillPair != null && (repo.bestBpm(it.drillPair) ?: 0) >= it.drillBpm }
            .map { it.id }
            .toSet()
        val quizMastered = context.applicationContext
            .getSharedPreferences("strings", Context.MODE_PRIVATE)
            .getBoolean("mastered", false)
        val chordQuizMastered = context.applicationContext
            .getSharedPreferences("chordquiz", Context.MODE_PRIVATE)
            .getBoolean("mastered", false)
        val earMastered = context.applicationContext
            .getSharedPreferences("ear", Context.MODE_PRIVATE)
            .getBoolean("mastered", false)
        var result: Set<String> = fromDrill
        if (quizMastered) result = result + "strings"
        if (chordQuizMastered) result = result + "chordquiz"
        if (earMastered) result = result + "ear"
        result
    }

    val allItems = roadmap.flatMap { it.items }
    val doneCount = allItems.count { it.id in ticked || it.id in autoDone }
    val fraction = if (allItems.isEmpty()) 0f else doneCount / allItems.size.toFloat()

    // Ngày đã tập (từ màn hình Hôm nay)
    val daysDone = remember {
        context.applicationContext
            .getSharedPreferences("today", Context.MODE_PRIVATE)
            .getStringSet("days", emptySet())!!
            .toSet()
    }
    val dayFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val now = remember { System.currentTimeMillis() }

    // Độ thuộc của ba bài quiz
    val stringsMastery = remember { masteryFraction(context, "strings", 12) }
    val chordMastery = remember { masteryFraction(context, "chordquiz", 2 * basicChords.size) }
    val earMastery = remember { masteryFraction(context, "ear", basicChords.size) }

    // Mặc định chỉ mở cấp đang làm dở, các cấp khác thu gọn cho đỡ rối mắt
    var expandedLevels by remember {
        mutableStateOf(
            setOf(
                roadmap.indexOfFirst { lvl -> lvl.items.any { it.id !in ticked && it.id !in autoDone } }
                    .coerceAtLeast(0)
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ProgressHero(
            fraction = fraction,
            doneCount = doneCount,
            total = allItems.size,
            now = now,
            days = daysDone,
            dayFormat = dayFormat
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MasteryTile("🎼", "Hợp âm", chordMastery, Modifier.weight(1f))
            MasteryTile("👂", "Luyện tai", earMastery, Modifier.weight(1f))
            MasteryTile("🧠", "Tên dây", stringsMastery, Modifier.weight(1f))
        }

        // Biểu đồ
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            ProgressSection()
        }

        // Lộ trình
        Column {
            Text("Lộ trình", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Chạm vào mục để xem chi tiết  ·  ⚡ = tự tick",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        roadmap.forEachIndexed { index, level ->
            LevelCard(
                emoji = levelEmojis.getOrElse(index) { "⭐" },
                level = level,
                expanded = index in expandedLevels,
                onToggle = {
                    expandedLevels =
                        if (index in expandedLevels) expandedLevels - index else expandedLevels + index
                },
                isAuto = { it.id in autoDone },
                isChecked = { it.id in autoDone || it.id in ticked },
                onCheck = { item, isChecked ->
                    ticked = if (isChecked) ticked + item.id else ticked - item.id
                    prefs.edit().putStringSet("done", ticked.toSet()).apply()
                }
            )
        }

        Spacer(Modifier.height(8.dp))
        ResetProgressSection(onDone = onProgressReset)
    }
}

/** Vòng tròn tiến độ vẽ bằng Canvas. */
@Composable
private fun ProgressRing(
    fraction: Float,
    ringSize: Dp,
    strokeWidth: Dp,
    ringColor: Color,
    trackColor: Color,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (fraction > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

/** Thẻ lớn ở đầu trang: vòng % hoàn thành lộ trình + 7 ngày gần nhất. */
@Composable
private fun ProgressHero(
    fraction: Float,
    doneCount: Int,
    total: Int,
    now: Long,
    days: Set<String>,
    dayFormat: SimpleDateFormat
) {
    val animated = rememberAnimatedFraction(fraction)
    val dayNames = arrayOf("CN", "T2", "T3", "T4", "T5", "T6", "T7")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(progressHeroGradient)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                ProgressRing(
                    fraction = animated,
                    ringSize = 104.dp,
                    strokeWidth = 11.dp,
                    ringColor = Color.White,
                    trackColor = Color.White.copy(alpha = 0.28f)
                ) {
                    Text(
                        "${(animated * 100).roundToInt()}%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Lộ trình",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Text(
                        "$doneCount/$total mục",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "🔥 ${DayStreak.currentStreak(days, now)} ngày liên tiếp",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.95f)
                    )
                    Text(
                        "${days.size} ngày đã tập",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            // 7 ngày gần nhất, hôm nay nằm ngoài cùng bên phải
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (offset in 6 downTo 0) {
                    val time = DayStreak.daysAgo(now, offset)
                    val done = dayFormat.format(Date(time)) in days
                    val cal = Calendar.getInstance().apply { timeInMillis = time }
                    val label = dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (done) Color.White else Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (done) Text("✓", color = Color(0xFFD9541E), fontWeight = FontWeight.Bold)
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
        }
    }
}

/** Ô nhỏ hiện độ thuộc của một bài quiz bằng vòng tròn. */
@Composable
private fun MasteryTile(emoji: String, label: String, fraction: Float, modifier: Modifier = Modifier) {
    val animated = rememberAnimatedFraction(fraction)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProgressRing(
            fraction = animated,
            ringSize = 68.dp,
            strokeWidth = 8.dp,
            ringColor = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ) {
            Text(
                "${(animated * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Text("$emoji $label", style = MaterialTheme.typography.labelLarge)
    }
}

/** Một cấp của lộ trình: bấm vào tiêu đề để mở/thu gọn danh sách mục. */
@Composable
private fun LevelCard(
    emoji: String,
    level: RoadmapLevel,
    expanded: Boolean,
    onToggle: () -> Unit,
    isAuto: (RoadmapItem) -> Boolean,
    isChecked: (RoadmapItem) -> Boolean,
    onCheck: (RoadmapItem, Boolean) -> Unit
) {
    val done = level.items.count { isChecked(it) }
    val total = level.items.size
    val complete = total > 0 && done == total
    val animated = rememberAnimatedFraction(if (total == 0) 0f else done / total.toFloat())
    var openItem by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(emoji, fontSize = 28.sp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(level.title, style = MaterialTheme.typography.titleMedium)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animated)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                }
            }
            Text(
                if (complete) "🎉" else "$done/$total",
                style = MaterialTheme.typography.labelLarge,
                color = if (complete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(if (expanded) "▾" else "▸", style = MaterialTheme.typography.titleMedium)
        }

        if (expanded) {
            Column(modifier = Modifier.padding(start = 8.dp, end = 16.dp, bottom = 12.dp)) {
                level.items.forEach { item ->
                    val auto = isAuto(item)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openItem = if (openItem == item.id) null else item.id },
                        verticalAlignment = Alignment.Top
                    ) {
                        Checkbox(
                            checked = isChecked(item),
                            enabled = !auto,
                            onCheckedChange = { onCheck(item, it) }
                        )
                        Column(modifier = Modifier.weight(1f).padding(top = 12.dp, bottom = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (auto) Text("  ⚡", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (openItem == item.id) {
                                Text(
                                    item.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}