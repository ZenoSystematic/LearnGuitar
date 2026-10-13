package com.gbtminhtri.guitar

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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

    val axisColor = MaterialTheme.colorScheme.outline
    val needleColor = if (inTune) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Tuner", style = MaterialTheme.typography.headlineSmall)

        if (!hasPermission) {
            Text(
                "Tuner cần dùng micro để nghe tiếng đàn. App chỉ phân tích cao độ ngay trên máy, không ghi âm hay lưu lại.",
                style = MaterialTheme.typography.bodyLarge
            )
            Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text("Cho phép dùng micro")
            }
            if (askedOnce) {
                Text(
                    "Nếu không hiện hộp thoại xin quyền, hãy vào Cài đặt, Ứng dụng, chọn app này, rồi bật quyền Micro.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            return@Column
        }

        // Chọn dây: tự nhận hoặc chọn tay (hữu ích khi dây đang lệch quá nhiều)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = manualTarget == null,
                onClick = { manualTarget = null },
                label = { Text("Tự động") }
            )
            guitarStrings.forEach { s ->
                FilterChip(
                    selected = manualTarget == s,
                    onClick = { manualTarget = s },
                    label = { Text("${s.number}·${s.name}") }
                )
            }
        }

        // Tên nốt
        Text(
            target?.name ?: "—",
            fontSize = 96.sp,
            color = if (inTune) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
        )
        Text(
            if (target != null) "Dây ${target.number}" else "Chưa nghe thấy tiếng đàn",
            style = MaterialTheme.typography.titleMedium
        )

        // Kim chỉ độ lệch, từ -50 đến +50 cent
        Canvas(modifier = Modifier.fillMaxWidth().height(56.dp)) {
            val w = size.width
            val cy = size.height / 2f
            val half = w / 2f - 12f
            drawLine(axisColor, Offset(12f, cy), Offset(w - 12f, cy), strokeWidth = 4f)
            listOf(-50, -25, 0, 25, 50).forEach { c ->
                val x = w / 2f + c / 50f * half
                val tick = if (c == 0) 18f else 10f
                drawLine(axisColor, Offset(x, cy - tick), Offset(x, cy + tick), strokeWidth = if (c == 0) 6f else 3f)
            }
            if (cents != null) {
                val x = w / 2f + cents.coerceIn(-50.0, 50.0).toFloat() / 50f * half
                drawCircle(needleColor, radius = 14f, center = Offset(x, cy))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Thấp", style = MaterialTheme.typography.bodySmall)
            Text("Đúng", style = MaterialTheme.typography.bodySmall)
            Text("Cao", style = MaterialTheme.typography.bodySmall)
        }

        // Gợi ý hành động
        Text(
            when {
                freq == null || cents == null -> "Gảy một dây buông và để dây ngân."
                inTune -> "Đúng nốt!"
                cents < 0 -> "Hơi thấp: vặn khóa để căng dây lên."
                else -> "Hơi cao: vặn khóa để nới dây ra."
            },
            style = MaterialTheme.typography.titleLarge
        )
        if (freq != null && target != null && cents != null) {
            Text(
                "Nghe được ${String.format("%.1f", freq)} Hz, chuẩn ${String.format("%.2f", target.freq)} Hz (${String.format("%+.0f", cents)} cent)",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        OutlinedButton(
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
        ) { Text(if (listening) "Dừng nghe" else "Bắt đầu nghe") }

        Text(
            "Mẹo: ở nơi yên tĩnh, đặt điện thoại gần đàn, gảy vừa phải rồi để dây ngân. " +
                    "Nên lên dây từ thấp lên (vặn cho nốt đi lên tới chuẩn) để dây giữ ổn định hơn.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}