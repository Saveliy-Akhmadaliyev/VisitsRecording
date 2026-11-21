package ru.plasmadynamics.visits.mobile.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import kotlinx.serialization.json.Json
import ru.plasmadynamics.visits.mobile.App
import ru.plasmadynamics.visits.mobile.db.TemplateFieldEntity
import ru.plasmadynamics.visits.mobile.db.VisitAnswerEntity
import ru.plasmadynamics.visits.mobile.sync.SyncWorker
import ru.plasmadynamics.visits.mobile.util.LocationHelper
import ru.plasmadynamics.visits.mobile.util.Photos
import java.io.File
import java.util.UUID

/**
 * Прохождение чек-листа: динамическая отрисовка пунктов шаблона, «проблема» +
 * комментарий, фото из камеры, автосохранение каждого действия в Room,
 * «Завершить визит» — валидация, GPS-координата, запуск синхронизации.
 */
@Composable
fun ChecklistScreen(visitId: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as App).container
    val db = container.db
    val scope = rememberCoroutineScope()

    val data by db.visitDao().observeWithChecklist(visitId).collectAsState(initial = null)
    var fields by remember { mutableStateOf<List<TemplateFieldEntity>>(emptyList()) }
    var validationError by remember { mutableStateOf<String?>(null) }

    // Пункты шаблона грузим один раз по templateId визита.
    LaunchedEffect(data?.visit?.templateId) {
        data?.visit?.templateId?.let { templateId ->
            fields = db.templateDao().getById(templateId)?.fields?.sortedBy { it.orderIndex }
                ?: emptyList()
        }
    }

    // Разрешение на геолокацию запрашиваем заранее (для fact-координаты).
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }
    LaunchedEffect(Unit) {
        val needed = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    val visit = data?.visit
    if (visit == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) { Text("Загрузка…") }
        return
    }

    // ---------- Сохранение ответа (автосохранение в Room) ----------

    fun saveAnswer(field: TemplateFieldEntity, transform: (VisitAnswerEntity?) -> VisitAnswerEntity) {
        scope.launch {
            val existing = db.answerDao().find(visitId, field.id)
            db.answerDao().upsert(transform(existing))
            // Первое действие по визиту переводит его в «В работе».
            val current = db.visitDao().get(visitId)
            if (current?.status == "PLANNED") db.visitDao().updateStatus(visitId, "IN_PROGRESS")
        }
    }

    // ---------- Камера ----------

    var cameraTmp by remember { mutableStateOf<File?>(null) }
    var cameraTarget by remember { mutableStateOf<Pair<File, ru.plasmadynamics.visits.mobile.db.MediaFileEntity>?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val tmp = cameraTmp
        val target = cameraTarget
        cameraTmp = null
        cameraTarget = null
        if (ok && tmp != null && target != null) {
            scope.launch {
                runCatching {
                    Photos.compress(tmp, target.first)
                    db.mediaDao().upsert(target.second)
                }.onFailure {
                    target.first.delete()
                    validationError = "Не удалось сохранить фото: ${it.message}"
                }
                tmp.delete()
            }
        } else {
            tmp?.delete()
        }
    }

    fun takePhoto(answerId: String?) {
        val tmp = File(File(context.cacheDir, "camera").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
        val (file, entity) = Photos.newPhoto(context, visitId, answerId)
        cameraTmp = tmp
        cameraTarget = file to entity
        cameraLauncher.launch(
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tmp),
        )
    }

    // ---------- Завершение визита ----------

    fun completeVisit() {
        scope.launch {
            val answers = data?.answers.orEmpty().associateBy { it.fieldId }
            val media = data?.mediaFiles.orEmpty()

            val missing = fields.filter { it.isRequired && answers[it.id]?.value.isNullOrBlank() }
            val needPhoto = fields.filter { f ->
                f.requirePhotoOnIssue && answers[f.id]?.hasIssue == true &&
                    media.none { it.answerId == answers[f.id]?.id }
            }
            if (missing.isNotEmpty() || needPhoto.isNotEmpty()) {
                val parts = mutableListOf<String>()
                if (missing.isNotEmpty()) {
                    parts += "Ответьте на обязательные пункты: " + missing.joinToString { it.title }
                }
                if (needPhoto.isNotEmpty()) {
                    parts += "Добавьте фото по проблемам: " + needPhoto.joinToString { it.title }
                }
                validationError = parts.joinToString("\n")
                return@launch
            }

            val location = LocationHelper.currentLocation(context)
            db.visitDao().complete(
                id = visitId,
                completedAt = System.currentTimeMillis(),
                lat = location?.first,
                lon = location?.second,
            )
            SyncWorker.enqueue(context)
            onDone()
        }
    }

    // ---------- UI ----------

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(visit.name, style = MaterialTheme.typography.titleLarge)
        Text(
            "${visit.locationName} · ${formatMillis(visit.scheduledAt)}",
            style = MaterialTheme.typography.bodySmall,
        )

        fields.forEach { field ->
            FieldCard(
                field = field,
                answer = data?.answers?.find { it.fieldId == field.id },
                photoCount = data?.mediaFiles?.count { it.answerId == data?.answers?.find { a -> a.fieldId == field.id }?.id } ?: 0,
                onValue = { value -> saveAnswer(field) { it?.copy(value = value) ?: VisitAnswerEntity(
                    id = UUID.randomUUID().toString(), visitId = visitId, fieldId = field.id, value = value,
                ) } },
                onIssue = { hasIssue -> saveAnswer(field) { it?.copy(hasIssue = hasIssue) ?: VisitAnswerEntity(
                    id = UUID.randomUUID().toString(), visitId = visitId, fieldId = field.id, hasIssue = hasIssue,
                ) } },
                onComment = { comment -> saveAnswer(field) { it?.copy(comment = comment) ?: VisitAnswerEntity(
                    id = UUID.randomUUID().toString(), visitId = visitId, fieldId = field.id, comment = comment,
                ) } },
                onPhoto = { takePhoto(data?.answers?.find { it.fieldId == field.id }?.id) },
            )
        }

        if (fields.isEmpty()) {
            Text("В шаблоне нет пунктов", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        validationError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        if (visit.status != "COMPLETED") {
            Button(onClick = { completeVisit() }, modifier = Modifier.fillMaxWidth()) {
                Text("Завершить визит")
            }
        }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("К списку") }
    }
}

