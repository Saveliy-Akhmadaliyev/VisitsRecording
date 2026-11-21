package ru.plasmadynamics.visits.api

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import ru.plasmadynamics.visits.api.dto.*
import ru.plasmadynamics.visits.domain.VisitStatus
import ru.plasmadynamics.visits.tables.*
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Аналитика для дашборда: /api/admin/dashboard
 * Агрегация на беке — фронтенд не выкачивает тысячи визитов и не считает статистику в браузере.
 */
fun Route.dashboardRoutes() {
    route("/dashboard") {

        // Сводные счётчики для верхних карточек: ?period=week|month|all (по умолчанию month).
        get("/summary") {
            val period = call.request.queryParameters["period"] ?: "month"
            val from = when (period) {
                "week" -> OffsetDateTime.now().minusDays(7)
                "month" -> OffsetDateTime.now().minusDays(30)
                "all" -> null
                else -> throw ApiException(HttpStatusCode.BadRequest, "period должен быть week, month или all")
            }

            call.respond(transaction {
                val range: Op<Boolean> = SqlExpressionBuilder.run {
                    if (from != null) VisitsTable.scheduledAt greaterEq from else Op.TRUE
                }

                val totalVisits = VisitsTable.selectAll().where { range }.count()

                val completedOnTime = VisitsTable.selectAll().where {
                    range and (VisitsTable.status eq VisitStatus.COMPLETED) and
                        (VisitsTable.completedAt lessEq VisitsTable.scheduledAt)
                }.count()

                val overdue = VisitsTable.selectAll().where {
                    range and (VisitsTable.status eq VisitStatus.PLANNED) and
                        (VisitsTable.scheduledAt less OffsetDateTime.now())
                }.count()

                val findings = (VisitAnswersTable innerJoin VisitsTable)
                    .selectAll().where { (VisitAnswersTable.hasIssue eq true) and range }
                    .count()

                DashboardSummary(
                    totalVisits = totalVisits,
                    completedOnTime = completedOnTime,
                    overdue = overdue,
                    findings = findings,
                )
            })
        }

        // Рейтинг инспекторов: закрытых проверок и среднее отклонение от плана.
        get("/inspectors-stats") {
            call.respond(transaction {
                // Явное условие джойна: у visits ДВА внешних ключа на users (admin_id и inspector_id),
                // Exposed не может выбрать сам — падает с IllegalStateException.
                val rows = VisitsTable
                    .join(UsersTable, JoinType.INNER, onColumn = VisitsTable.inspectorId, otherColumn = UsersTable.id)
                    .selectAll().where { VisitsTable.status eq VisitStatus.COMPLETED }
                    .map { row ->
                        val scheduled = row[VisitsTable.scheduledAt].toInstant()
                        val completed = row[VisitsTable.completedAt]!!.toInstant()
                        row[VisitsTable.inspectorId] to (row[UsersTable.fullName] to
                            Duration.between(scheduled, completed).toMinutes())
                    }

                rows.groupBy { it.first }
                    .map { (inspectorId, entries) ->
                        InspectorStat(
                            inspectorId = inspectorId,
                            fullName = entries.first().second.first,
                            completedVisits = entries.size.toLong(),
                            avgCompletionMinutes = entries.map { it.second.second }.average().toLong(),
                        )
                    }
                    .sortedByDescending { it.completedVisits }
            })
        }

        // Топ объектов по количеству проблем (has_issue = true).
        get("/problem-locations") {
            call.respond(transaction {
                (VisitAnswersTable innerJoin VisitsTable innerJoin LocationsTable)
                    .selectAll().where { VisitAnswersTable.hasIssue eq true }
                    .map { row -> row[VisitsTable.locationId] to row[LocationsTable.name] }
                    .groupBy({ it.first }, { it.second })
                    .map { (locationId, names) ->
                        ProblemLocation(locationId = locationId, name = names.first(), findings = names.size.toLong())
                    }
                    .sortedByDescending { it.findings }
                    .take(10)
            })
        }
    }
}
