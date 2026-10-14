package com.gbtminhtri.guitar

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Các hàm xóa dữ liệu tiến bộ. Chỉ xóa số liệu học tập, GIỮ nguyên cài đặt
 * (bật/tắt âm thanh, mức tốc độ đã chọn trong bài đố tên dây...).
 */
object ProgressReset {
    private fun prefs(context: Context, name: String) =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    /** Chuyển hợp âm: lịch sử, biểu đồ, BPM và số lượt đạt của mọi cặp hợp âm. */
    fun resetChordChange(context: Context) {
        val p = prefs(context, "drill")
        val e = p.edit()
        e.remove("history")
        p.all.keys
            .filter { it.startsWith("bpm_") || it.startsWith("passes_") }
            .forEach { e.remove(it) }
        e.apply()
    }

    /** Quạt/Rải: lịch sử, biểu đồ và BPM đã chọn cho từng mẫu. */
    fun resetPatterns(context: Context) {
        prefs(context, "drill").edit().remove("pattern_history").apply()
        prefs(context, "pattern").edit().clear().apply()
    }

    fun resetStringQuiz(context: Context) {
        prefs(context, "strings").edit()
            .remove("boxes").remove("best_streak").remove("mastered")
            .apply()
    }

    fun resetChordQuiz(context: Context) {
        prefs(context, "chordquiz").edit()
            .remove("boxes").remove("best_streak").remove("mastered").remove("unlocked")
            .apply()
    }

    fun resetEarTraining(context: Context) {
        prefs(context, "ear").edit()
            .remove("boxes").remove("best_streak").remove("mastered").remove("unlocked")
            .apply()
    }

    /** Các ngày đã tập (chuỗi ngày ở màn hình Hôm nay). */
    fun resetPracticeDays(context: Context) {
        prefs(context, "today").edit().remove("days").apply()
    }

    /** Thử thách hàng ngày: điểm cao nhất và ngày đã làm. */
    fun resetDailyChallenge(context: Context) {
        prefs(context, "daily").edit().clear().apply()
    }

    /** Các mục đã tick tay trong lộ trình (mục tự tick sẽ mất khi dữ liệu gốc bị xóa). */
    fun resetRoadmapTicks(context: Context) {
        prefs(context, "roadmap").edit().remove("done").apply()
    }
}

private data class ResetOption(
    val id: String,
    val title: String,
    val detail: String,
    val action: (Context) -> Unit
)

private val resetOptions = listOf(
    ResetOption(
        "chordchange", "Chuyển hợp âm",
        "Lịch sử, biểu đồ và BPM của bài chuyển hợp âm (tab Luyện tập).",
        ProgressReset::resetChordChange
    ),
    ResetOption(
        "patterns", "Quạt / Rải",
        "Lịch sử, biểu đồ và BPM đã chọn của từng mẫu quạt/rải.",
        ProgressReset::resetPatterns
    ),
    ResetOption(
        "strings", "Đố tên dây",
        "Độ thuộc 6 dây và chuỗi đúng dài nhất.",
        ProgressReset::resetStringQuiz
    ),
    ResetOption(
        "chordquiz", "Quiz hợp âm",
        "Độ thuộc các hợp âm và các mức đã mở khóa.",
        ProgressReset::resetChordQuiz
    ),
    ResetOption(
        "ear", "Luyện tai",
        "Độ thuộc khi nghe hợp âm và các mức đã mở khóa.",
        ProgressReset::resetEarTraining
    ),
    ResetOption(
        "days", "Ngày đã tập",
        "Số ngày tập và chuỗi ngày ở màn hình Hôm nay.",
        ProgressReset::resetPracticeDays
    ),
    ResetOption(
        "daily", "Thử thách hàng ngày",
        "Điểm cao nhất và ngày đã làm thử thách (chuỗi ngày nằm ở mục Ngày đã tập).",
        ProgressReset::resetDailyChallenge
    ),
    ResetOption(
        "roadmap", "Mục tick tay trong lộ trình",
        "Các mục bạn tự tick. Mục tự tick sẽ mất theo dữ liệu gốc bị xóa ở trên.",
        ProgressReset::resetRoadmapTicks
    )
)

/** Khu vực "Đặt lại tiến bộ" ở cuối tab Tiến bộ: mặc định thu gọn. onDone được gọi sau khi xóa xong để làm mới màn hình. */
@Composable
fun ResetProgressSection(onDone: () -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirming by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "⚙️ Đặt lại tiến bộ",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(if (open) "▾" else "▸", style = MaterialTheme.typography.titleMedium)
        }

        if (open) {
            Column(
                modifier = Modifier.padding(start = 8.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                resetOptions.forEach { option ->
                    val checked = option.id in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (checked) selected - option.id else selected + option.id
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Text(option.title, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { selected = resetOptions.map { it.id }.toSet() }) {
                        Text("Chọn tất cả")
                    }
                    TextButton(onClick = { selected = emptySet() }, enabled = selected.isNotEmpty()) {
                        Text("Bỏ chọn")
                    }
                }

                Button(
                    onClick = { confirming = true },
                    enabled = selected.isNotEmpty(),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(if (selected.isEmpty()) "Chọn phần cần đặt lại" else "Đặt lại ${selected.size} mục đã chọn")
                }
            }
        }
    }

    if (confirming) {
        val chosen = resetOptions.filter { it.id in selected }
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Đặt lại tiến bộ?") },
            text = {
                Text(
                    "Sẽ xóa:\n" + chosen.joinToString("\n") { "• ${it.title}: ${it.detail}" } +
                            "\n\nViệc này không thể hoàn tác."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    chosen.forEach { it.action(context) }
                    confirming = false
                    selected = emptySet()
                    open = false
                    Toast.makeText(context, "Đã đặt lại tiến bộ", Toast.LENGTH_SHORT).show()
                    onDone()
                }) { Text("Xóa", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Hủy") }
            }
        )
    }
}