@Composable
private fun FieldCard(
    field: TemplateFieldEntity,
    answer: VisitAnswerEntity?,
    photoCount: Int,
    onValue: (String) -> Unit,
    onIssue: (Boolean) -> Unit,
    onComment: (String) -> Unit,
    onPhoto: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                field.title + if (field.isRequired) " *" else "",
                style = MaterialTheme.typography.titleSmall,
            )

            when (field.fieldType) {
                "YES_NO" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("YES" to "Да", "NO" to "Нет").forEach { (value, label) ->
                        val selected = answer?.value == value
                        if (selected) {
                            Button(onClick = { onValue(value) }) { Text(label) }
                        } else {
                            OutlinedButton(onClick = { onValue(value) }) { Text(label) }
                        }
                    }
                }

                "RATING_1_5" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { n ->
                        val selected = answer?.value == n.toString()
                        if (selected) {
                            Button(onClick = { onValue(n.toString()) }) { Text(n.toString()) }
                        } else {
                            OutlinedButton(onClick = { onValue(n.toString()) }) { Text(n.toString()) }
                        }
                    }
                }

                "SELECT" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    selectOptions(field).forEach { option ->
                        val selected = answer?.value == option
                        if (selected) {
                            Button(onClick = { onValue(option) }, modifier = Modifier.fillMaxWidth()) { Text(option) }
                        } else {
                            OutlinedButton(onClick = { onValue(option) }, modifier = Modifier.fillMaxWidth()) { Text(option) }
                        }
                    }
                }

                else -> OutlinedTextField( // TEXT
                    value = answer?.value ?: "",
                    onValueChange = onValue,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ответ") },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = answer?.hasIssue == true,
                    onCheckedChange = onIssue,
                )
                Text("Выявлена проблема", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onPhoto) {
                    Text(if (photoCount > 0) "Фото ($photoCount)" else "Фото")
                }
            }

            if (answer?.hasIssue == true) {
                OutlinedTextField(
                    value = answer.comment ?: "",
                    onValueChange = onComment,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Комментарий") },
                )
                if (field.requirePhotoOnIssue && photoCount == 0) {
                    Text(
                        "По проблеме обязательно фото",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun selectOptions(field: TemplateFieldEntity): List<String> = try {
    field.optionsJson?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList()
} catch (e: Exception) {
    emptyList()
}
