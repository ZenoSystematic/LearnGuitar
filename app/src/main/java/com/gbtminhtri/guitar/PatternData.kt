package com.gbtminhtri.guitar

enum class PatternType { STRUM, PICK }

/**
 * labels: nhãn của từng ô trong một ô nhịp (4 nhịp).
 *   Quạt: "↓" xuống, "↑" lên, "" = tay vẫn đưa nhưng không chạm dây.
 *   Rải: số dây (6 trầm nhất ... 1 thanh nhất); "B" = dây trầm của hợp âm đang chọn.
 * stepsPerBeat: 1 = mỗi nhịp một ô (4 ô), 2 = chia đôi nhịp (8 ô).
 */
data class Pattern(
    val name: String,
    val description: String,
    val type: PatternType,
    val labels: List<String>,
    val stepsPerBeat: Int
)

val patterns = listOf(
    Pattern(
        "Quạt 1", "↓ ↓ ↓ ↓ : mỗi nhịp quạt xuống một cái, để làm quen tay.",
        PatternType.STRUM, listOf("↓", "↓", "↓", "↓"), 1
    ),
    Pattern(
        "Quạt 2", "↓↑ đều: tay đưa xuống lên liên tục, mọi ô đều chạm dây.",
        PatternType.STRUM, listOf("↓", "↑", "↓", "↑", "↓", "↑", "↓", "↑"), 2
    ),
    Pattern(
        "Quạt 3", "Mẫu phổ biến nhất. Ô có dấu · thì tay vẫn đưa nhưng không chạm dây.",
        PatternType.STRUM, listOf("↓", "", "↓", "↑", "", "↑", "↓", "↑"), 2
    ),
    Pattern(
        "Rải 1", "Dây trầm rồi lần lượt dây 3, 2, 1. Mỗi nhịp gảy một dây.",
        PatternType.PICK, listOf("B", "3", "2", "1"), 1
    ),
    Pattern(
        "Rải 2", "Dây trầm rồi xen kẽ dây 3, 2, 1 (8 nốt mỗi ô nhịp).",
        PatternType.PICK, listOf("B", "3", "2", "3", "1", "3", "2", "3"), 2
    )
)

/** Dây trầm (nốt gốc) của từng hợp âm cơ bản, dùng cho các mẫu rải. */
val bassStrings = mapOf(
    "Em" to 6, "Am" to 5, "C" to 5, "G" to 6, "D" to 4, "A" to 5, "E" to 6,
    "Dm" to 4, "F" to 6
)

/** Vòng hợp âm để tập đệm: mỗi ô nhịp (4 nhịp) đổi sang hợp âm kế tiếp. */
data class Progression(val name: String, val chords: List<String>)

val progressions = listOf(
    Progression("Em – Am", listOf("Em", "Am")),
    Progression("G – D – Em – C", listOf("G", "D", "Em", "C")),
    Progression("Am – G – C – D", listOf("Am", "G", "C", "D")),
    Progression("A – D – E – A", listOf("A", "D", "E", "A"))
)

fun chordByName(name: String): Chord = basicChords.first { it.name == name }