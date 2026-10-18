package com.gbtminhtri.guitar

import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Một loại nhịp (số chỉ nhịp).
 * beats: số phách mạnh trong ô nhịp. stepsPerBeat: mỗi phách chia thành mấy tiếng.
 * 2/4, 3/4, 4/4: mỗi phách một tiếng. 6/8: 2 phách, mỗi phách chia 3 (đếm 1 2 3 4 5 6, nhấn ở 1 và 4),
 * khi đó BPM là tốc độ của mỗi nhóm 3 (nốt đen chấm), cách chỉnh metronome thông thường cho nhịp 6/8.
 */
private data class TimeSig(
    val label: String,
    val beats: Int,
    val stepsPerBeat: Int,
    val hint: String
)

private val timeSigs = listOf(
    TimeSig("2/4", 2, 1, "2 phách: mạnh, nhẹ. Hợp với hành khúc, polka. Đếm: 1 2"),
    TimeSig("3/4", 3, 1, "3 phách: mạnh, nhẹ, nhẹ. Điệu valse (bùm, chát, chát). Đếm: 1 2 3"),
    TimeSig("4/4", 4, 1, "4 phách, phổ biến nhất trong pop, rock, ballad. Đếm: 1 2 3 4"),
    TimeSig(
        "6/8", 2, 3,
        "6 tiếng chia thành 2 nhóm 3, nhấn ở 1 và 4, cảm giác nhún nhảy như ballad 6/8. " +
                "BPM ở đây là tốc độ của mỗi nhóm 3. Đếm: 1 2 3 4 5 6"
    )
)

private fun tempoName(bpm: Int) = when {
    bpm < 60 -> "Rất chậm"
    bpm < 80 -> "Chậm"
    bpm < 110 -> "Vừa phải"
    bpm < 140 -> "Khá nhanh"
    else -> "Nhanh"
}

/**
 * Chấm nhịp kèm số đếm bên dưới. level: 2 = tiếng đầu ô nhịp (mạnh nhất),
 * 1 = đầu mỗi phách, 0 = các tiếng chia nhỏ. Chấm phình to khi đến lượt của nó.
 */
@Composable
private fun BeatDot(cell: Dp, level: Int, active: Boolean, label: String) {
    val factor = when (level) {
        2 -> if (active) 0.92f else 0.68f
        1 -> if (active) 0.80f else 0.58f
        else -> if (active) 0.68f else 0.46f
    }
    val dotSize by animateDpAsState(
        targetValue = cell * factor,
        animationSpec = tween(90),
        label = "beatDot"
    )
    Column(modifier = Modifier.width(cell), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(cell), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(
                        if (active) Color.White
                        else Color.White.copy(alpha = if (level == 0) 0.22f else 0.32f)
                    )
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (level == 2) FontWeight.Bold else FontWeight.Normal,
            color = Color.White.copy(alpha = if (level > 0) 0.95f else 0.7f)
        )
    }
}

@Composable
fun MetronomeScreen() {
    val context = LocalContext.current
    val prefs = remember {
        context.applicationContext.getSharedPreferences("metronome", Context.MODE_PRIVATE)
    }

    var bpm by remember { mutableIntStateOf(prefs.getInt("bpm", 80).coerceIn(40, 200)) }
    var sigIndex by remember { mutableIntStateOf(prefs.getInt("sig", 2).coerceIn(0, timeSigs.lastIndex)) }
    var playing by remember { mutableStateOf(false) }
    var currentStep by remember { mutableIntStateOf(-1) }

    val sig = timeSigs[sigIndex]
    val totalSteps = sig.beats * sig.stepsPerBeat

    val metronome = remember {
        Metronome(onStep = { s -> currentStep = s }) { }
    }

    // Dừng metronome khi rời màn hình
    DisposableEffect(Unit) {
        onDispose { metronome.stop() }
    }
    // Bấm Home hoặc khóa máy thì cũng dừng nhịp
    StopWhenBackgrounded {
        metronome.stop()
        playing = false
        currentStep = -1
    }

    fun setBpm(value: Int) {
        bpm = value.coerceIn(40, 200)
        metronome.bpm = bpm
        prefs.edit().putInt("bpm", bpm).apply()
    }

    fun changeSig(index: Int) {
        sigIndex = index
        currentStep = -1
        prefs.edit().putInt("sig", index).apply()
        // Đổi nhịp khi đang chạy thì có hiệu lực ngay ở bước kế tiếp
        metronome.stepsPerBeat = timeSigs[index].stepsPerBeat
        metronome.beatsPerBar = timeSigs[index].beats
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader("⏱️", "Metronome", "Giữ nhịp đều khi tập")

        // Thẻ lớn: tốc độ + nhịp + chấm nhịp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(UiHeroGradient)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${tempoName(bpm)}  ·  ${sig.label}",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f)
            )
            Text("$bpm", fontSize = 88.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "BPM",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(12.dp))
            val cell = if (totalSteps > 4) 40.dp else 48.dp
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(totalSteps) { i ->
                    val level = when {
                        i == 0 -> 2
                        i % sig.stepsPerBeat == 0 -> 1
                        else -> 0
                    }
                    BeatDot(
                        cell = cell,
                        level = level,
                        active = playing && currentStep == i,
                        label = (i + 1).toString()
                    )
                }
            }
        }

        // Chọn nhịp
        AppCard {
            Text(
                "Nhịp",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PillRow(
                labels = timeSigs.map { it.label },
                selectedIndex = sigIndex,
                onSelect = { changeSig(it) }
            )
            Text(sig.hint, style = MaterialTheme.typography.bodyMedium)
        }

        // Chỉnh tốc độ
        AppCard {
            BpmStepper(
                bpm = bpm,
                onMinus = { setBpm(bpm - 1) },
                onPlus = { setBpm(bpm + 1) }
            )
            Slider(
                value = bpm.toFloat(),
                onValueChange = { setBpm(it.toInt()) },
                valueRange = 40f..200f
            )
        }

        BigButton(
            text = if (playing) "⏹  Dừng" else "▶  Bắt đầu",
            container = if (playing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            onClick = {
                if (playing) {
                    metronome.stop()
                    currentStep = -1
                } else {
                    metronome.bpm = bpm
                    metronome.stepsPerBeat = sig.stepsPerBeat
                    metronome.beatsPerBar = sig.beats
                    metronome.start()
                }
                playing = !playing
            }
        )
    }
}