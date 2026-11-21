package ru.plasmadynamics.visits.mobile.util

import android.content.Context
import android.location.Location
import android.location.LocationManager

/**
 * Лучшее известное местоположение (для fact-координат при завершении визита).
 *
 * TODO(roadmap): перейти на FusedLocationProviderClient (play-services-location)
 * с запросом точного местоположения; пока LocationManager без Google-сервисов.
 * Возвращает null, если разрешение не выдано или провайдеры молчат — визит
 * закрывается без геометки.
 */
object LocationHelper {

    fun currentLocation(context: Context): Pair<Double, Double>? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        var best: Location? = null
        for (provider in PROVIDERS) {
            val last = runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: continue
            if (best == null || last.time > best!!.time) best = last
        }
        return best?.let { it.latitude to it.longitude }
    }

    private val PROVIDERS = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    )
}
