package com.gbtminhtri.guitar

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private data class Tab(val route: String, val label: String, val emoji: String)

// Các tab hiện trên thanh dưới. Các màn hình "metronome", "strings", "tuner", "chordquiz", "ear" mở từ màn hình Hôm nay (và Hợp âm).
private val tabs = listOf(
    Tab("today", "Hôm nay", "🏠"),
    Tab("chords", "Hợp âm", "🎼"),
    Tab("practice", "Luyện tập", "🔁"),
    Tab("pattern", "Quạt/Rải", "🎸"),
    Tab("roadmap", "Tiến bộ", "📈")
)

private fun NavHostController.goTo(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun GuitarApp() {
    val nav = rememberNavController()
    ChordSound.init(LocalContext.current) // để ChordSound tìm được file ghi âm trong res/raw
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                tabs.forEach { tab ->
                    val selected = current == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { nav.goTo(tab.route) },
                        icon = {
                            Text(
                                tab.emoji,
                                fontSize = 22.sp,
                                modifier = Modifier.alpha(if (selected) 1f else 0.55f)
                            )
                        },
                        label = { Text(tab.label, maxLines = 1, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "today",
            modifier = Modifier.padding(padding)
        ) {
            composable("today") { TodayScreen(onOpen = { route -> nav.goTo(route) }) }
            composable("metronome") { MetronomeScreen() }
            composable("strings") { StringQuizScreen() }
            composable("chordquiz") { ChordQuizScreen() }
            composable("ear") { EarTrainingScreen() }
            composable("tuner") { TunerScreen() }
            composable("chords") { ChordsScreen(onOpen = { route -> nav.goTo(route) }) }
            composable("practice") { DrillScreen() }
            composable("pattern") { PatternScreen() }
            composable("roadmap") { RoadmapScreen() }
        }
    }
}