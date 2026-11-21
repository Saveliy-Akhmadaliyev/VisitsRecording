package ru.plasmadynamics.visits.api.dto

import kotlinx.serialization.Serializable
import ru.plasmadynamics.visits.domain.FieldType
import ru.plasmadynamics.visits.domain.VisitStatus
import java.time.Instant
import java.util.UUID

/**
 * DTO мобильного API (контракт — идеи/mobile_api.md, отклонения — в AGENTS.md):
 * camelCase вместо snake_case, нет is_deleted (его роль играет статус CANCELLED),
 * объекты (locations) — отдельным списком, а не вложены в визит.
 */

// ---------- Presign: прямая загрузка медиа в MinIO/S3 ----------

@Serializable
data class PresignFileRequest(
    @Serializable(with = UUIDSerializer::class) val fileId: UUID,
    @Serializable(with = UUIDSerializer::class) val visitId: UUID,
    val contentType: String,
)

@Serializable
data class PresignRequest(val files: List<PresignFileRequest> = emptyList())

@Serializable
data class PresignedUrl(
    @Serializable(with = UUIDSerializer::class) val fileId: UUID,
    val s3Key: String,
    val uploadUrl: String,
)

@Serializable
data class PresignResponse(val urls: List<PresignedUrl> = emptyList())

// ---------- Sync: запрос (push) ----------

@Serializable
data class SyncRequest(
    // TODO(аутентификация): брать inspectorId из JWT, а не из тела запроса.
    @Serializable(with = UUIDSerializer::class) val inspectorId: UUID,
    /** server_time из ПРОШЛОГО ответа синка (не часы телефона!). null — первая синхронизация. */
    @Serializable(with = InstantSerializer::class) val lastSyncedAt: Instant? = null,
    val changes: SyncChanges = SyncChanges(),
)

@Serializable
data class SyncChanges(
    val visits: List<VisitPush> = emptyList(),
    val answers: List<AnswerPush> = emptyList(),
    val mediaFiles: List<MediaPush> = emptyList(),
)

/** Обновление визита с телефона: статус/время завершения/геометка. */
@Serializable
data class VisitPush(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val status: VisitStatus,
    @Serializable(with = InstantSerializer::class) val completedAt: Instant? = null,
    val factLat: Double? = null,
    val factLon: Double? = null,
)

/** Ответ на пункт шаблона. id генерируется на телефоне до отправки (идемпотентность). */
@Serializable
data class AnswerPush(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val visitId: UUID,
    @Serializable(with = UUIDSerializer::class) val fieldId: UUID,
    val value: String,
    val hasIssue: Boolean = false,
    val comment: String? = null,
)

/** Метаданные загруженного в S3 файла. id генерируется на телефоне. */
@Serializable
data class MediaPush(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val visitId: UUID,
    @Serializable(with = UUIDSerializer::class) val answerId: UUID? = null,
    val s3Key: String,
    val fileType: String,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
)

// ---------- Sync: ответ (pull) ----------

@Serializable
data class SyncResponse(
    /** Сохранить на телефоне и передать в следующий запрос как lastSyncedAt. */
    @Serializable(with = InstantSerializer::class) val serverTime: Instant,
    val updates: SyncUpdates = SyncUpdates(),
)

@Serializable
data class SyncUpdates(
    val visits: List<SyncVisit> = emptyList(),
    val templates: List<SyncTemplate> = emptyList(),
    val locations: List<SyncLocation> = emptyList(),
)

/** Визит для телефона. status — эффективный (OVERDUE вычисляется, как в админке). */
@Serializable
data class SyncVisit(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val name: String,
    @Serializable(with = UUIDSerializer::class) val templateId: UUID,
    @Serializable(with = UUIDSerializer::class) val locationId: UUID,
    @Serializable(with = InstantSerializer::class) val scheduledAt: Instant,
    @Serializable(with = InstantSerializer::class) val completedAt: Instant? = null,
    val factLat: Double? = null,
    val factLon: Double? = null,
    val status: String,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
)

@Serializable
data class SyncTemplate(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val name: String,
    val description: String,
    val version: Int,
    val isArchived: Boolean,
    val fields: List<SyncField> = emptyList(),
)

@Serializable
data class SyncField(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val orderIndex: Int,
    val title: String,
    val fieldType: FieldType,
    val options: List<String>? = null,
    val isRequired: Boolean = false,
    val requirePhotoOnIssue: Boolean = false,
)

@Serializable
data class SyncLocation(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val name: String,
    val address: String,
    val targetLat: Double? = null,
    val targetLon: Double? = null,
)
