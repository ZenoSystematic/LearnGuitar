package com.gbtminhtri.guitar

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
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
import kotlin.random.Random

private enum class DailyKind { DIAGRAM_TO_NAME, NAME_TO_DIAGRAM, EAR, STRING }
private enum class DailyPhase { INTRO, RUNNING, RESULT }

private data class DailyQuestion(
    val kind: DailyKind,
    val target: Chord? = null,
    val options: List<Chord> = emptyList(),
    val stringNumber: Int = 0,
    val letters: List<String> = emptyList(),
    val answerLetter: String = ""
)

/** Thứ tự 10 câu: bắt đầu nhẹ nhàng, xen kẽ các dạng để không chán. */
private val dailyKinds = listOf(
    DailyKind.DIAGRAM_TO_NAME, DailyKind.EAR, DailyKind.STRING,
    DailyKind.DIAGRAM_TO_NAME, DailyKind.NAME_TO_DIAGRAM, DailyKind.EAR,
    DailyKind.DIAGRAM_TO_NAME, DailyKind.STRING, DailyKind.NAME_TO_DIAGRAM, DailyKind.EAR
)

private val stringLetters = listOf("E", "A", "D", "G", "B", "E") // dây 6 -> dây 1

/** Cùng một ngày cho ra cùng một bộ câu hỏi (seed theo ngày). */
private fun buildDailyQuestions(seed: Int): List<DailyQuestion> {
    val rng = Random(seed)
    val order = basicChords.shuffled(rng)
    var chordIndex = 0
    return dailyKinds.map { kind ->
        if (kind == DailyKind.STRING) {
            val number = rng.nextInt(1, 7)
            val answer = stringLetters[6 - number]
            val others = listOf("E", "A", "D", "G", "B").filter { it != answer }.shuffled(rng).take(3)
            DailyQuestion(
                kind = kind,
                stringNumber = number,
                letters = (listOf(answer) + others).shuffled(rng),
                answerLetter = answer
            )
        } else {
            val target = order[chordIndex % order.size]
            chordIndex++
            val pool = basicChords.filter { it.name != target.name }
            val similarName = chordLookAlikes[target.name]?.randomOrNull(rng)
            val similar = pool.firstOrNull { it.name == similarName }
            val rest = pool.filter { it != similar }.shuffled(rng)
            val distractors = (listOfNotNull(similar) + rest).take(3)
            DailyQuestion(kind = kind, target = target, options = (listOf(target) + distractors).shuffled(rng))
        }
    }
}

private fun correctValue(q: DailyQuestion): String =
    if (q.kind == DailyKind.STRING) q.answerLetter else q.target!!.name

