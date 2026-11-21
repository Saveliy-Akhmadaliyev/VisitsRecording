package ru.plasmadynamics.visits.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * Справочник объектов (мест проверок). Вынесен отдельно от визита, чтобы
 * не было опечаток в адресах и чтобы строить аналитику «на каком объекте
 * чаще всего находят проблемы».
 *
 * updated_at — курсор для delta-синхронизации с мобильным приложением
 * (отклонение от «Сущности для бека.md», см. AGENTS.md).
 */
object LocationsTable : Table("locations") {
    val id = uuid("id")
    val name = varchar("name", 255)
    val address = text("address")
    val targetLat = decimal("target_lat", 9, 6).nullable()
    val targetLon = decimal("target_lon", 9, 6).nullable()
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
