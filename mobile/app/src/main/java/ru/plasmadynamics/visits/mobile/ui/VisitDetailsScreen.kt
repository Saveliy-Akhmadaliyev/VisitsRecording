package ru.plasmadynamics.visits.mobile.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.plasmadynamics.visits.mobile.App

/**
 * Детали визита: объект, адрес, плановое время, маршрут в навигатор,
 * ответы (если заполнены) и кнопка «Начать проверку».
 */
@Composable
fun VisitDetailsScreen(visitId: String, onBack: () -> Unit, onStartCheck: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as App).container
    val data by container.db.visitDao().observeWithChecklist(visitId).collectAsState(initial = null)

    val visit = data?.visit
    if (visit == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) { Text("Загрузка…") }
        return
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(visit.name, style = MaterialTheme.typography.titleLarge)
        Text(
            statusLabel(visit.status),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(4.dp))
        Text("Объект: ${visit.locationName}", style = MaterialTheme.typography.bodyMedium)
        Text(visit.locationAddress, style = MaterialTheme.typography.bodyMedium)
        Text("План: ${formatMillis(visit.scheduledAt)}", style = MaterialTheme.typography.bodyMedium)
        visit.completedAt?.let {
            Text("Закрыт: ${formatMillis(it)}", style = MaterialTheme.typography.bodyMedium)
        }

        // Маршрут в навигатор (если у объекта заданы координаты)
        val lat = visit.targetLat
        val lon = visit.targetLon
        if (lat != null && lon != null) {
            OutlinedButton(onClick = {
                val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(visit.locationName)})")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }) {
                Text("Построить маршрут")
            }
        }

        if (data!!.answers.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Ответы", style = MaterialTheme.typography.titleMedium)
            data!!.answers.forEach { answer ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        (data!!.mediaFiles.count { it.answerId == answer.id }).toString() + " фото",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(answer.value ?: "—", style = MaterialTheme.typography.bodyMedium)
                        if (answer.hasIssue) {
                            Text(
                                "Проблема" + (answer.comment?.let { ": $it" } ?: ""),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        if (visit.status in setOf("PLANNED", "OVERDUE", "IN_PROGRESS")) {
            Button(onClick = onStartCheck, modifier = Modifier.fillMaxWidth()) {
                Text(if (visit.status == "IN_PROGRESS") "Продолжить проверку" else "Начать проверку")
            }
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Назад") }
    }
}
