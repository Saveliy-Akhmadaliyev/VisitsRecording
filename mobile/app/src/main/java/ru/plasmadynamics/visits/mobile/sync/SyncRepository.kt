package ru.plasmadynamics.visits.mobile.sync

import ru.plasmadynamics.visits.mobile.db.AppDatabase
import ru.plasmadynamics.visits.mobile.db.MediaFileEntity
import ru.plasmadynamics.visits.mobile.db.SyncStatus
import ru.plasmadynamics.visits.mobile.db.TemplateEntity
import ru.plasmadynamics.visits.mobile.db.TemplateFieldEntity
import ru.plasmadynamics.visits.mobile.db.UploadStatus
import ru.plasmadynamics.visits.mobile.db.VisitEntity
import ru.plasmadynamics.visits.mobile.db.VisitWithChecklist
import ru.plasmadynamics.visits.mobile.net.AnswerPush
import ru.plasmadynamics.visits.mobile.net.MediaPush
import ru.plasmadynamics.visits.mobile.net.PresignFileRequest
import ru.plasmadynamics.visits.mobile.net.PresignRequest
import ru.plasmadynamics.visits.mobile.net.SyncChanges
import ru.plasmadynamics.visits.mobile.net.SyncRequest
import ru.plasmadynamics.visits.mobile.net.SyncUpdates
import ru.plasmadynamics.visits.mobile.net.VisitPush
import kotlinx.serialization.encodeToString
import ru.plasmadynamics.visits.mobile.net.VisitsApi
import java.io.File
import java.time.Instant

/**
 * Двусторонняя синхронизация — «сердце» приложения (идеи/roadmap_mobile.md, п.2):
 *
 * 1. Загрузка всех незагруженных фото в MinIO по presigned URL.
 * 2. Push: визиты PENDING_SYNC + их ответы + s3Key фото (id с телефона —
 *    повторная отправка не создаёт дублей, сервер делает upsert).
 * 3. Pull: новые назначения/шаблоны/объекты с сервера.
 * 4. Отправленные визиты -> SYNCED, локальные файлы фото удаляются.
 *
 * Конфликты — last-write-wins: локальные несинхронизированные правки не
 * перезаписываются ответом сервера (см. applyUpdates).
 */
