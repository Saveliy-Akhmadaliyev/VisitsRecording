package ru.plasmadynamics.visits.mobile.sync

import android.content.Context

/**
 * Настройки синхронизации. lastSyncedAt — serverTime из прошлого ответа
 * (часам телефона доверять нельзя, см. идеи/mobile_api.md).
 *
 * TODO(аутентификация): access/refresh токены — в EncryptedSharedPreferences,
 * inspectorId — из JWT.
 */
class SyncPrefs(context: Context) {

    private val sp = context.getSharedPreferences("visits_sync", Context.MODE_PRIVATE)

    var inspectorId: String?
        get() = sp.getString(KEY_INSPECTOR, null)
        set(value) = sp.edit().putString(KEY_INSPECTOR, value).apply()

    var lastSyncedAt: String?
        get() = sp.getString(KEY_LAST_SYNC, null)
        set(value) = sp.edit().putString(KEY_LAST_SYNC, value).apply()

    private companion object {
        const val KEY_INSPECTOR = "inspector_id"
        const val KEY_LAST_SYNC = "last_synced_at"
    }
}
