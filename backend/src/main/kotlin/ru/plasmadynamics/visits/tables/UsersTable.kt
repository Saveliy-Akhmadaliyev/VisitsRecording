package ru.plasmadynamics.visits.tables

import org.jetbrains.exposed.sql.Table
import ru.plasmadynamics.visits.domain.UserRole

/**
 * Пользователи: админы и инспекторы в одной таблице (единый эндпоинт /login).
 * is_active позволяет блокировать уволенных без удаления истории их отчётов.
 */
object UsersTable : Table("users") {
    val id = uuid("id")
    val fullName = varchar("full_name", 255)
    val login = varchar("login", 64).uniqueIndex("uq_users_login")
    val passwordHash = varchar("password_hash", 255)
    val role = enumerationByName("role", 16, UserRole::class)
    val isActive = bool("is_active").default(true)

    override val primaryKey = PrimaryKey(id)
}
