package ru.plasmadynamics.visits.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import ru.plasmadynamics.visits.api.dto.*
import ru.plasmadynamics.visits.domain.VisitStatus
import ru.plasmadynamics.visits.tables.*
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/**
 * /api/mobile/sync — единый эндпоинт двусторонней синхронизации: push + pull
 * за один запрос (экономит батарею и соединения при плохой связи).
 *
 * Push: upsert по PK — id генерируются на телефоне ДО отправки, повторная
 * отправка при обрыве сети не создаёт дублей. Если визит в БД уже COMPLETED,
 * входящий статус игнорируется (обновляются только метаданные) — защита от
 * отката завершённого визита при гонке запросов.
 *
 * Pull: всё, что изменилось после lastSyncedAt. lastSyncedAt — это serverTime
 * из ПРОШЛОГО ответа (часам телефона доверять нельзя). null — первая синхро-
 * низация, сервер отдаёт все визиты инспектора и весь справочник.
 *
 * CANCELLED = «мягкое удаление» (роль is_deleted из mobile_api.md): телефон
 * получает визит со статусом CANCELLED и убирает его из локального списка.
 */
fun Route.mobileRoutes() {
    post("/sync") {
        val req = call.receive<SyncRequest>()

        val response = transaction {
            applyVisitPushes(req.changes.visits)
            applyAnswerPushes(req.changes.answers)
            applyMediaPushes(req.changes.mediaFiles)

            // serverTime фиксируем ПОСЛЕ push: записи этого же запроса получат
            // updatedAt < serverTime и не вернутся повторно следующим pull'ом.
            val serverTime = Instant.now()

            val visits = pullVisits(req.inspectorId, req.lastSyncedAt)
            val templateIds = visits.map { it.templateId }.distinct()
            val locationIds = visits.map { it.locationId }.distinct()

            SyncResponse(
                serverTime = serverTime,
                updates = SyncUpdates(
                    visits = visits,
                    templates = pullTemplates(templateIds, req.lastSyncedAt),
                    locations = pullLocations(locationIds, req.lastSyncedAt),
                ),
            )
        }
        call.respond(response)
    }
}

// ---------- Push ----------

private fun applyVisitPushes(pushes: List<VisitPush>) {
    for (push in pushes) {
        val current = VisitsTable.selectAll().where { VisitsTable.id eq push.id }.singleOrNull()
            ?: throw ApiException(HttpStatusCode.NotFound, "Визит ${push.id} не найден")
        // Защита от отката: завершённый на сервере визит остаётся COMPLETED.
        val status =
            if (current[VisitsTable.status] == VisitStatus.COMPLETED) VisitStatus.COMPLETED else push.status
        VisitsTable.update({ VisitsTable.id eq push.id }) { stmt ->
            stmt[VisitsTable.status] = status
            push.completedAt?.let { stmt[VisitsTable.completedAt] = it.toOffsetDateTime() }
            push.factLat?.let { stmt[VisitsTable.factLat] = it.toBigDecimal() }
            push.factLon?.let { stmt[VisitsTable.factLon] = it.toBigDecimal() }
            stmt[VisitsTable.updatedAt] = OffsetDateTime.now()
        }
    }
}

private fun applyAnswerPushes(pushes: List<AnswerPush>) {
    for (push in pushes) {
        requireVisit(push.visitId)
        TemplateFieldsTable.selectAll().where { TemplateFieldsTable.id eq push.fieldId }.singleOrNull()
            ?: throw ApiException(HttpStatusCode.NotFound, "Пункт шаблона ${push.fieldId} не найден")
        VisitAnswersTable.upsert {
            it[VisitAnswersTable.id] = push.id
            it[VisitAnswersTable.visitId] = push.visitId
            it[VisitAnswersTable.fieldId] = push.fieldId
            it[VisitAnswersTable.value] = push.value
            it[VisitAnswersTable.hasIssue] = push.hasIssue
            it[VisitAnswersTable.comment] = push.comment
        }
    }
}

private fun applyMediaPushes(pushes: List<MediaPush>) {
    for (push in pushes) {
        requireVisit(push.visitId)
        MediaFilesTable.upsert {
            it[MediaFilesTable.id] = push.id
            it[MediaFilesTable.visitId] = push.visitId
            it[MediaFilesTable.answerId] = push.answerId
            it[MediaFilesTable.s3Key] = push.s3Key
            it[MediaFilesTable.fileType] = push.fileType
            it[MediaFilesTable.createdAt] = push.createdAt.toOffsetDateTime()
        }
    }
}

