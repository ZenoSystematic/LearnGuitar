package com.gbtminhtri.guitar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader("🎼", "Hợp âm", "Chạm tên hợp âm để nghe thử")

        // Hàng chọn hợp âm: chạm là chọn và nghe luôn
        ChordPillRow(
            selected = selected,
            onSelect = {
                selected = it
                ChordSound.play(it)
            }
        )

        // Thẻ lớn: tên hợp âm + nút nghe
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(UiHeroGradient)
                .clickable { ChordSound.play(selected) }
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(selected.name, fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "Chạm để nghe lại",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) { Text("🔊", fontSize = 30.sp) }
        }

        // Sơ đồ bên trái, cách bấm từng dây bên phải
        AppCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChordDiagram(
                    chord = selected,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(0.85f)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selected.frets.forEachIndexed { index, fret ->
                        val stringNumber = 6 - index
                        val text = when {
                            fret == -1 -> "không gảy"
                            fret == 0 -> "buông"
                            else -> {
                                val finger = selected.fingers[index]
                                val name = fingerNames[finger]
                                if (name != null) "phím $fret · ngón $name" else "phím $fret"
                            }
                        }
                        val muted = fret == -1
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            muted -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                            fret == 0 -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$stringNumber",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Text(
                                text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            Text(
                "Số trong vòng tròn là số dây (6 trầm nhất, 1 thanh nhất).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Lối tắt sang hai bài kiểm tra
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SoftButton("📝 Quiz hợp âm", onClick = { onOpen("chordquiz") }, modifier = Modifier.weight(1f))
            SoftButton("👂 Luyện tai", onClick = { onOpen("ear") }, modifier = Modifier.weight(1f))
        }
    }
}