package com.gbtminhtri.guitar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val fingerNames = mapOf(1 to "trỏ", 2 to "giữa", 3 to "áp út", 4 to "út")

/**
 * Màn hình Hợp âm: chọn hợp âm, xem sơ đồ + hướng dẫn, và NGHE ÂM MẪU.
 * Chạm vào tên hợp âm (ở hàng chọn hoặc tên lớn) hoặc nút 🔊 để nghe.
 * onOpen dùng để mở nhanh Quiz hợp âm / Luyện tai.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordsScreen(onOpen: (String) -> Unit = {}) {
    var selected by remember { mutableStateOf(basicChords.first()) }

    // Tạo sẵn âm thanh các hợp âm ở luồng nền, và dừng âm khi rời màn hình
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { basicChords.forEach { ChordSound.prepare(it) } }
    }
    DisposableEffect(Unit) { onDispose { ChordSound.stop() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hàng chọn hợp âm: chạm là chọn và nghe luôn
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            basicChords.forEach { chord ->
                FilterChip(
                    selected = chord == selected,
                    onClick = {
                        selected = chord
                        ChordSound.play(chord)
                    },
                    label = { Text(chord.name) }
                )
            }
        }

        // Tên hợp âm lớn + nút nghe lại
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                selected.name,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { ChordSound.play(selected) }
            )
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { ChordSound.play(selected) },
                contentAlignment = Alignment.Center
            ) { Text("🔊", fontSize = 26.sp) }
        }
        Text(
            "Chạm tên hợp âm hoặc nút 🔊 để nghe lại",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ChordDiagram(
            chord = selected,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .aspectRatio(0.85f)
        )

        // Hướng dẫn bằng chữ: dây nào, phím nào, ngón nào
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            selected.frets.forEachIndexed { index, fret ->
                val stringNumber = 6 - index
                val text = when {
                    fret == -1 -> "Dây $stringNumber: không gảy"
                    fret == 0 -> "Dây $stringNumber: buông"
                    else -> {
                        val finger = selected.fingers[index]
                        val name = fingerNames[finger]?.let { "ngón $it ($finger)" } ?: "bất kỳ ngón nào"
                        "Dây $stringNumber: phím $fret, $name"
                    }
                }
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }

        // Lối tắt sang hai bài kiểm tra
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(onClick = { onOpen("chordquiz") }, modifier = Modifier.weight(1f)) {
                Text("📝 Quiz hợp âm")
            }
            FilledTonalButton(onClick = { onOpen("ear") }, modifier = Modifier.weight(1f)) {
                Text("👂 Luyện tai")
            }
        }
    }
}