package ru.plasmadynamics.visits.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import ru.plasmadynamics.visits.api.dto.*
import ru.plasmadynamics.visits.tables.LocationsTable
import ru.plasmadynamics.visits.tables.VisitsTable
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Справочник объектов: /api/admin/locations
 * Удаление запрещено, если по объекту уже есть визиты (409).
 */
fun Route.locationsRoutes() {
    route("/locations") {

        get {
            call.respond(transaction {
                LocationsTable.selectAll()
                    .orderBy(LocationsTable.name to SortOrder.ASC)
                    .map { it.toLocationOut() }
            })
        }

        post {
            val req = call.receive<LocationSaveRequest>()
            if (req.name.isBlank()) throw ApiException(HttpStatusCode.BadRequest, "Название не может быть пустым")
            if (req.address.isBlank()) throw ApiException(HttpStatusCode.BadRequest, "Адрес не может быть пустым")

            val id = UUID.randomUUID()
            transaction {
                LocationsTable.insert {
                    it[LocationsTable.id] = id
                    it[name] = req.name
                    it[address] = req.address
                    it[targetLat] = req.targetLat?.let { d -> BigDecimal.valueOf(d) }
                    it[targetLon] = req.targetLon?.let { d -> BigDecimal.valueOf(d) }
                    it[updatedAt] = OffsetDateTime.now()
                }
            }
            call.respond(HttpStatusCode.Created, locationById(id))
        }

        put("{id}") {
            val id = call.pathId()
            val req = call.receive<LocationSaveRequest>()
            val location = transaction {
                val updated = LocationsTable.update({ LocationsTable.id eq id }) { stmt ->
                    stmt[name] = req.name
                    stmt[address] = req.address
                    stmt[targetLat] = req.targetLat?.let { d -> BigDecimal.valueOf(d) }
                    stmt[targetLon] = req.targetLon?.let { d -> BigDecimal.valueOf(d) }
                    stmt[updatedAt] = OffsetDateTime.now()
                }
                if (updated == 0) throw ApiException(HttpStatusCode.NotFound, "Объект не найден")
                LocationsTable.selectAll().where { LocationsTable.id eq id }.single().toLocationOut()
            }
            call.respond(location)
        }

        delete("{id}") {
            val id = call.pathId()
            transaction {
                val hasVisits = VisitsTable.selectAll().where { VisitsTable.locationId eq id }.limit(1).any()
                if (hasVisits) throw ApiException(HttpStatusCode.Conflict, "По объекту уже есть визиты — удаление запрещено")
                val deleted = LocationsTable.deleteWhere { SqlExpressionBuilder.run { LocationsTable.id eq id } }
                if (deleted == 0) throw ApiException(HttpStatusCode.NotFound, "Объект не найден")
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun locationById(id: UUID): LocationOut = transaction {
    LocationsTable.selectAll().where { LocationsTable.id eq id }.single().toLocationOut()
}

private fun ResultRow.toLocationOut() = LocationOut(
    id = this[LocationsTable.id],
    name = this[LocationsTable.name],
    address = this[LocationsTable.address],
    targetLat = this[LocationsTable.targetLat]?.toDouble(),
    targetLon = this[LocationsTable.targetLon]?.toDouble(),
)