private fun requireVisit(visitId: UUID) {
    VisitsTable.selectAll().where { VisitsTable.id eq visitId }.singleOrNull()
        ?: throw ApiException(HttpStatusCode.NotFound, "Визит $visitId не найден")
}

// ---------- Pull ----------

private fun pullVisits(inspectorId: UUID, lastSyncedAt: Instant?): List<SyncVisit> =
    VisitsTable.selectAll().where {
        val conds = mutableListOf<Op<Boolean>>()
        conds += VisitsTable.inspectorId eq inspectorId
        lastSyncedAt?.let { conds += VisitsTable.updatedAt greater it.toOffsetDateTime() }
        conds.reduce { acc, c -> acc and c }
    }
        .orderBy(VisitsTable.scheduledAt to SortOrder.ASC)
        .map { it.toSyncVisit() }

/**
 * Шаблоны: все версии шаблонов, на которые ссылаются отданные визиты (новый
 * визит может ссылаться на старую версию), плюс всё, что менялось после
 * lastSyncedAt (например, старую версию заархивировали при редактировании).
 */
private fun pullTemplates(visitTemplateIds: List<UUID>, lastSyncedAt: Instant?): List<SyncTemplate> {
    val templates = TemplatesTable.selectAll().where {
        when {
            lastSyncedAt == null -> Op.TRUE // первая синхронизация — весь справочник
            visitTemplateIds.isEmpty() -> TemplatesTable.updatedAt greater lastSyncedAt.toOffsetDateTime()
            else -> (TemplatesTable.id inList visitTemplateIds) or
                (TemplatesTable.updatedAt greater lastSyncedAt.toOffsetDateTime())
        }
    }.map { row ->
        SyncTemplate(
            id = row[TemplatesTable.id],
            name = row[TemplatesTable.name],
            description = row[TemplatesTable.description],
            version = row[TemplatesTable.version],
            isArchived = row[TemplatesTable.isArchived],
        )
    }
    if (templates.isEmpty()) return emptyList()

    val fieldsByTemplate = TemplateFieldsTable.selectAll().where {
        TemplateFieldsTable.templateId inList templates.map { it.id }
    }
        .groupBy({ it[TemplateFieldsTable.templateId] }, { it.toSyncField() })

    return templates.map { t -> t.copy(fields = fieldsByTemplate[t.id] ?: emptyList()) }
}

/** Объекты: на которые ссылаются отданные визиты, плюс изменённые после lastSyncedAt. */
private fun pullLocations(visitLocationIds: List<UUID>, lastSyncedAt: Instant?): List<SyncLocation> =
    LocationsTable.selectAll().where {
        when {
            lastSyncedAt == null -> Op.TRUE
            visitLocationIds.isEmpty() -> LocationsTable.updatedAt greater lastSyncedAt.toOffsetDateTime()
            else -> (LocationsTable.id inList visitLocationIds) or
                (LocationsTable.updatedAt greater lastSyncedAt.toOffsetDateTime())
        }
    }.map { row ->
        SyncLocation(
            id = row[LocationsTable.id],
            name = row[LocationsTable.name],
            address = row[LocationsTable.address],
            targetLat = row[LocationsTable.targetLat]?.toDouble(),
            targetLon = row[LocationsTable.targetLon]?.toDouble(),
        )
    }

// ---------- Маппинг строк ----------

private fun ResultRow.toSyncVisit() = SyncVisit(
    id = this[VisitsTable.id],
    name = this[VisitsTable.name],
    templateId = this[VisitsTable.templateId],
    locationId = this[VisitsTable.locationId],
    scheduledAt = this[VisitsTable.scheduledAt].toInstant(),
    completedAt = this[VisitsTable.completedAt]?.toInstant(),
    factLat = this[VisitsTable.factLat]?.toDouble(),
    factLon = this[VisitsTable.factLon]?.toDouble(),
    status = effectiveStatus(this[VisitsTable.status], this[VisitsTable.scheduledAt]),
    updatedAt = this[VisitsTable.updatedAt].toInstant(),
)

private fun ResultRow.toSyncField() = SyncField(
    id = this[TemplateFieldsTable.id],
    orderIndex = this[TemplateFieldsTable.orderIndex],
    title = this[TemplateFieldsTable.title],
    fieldType = this[TemplateFieldsTable.fieldType],
    options = this[TemplateFieldsTable.options],
    isRequired = this[TemplateFieldsTable.isRequired],
    requirePhotoOnIssue = this[TemplateFieldsTable.requirePhotoOnIssue],
)
