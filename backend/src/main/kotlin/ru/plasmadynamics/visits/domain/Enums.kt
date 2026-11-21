package ru.plasmadynamics.visits.domain

/** Роль пользователя: админ (админка) или инспектор (полевой сотрудник). */
enum class UserRole {
    ADMIN,
    INSPECTOR,
}

/** Тип ответа на пункт шаблона. */
enum class FieldType {
    TEXT,
    RATING_1_5,
    YES_NO,
    SELECT,
}

/** Статус визита. OVERDUE в БД не хранится — вычисляется на лету (PLANNED + scheduled_at < now). */
enum class VisitStatus {
    PLANNED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
}
