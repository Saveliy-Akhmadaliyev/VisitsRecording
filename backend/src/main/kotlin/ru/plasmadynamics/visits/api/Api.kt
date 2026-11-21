package ru.plasmadynamics.visits.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.UUID

/** Ошибка API с HTTP-статусом; обрабатывается в StatusPages (см. Application.kt). */
class ApiException(val status: HttpStatusCode, message: String) : RuntimeException(message)

/** UUID из пути; некорректный формат -> 400 через IllegalArgumentException. */
fun ApplicationCall.pathId(): UUID =
    parameters["id"]?.let { UUID.fromString(it) }
        ?: throw ApiException(HttpStatusCode.BadRequest, "Некорректный id")

/** Парсинг времени из query-параметра: принимает и "...Z", и "...+03:00". */
fun String.parseInstant(): Instant = try {
    Instant.parse(this)
} catch (e: DateTimeParseException) {
    try {
        OffsetDateTime.parse(this).toInstant()
    } catch (e2: DateTimeParseException) {
        throw ApiException(HttpStatusCode.BadRequest, "Некорректный формат времени: $this (ожидается ISO-8601)")
    }
}
