package ru.plasmadynamics.visits.mobile

import android.app.Application
import android.content.Context
import androidx.room.Room
import ru.plasmadynamics.visits.mobile.db.AppDatabase
import ru.plasmadynamics.visits.mobile.net.VisitsApi
import ru.plasmadynamics.visits.mobile.sync.SyncPrefs
import ru.plasmadynamics.visits.mobile.sync.SyncRepository

class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Ручной DI-контейнер (без Hilt — минимальность намеренная, см. README).
 * Доступ из UI и воркеров: (context.applicationContext as App).container
 */
class AppContainer(context: Context) {
    val prefs = SyncPrefs(context)
    val api = VisitsApi()
    val db = Room.databaseBuilder(context, AppDatabase::class.java, "visits.db").build()
    val syncRepository = SyncRepository(api, db, prefs)
}
