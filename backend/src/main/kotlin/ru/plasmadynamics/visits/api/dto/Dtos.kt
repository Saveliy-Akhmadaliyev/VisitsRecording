package ru.plasmadynamics.visits.api.dto

import kotlinx.serialization.Serializable
import ru.plasmadynamics.visits.domain.FieldType
import ru.plasmadynamics.visits.domain.UserRole
import java.time.Instant
import java.util.UUID

// ---------- Пользователи ----------

@Serializable
data class UserOut(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val fullName: String,
    val login: String,
    val role: UserRole,
    val isActive: Boolean,
)

@Serializable
data class UserCreateRequest(
    val fullName: String,
    val login: String,
    val password: String,
    val role: UserRole,
)

@Serializable
data class UserUpdateRequest(
    val fullName: String? = null,
    val isActive: Boolean? = null,
)

// ---------- Объекты ----------

@Serializable
data class LocationOut(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val name: String,
    val address: String,
    val targetLat: Double? = null,
    val targetLon: Double? = null,
)

@Serializable
data class LocationSaveRequest(
    val name: String,
    val address: String,
    val targetLat: Double? = null,
    val targetLon: Double? = null,
)

// ---------- Шаблоны ----------

@Serializable
data class TemplateFieldDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID? = null,
    val orderIndex: Int,
    val title: String,
    val fieldType: FieldType,
    val options: List<String>? = null,
    val isRequired: Boolean = false,
    val requirePhotoOnIssue: Boolean = false,
)

@Serializable
data class TemplateOut(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val originId: UUID,
    val name: String,
    val description: String,
    val version: Int,
    val isArchived: Boolean,
    val fields: List<TemplateFieldDto> = emptyList(),
)

@Serializable
data class TemplateSaveRequest(
    val name: String,
    val description: String,
    val fields: List<TemplateFieldDto>,
)

// ---------- Визиты ----------

/** status — эффективный статус: хранимый или вычисленный OVERDUE (PLANNED + scheduled_at < now). */
@Serializable
data class VisitOut(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val name: String,
    @Serializable(with = UUIDSerializer::class) val templateId: UUID,
    @Serializable(with = UUIDSerializer::class) val locationId: UUID,
    @Serializable(with = UUIDSerializer::class) val adminId: UUID,
    @Serializable(with = UUIDSerializer::class) val inspectorId: UUID,
    @Serializable(with = InstantSerializer::class) val scheduledAt: Instant,
    @Serializable(with = InstantSerializer::class) val completedAt: Instant? = null,
    val factLat: Double? = null,
    val factLon: Double? = null,
    val status: String,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
)

@Serializable
data class VisitCreateRequest(
    @Serializable(with = UUIDSerializer::class) val templateId: UUID,
    @Serializable(with = UUIDSerializer::class) val locationId: UUID,
    @Serializable(with = UUIDSerializer::class) val inspectorId: UUID,
    @Serializable(with = InstantSerializer::class) val scheduledAt: Instant,
    val name: String? = null,
)

@Serializable
data class VisitUpdateRequest(
    @Serializable(with = InstantSerializer::class) val scheduledAt: Instant? = null,
    @Serializable(with = UUIDSerializer::class) val inspectorId: UUID? = null,
)

@Serializable
data class VisitAnswerOut(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val fieldId: UUID,
    val fieldTitle: String,
    val value: String,
    val hasIssue: Boolean,
    val comment: String? = null,
)

@Serializable
data class MediaFileOut(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val answerId: UUID? = null,
    val s3Key: String,
    val fileType: String,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
)

/** Карточка визита: сам визит + ответы инспектора + метаданные фото. */
@Serializable
data class VisitDetailOut(
    val visit: VisitOut,
    val answers: List<VisitAnswerOut>,
    val media: List<MediaFileOut>,
)

// ---------- Дашборд ----------

@Serializable
data class DashboardSummary(
    val totalVisits: Long,
    val completedOnTime: Long,
    val overdue: Long,
    val findings: Long,
)

@Serializable
data class InspectorStat(
    @Serializable(with = UUIDSerializer::class) val inspectorId: UUID,
    val fullName: String,
    val completedVisits: Long,
    /** Среднее отклонение от плана в минутах: < 0 — закрывает раньше срока. */
    val avgCompletionMinutes: Long? = null,
)

@Serializable
data class ProblemLocation(
    @Serializable(with = UUIDSerializer::class) val locationId: UUID,
    val name: String,
    val findings: Long,
)

// ---------- Прочее ----------

@Serializable
data class ErrorResponse(val error: String)
