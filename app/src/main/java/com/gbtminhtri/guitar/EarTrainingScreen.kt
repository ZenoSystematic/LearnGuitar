package com.gbtminhtri.guitar

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ===== Cấu hình học tập: chỉnh các số này theo ý bạn =====
private const val EAR_FAST_MS = 6000L      // đúng trong thời gian này mới tính là "nhanh" và tăng mức thuộc
private const val EAR_MASTERED_BOX = 3     // mức thuộc từ đây trở lên thì tính là đã nhận ra hợp âm đó
private const val EAR_GOAL_STREAK = 8      // đúng liên tiếp bấy nhiêu câu thì mở khóa mức tiếp theo
private const val EAR_TIMEOUT = "⏱"        // giá trị đặc biệt: hết giờ

private val earMinorNames = setOf("Em", "Am", "Dm")

/** Một mức độ khó của bài luyện tai. */
private data class EarStage(
    val name: String,
    val hint: String,
    val poolNames: List<String>,    // các hợp âm xuất hiện ở mức này
    val optionCount: Int,           // số đáp án để chọn
    val oppositeQuality: Boolean,   // true: đáp án sai luôn khác loại (trưởng/thứ) với đáp án đúng
    val limitMs: Long,              // thời gian cho mỗi câu
    val replays: Int                // số lần được nghe lại (-1 = không giới hạn)
)

private val earStages = listOf(
    EarStage(
        "1 · Vui hay buồn",
        "2 đáp án: một hợp âm trưởng (nghe sáng, vui) và một hợp âm thứ (nghe buồn, tối). Em, Am và Dm là hợp âm thứ.",
        basicChords.map { it.name }, 2, true, 20_000L, -1
    ),
    EarStage(
        "2 · Ba lựa chọn", "5 hợp âm, 3 đáp án, nghe lại thoải mái.",
        listOf("Em", "Am", "C", "G", "D"), 3, false, 20_000L, -1
    ),
    EarStage(
        "3 · Đủ bộ", "Đủ ${basicChords.size} hợp âm, 4 đáp án, có cả những hợp âm dễ nhầm (Em/E, Am/A, D/Dm).",
        basicChords.map { it.name }, 4, false, 15_000L, -1
    ),
    EarStage(
        "4 · Nghe ít lại", "Chỉ được nghe lại 1 lần.",
        basicChords.map { it.name }, 4, false, 12_000L, 1
    ),
    EarStage(
        "5 · Nghe một lần", "Hợp âm chỉ vang lên đúng một lần, không nghe lại được.",
        basicChords.map { it.name }, 4, false, 8_000L, 0
    )
)

private data class EarQuestion(val item: Int, val target: Chord, val options: List<Chord>)

private fun earCandidateItems(stage: EarStage): List<Int> =
    basicChords.indices.filter { basicChords[it].name in stage.poolNames }