class SyncRepository(
    private val api: VisitsApi,
    private val db: AppDatabase,
    private val prefs: SyncPrefs,
) {

    suspend fun syncNow() {
        val inspectorId = prefs.inspectorId ?: error("Инспектор не выбран")

        uploadPendingMedia()

        val pending = db.visitDao().getPending()
        val response = api.sync(
            SyncRequest(
                inspectorId = inspectorId,
                lastSyncedAt = prefs.lastSyncedAt,
                changes = buildChanges(pending),
            ),
        )
        applyUpdates(response.updates)

        db.visitDao().markSynced(pending.map { it.visit.id })
        pending.flatMap { it.mediaFiles }.forEach { File(it.localFilePath).delete() }
        prefs.lastSyncedAt = response.serverTime
    }

    /** Фото уходят в S3 ДО отправки отчёта: отчёт без фото не отправляем. */
    private suspend fun uploadPendingMedia() {
        val files = db.mediaDao().getNotUploaded()
        if (files.isEmpty()) return

        val response = api.presign(
            PresignRequest(files.map { PresignFileRequest(it.id, it.visitId, it.fileType) }),
        )
        val urlsById = response.urls.associateBy { it.fileId }
        for (file in files) {
            val url = urlsById[file.id] ?: error("Сервер не выдал ссылку для файла ${file.id}")
            if (!api.upload(url.uploadUrl, File(file.localFilePath), file.fileType)) {
                error("Не удалось загрузить фото ${file.id} в хранилище")
            }
            db.mediaDao().markUploaded(file.id, url.s3Key)
        }
    }

    private fun buildChanges(pending: List<VisitWithChecklist>): SyncChanges = SyncChanges(
        visits = pending.map { v ->
            VisitPush(
                id = v.visit.id,
                status = v.visit.status,
                completedAt = v.visit.completedAt?.let { Instant.ofEpochMilli(it).toString() },
                factLat = v.visit.factLat,
                factLon = v.visit.factLon,
            )
        },
        answers = pending.flatMap { v ->
            v.answers.map {
                AnswerPush(
                    id = it.id,
                    visitId = it.visitId,
                    fieldId = it.fieldId,
                    value = it.value ?: "",
                    hasIssue = it.hasIssue,
                    comment = it.comment,
                )
            }
        },
        mediaFiles = pending.flatMap { v ->
            v.mediaFiles
                .filter { it.uploadStatus == UploadStatus.UPLOADED }
                .map { MediaPush(
                    id = it.id,
                    visitId = it.visitId,
                    answerId = it.answerId,
                    s3Key = it.s3Key ?: error("У загруженного файла нет s3Key: ${it.id}"),
                    fileType = it.fileType,
                    createdAt = Instant.ofEpochMilli(it.createdAt).toString(),
                ) }
        },
    )

    /** Применение ответа сервера к локальной БД. */
    private suspend fun applyUpdates(updates: SyncUpdates) {
        // Шаблоны: справочник, телефон их только читает.
        db.templateDao().upsertTemplates(
            updates.templates.map { TemplateEntity(it.id, it.name, it.description, it.version) },
        )
        db.templateDao().upsertFields(
            updates.templates.flatMap { t ->
                t.fields.map { f ->
                    TemplateFieldEntity(
                        id = f.id,
                        templateId = t.id,
                        orderIndex = f.orderIndex,
                        title = f.title,
                        fieldType = f.fieldType,
                        optionsJson = f.options?.let { OPTIONS_JSON.encodeToString(it) },
                        isRequired = f.isRequired,
                        requirePhotoOnIssue = f.requirePhotoOnIssue,
                    )
                }
            },
        )

        // Объекты денормализованы в визиты (entities_mobile.md) — применяем при маппинге.
        val locationsById = updates.locations.associateBy { it.id }

        for (v in updates.visits) {
            val local = db.visitDao().get(v.id)

            // Локальные несинхронизированные правки выигрывают (last-write-wins).
            if (local?.syncStatus == SyncStatus.PENDING_SYNC) continue

            if (v.status == "CANCELLED") {
                // «Мягкое удаление»: CANCELLED с сервера — убрать визит с телефона.
                db.mediaDao().getByVisitIds(listOf(v.id)).forEach { File(it.localFilePath).delete() }
                db.visitDao().delete(v.id) // каскад чистит ответы и media_files
                continue
            }

            val loc = locationsById[v.locationId]

            // Инспектор уже начал проверку, а админ перенёс визит: локальный статус
            // и ответы сохраняем, обновляем только метаданные.
            if (local != null && local.status == "IN_PROGRESS" &&
                (v.status == "PLANNED" || v.status == "OVERDUE")
            ) {
                db.visitDao().upsert(
                    listOf(
                        local.copy(
                            name = v.name,
                            scheduledAt = parseIso(v.scheduledAt),
                            locationName = loc?.name ?: local.locationName,
                            locationAddress = loc?.address ?: local.locationAddress,
                            targetLat = loc?.targetLat ?: local.targetLat,
                            targetLon = loc?.targetLon ?: local.targetLon,
                        ),
                    ),
                )
                continue
            }

            db.visitDao().upsert(
                listOf(
                    VisitEntity(
                        id = v.id,
                        name = v.name,
                        templateId = v.templateId,
                        locationName = loc?.name ?: local?.locationName ?: "",
                        locationAddress = loc?.address ?: local?.locationAddress ?: "",
                        targetLat = loc?.targetLat ?: local?.targetLat,
                        targetLon = loc?.targetLon ?: local?.targetLon,
                        scheduledAt = parseIso(v.scheduledAt),
                        completedAt = v.completedAt?.let(::parseIso),
                        factLat = v.factLat,
                        factLon = v.factLon,
                        status = v.status,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                ),
            )
        }
    }

    private fun parseIso(iso: String): Long = Instant.parse(iso).toEpochMilli()

    private companion object {
        private val OPTIONS_JSON = kotlinx.serialization.json.Json
    }
}
