package ru.plasmadynamics.visits.mobile.net

import kotlinx.serialization.Serializable

/**
 * DTO мобильного API — зеркалят backend/.../api/dto/MobileDtos.kt (camelCase).
 * Менять синхронно там и здесь.
 */

// ---------- Presign ----------

@Serializable
data class PresignFileRequest(
    val fileId: String,
    val visitId: String,
    val contentType: String,
)

@Serializable
data class PresignRequest(val files: List<PresignFileRequest>)

@Serializable
data class PresignedUrl(
    val fileId: String,
    val s3Key: String,
    val uploadUrl: String,
)

@Serializable
data class PresignResponse(val urls: List<PresignedUrl>)

// ---------- Sync: запрос (push) ----------

@Serializable
data class SyncRequest(
    // TODO(аутентификация): убрать, inspectorId будет в JWT.
    val inspectorId: String,
    /** serverTime из прошлого ответа; null — первая синхронизация. */
    val lastSyncedAt: String? = null,
    val changes: SyncChanges = SyncChanges(),
)

@Serializable
data class SyncChanges(
    val visits: List<VisitPush> = emptyList(),
    val answers: List<AnswerPush> = emptyList(),
    val mediaFiles: List<MediaPush> = emptyList(),
)

@Serializable
data class VisitPush(
    val id: String,
    val status: String,
    val completedAt: String? = null,
    val factLat: Double? = null,
    val factLon: Double? = null,
)

@Serializable
data class AnswerPush(
    val id: String,
    val visitId: String,
    val fieldId: String,
    val value: String = "",
    val hasIssue: Boolean = false,
    val comment: String? = null,
)

@Serializable
data class MediaPush(
    val id: String,
    val visitId: String,
    val answerId: String? = null,
    val s3Key: String,
    val fileType: String,
    val createdAt: String,
)

// ---------- Sync: ответ (pull) ----------

@Serializable
data class SyncResponse(
    val serverTime: String,
    val updates: SyncUpdates = SyncUpdates(),
)

@Serializable
data class SyncUpdates(
    val visits: List<SyncVisit> = emptyList(),
    val templates: List<SyncTemplate> = emptyList(),
    val locations: List<SyncLocation> = emptyList(),
)

@Serializable
data class SyncVisit(
    val id: String,
    val name: String,
    val templateId: String,
    val locationId: String,
    val scheduledAt: String,
    val completedAt: String? = null,
    val factLat: Double? = null,
    val factLon: Double? = null,
    val status: String, // эффективный статус, OVERDUE вычисляет сервер
    val updatedAt: String,
)

@Serializable
data class SyncTemplate(
    val id: String,
    val name: String,
    val description: String,
    val version: Int,
    val isArchived: Boolean = false,
    val fields: List<SyncField> = emptyList(),
)

@Serializable
data class SyncField(
    val id: String,
    val orderIndex: Int,
    val title: String,
    val fieldType: String,
    val options: List<String>? = null,
    val isRequired: Boolean = false,
    val requirePhotoOnIssue: Boolean = false,
)

@Serializable
data class SyncLocation(
    val id: String,
    val name: String,
    val address: String,
    val targetLat: Double? = null,
    val targetLon: Double? = null,
)

// ---------- DEV: выбор инспектора до появления аутентификации ----------

@Serializable
data class UserOut(
    val id: String,
    val fullName: String,
    val login: String,
    val role: String,
    val isActive: Boolean,
)
