package com.gbtminhtri.guitar

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * drillPair + drillBpm: nếu có, mục này tự động tick khi bài tập chuyển hợp âm
 * (tab Luyện tập) đã đạt ít nhất drillBpm BPM với cặp hợp âm đó.
 * drillPair là tên hai hợp âm xếp theo abc, nối bằng "-", ví dụ "Am-Em".
 */
data class RoadmapItem(
    val id: String,
    val title: String,
    val detail: String,
    val drillPair: String? = null,
    val drillBpm: Int = 0
)

data class RoadmapLevel(val title: String, val items: List<RoadmapItem>)

// Bạn có thể sửa, thêm, bớt mục tùy ý. Chỉ cần mỗi id là duy nhất.
private val roadmap = listOf(
    RoadmapLevel(
        "Cấp 1: Làm quen",
        listOf(
            RoadmapItem("strings", "Biết tên 6 dây", "Từ dây 6 đến dây 1: E A D G B E. Thuộc cả 12 câu trong bài đố tên dây (đúng trong 3 giây) thì tự tick."),
            RoadmapItem("tune", "Lên dây chuẩn bằng Tuner", "Dùng Tuner trong app (màn hình Hôm nay, bước đầu tiên). Chỉnh dây mỗi lần trước khi tập."),
            RoadmapItem("posture", "Tư thế ngồi và cầm đàn", "Ngồi thoải mái, tập 5 phút không bị mỏi tay hay đau vai.")
        )
    ),
    RoadmapLevel(
        "Cấp 2: Hợp âm đầu tiên",
        listOf(
            RoadmapItem("em", "Em sạch", "Gảy từng dây, cả 6 dây đều kêu rõ, không tịt."),
            RoadmapItem("am", "Am sạch", "Không gảy dây 6, 5 dây còn lại kêu rõ."),
            RoadmapItem(
                "em-am", "Chuyển Em ↔ Am đạt 50 BPM",
                "Tab Luyện tập: không quá 2 lỗi trong 60 giây.", "Am-Em", 50
            ),
            RoadmapItem("strum1", "Quạt 1 đều ở 60 BPM", "Tab Quạt/Rải: quạt 60 giây không lệch nhịp."),
            RoadmapItem("prog1", "Đệm vòng Em – Am bằng Quạt 3 ở 60 BPM", "Tab Quạt/Rải: chọn vòng Em – Am, chơi trọn 2 phút.")
        )
    ),
    RoadmapLevel(
        "Cấp 3: Mở rộng",
        listOf(
            RoadmapItem("cgd", "C, G, D sạch", "Mỗi hợp âm gảy từng dây đều kêu rõ."),
            RoadmapItem(
                "g-c", "Chuyển G ↔ C đạt 60 BPM",
                "Tab Luyện tập: không quá 2 lỗi trong 60 giây.", "C-G", 60
            ),
            RoadmapItem("strum3", "Quạt 3 ở 70 BPM", "Tab Quạt/Rải: quạt 60 giây đều tay."),
            RoadmapItem("prog2", "Đệm vòng G – D – Em – C ở 60 BPM", "Tab Quạt/Rải: chơi trọn 2 phút, đổi hợp âm kịp nhịp.")
        )
    ),
    RoadmapLevel(
        "Cấp 4: Rải",
        listOf(
            RoadmapItem("pick1", "Rải 1 ở 60 BPM", "Tab Quạt/Rải: mỗi nhịp một dây, 60 giây không vấp."),
            RoadmapItem("pick2", "Rải 2 ở 60 BPM", "Tab Quạt/Rải: 8 nốt mỗi ô nhịp, đều và rõ tiếng."),
            RoadmapItem("pickprog", "Rải theo vòng Em – Am ở 60 BPM", "Tab Quạt/Rải: vừa rải vừa đổi hợp âm.")
        )
    )
)

@Composable
fun RoadmapScreen() {
    val context = LocalContext.current
    val prefs = remember {
        context.applicationContext.getSharedPreferences("roadmap", Context.MODE_PRIVATE)
    }
    val repo = remember { DrillRepository(context.applicationContext) }

    var ticked by remember { mutableStateOf(prefs.getStringSet("done", emptySet())!!.toSet()) }
    val autoDone = remember {
        val fromDrill = roadmap.flatMap { it.items }
            .filter { it.drillPair != null && (repo.bestBpm(it.drillPair) ?: 0) >= it.drillBpm }
            .map { it.id }
            .toSet()
        val quizMastered = context.applicationContext
            .getSharedPreferences("strings", Context.MODE_PRIVATE)
            .getBoolean("mastered", false)
        if (quizMastered) fromDrill + "strings" else fromDrill
    }

    val allItems = roadmap.flatMap { it.items }
    val doneCount = allItems.count { it.id in ticked || it.id in autoDone }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProgressSection()
        Spacer(Modifier.height(16.dp))
        Text("Lộ trình của mình", style = MaterialTheme.typography.headlineSmall)
        Text("Đã xong $doneCount/${allItems.size} mục", style = MaterialTheme.typography.titleMedium)
        Text(
            "Chỉ tick khi thật sự làm được, đừng tick cho đủ. Các mục chuyển hợp âm tự tick từ tab Luyện tập.",
            style = MaterialTheme.typography.bodySmall
        )

        roadmap.forEach { level ->
            Spacer(Modifier.height(8.dp))
            Text(level.title, style = MaterialTheme.typography.titleLarge)
            level.items.forEach { item ->
                val auto = item.id in autoDone
                val checked = auto || item.id in ticked
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(
                        checked = checked,
                        enabled = !auto,
                        onCheckedChange = { isChecked ->
                            ticked = if (isChecked) ticked + item.id else ticked - item.id
                            prefs.edit().putStringSet("done", ticked.toSet()).apply()
                        }
                    )
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            item.detail + if (auto) " (tự động tick)" else "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}