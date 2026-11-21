package ru.plasmadynamics.visits.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * Медиафайлы (фотографии). id генерируется на телефоне; сам файл лежит
 * в MinIO/S3 по ключу s3_key — бек хранит только метаданные. К одному
 * пункту проверки можно прикрепить любое количество фото.
 */
object MediaFilesTable : Table("media_files") {
    val id = uuid("id")
    val visitId = uuid("visit_id").references(VisitsTable.id)
    val answerId = uuid("answer_id").references(VisitAnswersTable.id).nullable()
    val s3Key = varchar("s3_key", 512)
    val fileType = varchar("file_type", 128)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
