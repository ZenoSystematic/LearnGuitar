package com.gbtminhtri.guitar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private val tabs = listOf(
    "metronome" to "Metronome",
    "tuner" to "Tuner",
    "chords" to "Hợp âm",
    "practice" to "Luyện tập"
)

@Composable
fun GuitarApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { (route, label) ->
                    NavigationBarItem(
                        selected = current == route,
                        onClick = {
                            nav.navigate(route) {
                                popUpTo(nav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {},
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "metronome",
            modifier = Modifier.padding(padding)
        ) {
            composable("metronome") { MetronomeScreen() }
            composable("tuner") { Placeholder("Tuner (giai đoạn 3)") }
            composable("chords") { Placeholder("Thư viện hợp âm (giai đoạn 2)") }
            composable("practice") { Placeholder("Luyện tập (giai đoạn 4)") }
        }
    }
}

@Composable
private fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text)
    }
}
