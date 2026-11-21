package ru.plasmadynamics.visits

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import ru.plasmadynamics.visits.api.*
import ru.plasmadynamics.visits.api.dto.ErrorResponse
import ru.plasmadynamics.visits.config.AppConfig
import ru.plasmadynamics.visits.db.DatabaseFactory

fun main() {
    val config = AppConfig.fromEnv()
    DatabaseFactory.init(config.database)

    embeddedServer(Netty, port = config.http.port, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }

    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.message ?: "Ошибка"))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Некорректный запрос"))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Некорректное тело запроса"))
        }
    }

    routing {
        get("/health") { call.respondText("OK") }

        // TODO(аутентификация): закрыть /api/admin проверкой JWT и роли ADMIN.
        route("/api/admin") {
            usersRoutes()
            locationsRoutes()
            templatesRoutes()
            visitsRoutes()
            dashboardRoutes()
        }
    }
}
