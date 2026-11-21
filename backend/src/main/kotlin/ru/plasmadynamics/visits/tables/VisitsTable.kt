package ru.plasmadynamics.visits.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import ru.plasmadynamics.visits.domain.VisitStatus

/**
 * Назначенный визит: кто назначил, кто выполняет, куда и когда ехать,
 * по какому шаблону. updated_at — курсор для синхронизации с мобилкой
 * («визиты инспектора, изменённые после X»).
 */
object VisitsTable : Table("visits") {
    val id = uuid("id")
    val name = varchar("name", 255)
    val templateId = uuid("template_id").references(TemplatesTable.id)
    val locationId = uuid("location_id").references(LocationsTable.id)
    val adminId = uuid("admin_id").references(UsersTable.id)
    val inspectorId = uuid("inspector_id").references(UsersTable.id)
    val scheduledAt = timestampWithTimeZone("scheduled_at")
    val completedAt = timestampWithTimeZone("completed_at").nullable()
    val factLat = decimal("fact_lat", 9, 6).nullable()
    val factLon = decimal("fact_lon", 9, 6).nullable()
    val status = enumerationByName("status", 16, VisitStatus::class)
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
