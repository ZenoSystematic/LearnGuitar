package com.gbtminhtri.guitar

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternScreen() {
    val context = LocalContext.current
    val prefs = remember {
        context.applicationContext.getSharedPreferences("pattern", Context.MODE_PRIVATE)
    }

    val repo = remember { DrillRepository(context.applicationContext) }
    var chord by remember { mutableStateOf(basicChords.first { it.name == "G" }) }
    var progression by remember { mutableStateOf<Progression?>(null) } // null = tập 1 hợp âm
    var pattern by remember { mutableStateOf(patterns.first()) }
    var bpm by remember { mutableIntStateOf(prefs.getInt("bpm_${pattern.name}", 60)) }
    var playing by remember { mutableStateOf(false) }
    var currentStep by remember { mutableIntStateOf(-1) }
    var bar by remember { mutableIntStateOf(-1) }
    var startedAt by remember { mutableLongStateOf(0L) }
    // (tên mẫu · hợp âm hoặc vòng, BPM) của lượt vừa tập xong, đang chờ bạn đánh giá
    var pendingRating by remember { mutableStateOf<Pair<String, Int>?>(null) }

    // Mỗi khi đến ô đầu của ô nhịp (step 0) thì sang ô nhịp mới -> đổi hợp âm trong vòng
    val metronome = remember {
        Metronome(onStep = { s ->
            if (s == 0) bar += 1
            currentStep = s
        }) { }
    }
    DisposableEffect(Unit) { onDispose { metronome.stop() } }

    fun stopPlaying() {
        if (playing && System.currentTimeMillis() - startedAt >= 20_000L) {
            pendingRating = Pair("${pattern.name} · ${progression?.name ?: chord.name}", bpm)
        }
        metronome.stop()
        playing = false
        currentStep = -1
    }

    fun changeBpm(newBpm: Int) {
        bpm = newBpm.coerceIn(40, 160)
        metronome.bpm = bpm
        prefs.edit().putInt("bpm_${pattern.name}", bpm).apply()
    }

    val prog = progression
    val barIndex = maxOf(bar, 0)
    val activeChord = if (prog != null) chordByName(prog.chords[barIndex % prog.chords.size]) else chord
    val nextChord = if (prog != null) chordByName(prog.chords[(barIndex + 1) % prog.chords.size]) else null

    val bass = bassStrings[activeChord.name] ?: 6
    fun labelFor(l: String) = when {
        l == "B" -> bass.toString()
        l.isEmpty() -> "·"
        else -> l
    }
    val counts = if (pattern.stepsPerBeat == 2) {
        listOf("1", "&", "2", "&", "3", "&", "4", "&")
    } else {
        listOf("1", "2", "3", "4")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Chế độ: 1 hợp âm hoặc vòng hợp âm
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Tập với", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = prog == null,
                    onClick = { stopPlaying(); progression = null },
                    label = { Text("1 hợp âm") }
                )
                progressions.forEach { p ->
                    FilterChip(
                        selected = prog == p,
                        onClick = { stopPlaying(); bar = -1; progression = p },
                        label = { Text(p.name) }
                    )
                }
            }
        }

        if (prog == null) {
            // Chọn 1 hợp âm
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                basicChords.forEach { c ->
                    FilterChip(
                        selected = c == chord,
                        onClick = { chord = c },
                        label = { Text(c.name) }
                    )
                }
            }
        } else {
            Text(activeChord.name, fontSize = 56.sp)
            Text(
                "Tiếp theo: ${nextChord?.name}. Mỗi ô nhịp (4 nhịp) đổi một hợp âm.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Chọn mẫu
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Mẫu", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                patterns.forEach { p ->
                    FilterChip(
                        selected = p == pattern,
                        onClick = {
                            stopPlaying()
                            pattern = p
                            bpm = prefs.getInt("bpm_${p.name}", 60)
                        },
                        label = { Text(p.name) }
                    )
                }
            }
        }

        Text(pattern.description, style = MaterialTheme.typography.bodyMedium)
        if (pattern.type == PatternType.PICK) {
            Text(
                "Số là dây số mấy (6 trầm nhất, 1 thanh nhất). Với hợp âm ${activeChord.name}, dây trầm là dây $bass.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Dãy ô sáng theo nhịp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            pattern.labels.forEachIndexed { i, l ->
                val active = playing && currentStep == i
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        labelFor(l),
                        fontSize = 20.sp,
                        color = if (active) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            counts.forEach { c ->
                Text(
                    c,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // BPM
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(onClick = { changeBpm(bpm - 5) }) { Text("−") }
            Text("$bpm BPM", style = MaterialTheme.typography.headlineMedium)
            OutlinedButton(onClick = { changeBpm(bpm + 5) }) { Text("+") }
        }

        Button(
            onClick = {
                if (playing) {
                    stopPlaying()
                } else {
                    metronome.bpm = bpm
                    metronome.beatsPerBar = 4
                    metronome.stepsPerBeat = pattern.stepsPerBeat
                    currentStep = -1
                    bar = -1
                    startedAt = System.currentTimeMillis()
                    pendingRating = null
                    metronome.start()
                    playing = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text(if (playing) "Dừng" else "Bắt đầu") }

        val rating = pendingRating
        if (rating != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Lượt vừa rồi: ${rating.first} ở ${rating.second} BPM. Bạn chơi sạch không?",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Sạch nghĩa là đều nhịp, không vấp, tiếng rõ. Hãy trả lời thật thà.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            repo.savePatternResult(
                                DrillResult(System.currentTimeMillis(), rating.first, rating.second, 0, true)
                            )
                            pendingRating = null
                        }) { Text("Sạch") }
                        OutlinedButton(onClick = {
                            repo.savePatternResult(
                                DrillResult(System.currentTimeMillis(), rating.first, rating.second, 0, false)
                            )
                            pendingRating = null
                        }) { Text("Chưa sạch") }
                        TextButton(onClick = { pendingRating = null }) { Text("Bỏ qua") }
                    }
                }
            }
        }

        Text(
            "Mẹo: tập tay phải trước, bóp nhẹ các dây (chưa bấm hợp âm). Khi nhịp đã đều thì mới bấm hợp âm.",
            style = MaterialTheme.typography.bodySmall
        )

        // Sơ đồ hợp âm để đối chiếu cách bấm
        ChordDiagram(
            chord = activeChord,
            modifier = Modifier.fillMaxWidth(0.5f).aspectRatio(0.85f)
        )
    }
}