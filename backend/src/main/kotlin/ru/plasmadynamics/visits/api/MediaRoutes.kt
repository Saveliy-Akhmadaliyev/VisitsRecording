package ru.plasmadynamics.visits.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.minio.errors.MinioException
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import ru.plasmadynamics.visits.api.dto.*
import ru.plasmadynamics.visits.storage.MediaStorage
import ru.plasmadynamics.visits.tables.VisitsTable
import java.io.IOException

/**
 * /api/mobile/media/presign — presigned PUT-ссылки для прямой загрузки фото
 * в MinIO/S3 (мимо бека). Ключ: visits/{visitId}/{fileId}{ext} — повторный
 * запрос для того же файла выдаёт ту же ссылку на тот же ключ (идемпотентно).
 */
fun Route.mediaRoutes(storage: MediaStorage) {
    post("/media/presign") {
        val req = call.receive<PresignRequest>()
        if (req.files.isEmpty()) {
            throw ApiException(HttpStatusCode.BadRequest, "Список files пуст")
        }

        val urls = transaction {
            req.files.map { file ->
                val visitExists = VisitsTable.selectAll().where { VisitsTable.id eq file.visitId }.any()
                if (!visitExists) {
                    throw ApiException(HttpStatusCode.NotFound, "Визит ${file.visitId} не найден")
                }
                val key = "visits/${file.visitId}/${file.fileId}${extensionFor(file.contentType)}"
                val uploadUrl = try {
                    storage.presignPut(key)
                } catch (e: IOException) {
                    throw ApiException(
                        HttpStatusCode.ServiceUnavailable,
                        "Хранилище медиа (MinIO) недоступно: ${e.message}",
                    )
                } catch (e: MinioException) {
                    throw ApiException(
                        HttpStatusCode.ServiceUnavailable,
                        "Ошибка хранилища медиа (MinIO): ${e.message}",
                    )
                }
                PresignedUrl(
                    fileId = file.fileId,
                    s3Key = key,
                    uploadUrl = uploadUrl,
                )
            }
        }
        call.respond(PresignResponse(urls))
    }
}

/** Расширение файла из content-type: image/jpeg -> .jpg; неизвестное -> без расширения. */
private fun extensionFor(contentType: String): String = when (contentType.lowercase().trim()) {
    "image/jpeg", "image/jpg" -> ".jpg"
    "image/png" -> ".png"
    "image/webp" -> ".webp"
    "image/heic", "image/heif" -> ".heic"
    "video/mp4" -> ".mp4"
    else -> ""
}
