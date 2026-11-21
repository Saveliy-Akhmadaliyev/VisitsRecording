package ru.plasmadynamics.visits.config

/**
 * Конфигурация приложения. Значения берутся из переменных окружения,
 * дефолты совпадают с docker-compose.yml в корне репозитория.
 */
data class AppConfig(
    val http: HttpConfig,
    val database: DatabaseConfig,
    val storage: StorageConfig,
) {
    data class HttpConfig(val port: Int)

    data class DatabaseConfig(
        val url: String,
        val user: String,
        val password: String,
    )

    /** MinIO/S3: сами файлы лежат там, бек хранит только метаданные. */
    data class StorageConfig(
        val endpoint: String,
        val accessKey: String,
        val secretKey: String,
        val bucket: String,
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
            storage = StorageConfig(
                endpoint = System.getenv("S3_ENDPOINT") ?: "http://localhost:9000",
                accessKey = System.getenv("S3_ACCESS_KEY") ?: "minioadmin",
                secretKey = System.getenv("S3_SECRET_KEY") ?: "minioadmin",
                bucket = System.getenv("S3_BUCKET") ?: "visits-media",
            ),
        )
    }
}
