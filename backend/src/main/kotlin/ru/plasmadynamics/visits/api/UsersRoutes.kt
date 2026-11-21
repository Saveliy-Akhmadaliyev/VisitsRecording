package ru.plasmadynamics.visits.api

import at.favre.lib.crypto.bcrypt.BCrypt
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import ru.plasmadynamics.visits.api.dto.*
import ru.plasmadynamics.visits.domain.UserRole
import ru.plasmadynamics.visits.tables.UsersTable
import java.util.UUID

/**
 * Управление пользователями: /api/admin/users
 * Список (с фильтром по роли), создание, редактирование/деактивация.
 */
fun Route.usersRoutes() {
    route("/users") {

        // Список сотрудников: для таблицы персонала и выпадающего списка при назначении визита.
        get {
            val role = call.request.queryParameters["role"]?.let { UserRole.valueOf(it) }
            val users = transaction {
                UsersTable.selectAll()
                    .where { if (role == null) Op.TRUE else UsersTable.role eq role }
                    .orderBy(UsersTable.fullName to SortOrder.ASC)
                    .map { it.toUserOut() }
            }
            call.respond(users)
        }

        // Создать сотрудника (пароль хэшируется bcrypt'ом).
        post {
            val req = call.receive<UserCreateRequest>()
            if (req.fullName.isBlank()) throw ApiException(HttpStatusCode.BadRequest, "ФИО не может быть пустым")
            if (req.login.isBlank()) throw ApiException(HttpStatusCode.BadRequest, "Логин не может быть пустым")
            if (req.password.length < 6) throw ApiException(HttpStatusCode.BadRequest, "Пароль должен быть не короче 6 символов")

            val passwordHash = BCrypt.withDefaults().hashToString(10, req.password.toCharArray())
            val user = transaction {
                val loginTaken = UsersTable.selectAll().where { UsersTable.login eq req.login }.limit(1).any()
                if (loginTaken) throw ApiException(HttpStatusCode.Conflict, "Логин уже занят")

                val id = UUID.randomUUID()
                UsersTable.insert {
                    it[UsersTable.id] = id
                    it[UsersTable.fullName] = req.fullName
                    it[UsersTable.login] = req.login
                    it[UsersTable.passwordHash] = passwordHash
                    it[UsersTable.role] = req.role
                    it[UsersTable.isActive] = true
                }
                UsersTable.selectAll().where { UsersTable.id eq id }.single().toUserOut()
            }
            call.respond(HttpStatusCode.Created, user)
        }

        // Обновить данные или деактивировать (is_active=false вместо удаления — история сохраняется).
        patch("{id}") {
            val id = call.pathId()
            val req = call.receive<UserUpdateRequest>()
            val user = transaction {
                val updated = UsersTable.update({ UsersTable.id eq id }) { stmt ->
                    req.fullName?.let { stmt[UsersTable.fullName] = it }
                    req.isActive?.let { stmt[UsersTable.isActive] = it }
                }
                if (updated == 0) throw ApiException(HttpStatusCode.NotFound, "Пользователь не найден")
                UsersTable.selectAll().where { UsersTable.id eq id }.single().toUserOut()
            }
            call.respond(user)
        }
    }
}

private fun ResultRow.toUserOut() = UserOut(
    id = this[UsersTable.id],
    fullName = this[UsersTable.fullName],
    login = this[UsersTable.login],
    role = this[UsersTable.role],
    isActive = this[UsersTable.isActive],
)
