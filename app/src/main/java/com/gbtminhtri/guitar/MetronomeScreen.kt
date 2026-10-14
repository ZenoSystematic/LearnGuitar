package com.gbtminhtri.guitar

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun tempoName(bpm: Int) = when {
    bpm < 60 -> "Rất chậm"
    bpm < 80 -> "Chậm"
    bpm < 110 -> "Vừa phải"
    bpm < 140 -> "Khá nhanh"
    else -> "Nhanh"
}

/** Chấm nhịp: phình to khi đến phách của nó; phách 1 (accent) to hơn một chút. */
@Composable
private fun BeatDot(active: Boolean, accent: Boolean) {
    val dotSize by animateDpAsState(
        targetValue = when {
            active && accent -> 44.dp
            active -> 38.dp
            accent -> 32.dp
            else -> 26.dp
        },
        animationSpec = tween(90),
        label = "beatDot"
    )
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(if (active) Color.White else Color.White.copy(alpha = 0.3f))
        )
    }
}

@Composable
fun MetronomeScreen() {
    var bpm by remember { mutableIntStateOf(80) }
    var playing by remember { mutableStateOf(false) }
    var currentBeat by remember { mutableIntStateOf(-1) }
    val beatsPerBar = 4

    val metronome = remember {
        Metronome { beat -> currentBeat = beat }
    }

    // Dừng metronome khi rời màn hình
    DisposableEffect(Unit) {
        onDispose { metronome.stop() }
    }

    fun setBpm(value: Int) {
        bpm = value.coerceIn(40, 200)
        metronome.bpm = bpm
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader("⏱️", "Metronome", "Giữ nhịp đều khi tập")

        // Thẻ lớn: tốc độ + chấm nhịp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(UiHeroGradient)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                tempoName(bpm),
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(beatsPerBar) { i ->
                    BeatDot(active = playing && currentBeat == i, accent = i == 0)
                }
            }
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
                    currentBeat = -1
                } else {
                    metronome.bpm = bpm
                    metronome.beatsPerBar = beatsPerBar
                    metronome.start()
                }
                playing = !playing
            }
        )
    }
}