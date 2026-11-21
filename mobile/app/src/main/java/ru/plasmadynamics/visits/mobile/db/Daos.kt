package ru.plasmadynamics.visits.mobile.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {

    @Upsert
    suspend fun upsertTemplates(templates: List<TemplateEntity>)

    @Upsert
    suspend fun upsertFields(fields: List<TemplateFieldEntity>)

    @Transaction
    @Query("SELECT * FROM templates ORDER BY name")
    fun observeAll(): Flow<List<TemplateWithFields>>

    @Transaction
    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getById(id: String): TemplateWithFields?
}

@Dao
interface VisitDao {

    @Upsert
    suspend fun upsert(visits: List<VisitEntity>)

    @Query("SELECT * FROM visits WHERE id = :id")
    suspend fun get(id: String): VisitEntity?

    @Query("SELECT * FROM visits ORDER BY scheduledAt ASC")
    fun observeAll(): Flow<List<VisitEntity>>

    @Transaction
    @Query("SELECT * FROM visits WHERE id = :id")
    fun observeWithChecklist(id: String): Flow<VisitWithChecklist?>

    @Transaction
    @Query("SELECT * FROM visits WHERE syncStatus = 'PENDING_SYNC'")
    suspend fun getPending(): List<VisitWithChecklist>

    @Query("UPDATE visits SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query(
        "UPDATE visits SET factLat = :lat, factLon = :lon, completedAt = :completedAt, " +
            "status = 'COMPLETED', syncStatus = 'PENDING_SYNC' WHERE id = :id",
    )
    suspend fun complete(id: String, completedAt: Long, lat: Double?, lon: Double?)

    @Query("UPDATE visits SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM visits WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AnswerDao {

    @Query("SELECT * FROM visit_answers WHERE visitId = :visitId AND fieldId = :fieldId")
    suspend fun find(visitId: String, fieldId: String): VisitAnswerEntity?

    @Upsert
    suspend fun upsert(answer: VisitAnswerEntity)
}

@Dao
interface MediaDao {

    @Upsert
    suspend fun upsert(media: MediaFileEntity)

    @Query("SELECT * FROM media_files WHERE uploadStatus = 'NOT_UPLOADED'")
    suspend fun getNotUploaded(): List<MediaFileEntity>

    @Query("UPDATE media_files SET s3Key = :s3Key, uploadStatus = 'UPLOADED_TO_S3' WHERE id = :id")
    suspend fun markUploaded(id: String, s3Key: String)

    @Query("SELECT * FROM media_files WHERE visitId IN (:visitIds)")
    suspend fun getByVisitIds(visitIds: List<String>): List<MediaFileEntity>
}
