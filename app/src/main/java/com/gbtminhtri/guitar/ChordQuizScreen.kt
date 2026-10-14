package com.gbtminhtri.guitar

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ===== Cấu hình học tập: chỉnh các số này theo ý bạn =====
private const val QUIZ_FAST_MS = 4000L      // đúng trong thời gian này mới tính là "nhanh" và tăng mức thuộc
private const val QUIZ_MASTERED_BOX = 3     // mức thuộc từ đây trở lên thì tính là đã thuộc (tối đa 4)
private const val QUIZ_GOAL_STREAK = 8      // đúng liên tiếp bấy nhiêu câu thì mở khóa mức tiếp theo
private const val QUIZ_TIMEOUT = "⏱"        // giá trị đặc biệt: hết giờ

/** Một mức độ khó. Mức càng cao: nhiều hợp âm hơn, đáp án dễ nhầm hơn, thời gian ít hơn. */
private data class ChordQuizStage(
    val name: String,
    val hint: String,
    val poolNames: List<String>,   // các hợp âm xuất hiện ở mức này
    val bothDirections: Boolean,   // true: có thêm dạng "cho tên, chọn hình"
    val optionCount: Int,          // số đáp án để chọn
    val limitMs: Long              // thời gian cho mỗi câu
)

private val quizStages = listOf(
    ChordQuizStage(
        "1 · Khởi đầu", "3 hợp âm khác hẳn nhau, thời gian thoải mái.",
        listOf("Em", "C", "D"), false, 3, 20_000L
    ),
    ChordQuizStage(
        "2 · Mở rộng", "5 hợp âm, 4 đáp án để chọn.",
        listOf("Em", "Am", "C", "G", "D"), false, 4, 15_000L
    ),
    ChordQuizStage(
        "3 · Đủ bộ", "Đủ ${basicChords.size} hợp âm. Có những cặp dễ nhầm như Em/E, Am/A, D/Dm.",
        basicChords.map { it.name }, false, 4, 10_000L
    ),
    ChordQuizStage(
        "4 · Hai chiều", "Có thêm dạng: cho tên, chọn đúng hình bấm.",
        basicChords.map { it.name }, true, 4, 8_000L
    ),
    ChordQuizStage(
        "5 · Phản xạ", "Đủ hai chiều, thời gian rất ngắn.",
        basicChords.map { it.name }, true, 4, 5_000L
    )
)

/**
 * item 0..n-1  : cho HÌNH, chọn TÊN   (n = số hợp âm)
 * item n..2n-1 : cho TÊN, chọn HÌNH
 */
private data class ChordQuestion(
    val item: Int,
    val target: Chord,
    val nameToDiagram: Boolean,
    val options: List<Chord>
)

private fun candidateItems(stage: ChordQuizStage): List<Int> {
    val n = basicChords.size
    val idx = basicChords.indices.filter { basicChords[it].name in stage.poolNames }
    return if (stage.bothDirections) idx + idx.map { it + n } else idx
}

