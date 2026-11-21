package ru.plasmadynamics.visits.mobile.ui

import ru.plasmadynamics.visits.mobile.db.SyncStatus
import ru.plasmadynamics.visits.mobile.db.VisitEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Общие подписки/форматирование для UI. */

val STATUS_LABELS = mapOf(
    "PLANNED" to "Запланирован",
    "IN_PROGRESS" to "В работе",
    "COMPLETED" to "Завершён",
    "CANCELLED" to "Отменён",
    "OVERDUE" to "Просрочен",
)

fun statusLabel(status: String): String = STATUS_LABELS[status] ?: status

fun formatMillis(millis: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(millis))

/** Визиты, которые ещё предстоит пройти (вкладка «Запланированные»). */
fun VisitEntity.isPlanned(): Boolean =
    status in setOf("PLANNED", "OVERDUE", "IN_PROGRESS") && syncStatus == SyncStatus.SYNCED

/** Завершённые и подтверждённые сервером (вкладка «Завершённые»). */
fun VisitEntity.isDone(): Boolean =
    status == "COMPLETED" && syncStatus == SyncStatus.SYNCED
