package com.gbtminhtri.guitar

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.ln

private data class GuitarString(val number: Int, val name: String, val freq: Double)

// Lên dây chuẩn: E2 A2 D3 G3 B3 E4
private val guitarStrings = listOf(
    GuitarString(6, "E", 82.41),
    GuitarString(5, "A", 110.00),
    GuitarString(4, "D", 146.83),
    GuitarString(3, "G", 196.00),
    GuitarString(2, "B", 246.94),
    GuitarString(1, "E", 329.63)
)

private const val IN_TUNE_CENTS = 5.0 // lệch trong khoảng này thì coi là đúng nốt

/** Độ lệch so với nốt chuẩn, tính bằng cent (100 cent = 1 nửa cung). */
private fun centsOff(freq: Double, target: Double): Double = 1200.0 * ln(freq / target) / ln(2.0)

/** Lấy trung vị của vài lần đo gần nhất để số không nhảy lung tung. */
private class Smoother {
    private val recent = ArrayList<Double>()
    private var misses = 0

    fun push(freq: Double?): Double? {
        if (freq == null) {
            misses++
            if (misses > 6) recent.clear() // im lặng đủ lâu thì xóa kết quả cũ
        } else {
            misses = 0
            recent.add(freq)
            if (recent.size > 5) recent.removeAt(0)
        }
        return if (recent.isEmpty()) null else recent.sorted()[recent.size / 2]
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerScreen() {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var askedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        askedOnce = true
    }

    var frequency by remember { mutableStateOf<Double?>(null) }
    var listening by remember { mutableStateOf(false) }
    var manualTarget by remember { mutableStateOf<GuitarString?>(null) } // null = tự nhận dây

    val smoother = remember { Smoother() }
    val engine = remember { TunerEngine { f -> frequency = smoother.push(f) } }
    DisposableEffect(Unit) { onDispose { engine.stop() } }
    // Xuống nền thì nhả micro; quay lại bấm "Bắt đầu nghe" để nghe tiếp
    StopWhenBackgrounded {
        if (listening) {
            engine.stop()
            listening = false
            frequency = null
        }
    }

    // Vào màn hình thì xin quyền micro (nếu chưa có), có quyền thì tự bắt đầu nghe
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission && !listening) {
            engine.start()
            listening = true
        }
    }

    val freq = frequency
    val target = manualTarget ?: freq?.let { f ->
        guitarStrings.minByOrNull { abs(centsOff(f, it.freq)) }
    }
    val cents = if (freq != null && target != null) centsOff(freq, target.freq) else null
    val inTune = cents != null && abs(cents) <= IN_TUNE_CENTS

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader("🎚️", "Tuner", "Lên dây chuẩn cho đàn")

        if (!hasPermission) {
            AppCard {
                Text(
                    "Tuner cần dùng micro để nghe tiếng đàn. App chỉ phân tích cao độ ngay trên máy, không ghi âm hay lưu lại.",
                    style = MaterialTheme.typography.bodyLarge
                )
                BigButton(
                    text = "🎙️  Cho phép dùng micro",
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                )
                if (askedOnce) {
                    Text(
                        "Nếu không hiện hộp thoại xin quyền, hãy vào Cài đặt, Ứng dụng, chọn app này, rồi bật quyền Micro.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Column
        }

        // Chọn dây: tự nhận hoặc chọn tay (hữu ích khi dây đang lệch quá nhiều)
        PillRow(
            labels = listOf("Tự động") + guitarStrings.map { "${it.number}·${it.name}" },
            selectedIndex = manualTarget?.let { guitarStrings.indexOf(it) + 1 } ?: 0,
            onSelect = { i -> manualTarget = if (i == 0) null else guitarStrings[i - 1] }
        )

        // Thẻ lớn: tên nốt + kim chỉ độ lệch. Xanh lá khi đúng nốt, cam khi đang lệch.
        val onCard = if (target != null) Color.White else MaterialTheme.colorScheme.onSurface
        val cardModifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .let { m ->
                when {
                    target == null -> m.background(MaterialTheme.colorScheme.surfaceVariant)
                    inTune -> m.background(UiSuccessGradient)
                    else -> m.background(UiHeroGradient)
                }
            }
        Column(
            modifier = cardModifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                target?.name ?: "—",
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold,
                color = onCard
            )
            Text(
                if (target != null) "Dây ${target.number}" else "Chưa nghe thấy tiếng đàn",
                style = MaterialTheme.typography.titleMedium,
                color = onCard.copy(alpha = 0.9f)
            )

            // Kim chỉ độ lệch, từ -50 đến +50 cent
            Canvas(modifier = Modifier.fillMaxWidth().height(56.dp)) {
                val w = size.width
                val cy = size.height / 2f
                val half = w / 2f - 12f
                val axis = onCard.copy(alpha = 0.7f)
                drawLine(axis, Offset(12f, cy), Offset(w - 12f, cy), strokeWidth = 4f)
                listOf(-50, -25, 0, 25, 50).forEach { c ->
                    val x = w / 2f + c / 50f * half
                    val tick = if (c == 0) 18f else 10f
                    drawLine(axis, Offset(x, cy - tick), Offset(x, cy + tick), strokeWidth = if (c == 0) 6f else 3f)
                }
                if (cents != null) {
                    val x = w / 2f + cents.coerceIn(-50.0, 50.0).toFloat() / 50f * half
                    drawCircle(Color.White, radius = 16f, center = Offset(x, cy))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Thấp", style = MaterialTheme.typography.bodySmall, color = onCard.copy(alpha = 0.85f))
                Text("Đúng", style = MaterialTheme.typography.bodySmall, color = onCard.copy(alpha = 0.85f))
                Text("Cao", style = MaterialTheme.typography.bodySmall, color = onCard.copy(alpha = 0.85f))
            }
        }

        // Gợi ý hành động
        when {
            freq == null || cents == null ->
                Banner("Gảy một dây buông và để dây ngân.", UiTone.INFO, "🎸")
            inTune ->
                Banner("Đúng nốt!", UiTone.SUCCESS, "✅")
            cents < 0 ->
                Banner("Hơi thấp: vặn khóa để căng dây lên.", UiTone.WARN, "⬆️")
            else ->
                Banner("Hơi cao: vặn khóa để nới dây ra.", UiTone.WARN, "⬇️")
        }
        if (freq != null && target != null && cents != null) {
            Text(
                "Nghe được ${String.format("%.1f", freq)} Hz, chuẩn ${String.format("%.2f", target.freq)} Hz (${String.format("%+.0f", cents)} cent)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SoftButton(
            text = if (listening) "⏸  Dừng nghe" else "🎙️  Bắt đầu nghe",
            onClick = {
                if (listening) {
                    engine.stop()
                    listening = false
                    frequency = null
                } else {
                    engine.start()
                    listening = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            "💡 Ở nơi yên tĩnh, đặt điện thoại gần đàn, gảy vừa phải rồi để dây ngân. " +
                    "Nên lên dây từ thấp lên (vặn cho nốt đi lên tới chuẩn) để dây giữ ổn định hơn.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}