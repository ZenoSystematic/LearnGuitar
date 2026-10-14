package com.gbtminhtri.guitar

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/** Vẽ sơ đồ hợp âm: dây trầm bên trái, dây thanh bên phải, phím 1 ở trên cùng. */
@Composable
fun ChordDiagram(chord: Chord, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.onSurface
    val dotColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onPrimary

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val left = w * 0.12f
        val right = w * 0.88f
        val top = h * 0.16f
        val bottom = h * 0.96f
        val strings = 6
        val fretCount = 5
        val stringGap = (right - left) / (strings - 1)
        val fretGap = (bottom - top) / fretCount

        // Các thanh phím (thanh trên cùng đậm hơn = lược đàn)
        for (i in 0..fretCount) {
            val y = top + i * fretGap
            drawLine(
                color = lineColor,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = if (i == 0) 10f else 3f
            )
        }

        // Sáu dây đàn
        for (s in 0 until strings) {
            val x = left + s * stringGap
            drawLine(lineColor, Offset(x, top), Offset(x, bottom), strokeWidth = 3f)
        }

        val dotRadius = stringGap * 0.38f
        val markerY = top - fretGap * 0.5f
        val paint = Paint().apply {
            color = textColor.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = dotRadius * 1.3f
            isAntiAlias = true
            isFakeBoldText = true
        }

        // Barre: một ngón ấn ngang nhiều dây ở cùng một phím (ví dụ hợp âm F) -> vẽ một thanh thay vì nhiều chấm
        val barreStrings: List<Int> = chord.frets.indices
            .filter { chord.fingers[it] > 0 && chord.frets[it] > 0 }
            .groupBy { chord.fingers[it] * 10 + chord.frets[it] }
            .values.firstOrNull { it.size >= 2 } ?: emptyList()
        if (barreStrings.isNotEmpty()) {
            val xl = left + barreStrings.first() * stringGap
            val xr = left + barreStrings.last() * stringGap
            val by = top + (chord.frets[barreStrings.first()] - 0.5f) * fretGap
            drawRoundRect(
                color = dotColor,
                topLeft = Offset(xl - dotRadius, by - dotRadius),
                size = Size(xr - xl + 2 * dotRadius, 2 * dotRadius),
                cornerRadius = CornerRadius(dotRadius, dotRadius)
            )
            drawContext.canvas.nativeCanvas.drawText(
                chord.fingers[barreStrings.first()].toString(), xl, by + paint.textSize / 3f, paint
            )
        }

        chord.frets.forEachIndexed { s, fret ->
            val x = left + s * stringGap
            when {
                fret == -1 -> { // dấu X: không gảy dây này
                    val d = dotRadius * 0.6f
                    drawLine(lineColor, Offset(x - d, markerY - d), Offset(x + d, markerY + d), strokeWidth = 4f)
                    drawLine(lineColor, Offset(x - d, markerY + d), Offset(x + d, markerY - d), strokeWidth = 4f)
                }
                fret == 0 -> { // vòng tròn rỗng: dây buông
                    drawCircle(lineColor, radius = dotRadius * 0.7f, center = Offset(x, markerY), style = Stroke(width = 4f))
                }
                s in barreStrings -> {} // đã vẽ thành thanh barre ở trên
                else -> { // chấm tròn: vị trí bấm, kèm số ngón tay
                    val y = top + (fret - 0.5f) * fretGap
                    drawCircle(dotColor, radius = dotRadius, center = Offset(x, y))
                    val finger = chord.fingers[s]
                    if (finger > 0) {
                        drawContext.canvas.nativeCanvas.drawText(
                            finger.toString(), x, y + paint.textSize / 3f, paint
                        )
                    }
                }
            }
        }
    }
}