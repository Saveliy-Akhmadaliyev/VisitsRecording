package ru.plasmadynamics.visits.mobile.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VisitsTheme {
                VisitsApp()
            }
        }
    }
}

/** Навигация — простой стек экранов без nav-библиотеки (минимальность намеренная). */
private sealed interface Screen {
    data object List : Screen
    data class Details(val visitId: String) : Screen
    data class Checklist(val visitId: String) : Screen
}

@Composable
fun VisitsApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }

    BackHandler(enabled = screen != Screen.List) { screen = Screen.List }

    when (val s = screen) {
        Screen.List -> VisitsScreen(onOpenVisit = { screen = Screen.Details(it) })
        is Screen.Details -> VisitDetailsScreen(
            visitId = s.visitId,
            onBack = { screen = Screen.List },
            onStartCheck = { screen = Screen.Checklist(s.visitId) },
        )
        is Screen.Checklist -> ChecklistScreen(
            visitId = s.visitId,
            onDone = { screen = Screen.List },
        )
    }
}
