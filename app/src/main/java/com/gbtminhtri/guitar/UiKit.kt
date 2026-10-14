package com.gbtminhtri.guitar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bộ thành phần giao diện dùng chung cho toàn app, để các màn hình có cùng một phong cách:
 * tiêu đề có biểu tượng, thẻ bo tròn, nút chọn dạng viên thuốc, nút lớn, bộ chỉnh BPM...
 */

val UiHeroGradient = Brush.linearGradient(listOf(Color(0xFFFF9A3C), Color(0xFFD9541E)))
val UiSuccessGradient = Brush.linearGradient(listOf(Color(0xFF3DBE8B), Color(0xFF1F8A5B)))

/** Tiêu đề màn hình: ô biểu tượng + tên + dòng phụ (tuỳ chọn) + nút bên phải (tuỳ chọn). */
@Composable
fun ScreenHeader(
    emoji: String,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 26.sp) }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

/** Thẻ nền bo tròn chứa một nhóm nội dung. */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/** Hàng nút chọn dạng viên thuốc, cuộn ngang được. selectedIndex = -1 nếu chưa chọn gì. */
@Composable
fun PillRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Hàng chọn hợp âm (dùng bộ hợp âm của app). */
@Composable
fun ChordPillRow(selected: Chord?, onSelect: (Chord) -> Unit, modifier: Modifier = Modifier) {
    PillRow(
        labels = basicChords.map { it.name },
        selectedIndex = if (selected == null) -1 else basicChords.indexOf(selected),
        onSelect = { onSelect(basicChords[it]) },
        modifier = modifier
    )
}

/** Nút hành động lớn. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.primary
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = contentColorFor(container)
        )
    ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

/** Nút phụ, màu dịu. */
@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

/** Nút tròn nhỏ, dùng cho − và +. */
@Composable
fun RoundButton(symbol: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/** Bộ chỉnh BPM: số to ở giữa, nút − và + hai bên. */
@Composable
fun BpmStepper(bpm: Int, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RoundButton("−", onMinus)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$bpm", fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Text(
                "BPM",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RoundButton("+", onPlus)
    }
}

enum class UiTone { SUCCESS, WARN, INFO }

/** Thanh thông báo có màu: xanh = tốt, đỏ = cần chú ý, cam = thông tin. */
@Composable
fun Banner(text: String, tone: UiTone = UiTone.INFO, emoji: String? = null, modifier: Modifier = Modifier) {
    val accent = when (tone) {
        UiTone.SUCCESS -> MaterialTheme.colorScheme.tertiary
        UiTone.WARN -> MaterialTheme.colorScheme.error
        UiTone.INFO -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.16f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (emoji != null) Text(emoji, fontSize = 22.sp)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

/** Vòng tròn tiến độ vẽ bằng Canvas. */
@Composable
fun UiRing(
    fraction: Float,
    ringSize: Dp,
    strokeWidth: Dp,
    ringColor: Color,
    trackColor: Color,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (fraction > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceAtMost(1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}