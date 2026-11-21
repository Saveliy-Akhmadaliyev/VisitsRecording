package ru.plasmadynamics.visits.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import ru.plasmadynamics.visits.domain.VisitStatus
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
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

/** Instant -> OffsetDateTime (UTC) для колонок timestampWithTimeZone в Exposed. */
fun Instant.toOffsetDateTime(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

/**
 * Эффективный статус визита: хранимый, либо вычисленный OVERDUE
 * (PLANNED + scheduled_at < now). OVERDUE нигде не хранится — см. AGENTS.md.
 */
fun effectiveStatus(status: VisitStatus, scheduledAt: OffsetDateTime): String =
    if (status == VisitStatus.PLANNED && scheduledAt.toInstant() < Instant.now()) "OVERDUE" else status.name
