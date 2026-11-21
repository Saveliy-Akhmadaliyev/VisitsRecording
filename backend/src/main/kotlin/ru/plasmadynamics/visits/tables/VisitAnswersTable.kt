package ru.plasmadynamics.visits.tables

import org.jetbrains.exposed.sql.Table

/**
 * Ответы на пункты проверки. id генерируется НА ТЕЛЕФОНЕ (UUIDv4) ещё до
 * появления сети — при синхронизации записи двух инспекторов не конфликтуют,
 * а повторная отправка не создаёт дублей (upsert по PK).
 */
object VisitAnswersTable : Table("visit_answers") {
    val id = uuid("id")
    val visitId = uuid("visit_id").references(VisitsTable.id)
    val fieldId = uuid("field_id").references(TemplateFieldsTable.id)
    val value = text("value")
    val hasIssue = bool("has_issue").default(false)
    val comment = text("comment").nullable()

    override val primaryKey = PrimaryKey(id)
}
