package ru.plasmadynamics.visits.mobile.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ru.plasmadynamics.visits.mobile.db.MediaFileEntity
import java.io.File
import java.util.UUID

/**
 * Работа с фото: снимок с камеры -> сжатие (до 1600px по длинной стороне,
 * JPEG 80%) -> закрытая папка filesDir/pending_photos/{uuid}.jpg.
 * Файл живёт там, пока SyncWorker не подтвердит отправку (потом удаляется).
 */
object Photos {

    private const val DIR = "pending_photos"
    private const val MAX_SIDE = 1600
    private const val JPEG_QUALITY = 80

    /** Итоговый файл + запись для Room (id генерируется на телефоне). */
    fun newPhoto(context: Context, visitId: String, answerId: String?): Pair<File, MediaFileEntity> {
        val id = UUID.randomUUID().toString()
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val file = File(dir, "$id.jpg")
        return file to MediaFileEntity(
            id = id,
            visitId = visitId,
            answerId = answerId,
            localFilePath = file.absolutePath,
            createdAt = System.currentTimeMillis(),
        )
    }

    /** Сжимает снимок камеры (tmp в cache) в итоговый файл pending_photos. */
    fun compress(src: File, dest: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("Не удалось декодировать фото")

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(src.absolutePath, opts) ?: error("Не удалось декодировать фото")

        val scaled = scaleDown(bitmap, MAX_SIDE)
        dest.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (scaled !== bitmap) bitmap.recycle()
        scaled.recycle()
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxSide) return bitmap
        val k = maxSide.toFloat() / largest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * k).toInt(), (bitmap.height * k).toInt(), true)
    }
}