private fun buildEarQuestion(item: Int, stage: EarStage): EarQuestion {
    val target = basicChords[item]
    val pool = basicChords.filter { it.name in stage.poolNames && it.name != target.name }
    val distractors: List<Chord> = if (stage.oppositeQuality) {
        val targetIsMinor = target.name in earMinorNames
        listOf(pool.filter { (it.name in earMinorNames) != targetIsMinor }.random())
    } else {
        // Từ mức 4 đáp án: luôn có một hợp âm dễ nhầm với đáp án đúng
        val similarName = chordLookAlikes[target.name]?.randomOrNull()
        val similar = pool.firstOrNull { it.name == similarName }
        val ordered = listOfNotNull(similar.takeIf { stage.optionCount >= 4 }) +
                pool.filter { it != similar }.shuffled()
        ordered.take(stage.optionCount - 1)
    }
    return EarQuestion(item, target, (listOf(target) + distractors).shuffled())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarTrainingScreen() {
    val context = LocalContext.current
    val prefs = remember {
        context.applicationContext.getSharedPreferences("ear", Context.MODE_PRIVATE)
    }
    val n = basicChords.size

    val boxes = remember {
        mutableStateListOf<Int>().apply { addAll(parseQuizBoxes(prefs.getString("boxes", null), n)) }
    }
    var unlocked by remember {
        mutableIntStateOf(prefs.getInt("unlocked", 0).coerceIn(0, earStages.size - 1))
    }
    var stageIndex by remember { mutableIntStateOf(unlocked) }
    var soundOn by remember { mutableStateOf(prefs.getBoolean("sound", true)) }

    var question by remember {
        val st = earStages[stageIndex]
        mutableStateOf(buildEarQuestion(pickWeightedItem(earCandidateItems(st), boxes, null), st))
    }
    var selected by remember { mutableStateOf<String?>(null) }
    var replaysLeft by remember { mutableIntStateOf(earStages[stageIndex].replays) }
    var streak by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(prefs.getInt("best_streak", 0)) }
    var correctCount by remember { mutableIntStateOf(0) }
    var answered by remember { mutableIntStateOf(0) }
    var unlockMessage by remember { mutableStateOf<String?>(null) }

    var startedAtMs by remember { mutableLongStateOf(0L) }
    var lastTimeMs by remember { mutableLongStateOf(0L) }
    var timeFraction by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { basicChords.forEach { ChordSound.prepare(it) } }
    }
    DisposableEffect(Unit) { onDispose { ChordSound.stop() } }

    fun saveBoxes() {
        prefs.edit()
            .putString("boxes", boxes.joinToString(","))
            .putBoolean("mastered", boxes.all { it >= EAR_MASTERED_BOX })
            .apply()
    }

    fun newQuestion(previous: Int?) {
        val st = earStages[stageIndex]
        question = buildEarQuestion(pickWeightedItem(earCandidateItems(st), boxes, previous), st)
        replaysLeft = st.replays
        selected = null
    }

    fun answer(option: Chord) {
        if (selected != null) return
        val timeMs = System.currentTimeMillis() - startedAtMs
        lastTimeMs = timeMs
        selected = option.name
        answered += 1
        val item = question.item

        if (option.name == question.target.name) {
            correctCount += 1
            streak += 1
            if (streak > best) {
                best = streak
                prefs.edit().putInt("best_streak", best).apply()
            }
            if (timeMs <= EAR_FAST_MS) boxes[item] = minOf(4, boxes[item] + 1)
            if (streak >= EAR_GOAL_STREAK && stageIndex == unlocked && unlocked < earStages.size - 1) {
                unlocked += 1
                prefs.edit().putInt("unlocked", unlocked).apply()
                unlockMessage = "🎉 Đã mở khóa mức ${earStages[unlocked].name}!"
            }
        } else {
            streak = 0
            boxes[item] = maxOf(0, boxes[item] - 2)
            if (soundOn) SoundFx.playWrong()
        }
        saveBoxes()
    }

    fun timeUp() {
        if (selected != null) return
        selected = EAR_TIMEOUT
        lastTimeMs = earStages[stageIndex].limitMs
        answered += 1
        streak = 0
        val item = question.item
        boxes[item] = maxOf(0, boxes[item] - 2)
        if (soundOn) SoundFx.playWrong()
        saveBoxes()
    }

    // Mỗi câu hỏi mới: phát hợp âm ngay
    LaunchedEffect(question) {
        ChordSound.play(question.target)
    }

    // Đếm ngược cho từng câu hỏi
    LaunchedEffect(question, stageIndex) {
        val limit = earStages[stageIndex].limitMs
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
        if (selected != null && selected == question.target.name) {
            delay(1800)
            newQuestion(question.item)
        }
    }

    val stage = earStages[stageIndex]
    val q = question
    val chosen = selected
    val masteryPercent = boxes.sum() * 100 / (n * 4)
    val mastered = boxes.all { it >= EAR_MASTERED_BOX }
    val canReplay = chosen != null || replaysLeft != 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Luyện tai",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                soundOn = !soundOn
                prefs.edit().putBoolean("sound", soundOn).apply()
            }) { Text(if (soundOn) "🔔 Âm báo sai: bật" else "🔕 Âm báo sai: tắt") }
        }

        // Chọn mức độ (mức chưa mở khóa thì bị khóa)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            earStages.forEachIndexed { index, st ->
                FilterChip(
                    selected = stageIndex == index,
                    enabled = index <= unlocked,
                    onClick = {
                        stageIndex = index
                        streak = 0
                        unlockMessage = null
                        newQuestion(null)
                    },
                    label = { Text(if (index <= unlocked) st.name else "🔒 ${st.name}") }
                )
            }
        }

        Text(
            stage.hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (stageIndex == unlocked && unlocked < earStages.size - 1) {
            Text(
                "Mở khóa mức sau: đúng liên tiếp $EAR_GOAL_STREAK câu ($streak/$EAR_GOAL_STREAK)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        unlockMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        QuizTimerBar(fractionProvider = { timeFraction }, limitMs = stage.limitMs)

        // Câu hỏi: nút nghe lớn
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Đây là hợp âm nào?",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .alpha(if (canReplay) 1f else 0.4f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(enabled = canReplay) {
                        if (chosen == null && replaysLeft > 0) replaysLeft -= 1
                        ChordSound.play(q.target)
                    },
                contentAlignment = Alignment.Center
            ) { Text("🔊", fontSize = 48.sp) }
            Text(
                when {
                    chosen != null || replaysLeft < 0 -> "Chạm để nghe lại"
                    replaysLeft > 0 -> "Chạm để nghe lại (còn $replaysLeft lần)"
                    else -> "Hết lượt nghe lại"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (chosen != null) {
                // Cho thấy hình bấm của đáp án để nối âm thanh với hình
                Text(q.target.name, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                ChordDiagram(
                    chord = q.target,
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .aspectRatio(0.85f)
                )
            }
        }

        // Các đáp án
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            q.options.forEach { option ->
                val container = when {
                    chosen == null -> MaterialTheme.colorScheme.primary
                    option.name == q.target.name -> MaterialTheme.colorScheme.tertiary
                    option.name == chosen -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                Button(
                    onClick = { answer(option) },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = container,
                        contentColor = contentColorFor(container)
                    )
                ) { Text(option.name, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            }
        }

        // Phản hồi sau khi trả lời
        if (chosen != null) {
            val seconds = String.format("%.1f", lastTimeMs / 1000.0)
            when {
                chosen == EAR_TIMEOUT -> Text(
                    "Hết giờ! Đáp án là ${q.target.name}.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                chosen == q.target.name && lastTimeMs <= EAR_FAST_MS -> Text(
                    "Đúng rồi! ⚡ $seconds giây",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
                chosen == q.target.name -> Text(
                    "Đúng, nhưng hơi chậm ($seconds giây). Dưới ${EAR_FAST_MS / 1000} giây mới tính là thuộc.",
                    style = MaterialTheme.typography.titleMedium
                )
                else -> Text(
                    "Chưa đúng. Đáp án là ${q.target.name}.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (chosen != q.target.name) {
                Button(
                    onClick = { newQuestion(q.item) },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) { Text("Câu tiếp ▶") }
            }
        }

        // Độ thuộc
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
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }
            QuizMasteryRow("Nghe → tên", basicChords.map { it.name }, boxes.toList())
            Text(
                if (mastered) "🏆 Tai bạn đã nhận ra cả ${basicChords.size} hợp âm!"
                else "Mục tiêu: tất cả các chấm đạt màu xanh lá trở lên. Câu đúng và nhanh (dưới ${EAR_FAST_MS / 1000} giây) mới làm chấm lên màu; câu sai làm chấm tụt xuống.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Phiên này: đúng $correctCount/$answered · Chuỗi đúng: $streak · Dài nhất: $best",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}