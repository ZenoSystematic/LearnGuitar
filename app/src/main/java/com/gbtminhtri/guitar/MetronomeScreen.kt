package com.gbtminhtri.guitar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("$bpm", fontSize = 96.sp)
        Text("BPM", style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                bpm = (bpm - 1).coerceAtLeast(40)
                metronome.bpm = bpm
            }) { Text("−") }

            Slider(
                value = bpm.toFloat(),
                onValueChange = {
                    bpm = it.toInt()
                    metronome.bpm = bpm
                },
                valueRange = 40f..200f,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )

            OutlinedButton(onClick = {
                bpm = (bpm + 1).coerceAtMost(200)
                metronome.bpm = bpm
            }) { Text("+") }
        }

        Spacer(Modifier.height(24.dp))

        // Chỉ báo nhịp: chấm sáng theo từng phách
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(beatsPerBar) { i ->
                val active = playing && currentBeat == i
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
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
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (playing) "Dừng" else "Bắt đầu")
        }
    }
}
