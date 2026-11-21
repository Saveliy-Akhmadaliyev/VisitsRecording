package ru.plasmadynamics.visits.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import ru.plasmadynamics.visits.api.dto.*
import ru.plasmadynamics.visits.domain.UserRole
import ru.plasmadynamics.visits.domain.VisitStatus
import ru.plasmadynamics.visits.tables.*
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Визиты: /api/admin/visits
 * Список с фильтрами (календарь), назначение, перенос, карточка результата, отмена.
 *
 * OVERDUE не хранится: статус вычисляется на лету (PLANNED + scheduled_at < now).
 */
fun Route.visitsRoutes() {
    route("/visits") {

        // Список визитов: ?date_from=&date_to=&status=&inspector_id=
        // status принимает PLANNED/IN_PROGRESS/COMPLETED/CANCELLED и вычисляемый OVERDUE.
        get {
            val qp = call.request.queryParameters
            val dateFrom = qp["date_from"]?.parseInstant()
            val dateTo = qp["date_to"]?.parseInstant()
            val statusParam = qp["status"]
            val inspectorId = qp["inspector_id"]?.let { UUID.fromString(it) }

            val knownStatuses = setOf("PLANNED", "IN_PROGRESS", "COMPLETED", "CANCELLED", "OVERDUE")
            if (statusParam != null && statusParam !in knownStatuses) {
                throw ApiException(HttpStatusCode.BadRequest, "Неизвестный статус: $statusParam")
            }

            val visits = transaction {
                VisitsTable.selectAll().where {
                    val conds = mutableListOf<Op<Boolean>>()
                    dateFrom?.let { conds += VisitsTable.scheduledAt greaterEq it.toOffsetDateTime() }
                    dateTo?.let { conds += VisitsTable.scheduledAt less it.toOffsetDateTime() }
                    inspectorId?.let { conds += VisitsTable.inspectorId eq it }
                    when (statusParam) {
                        "OVERDUE" -> conds += (VisitsTable.status eq VisitStatus.PLANNED) and
                            (VisitsTable.scheduledAt less OffsetDateTime.now())
                        null -> {}
                        else -> conds += VisitsTable.status eq VisitStatus.valueOf(statusParam)
                    }
                    if (conds.isEmpty()) Op.TRUE else conds.reduce { acc, c -> acc and c }
                }
                    .orderBy(VisitsTable.scheduledAt to SortOrder.ASC)
                    .map { it.toVisitOut() }
            }
            call.respond(visits)
        }

        // Назначить визит.
        post {
            val req = call.receive<VisitCreateRequest>()
            val id = UUID.randomUUID()
            transaction {
                val template = TemplatesTable.selectAll().where { TemplatesTable.id eq req.templateId }.singleOrNull()
                    ?: throw ApiException(HttpStatusCode.NotFound, "Шаблон не найден")
                LocationsTable.selectAll().where { LocationsTable.id eq req.locationId }.singleOrNull()
                    ?: throw ApiException(HttpStatusCode.NotFound, "Объект не найден")
                val inspector = UsersTable.selectAll().where { UsersTable.id eq req.inspectorId }.singleOrNull()
                    ?: throw ApiException(HttpStatusCode.NotFound, "Инспектор не найден")
                if (inspector[UsersTable.role] != UserRole.INSPECTOR) {
                    throw ApiException(HttpStatusCode.BadRequest, "Визит можно назначить только инспектору")
                }

                // TODO(аутентификация): брать admin_id из JWT. Пока — первый активный администратор.
                val adminId = UsersTable.selectAll()
                    .where { (UsersTable.role eq UserRole.ADMIN) and (UsersTable.isActive eq true) }
                    .limit(1).singleOrNull()?.get(UsersTable.id)
                    ?: throw ApiException(HttpStatusCode.InternalServerError, "В системе нет активного администратора")

                VisitsTable.insert {
                    it[VisitsTable.id] = id
                    it[VisitsTable.name] = req.name ?: template[TemplatesTable.name]
                    it[VisitsTable.templateId] = req.templateId
                    it[VisitsTable.locationId] = req.locationId
                    it[VisitsTable.adminId] = adminId
                    it[VisitsTable.inspectorId] = req.inspectorId
                    it[VisitsTable.scheduledAt] = req.scheduledAt.toOffsetDateTime()
                    it[VisitsTable.completedAt] = null
                    it[VisitsTable.status] = VisitStatus.PLANNED
                    it[VisitsTable.updatedAt] = OffsetDateTime.now()
                }
            }
            call.respond(HttpStatusCode.Created, visitById(id))
        }

        // Перенести дату или сменить исполнителя (только пока визит PLANNED).
        patch("{id}") {
            val id = call.pathId()
            val req = call.receive<VisitUpdateRequest>()
            val visit = transaction {
                val current = VisitsTable.selectAll().where { VisitsTable.id eq id }.singleOrNull()
                    ?: throw ApiException(HttpStatusCode.NotFound, "Визит не найден")
                if (current[VisitsTable.status] != VisitStatus.PLANNED) {
                    throw ApiException(HttpStatusCode.Conflict, "Изменить можно только запланированный визит")
                }
                if (req.inspectorId != null) {
                    val inspector = UsersTable.selectAll().where { UsersTable.id eq req.inspectorId }.singleOrNull()
                        ?: throw ApiException(HttpStatusCode.NotFound, "Инспектор не найден")
                    if (inspector[UsersTable.role] != UserRole.INSPECTOR) {
                        throw ApiException(HttpStatusCode.BadRequest, "Визит можно назначить только инспектору")
                    }
                }
                VisitsTable.update({ VisitsTable.id eq id }) { stmt ->
                    req.scheduledAt?.let { stmt[scheduledAt] = it.toOffsetDateTime() }
                    req.inspectorId?.let { stmt[inspectorId] = it }
                    stmt[updatedAt] = OffsetDateTime.now()
                }
                visitByIdRow(id)
            }
            call.respond(visit)
        }

        // Карточка визита: ответы инспектора + метаданные фото.
        // TODO(медиа): добавить presigned URL к MinIO для просмотра фото в браузере.
        get("{id}") {
            val id = call.pathId()
            call.respond(transaction {
                val visitRow = VisitsTable.selectAll().where { VisitsTable.id eq id }.singleOrNull()
                    ?: throw ApiException(HttpStatusCode.NotFound, "Визит не найден")

                val answers = (VisitAnswersTable innerJoin TemplateFieldsTable)
                    .selectAll().where { VisitAnswersTable.visitId eq id }
                    .map { row ->
                        VisitAnswerOut(
                            id = row[VisitAnswersTable.id],
                            fieldId = row[VisitAnswersTable.fieldId],
                            fieldTitle = row[TemplateFieldsTable.title],
                            value = row[VisitAnswersTable.value],
                            hasIssue = row[VisitAnswersTable.hasIssue],
                            comment = row[VisitAnswersTable.comment],
                        )
                    }

                val media = MediaFilesTable.selectAll().where { MediaFilesTable.visitId eq id }
                    .map { row ->
                        MediaFileOut(
                            id = row[MediaFilesTable.id],
                            answerId = row[MediaFilesTable.answerId],
                            s3Key = row[MediaFilesTable.s3Key],
                            fileType = row[MediaFilesTable.fileType],
                            createdAt = row[MediaFilesTable.createdAt].toInstant(),
                        )
                    }

                VisitDetailOut(visit = visitRow.toVisitOut(), answers = answers, media = media)
            })
        }

        // Отменить визит (только PLANNED): статус CANCELLED, мобилка уберёт его при синке.
        delete("{id}") {
            val id = call.pathId()
            transaction {
                val current = VisitsTable.selectAll().where { VisitsTable.id eq id }.singleOrNull()
                    ?: throw ApiException(HttpStatusCode.NotFound, "Визит не найден")
                if (current[VisitsTable.status] != VisitStatus.PLANNED) {
                    throw ApiException(HttpStatusCode.Conflict, "Отменить можно только запланированный визит")
                }
                VisitsTable.update({ VisitsTable.id eq id }) { stmt ->
                    stmt[status] = VisitStatus.CANCELLED
                    stmt[updatedAt] = OffsetDateTime.now()
                }
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun visitById(id: UUID): VisitOut = transaction { visitByIdRow(id) }

private fun visitByIdRow(id: UUID): VisitOut =
    VisitsTable.selectAll().where { VisitsTable.id eq id }.single().toVisitOut()

private fun ResultRow.toVisitOut() = VisitOut(
    id = this[VisitsTable.id],
    name = this[VisitsTable.name],
    templateId = this[VisitsTable.templateId],
    locationId = this[VisitsTable.locationId],
    adminId = this[VisitsTable.adminId],
    inspectorId = this[VisitsTable.inspectorId],
    scheduledAt = this[VisitsTable.scheduledAt].toInstant(),
    completedAt = this[VisitsTable.completedAt]?.toInstant(),
    factLat = this[VisitsTable.factLat]?.toDouble(),
    factLon = this[VisitsTable.factLon]?.toDouble(),
    status = effectiveStatus(this[VisitsTable.status], this[VisitsTable.scheduledAt]),
    updatedAt = this[VisitsTable.updatedAt].toInstant(),
)
