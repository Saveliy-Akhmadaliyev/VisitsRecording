package ru.plasmadynamics.visits.mobile.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TemplateEntity::class,
        TemplateFieldEntity::class,
        VisitEntity::class,
        VisitAnswerEntity::class,
        MediaFileEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao
    abstract fun visitDao(): VisitDao
    abstract fun answerDao(): AnswerDao
    abstract fun mediaDao(): MediaDao
}
