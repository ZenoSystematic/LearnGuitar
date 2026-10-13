package com.gbtminhtri.guitar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val fingerNames = mapOf(1 to "trỏ", 2 to "giữa", 3 to "áp út", 4 to "út")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordsScreen() {
    var selected by remember { mutableStateOf(basicChords.first()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hàng chọn hợp âm (cuộn ngang được)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            basicChords.forEach { chord ->
                FilterChip(
                    selected = chord == selected,
                    onClick = { selected = chord },
                    label = { Text(chord.name) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(selected.name, fontSize = 48.sp, style = MaterialTheme.typography.displaySmall)

        ChordDiagram(
            chord = selected,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(0.85f)
        )

        Spacer(Modifier.height(16.dp))

        // Hướng dẫn bằng chữ: dây nào, phím nào, ngón nào
        Column(modifier = Modifier.fillMaxWidth()) {
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
    }
}