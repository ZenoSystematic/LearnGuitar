package com.gbtminhtri.guitar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrillScreen() {
    val context = LocalContext.current
    val repo = remember { DrillRepository(context.applicationContext) }

    var chordA by remember { mutableStateOf(basicChords.first { it.name == "G" }) }
    var chordB by remember { mutableStateOf(basicChords.first { it.name == "C" }) }
    var beatsPerChange by remember { mutableIntStateOf(4) }
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var secondsLeft by remember { mutableIntStateOf(DRILL_SECONDS) }
    var errors by remember { mutableIntStateOf(0) }
    var bar by remember { mutableIntStateOf(-1) }
    var message by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    val pair = listOf(chordA.name, chordB.name).sorted().joinToString("-")
    var bpm by remember { mutableIntStateOf(repo.getBpm(pair)) }
    LaunchedEffect(pair) { bpm = repo.getBpm(pair) }

    // Mỗi khi metronome đến nhịp 1 của ô nhịp thì đổi hợp âm
    val metronome = remember { Metronome { beat -> if (beat == 0) bar += 1 } }
    DisposableEffect(Unit) { onDispose { metronome.stop() } }

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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (phase == Phase.RUNNING) {
            val showA = bar <= 0 || bar % 2 == 0
            val current = if (showA) chordA else chordB
            val next = if (showA) chordB else chordA

            Text("$secondsLeft s", fontSize = 40.sp)
            Text(current.name, fontSize = 72.sp)
            Text("Tiếp theo: ${next.name}", style = MaterialTheme.typography.titleMedium)
            ChordDiagram(
                chord = current,
                modifier = Modifier.fillMaxWidth(0.6f).aspectRatio(0.85f)
            )
            Text("Lỗi: $errors / $MAX_ERRORS", style = MaterialTheme.typography.titleLarge)
            Button(
                onClick = { errors += 1 },
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) { Text("Mình vừa mắc lỗi (+1)") }
            OutlinedButton(
                onClick = { metronome.stop(); phase = Phase.IDLE },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Dừng") }
        } else {
            if (message.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        message,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            ChordPicker("Hợp âm 1", chordA) { chordA = it }
            ChordPicker("Hợp âm 2", chordB) { chordB = it }

            Text("Đổi hợp âm sau", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4, 2).forEach { n ->
                    FilterChip(
                        selected = beatsPerChange == n,
                        onClick = { beatsPerChange = n },
                        label = { Text("$n nhịp") }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(onClick = {
                    bpm = (bpm - BPM_STEP).coerceAtLeast(30)
                    repo.setBpm(pair, bpm)
                    repo.setPasses(pair, 0)
                }) { Text("−") }
                Text("$bpm BPM", style = MaterialTheme.typography.headlineMedium)
                OutlinedButton(onClick = {
                    bpm = (bpm + BPM_STEP).coerceAtMost(200)
                    repo.setBpm(pair, bpm)
                    repo.setPasses(pair, 0)
                }) { Text("+") }
            }

            val best = remember(pair, refresh) { repo.bestBpm(pair) }
            Text(
                if (best != null) "BPM sạch cao nhất: $best" else "Chưa có lượt đạt nào",
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = { startDrill() },
                enabled = chordA != chordB,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Bắt đầu $DRILL_SECONDS giây") }
            if (chordA == chordB) {
                Text("Hãy chọn 2 hợp âm khác nhau", style = MaterialTheme.typography.bodyMedium)
            }

            val history = remember(pair, refresh) {
                repo.loadAll().filter { it.pair == pair }.takeLast(5).reversed()
            }
            if (history.isNotEmpty()) {
                val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
                Text("Lượt gần đây", style = MaterialTheme.typography.titleMedium)
                history.forEach { r ->
                    Text(
                        "${if (r.passed) "✓" else "✗"} ${fmt.format(Date(r.timeMillis))} · ${r.bpm} BPM · ${r.errors} lỗi"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChordPicker(label: String, selected: Chord, onSelect: (Chord) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            basicChords.forEach { c ->
                FilterChip(
                    selected = c == selected,
                    onClick = { onSelect(c) },
                    label = { Text(c.name) }
                )
            }
        }
    }
}