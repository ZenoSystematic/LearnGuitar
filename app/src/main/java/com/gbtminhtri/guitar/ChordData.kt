package com.gbtminhtri.guitar

/**
 * frets và fingers có 6 phần tử, theo thứ tự từ dây 6 (trầm nhất) đến dây 1 (thanh nhất).
 * frets: -1 = không gảy dây đó, 0 = dây buông, 1..5 = bấm ở phím đó.
 * fingers: 0 = không dùng ngón, 1 = trỏ, 2 = giữa, 3 = áp út, 4 = út.
 */
data class Chord(
    val name: String,
    val frets: List<Int>,
    val fingers: List<Int>
)

val basicChords = listOf(
    Chord("Em", listOf(0, 2, 2, 0, 0, 0), listOf(0, 2, 3, 0, 0, 0)),
    Chord("Am", listOf(-1, 0, 2, 2, 1, 0), listOf(0, 0, 2, 3, 1, 0)),
    Chord("C", listOf(-1, 3, 2, 0, 1, 0), listOf(0, 3, 2, 0, 1, 0)),
    Chord("G", listOf(3, 2, 0, 0, 0, 3), listOf(2, 1, 0, 0, 0, 3)),
    Chord("D", listOf(-1, -1, 0, 2, 3, 2), listOf(0, 0, 0, 1, 3, 2)),
    Chord("A", listOf(-1, 0, 2, 2, 2, 0), listOf(0, 0, 1, 2, 3, 0)),
    Chord("E", listOf(0, 2, 2, 1, 0, 0), listOf(0, 2, 3, 1, 0, 0))
)