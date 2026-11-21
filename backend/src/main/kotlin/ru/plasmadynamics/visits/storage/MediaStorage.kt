package ru.plasmadynamics.visits.storage

import io.minio.BucketExistsArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.http.Method
import ru.plasmadynamics.visits.config.AppConfig
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Медиафайлы (фото) лежат в MinIO/S3 — бек хранит только метаданные (media_files).
 * Телефон загружает файлы напрямую в S3 по presigned PUT-ссылкам, которые
 * выдаёт этот класс (минимум трафика через бек).
 */
class MediaStorage(config: AppConfig.StorageConfig) {

    private val bucket = config.bucket
    private val client = MinioClient.builder()
        .endpoint(config.endpoint)
        .credentials(config.accessKey, config.secretKey)
        .build()

    private val bucketReady = AtomicBoolean(false)

    /**
     * Presigned URL для прямой загрузки файла телефоном (HTTP PUT).
     * Живёт 30 минут — хватает на плохую связь. Подпись не привязана к
     * Content-Type, поэтому клиент может слать любой (MinIO не проверяет
     * неподписанные заголовки).
     */
    fun presignPut(key: String): String {
        ensureBucket()
        return client.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT)
                .bucket(bucket)
                .`object`(key)
                .expiry(EXPIRY_MINUTES, TimeUnit.MINUTES)
                .build()
        )
    }

    /** Создаёт bucket при первом обращении (однократно, дальше — кэш). */
    private fun ensureBucket() {
        if (bucketReady.get()) return
        synchronized(this) {
            if (bucketReady.get()) return
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
            }
            bucketReady.set(true)
        }
    }

    private companion object {
        const val EXPIRY_MINUTES = 30
    }
}
