package ru.plasmadynamics.visits.tables

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.json.jsonb
import ru.plasmadynamics.visits.domain.FieldType

/**
 * Пункты шаблона. Хранятся отдельными строками (а не одним JSON), чтобы
 * админка могла строить графики по конкретным пунктам (например, средняя
 * оценка по пункту «Чистота зоны»).
 *
 * options — варианты ответа для типа SELECT (JSONB).
 * updated_at — курсор для delta-синхронизации (отклонение от
 * «Сущности для бека.md», см. AGENTS.md).
 */
object TemplateFieldsTable : Table("template_fields") {
    val id = uuid("id")
    val templateId = uuid("template_id").references(TemplatesTable.id)
    val orderIndex = integer("order_index")
    val title = varchar("title", 1024)
    val fieldType = enumerationByName("field_type", 16, FieldType::class)
    val options = jsonb("options", Json, ListSerializer(String.serializer())).nullable()
    val isRequired = bool("is_required").default(false)
    val requirePhotoOnIssue = bool("require_photo_on_issue").default(false)
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
