package com.gbtminhtri.guitar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ===== Tiêu chí luyện tập: chỉnh các số này theo thực tế khi tập =====
private const val DRILL_SECONDS = 60       // mỗi lượt tập bao lâu
private const val MAX_ERRORS = 2           // tối đa bao nhiêu lỗi thì tính là đạt
private const val PASSES_TO_LEVEL_UP = 2   // đạt liên tiếp mấy lượt thì tăng BPM
private const val BPM_STEP = 5             // mỗi lần tăng bao nhiêu BPM

private enum class Phase { IDLE, RUNNING, DONE }

@Composable
fun DrillScreen() {
    val context = LocalContext.current
    val repo = remember { DrillRepository(context.applicationContext) }
    val haptics = rememberHaptics()

    var chordA by remember { mutableStateOf(basicChords.first { it.name == "G" }) }
    var chordB by remember { mutableStateOf(basicChords.first { it.name == "C" }) }
    var beatsPerChange by remember { mutableIntStateOf(4) }
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var secondsLeft by remember { mutableIntStateOf(DRILL_SECONDS) }
    var errors by remember { mutableIntStateOf(0) }
    var bar by remember { mutableIntStateOf(-1) }
    var message by remember { mutableStateOf("") }
    var lastPassed by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }

    val pair = listOf(chordA.name, chordB.name).sorted().joinToString("-")
    var bpm by remember { mutableIntStateOf(repo.getBpm(pair)) }
    LaunchedEffect(pair) { bpm = repo.getBpm(pair) }

    // Mỗi khi metronome đến nhịp 1 của ô nhịp thì đổi hợp âm
    val metronome = remember { Metronome { beat -> if (beat == 0) bar += 1 } }
    DisposableEffect(Unit) { onDispose { metronome.stop() } }
    // Xuống nền giữa lượt tập thì hủy lượt đó (đồng hồ không chạy ngầm rồi lưu kết quả sai)
    StopWhenBackgrounded {
        if (phase == Phase.RUNNING) {
            metronome.stop()
            phase = Phase.IDLE
        }
    }

    fun finishDrill() {
        metronome.stop()
        val passed = errors <= MAX_ERRORS
        repo.save(DrillResult(System.currentTimeMillis(), pair, bpm, errors, passed))
        var passes = if (passed) repo.getPasses(pair) + 1 else 0
        message = if (!passed) {
            "Chưa đạt: $errors lỗi (tối đa $MAX_ERRORS). Giữ nguyên $bpm BPM, thử lại nhé."
        } else if (passes >= PASSES_TO_LEVEL_UP) {
            bpm += BPM_STEP
            repo.setBpm(pair, bpm)
            passes = 0
            "Đạt! Đủ $PASSES_TO_LEVEL_UP lượt liên tiếp, tăng lên $bpm BPM."
        } else {
            "Đạt! Cần thêm ${PASSES_TO_LEVEL_UP - passes} lượt đạt nữa ở $bpm BPM để tăng tốc."
        }
        lastPassed = passed
        if (passed) haptics.correct() else haptics.wrong()
        repo.setPasses(pair, passes)
        phase = Phase.DONE
        refresh++
    }

    fun startDrill() {
        errors = 0
        secondsLeft = DRILL_SECONDS
        bar = -1
        message = ""
        metronome.bpm = bpm
        metronome.beatsPerBar = beatsPerChange
        phase = Phase.RUNNING
        metronome.start()
    }

    // Đồng hồ đếm ngược
    LaunchedEffect(phase) {
        if (phase == Phase.RUNNING) {
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft -= 1
            }
            finishDrill()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (phase == Phase.RUNNING) {
            val showA = bar <= 0 || bar % 2 == 0
            val current = if (showA) chordA else chordB
            val next = if (showA) chordB else chordA

            // Thẻ lớn: đồng hồ vòng + hợp âm hiện tại
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(UiHeroGradient)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                UiRing(
                    fraction = secondsLeft / DRILL_SECONDS.toFloat(),
                    ringSize = 92.dp,
                    strokeWidth = 10.dp,
                    ringColor = Color.White,
                    trackColor = Color.White.copy(alpha = 0.28f)
                ) {
                    Text(
                        "$secondsLeft",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Column {
                    Text(
                        "Hợp âm hiện tại",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Text(current.name, fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "Tiếp theo: ${next.name}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.95f)
                    )
                }
            }

            AppCard {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ChordDiagram(
                        chord = current,
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .aspectRatio(0.85f)
                    )
                }
            }

            // Số lỗi: mỗi lỗi là một chấm đỏ
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Lỗi $errors / $MAX_ERRORS",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (errors > MAX_ERRORS) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(maxOf(MAX_ERRORS, errors)) { i ->
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i < errors) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }
                }
            }

            BigButton(
                text = "Mình vừa mắc lỗi (+1)",
                container = MaterialTheme.colorScheme.error,
                onClick = {
                    errors += 1
                    haptics.wrong()
                }
            )
            TextButton(
                onClick = { metronome.stop(); phase = Phase.IDLE },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Dừng") }
        } else {
            ScreenHeader("🔁", "Luyện chuyển hợp âm", "Đổi qua lại 2 hợp âm cho mượt")

            if (message.isNotEmpty()) {
                Banner(
                    text = message,
                    tone = if (lastPassed) UiTone.SUCCESS else UiTone.WARN,
                    emoji = if (lastPassed) "🎉" else "💪"
                )
            }

            // Chọn hai hợp âm, xem trước hình bấm cạnh nhau
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ChordDiagram(chord = chordA, modifier = Modifier.fillMaxWidth().aspectRatio(0.85f))
                        Text(chordA.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Text("⇄", fontSize = 28.sp)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ChordDiagram(chord = chordB, modifier = Modifier.fillMaxWidth().aspectRatio(0.85f))
                        Text(chordB.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    "Hợp âm 1",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ChordPillRow(chordA, onSelect = { chordA = it })
                Text(
                    "Hợp âm 2",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ChordPillRow(chordB, onSelect = { chordB = it })
            }

            // Tốc độ + đổi sau mấy nhịp
            AppCard {
                BpmStepper(
                    bpm = bpm,
                    onMinus = {
                        bpm = (bpm - BPM_STEP).coerceAtLeast(30)
                        repo.setBpm(pair, bpm)
                        repo.setPasses(pair, 0)
                    },
                    onPlus = {
                        bpm = (bpm + BPM_STEP).coerceAtMost(200)
                        repo.setBpm(pair, bpm)
                        repo.setPasses(pair, 0)
                    }
                )
                val best = remember(pair, refresh) { repo.bestBpm(pair) }
                Text(
                    if (best != null) "🏆 BPM sạch cao nhất: $best" else "Chưa có lượt đạt nào",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    "Đổi hợp âm sau",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val options = listOf(4, 2)
                PillRow(
                    labels = options.map { "$it nhịp" },
                    selectedIndex = options.indexOf(beatsPerChange),
                    onSelect = { beatsPerChange = options[it] }
                )
            }

            BigButton(
                text = "▶  Bắt đầu $DRILL_SECONDS giây",
                enabled = chordA != chordB,
                onClick = { startDrill() }
            )
            if (chordA == chordB) {
                Banner("Hãy chọn 2 hợp âm khác nhau", UiTone.WARN, "☝️")
            }

            val history = remember(pair, refresh) {
                repo.loadAll().filter { it.pair == pair }.takeLast(5).reversed()
            }
            if (history.isNotEmpty()) {
                val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
                AppCard {
                    Text("Lượt gần đây", style = MaterialTheme.typography.titleMedium)
                    history.forEach { r ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (r.passed) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.error
                                    )
                            )
                            Text(
                                fmt.format(Date(r.timeMillis)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${r.bpm} BPM · ${r.errors} lỗi",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}