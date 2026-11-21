package ru.plasmadynamics.visits.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import ru.plasmadynamics.visits.api.dto.*
import ru.plasmadynamics.visits.domain.FieldType
import ru.plasmadynamics.visits.tables.TemplateFieldsTable
import ru.plasmadynamics.visits.tables.TemplatesTable
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Конструктор шаблонов: /api/admin/templates
 *
 * PUT не редактирует строку, а создаёт НОВУЮ версию (version+1, новый UUID,
 * origin_id наследуется) и архивирует старую — назначенные визиты не ломаются.
 */
fun Route.templatesRoutes() {
    route("/templates") {

        // Список актуальных шаблонов (is_archived=false) вместе с пунктами.
        get {
            call.respond(transaction {
                val rows = TemplatesTable.selectAll()
                    .where { TemplatesTable.isArchived eq false }
                    .orderBy(TemplatesTable.name to SortOrder.ASC)
                    .toList()

                val fieldsByTemplate = TemplateFieldsTable.selectAll()
                    .orderBy(TemplateFieldsTable.orderIndex to SortOrder.ASC)
                    .groupBy { it[TemplateFieldsTable.templateId] }

                rows.map { row ->
                    row.toTemplateOut(
                        fieldsByTemplate[row[TemplatesTable.id]].orEmpty().map { it.toFieldDto() }
                    )
                }
            })
        }

        // Детальная структура шаблона (архивные версии тоже читаются — нужны для старых визитов).
        get("{id}") {
            call.respond(templateById(call.pathId()))
        }

        // Создать шаблон (версия 1, origin_id = собственный id).
        post {
            val req = call.receive<TemplateSaveRequest>()
            validateTemplate(req)

            val id = UUID.randomUUID()
            transaction {
                TemplatesTable.insert {
                    it[TemplatesTable.id] = id
                    it[originId] = id
                    it[name] = req.name
                    it[description] = req.description
                    it[version] = 1
                    it[isArchived] = false
                    it[updatedAt] = OffsetDateTime.now()
                }
                insertFields(id, req.fields)
            }
            call.respond(HttpStatusCode.Created, templateById(id))
        }

        // Обновить = новая версия: старая архивируется, пункты копируются заново из запроса.
        put("{id}") {
            val oldId = call.pathId()
            val req = call.receive<TemplateSaveRequest>()
            validateTemplate(req)

            val newId = UUID.randomUUID()
            transaction {
                val old = TemplatesTable.selectAll().where { TemplatesTable.id eq oldId }.singleOrNull()
                    ?: throw ApiException(HttpStatusCode.NotFound, "Шаблон не найден")

                TemplatesTable.insert {
                    it[TemplatesTable.id] = newId
                    it[originId] = old[TemplatesTable.originId]
                    it[name] = req.name
                    it[description] = req.description
                    it[version] = old[TemplatesTable.version] + 1
                    it[isArchived] = false
                    it[updatedAt] = OffsetDateTime.now()
                }
                insertFields(newId, req.fields)

                TemplatesTable.update({ TemplatesTable.id eq oldId }) { stmt ->
                    stmt[isArchived] = true
                    stmt[updatedAt] = OffsetDateTime.now()
                }
            }
            call.respond(HttpStatusCode.Created, templateById(newId))
        }

        // Архивировать: существующие визиты остаются, новые назначать нельзя.
        delete("{id}") {
            val id = call.pathId()
            transaction {
                val updated = TemplatesTable.update({ TemplatesTable.id eq id }) { stmt ->
                    stmt[isArchived] = true
                    stmt[updatedAt] = OffsetDateTime.now()
                }
                if (updated == 0) throw ApiException(HttpStatusCode.NotFound, "Шаблон не найден")
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun validateTemplate(req: TemplateSaveRequest) {
    if (req.name.isBlank()) throw ApiException(HttpStatusCode.BadRequest, "Название шаблона не может быть пустым")
    if (req.fields.isEmpty()) throw ApiException(HttpStatusCode.BadRequest, "Шаблон должен содержать хотя бы один пункт")
    req.fields.forEach { f ->
        if (f.title.isBlank()) throw ApiException(HttpStatusCode.BadRequest, "Текст пункта не может быть пустым")
        if (f.fieldType == FieldType.SELECT && f.options.isNullOrEmpty()) {
            throw ApiException(HttpStatusCode.BadRequest, "Для пункта «${f.title}» с выбором нужно задать варианты ответа")
        }
    }
}

private fun insertFields(templateId: UUID, fields: List<TemplateFieldDto>) {
    fields.forEach { f ->
        TemplateFieldsTable.insert {
            it[TemplateFieldsTable.id] = UUID.randomUUID()
            it[TemplateFieldsTable.templateId] = templateId
            it[orderIndex] = f.orderIndex
            it[title] = f.title
            it[fieldType] = f.fieldType
            it[options] = if (f.fieldType == FieldType.SELECT) f.options else null
            it[isRequired] = f.isRequired
            it[requirePhotoOnIssue] = f.requirePhotoOnIssue
            it[updatedAt] = OffsetDateTime.now()
        }
    }
}

private fun templateById(id: UUID): TemplateOut = transaction {
    val row = TemplatesTable.selectAll().where { TemplatesTable.id eq id }.singleOrNull()
        ?: throw ApiException(HttpStatusCode.NotFound, "Шаблон не найден")

    val fields = TemplateFieldsTable.selectAll()
        .where { TemplateFieldsTable.templateId eq id }
        .orderBy(TemplateFieldsTable.orderIndex to SortOrder.ASC)
        .map { it.toFieldDto() }

    row.toTemplateOut(fields)
}

private fun ResultRow.toTemplateOut(fields: List<TemplateFieldDto>) = TemplateOut(
    id = this[TemplatesTable.id],
    originId = this[TemplatesTable.originId],
    name = this[TemplatesTable.name],
    description = this[TemplatesTable.description],
    version = this[TemplatesTable.version],
    isArchived = this[TemplatesTable.isArchived],
    fields = fields,
)

private fun ResultRow.toFieldDto() = TemplateFieldDto(
    id = this[TemplateFieldsTable.id],
    orderIndex = this[TemplateFieldsTable.orderIndex],
    title = this[TemplateFieldsTable.title],
    fieldType = this[TemplateFieldsTable.fieldType],
    options = this[TemplateFieldsTable.options],
    isRequired = this[TemplateFieldsTable.isRequired],
    requirePhotoOnIssue = this[TemplateFieldsTable.requirePhotoOnIssue],
)
