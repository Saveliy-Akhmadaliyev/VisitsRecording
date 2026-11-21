package ru.plasmadynamics.visits.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.plasmadynamics.visits.mobile.App
import ru.plasmadynamics.visits.mobile.db.SyncStatus
import ru.plasmadynamics.visits.mobile.db.VisitEntity
import ru.plasmadynamics.visits.mobile.net.UserOut

/**
 * Главный экран: вкладки «Запланированные / Ожидают отправки / Завершённые»,
 * плашка синхронизации + кнопка «Обновить».
 */
@Composable
fun VisitsScreen(onOpenVisit: (String) -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as App).container
    val scope = rememberCoroutineScope()

    val visits by container.db.visitDao().observeAll().collectAsState(initial = emptyList())
    var syncing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var inspectorChosen by remember { mutableStateOf(container.prefs.inspectorId != null) }

    if (!inspectorChosen) {
        InspectorPicker(onPicked = {
            container.prefs.inspectorId = it
            inspectorChosen = true
        })
        return
    }

    val planned = visits.filter { it.isPlanned() }
    val pending = visits.filter { it.syncStatus == SyncStatus.PENDING_SYNC }
    val done = visits.filter { it.isDone() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {

        // Плашка статуса синхронизации
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Синхронизировано: " +
                    (container.prefs.lastSyncedAt?.let { formatIsoShort(it) } ?: "ещё не было"),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = !syncing,
                onClick = {
                    scope.launch {
                        syncing = true
                        error = null
                        error = runCatching { container.syncRepository.syncNow() }
                            .exceptionOrNull()?.message
                        syncing = false
                    }
                },
            ) {
                if (syncing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Обновить")
                }
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))

        var tab by remember { mutableStateOf(0) }
        val tabs = listOf(
            "Запланированные (${planned.size})",
            "Ожидают отправки (${pending.size})",
            "Завершённые (${done.size})",
        )
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }

        val list = when (tab) {
            0 -> planned
            1 -> pending
            else -> done
        }
        if (list.isEmpty()) {
            Text(
                "Нет визитов",
                Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { visit ->
                    VisitCard(visit, onClick = { onOpenVisit(visit.id) })
                }
            }
        }
    }
}

@Composable
private fun VisitCard(visit: VisitEntity, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Text(visit.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${visit.locationName} · ${formatMillis(visit.scheduledAt)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                statusLabel(visit.status) +
                    if (visit.syncStatus == SyncStatus.PENDING_SYNC) " · ожидает отправки" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * DEV-заглушка вместо экрана входа: аутентификации на беке ещё нет, поэтому
 * инспектор выбирается из списка (TODO: заменить на логин/пароль + JWT).
 */
@Composable
private fun InspectorPicker(onPicked: (String) -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as App).container
    var users by remember { mutableStateOf<List<UserOut>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val all = container.api.users()
            users = all.filter { it.role == "INSPECTOR" && it.isActive }
        } catch (e: Exception) {
            error = e.message
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Выберите инспектора", style = MaterialTheme.typography.titleLarge)
        Text(
            "Временный экран: аутентификация появится позже",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when (val list = users) {
            null -> if (error == null) CircularProgressIndicator()
            else -> list.forEach { user ->
                TextButton(onClick = { onPicked(user.id) }) {
                    Text("${user.fullName} (${user.login})")
                }
            }
        }
    }
}

private fun formatIsoShort(iso: String): String = try {
    formatMillis(java.time.Instant.parse(iso).toEpochMilli())
} catch (e: Exception) {
    iso
}
