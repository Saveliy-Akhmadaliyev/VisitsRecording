package ru.plasmadynamics.visits.config

/**
 * Конфигурация приложения. Значения берутся из переменных окружения,
 * дефолты совпадают с docker-compose.yml в корне репозитория.
 */
data class AppConfig(
    val http: HttpConfig,
    val database: DatabaseConfig,
) {
    data class HttpConfig(val port: Int)

    data class DatabaseConfig(
        val url: String,
        val user: String,
        val password: String,
    )

    companion object {
        fun fromEnv(): AppConfig = AppConfig(
            http = HttpConfig(
                port = System.getenv("PORT")?.toInt() ?: 8080,
            ),
            database = DatabaseConfig(
                url = System.getenv("DATABASE_URL")
                    ?: "jdbc:postgresql://localhost:5432/visits",
                user = System.getenv("DATABASE_USER") ?: "visits",
                password = System.getenv("DATABASE_PASSWORD") ?: "visits",
            ),
        )
    }
}