@Composable
private fun StreakChip(streak: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            "🔥 $streak",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/** Hàng nút đáp án bằng chữ (tên hợp âm hoặc tên nốt của dây). */
@Composable
private fun AnswerButtons(labels: List<String>, correct: String, chosen: String?, onPick: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { label ->
            val container = when {
                chosen == null -> MaterialTheme.colorScheme.primary
                label == correct -> MaterialTheme.colorScheme.tertiary
                label == chosen -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Button(
                onClick = { onPick(label) },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = container,
                    contentColor = contentColorFor(container)
                )
            ) { Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** Lưới 2x2 các hình hợp âm để chọn. */
@Composable
private fun DiagramGrid(options: List<Chord>, correct: String, chosen: String?, onPick: (String) -> Unit) {
    options.chunked(2).forEach { rowOptions ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            rowOptions.forEach { option ->
                val borderColor = when {
                    chosen == null -> Color.Transparent
                    option.name == correct -> MaterialTheme.colorScheme.tertiary
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
                        .clickable { onPick(option.name) }
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

@Composable
fun DailyChallengeScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.applicationContext.getSharedPreferences("daily", Context.MODE_PRIVATE)
    }
    val haptics = rememberHaptics()
    val todayKey = remember { DayStreak.todayKey() }

    var doneToday by remember { mutableStateOf(prefs.getString("done_date", "") == todayKey) }
    var scoreToday by remember { mutableIntStateOf(if (doneToday) prefs.getInt("score", 0) else 0) }
    var bestEver by remember { mutableIntStateOf(prefs.getInt("best", 0)) }
    var streak by remember { mutableIntStateOf(DayStreak.currentStreak(DayStreak.days(context))) }

    var phase by remember { mutableStateOf(DailyPhase.INTRO) }
    var attempt by remember { mutableIntStateOf(0) } // 0 = thử thách chính thức của ngày, > 0 = luyện thêm
    var questions by remember { mutableStateOf(emptyList<DailyQuestion>()) }
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { basicChords.forEach { ChordSound.prepare(it) } }
    }
    DisposableEffect(Unit) { onDispose { ChordSound.stop() } }

    fun start(newAttempt: Int) {
        attempt = newAttempt
        questions = buildDailyQuestions(todayKey.hashCode() + newAttempt * 7919)
        index = 0
        score = 0
        selected = null
        phase = DailyPhase.RUNNING
    }

    fun finish() {
        if (attempt == 0) {
            // Chỉ lượt chính thức mới tính ngày tập và điểm cao nhất
            DayStreak.markToday(context)
            prefs.edit()
                .putString("done_date", todayKey)
                .putInt("score", score)
                .putInt("total", prefs.getInt("total", 0) + 1)
                .apply()
            doneToday = true
            scoreToday = score
            if (score > bestEver) {
                bestEver = score
                prefs.edit().putInt("best", score).apply()
            }
        }
        streak = DayStreak.currentStreak(DayStreak.days(context))
        phase = DailyPhase.RESULT
    }

    fun next() {
        if (index + 1 >= questions.size) {
            finish()
        } else {
            index += 1
            selected = null
        }
    }

    fun answer(choice: String) {
        if (selected != null) return
        val q = questions[index]
        selected = choice
        if (choice == correctValue(q)) {
            score += 1
            haptics.correct()
            // Câu về hợp âm: đúng thì hợp âm vang lên; câu về dây: tiếng "ting-ting"
            if (q.target != null) ChordSound.play(q.target) else SoundFx.playCorrect()
        } else {
            haptics.wrong()
            SoundFx.playWrong()
        }
    }

    // Câu nghe: tự phát hợp âm khi câu hỏi hiện ra
    LaunchedEffect(phase, index, questions) {
        if (phase == DailyPhase.RUNNING && questions.isNotEmpty()) {
            val q = questions[index]
            if (q.kind == DailyKind.EAR && q.target != null) ChordSound.play(q.target)
        }
    }

    // Trả lời đúng thì tự sang câu tiếp sau một chút
    LaunchedEffect(selected) {
        val chosen = selected
        if (phase == DailyPhase.RUNNING && chosen != null && chosen == correctValue(questions[index])) {
            delay(1400)
            next()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        when (phase) {
            // ===== Giới thiệu =====
            DailyPhase.INTRO -> {
                ScreenHeader("🏆", "Thử thách hôm nay", "10 câu · khoảng 3 phút")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (doneToday) UiSuccessGradient else UiHeroGradient)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🔥", fontSize = 44.sp)
                    Text("$streak", fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        if (streak == 0) "Bắt đầu chuỗi mới hôm nay" else "ngày liên tiếp",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                }

                AppCard {
                    if (doneToday) {
                        Text("✅ Hôm nay bạn đã hoàn thành: $scoreToday/10", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Text(
                            "Mỗi ngày một bộ câu hỏi mới: nhìn hình, nghe tai, tên dây. " +
                                    "Hoàn thành là được tính một ngày tập và chuỗi tăng thêm 1.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Text(
                        "🏅 Điểm cao nhất: $bestEver/10",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                BigButton(
                    text = if (doneToday) "Luyện thêm (không tính điểm)" else "▶  Bắt đầu thử thách",
                    onClick = { start(if (doneToday) attempt + 1 else 0) }
                )
            }

            // ===== Đang làm =====
            DailyPhase.RUNNING -> {
                val q = questions[index]
                val chosen = selected
                val correct = correctValue(q)

                ScreenHeader(
                    "🏆", "Thử thách hôm nay",
                    (if (attempt == 0) "" else "Luyện thêm · ") + "Câu ${index + 1}/${questions.size}",
                    trailing = { StreakChip(streak) }
                )

                // Thanh tiến độ
                val progress = (index + (if (chosen != null) 1 else 0)) / questions.size.toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }

                // Câu hỏi
                AppCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (q.kind) {
                            DailyKind.DIAGRAM_TO_NAME -> {
                                Text("Đây là hợp âm nào?", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                                ChordDiagram(
                                    chord = q.target!!,
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f)
                                        .aspectRatio(0.85f)
                                )
                                if (chosen != null) Text(correct, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            }
                            DailyKind.NAME_TO_DIAGRAM -> {
                                Text("Hình nào là hợp âm", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                                Text(correct, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                            }
                            DailyKind.EAR -> {
                                Text("Nghe và chọn hợp âm", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable { ChordSound.play(q.target!!) },
                                    contentAlignment = Alignment.Center
                                ) { Text("🔊", fontSize = 40.sp) }
                                Text(
                                    "Chạm để nghe lại",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (chosen != null) Text(correct, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            }
                            DailyKind.STRING -> {
                                Text(
                                    "Dây số ${q.stringNumber} là nốt gì?",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "(dây 6 trầm nhất, dây 1 thanh nhất)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Đáp án
                when (q.kind) {
                    DailyKind.NAME_TO_DIAGRAM -> DiagramGrid(q.options, correct, chosen, { answer(it) })
                    DailyKind.STRING -> AnswerButtons(q.letters, correct, chosen, { answer(it) })
                    else -> AnswerButtons(q.options.map { it.name }, correct, chosen, { answer(it) })
                }

                // Phản hồi
                if (chosen != null) {
                    if (chosen == correct) {
                        Banner("Đúng rồi!", UiTone.SUCCESS, "✅")
                    } else {
                        Banner("Chưa đúng. Đáp án là $correct.", UiTone.WARN, "❌")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (q.target != null) {
                                SoftButton(
                                    "🔊 Nghe $correct",
                                    onClick = { ChordSound.play(q.target) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Button(
                                onClick = { next() },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) { Text(if (index + 1 >= questions.size) "Xem kết quả ▶" else "Câu tiếp ▶") }
                        }
                    }
                }
            }

            // ===== Kết quả =====
            DailyPhase.RESULT -> {
                val emoji = when {
                    score >= 9 -> "🏆"
                    score >= 7 -> "🎉"
                    score >= 5 -> "💪"
                    else -> "🌱"
                }
                val message = when {
                    score >= 9 -> "Xuất sắc!"
                    score >= 7 -> "Làm tốt lắm!"
                    score >= 5 -> "Khá tốt, mai làm tốt hơn nhé."
                    else -> "Không sao, luyện thêm là nhớ."
                }

                ScreenHeader("🏆", "Kết quả")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (score >= 7) UiSuccessGradient else UiHeroGradient)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(emoji, fontSize = 56.sp)
                    Text("$score/${questions.size}", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(message, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.95f))
                }

                if (attempt == 0) {
                    Banner("Chuỗi hiện tại: $streak ngày liên tiếp", UiTone.SUCCESS, "🔥")
                } else {
                    Banner("Đây là lượt luyện thêm, không tính vào chuỗi ngày.", UiTone.INFO, "ℹ️")
                }

                BigButton(text = "Xong", onClick = onDone)
                SoftButton(
                    "Luyện thêm",
                    onClick = { start(attempt + 1) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}