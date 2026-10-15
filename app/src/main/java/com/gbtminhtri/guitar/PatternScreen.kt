package com.gbtminhtri.guitar

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    StopWhenBackgrounded { if (playing) stopPlaying() }

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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader("🎸", "Quạt / Rải", "Luyện tay phải theo nhịp")

        // Tập với 1 hợp âm hoặc cả vòng hợp âm
        AppCard {
            Text(
                "Tập với",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PillRow(
                labels = listOf("1 hợp âm") + progressions.map { it.name },
                selectedIndex = if (prog == null) 0 else progressions.indexOf(prog) + 1,
                onSelect = { i ->
                    stopPlaying()
                    if (i == 0) {
                        progression = null
                    } else {
                        bar = -1
                        progression = progressions[i - 1]
                    }
                }
            )

            if (prog == null) {
                ChordPillRow(chord, onSelect = { chord = it })
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Đang chơi",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(activeChord.name, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "Tiếp theo: ${nextChord?.name}",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    "Mỗi ô nhịp (4 nhịp) đổi một hợp âm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Chọn mẫu + mô tả
        AppCard {
            Text(
                "Mẫu",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PillRow(
                labels = patterns.map { it.name },
                selectedIndex = patterns.indexOf(pattern),
                onSelect = { i ->
                    stopPlaying()
                    pattern = patterns[i]
                    bpm = prefs.getInt("bpm_${patterns[i].name}", 60)
                }
            )
            Text(pattern.description, style = MaterialTheme.typography.bodyMedium)
            if (pattern.type == PatternType.PICK) {
                Text(
                    "Số là dây số mấy (6 trầm nhất, 1 thanh nhất). Với hợp âm ${activeChord.name}, dây trầm là dây $bass.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Dãy ô sáng theo nhịp
        AppCard {
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            labelFor(l),
                            fontSize = 20.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Tốc độ
        AppCard {
            BpmStepper(
                bpm = bpm,
                onMinus = { changeBpm(bpm - 5) },
                onPlus = { changeBpm(bpm + 5) }
            )
        }

        BigButton(
            text = if (playing) "⏹  Dừng" else "▶  Bắt đầu",
            container = if (playing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
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
            }
        )

        // Tự đánh giá sau mỗi lượt đủ dài
        val rating = pendingRating
        if (rating != null) {
            AppCard {
                Text(
                    "Lượt vừa rồi: ${rating.first} ở ${rating.second} BPM. Bạn chơi sạch không?",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "Sạch nghĩa là đều nhịp, không vấp, tiếng rõ. Hãy trả lời thật thà.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            repo.savePatternResult(
                                DrillResult(System.currentTimeMillis(), rating.first, rating.second, 0, true)
                            )
                            pendingRating = null
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("✅ Sạch") }
                    OutlinedButton(
                        onClick = {
                            repo.savePatternResult(
                                DrillResult(System.currentTimeMillis(), rating.first, rating.second, 0, false)
                            )
                            pendingRating = null
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Chưa sạch") }
                    TextButton(onClick = { pendingRating = null }) { Text("Bỏ qua") }
                }
            }
        }

        // Sơ đồ hợp âm để đối chiếu cách bấm + mẹo
        AppCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ChordDiagram(
                    chord = activeChord,
                    modifier = Modifier
                        .width(140.dp)
                        .aspectRatio(0.85f)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(activeChord.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "💡 Tập tay phải trước: bóp nhẹ các dây (chưa bấm hợp âm). Khi nhịp đã đều thì mới bấm hợp âm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}