package ru.plasmadynamics.visits.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import ru.plasmadynamics.visits.config.AppConfig

/**
 * Инициализация БД: пул соединений (HikariCP) → миграции (Flyway) → Exposed.
 * Миграции применяются автоматически при каждом старте — ручных шагов нет.
 */
object DatabaseFactory {

    fun init(config: AppConfig.DatabaseConfig) {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        })

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        Database.connect(dataSource)
    }
}
