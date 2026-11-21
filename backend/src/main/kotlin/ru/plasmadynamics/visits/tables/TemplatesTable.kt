package ru.plasmadynamics.visits.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * Шапка шаблона проверки. Каждая редакция шаблона — отдельная строка
 * (новый UUID, version + 1): уже назначенные визиты продолжают ссылаться
 * на старую версию и не ломаются при правках.
 *
 * origin_id — id самой первой версии: все версии одного шаблона
 * сгруппированы по нему (отклонение от «Сущности для бека.md», см. AGENTS.md).
 *
 * updated_at — курсор для delta-синхронизации с мобильным приложением
 * (отклонение от «Сущности для бека.md», см. AGENTS.md).
 */
object TemplatesTable : Table("templates") {
    val id = uuid("id")
    val originId = uuid("origin_id").references(id)
    val name = varchar("name", 255)
    val description = text("description")
    val version = integer("version")
    val isArchived = bool("is_archived").default(false)
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
