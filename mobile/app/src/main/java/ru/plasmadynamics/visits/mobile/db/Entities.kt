package ru.plasmadynamics.visits.mobile.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Локальная БД (Room) — offline-first ядро. Схема — идеи/entities_mobile.md:
 * справочники (шаблоны) заполняются только с сервера, рабочие данные (визиты,
 * ответы, очередь фото) — локально, со статусами синхронизации.
 */

object SyncStatus {
    const val SYNCED = "SYNCED"
    const val PENDING_SYNC = "PENDING_SYNC"
}

object UploadStatus {
    const val NOT_UPLOADED = "NOT_UPLOADED"
    const val UPLOADED = "UPLOADED_TO_S3"
}

// ---------- Справочник: шаблоны (только чтение, приходит с сервера) ----------

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String, // UUID с сервера
    val name: String,
    val description: String?,
    val version: Int,
)

@Entity(
    tableName = "template_fields",
    foreignKeys = [
        ForeignKey(
            entity = TemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("templateId")],
)
data class TemplateFieldEntity(
    @PrimaryKey val id: String, // UUID пункта
    val templateId: String,
    val orderIndex: Int,
    val title: String,
    val fieldType: String, // TEXT / RATING_1_5 / YES_NO / SELECT
    val optionsJson: String?, // варианты SELECT в виде JSON-массива
    val isRequired: Boolean,
    val requirePhotoOnIssue: Boolean,
)

// ---------- Рабочие данные ----------

@Entity(tableName = "visits")
data class VisitEntity(
    @PrimaryKey val id: String, // UUID с сервера
    val name: String,
    val templateId: String,

    // Денормализованные данные объекта (приходят с сервера) — без лишних джойнов
    val locationName: String,
    val locationAddress: String,
    val targetLat: Double?,
    val targetLon: Double?,

    val scheduledAt: Long, // epoch millis
    val completedAt: Long? = null,

    // Фактические координаты при закрытии визита
    val factLat: Double? = null,
    val factLon: Double? = null,

    // Бизнес-статус: PLANNED / IN_PROGRESS / COMPLETED / CANCELLED / OVERDUE (эффективный, с сервера)
    val status: String,

    // Технический статус для SyncWorker
    val syncStatus: String = SyncStatus.SYNCED,
)

@Entity(
    tableName = "visit_answers",
    foreignKeys = [
        ForeignKey(
            entity = VisitEntity::class,
            parentColumns = ["id"],
            childColumns = ["visitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // Один ответ на один пункт в рамках визита
    indices = [Index(value = ["visitId", "fieldId"], unique = true)],
)
data class VisitAnswerEntity(
    @PrimaryKey val id: String, // UUIDv4, генерируется на телефоне до отправки
    val visitId: String,
    val fieldId: String,
    val value: String? = null, // текст, цифра оценки или YES / NO
    val hasIssue: Boolean = false,
    val comment: String? = null,
)

@Entity(
    tableName = "media_files",
    foreignKeys = [
        ForeignKey(
            entity = VisitEntity::class,
            parentColumns = ["id"],
            childColumns = ["visitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("visitId"), Index("answerId")],
)
data class MediaFileEntity(
    @PrimaryKey val id: String, // UUIDv4, генерируется при съёмке
    val visitId: String,
    val answerId: String?, // к какому ответу прикреплено фото
    val localFilePath: String, // filesDir/pending_photos/{uuid}.jpg
    val s3Key: String? = null, // заполняется после presign
    val fileType: String = "image/jpeg",
    val createdAt: Long,
    val uploadStatus: String = UploadStatus.NOT_UPLOADED,
)

// ---------- Проекции для UI (один Flow на экран) ----------

data class VisitWithChecklist(
    @Embedded val visit: VisitEntity,
    @Relation(parentColumn = "id", entityColumn = "visitId")
    val answers: List<VisitAnswerEntity>,
    @Relation(parentColumn = "id", entityColumn = "visitId")
    val mediaFiles: List<MediaFileEntity>,
)

data class TemplateWithFields(
    @Embedded val template: TemplateEntity,
    @Relation(parentColumn = "id", entityColumn = "templateId")
    val fields: List<TemplateFieldEntity>,
)