private fun buildChordQuestion(item: Int, stage: ChordQuizStage): ChordQuestion {
    val n = basicChords.size
    val target = basicChords[item % n]
    val pool = basicChords.filter { it.name in stage.poolNames && it.name != target.name }
    // Từ mức có 4 đáp án: luôn có một hợp âm dễ nhầm với đáp án đúng
    val similarName = chordLookAlikes[target.name]?.randomOrNull()
    val similar = pool.firstOrNull { it.name == similarName }
    val ordered = listOfNotNull(similar.takeIf { stage.optionCount >= 4 }) +
            pool.filter { it != similar }.shuffled()
    val options = (listOf(target) + ordered.take(stage.optionCount - 1)).shuffled()
    return ChordQuestion(item, target, item >= n, options)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordQuizScreen() {
    val context = LocalContext.current
    val prefs = remember {
        context.applicationContext.getSharedPreferences("chordquiz", Context.MODE_PRIVATE)
    }
    val n = basicChords.size

    val boxes = remember {
        mutableStateListOf<Int>().apply { addAll(parseQuizBoxes(prefs.getString("boxes", null), 2 * n)) }
    }
    var unlocked by remember {
        mutableIntStateOf(prefs.getInt("unlocked", 0).coerceIn(0, quizStages.size - 1))
    }
    var stageIndex by remember { mutableIntStateOf(unlocked) }
    var soundOn by remember { mutableStateOf(prefs.getBoolean("sound", true)) }

    var question by remember {
        val st = quizStages[stageIndex]
        mutableStateOf(buildChordQuestion(pickWeightedItem(candidateItems(st), boxes, null), st))
    }
    var selected by remember { mutableStateOf<String?>(null) }
    var streak by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(prefs.getInt("best_streak", 0)) }
    var correctCount by remember { mutableIntStateOf(0) }
    var answered by remember { mutableIntStateOf(0) }
    var unlockMessage by remember { mutableStateOf<String?>(null) }

    var startedAtMs by remember { mutableLongStateOf(0L) }
    var lastTimeMs by remember { mutableLongStateOf(0L) }
    var timeFraction by remember { mutableFloatStateOf(1f) }

    // Tạo sẵn âm thanh ở luồng nền; dừng âm khi rời màn hình
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { basicChords.forEach { ChordSound.prepare(it) } }
    }
    DisposableEffect(Unit) { onDispose { ChordSound.stop() } }

    fun saveBoxes() {
        prefs.edit()
            .putString("boxes", boxes.joinToString(","))
            .putBoolean("mastered", boxes.all { it >= QUIZ_MASTERED_BOX })
            .apply()
    }

    fun newQuestion(previous: Int?) {
        val st = quizStages[stageIndex]
        question = buildChordQuestion(pickWeightedItem(candidateItems(st), boxes, previous), st)
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
            // Chỉ tăng mức thuộc khi trả lời đúng VÀ nhanh
            if (timeMs <= QUIZ_FAST_MS) boxes[item] = minOf(4, boxes[item] + 1)
            // Đúng thì hợp âm đó vang lên
            if (soundOn) ChordSound.play(question.target)
            // Đủ chuỗi đúng ở mức cao nhất đang mở thì mở khóa mức tiếp theo
            if (streak >= QUIZ_GOAL_STREAK && stageIndex == unlocked && unlocked < quizStages.size - 1) {
                unlocked += 1
                prefs.edit().putInt("unlocked", unlocked).apply()
                unlockMessage = "🎉 Đã mở khóa mức ${quizStages[unlocked].name}!"
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
        selected = QUIZ_TIMEOUT
        lastTimeMs = quizStages[stageIndex].limitMs
        answered += 1
        streak = 0
        val item = question.item
        boxes[item] = maxOf(0, boxes[item] - 2)
        if (soundOn) SoundFx.playWrong()
        saveBoxes()
    }

    // Đếm ngược cho từng câu hỏi
    LaunchedEffect(question, stageIndex) {
        val limit = quizStages[stageIndex].limitMs
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

    // Trả lời đúng thì để hợp âm vang một chút rồi tự sang câu tiếp
    LaunchedEffect(selected) {
        if (selected != null && selected == question.target.name) {
            delay(1600)
            newQuestion(question.item)
        }
    }

    val stage = quizStages[stageIndex]
    val q = question
    val chosen = selected
    val masteryPercent = boxes.sum() * 100 / (2 * n * 4)
    val mastered = boxes.all { it >= QUIZ_MASTERED_BOX }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Quiz hợp âm",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                soundOn = !soundOn
                prefs.edit().putBoolean("sound", soundOn).apply()
            }) { Text(if (soundOn) "🔊 Bật tiếng" else "🔇 Tắt tiếng") }
        }

        // Chọn mức độ (mức chưa mở khóa thì bị khóa)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quizStages.forEachIndexed { index, st ->
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
        if (stageIndex == unlocked && unlocked < quizStages.size - 1) {
            Text(
                "Mở khóa mức sau: đúng liên tiếp $QUIZ_GOAL_STREAK câu ($streak/$QUIZ_GOAL_STREAK)",
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

        // Câu hỏi
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!q.nameToDiagram) {
                Text(
                    "Đây là hợp âm nào?",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                ChordDiagram(
                    chord = q.target,
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .aspectRatio(0.85f)
                )
                if (chosen != null) {
                    Text(q.target.name, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    "Hình nào là hợp âm",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(q.target.name, fontSize = 56.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Các đáp án
        if (!q.nameToDiagram) {
            // Cho hình, chọn tên
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
        } else {
            // Cho tên, chọn hình: 4 hình xếp lưới 2x2, sau khi trả lời hiện tên dưới mỗi hình
            q.options.chunked(2).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowOptions.forEach { option ->
                        val borderColor = when {
                            chosen == null -> Color.Transparent
                            option.name == q.target.name -> MaterialTheme.colorScheme.tertiary
                            option.name == chosen -> MaterialTheme.colorScheme.error
                            else -> Color.Transparent
                        }
                        val shape = RoundedCornerShape(18.dp)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(shape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(3.dp, borderColor, shape)
                                .clickable { answer(option) }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ChordDiagram(
                                chord = option,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.85f)
                            )
                            if (chosen != null) {
                                Text(option.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    repeat(2 - rowOptions.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        // Phản hồi sau khi trả lời
        if (chosen != null) {
            val seconds = String.format("%.1f", lastTimeMs / 1000.0)
            when {
                chosen == QUIZ_TIMEOUT -> Text(
                    "Hết giờ! Đáp án là ${q.target.name}.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                chosen == q.target.name && lastTimeMs <= QUIZ_FAST_MS -> Text(
                    "Đúng rồi! ⚡ $seconds giây",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
                chosen == q.target.name -> Text(
                    "Đúng, nhưng hơi chậm ($seconds giây). Dưới ${QUIZ_FAST_MS / 1000} giây mới tính là thuộc.",
                    style = MaterialTheme.typography.titleMedium
                )
                else -> Text(
                    "Chưa đúng. Đáp án là ${q.target.name}.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (chosen != q.target.name) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { ChordSound.play(q.target) },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) { Text("🔊 Nghe ${q.target.name}") }
                    Button(
                        onClick = { newQuestion(q.item) },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) { Text("Câu tiếp ▶") }
                }
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
            val labels = basicChords.map { it.name }
            QuizMasteryRow("Hình → tên", labels, boxes.subList(0, n))
            QuizMasteryRow("Tên → hình", labels, boxes.subList(n, 2 * n))
            Text(
                if (mastered) "🏆 Bạn đã thuộc cả ${basicChords.size} hợp âm ở hai chiều!"
                else "Mục tiêu: tất cả các chấm đạt màu xanh lá trở lên. Câu đúng và nhanh (dưới ${QUIZ_FAST_MS / 1000} giây) mới làm chấm lên màu; câu sai làm chấm tụt xuống.",
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