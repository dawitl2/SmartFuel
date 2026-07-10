package com.smartfuel.mobile

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ManualLocationService : Service(), LocationListener {
    private lateinit var locationManager: LocationManager

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        startForeground(301, trackingNotification())
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startLocationUpdates()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(this) }
        super.onDestroy()
    }

    override fun onLocationChanged(location: Location) {
        val prefs = getSharedPreferences("smartfuel-cache", Context.MODE_PRIVATE)
        if (prefs.getString("source", "mock") != "manual") return
        val state = loadManualState(prefs)
        if (!state.trackingEnabled || state.pendingMessage != null) return

        val next = processLocation(state, location)
        if (next != state) {
            saveManualState(prefs, next)
            if (next.pendingMessage != null) showManualLocationNotification(this, next.pendingMessage)
        }
    }

    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    private fun startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        runCatching { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60_000L, 8f, this) }
        runCatching { locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60_000L, 8f, this) }
    }

    private fun processLocation(state: ManualState, location: Location): ManualState {
        val now = System.currentTimeMillis()
        val lat = location.latitude
        val lon = location.longitude

        if (isConnectedToHomeWifi(state.homeWifiSsid)) {
            return state.copy(
                lastLat = lat,
                lastLon = lon,
                tripStartLat = lat,
                tripStartLon = lon,
                stationarySinceMs = now
            )
        }

        if (state.homeLat != null && state.homeLon != null && distanceMeters(state.homeLat, state.homeLon, lat, lon) < 80.0) {
            return state.copy(
                lastLat = lat,
                lastLon = lon,
                tripStartLat = lat,
                tripStartLon = lon,
                stationarySinceMs = now
            )
        }

        val knownPlace = (state.places + state.surprisePlaces).firstOrNull { place ->
            place.latitude != null &&
                place.longitude != null &&
                distanceMeters(place.latitude, place.longitude, lat, lon) <= state.placeRadiusMeters &&
                state.lastPromptPlaceId != place.id
        }
        if (knownPlace != null) {
            return state.copy(
                lastLat = lat,
                lastLon = lon,
                pendingPlaceId = knownPlace.id,
                pendingPlaceName = knownPlace.name,
                pendingKm = knownPlace.kilometers,
                pendingMessage = "Are you at ${knownPlace.name}? Confirm to count ${knownPlace.kilometers.format1()} km.",
                pendingLat = lat,
                pendingLon = lon,
                lastPromptPlaceId = knownPlace.id
            )
        }

        if (state.lastLat == null || state.lastLon == null) {
            return state.copy(lastLat = lat, lastLon = lon, tripStartLat = lat, tripStartLon = lon, stationarySinceMs = now)
        }

        val movedFromLast = distanceMeters(state.lastLat, state.lastLon, lat, lon)
        if (movedFromLast > 500.0) {
            return state.copy(
                lastLat = lat,
                lastLon = lon,
                tripStartLat = state.lastLat,
                tripStartLon = state.lastLon,
                stationarySinceMs = now,
                lastPromptPlaceId = null
            )
        }

        val stationarySince = if (movedFromLast <= state.stopRadiusMeters) {
            state.stationarySinceMs.takeIf { it > 0L } ?: now
        } else {
            now
        }

        val startLat = state.tripStartLat ?: state.lastLat
        val startLon = state.tripStartLon ?: state.lastLon
        val guessedKm = distanceMeters(startLat, startLon, lat, lon) / 1000.0 * 1.2
        val stoppedLongEnough = now - stationarySince >= (state.stopMinutes * 60_000.0).toLong()

        return if (stoppedLongEnough && guessedKm >= 0.5) {
            state.copy(
                lastLat = lat,
                lastLon = lon,
                stationarySinceMs = stationarySince,
                pendingPlaceId = "new-stop",
                pendingPlaceName = "New destination",
                pendingKm = guessedKm,
                pendingMessage = "You seem stopped somewhere new. Is this a trip? SmartFuel guessed ${guessedKm.format1()} km; edit it before confirming.",
                pendingLat = lat,
                pendingLon = lon
            )
        } else {
            state.copy(lastLat = lat, lastLon = lon, stationarySinceMs = stationarySince)
        }
    }

    private fun isConnectedToHomeWifi(homeWifiSsid: String): Boolean {
        if (homeWifiSsid.isBlank()) return false
        return try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ssid = wifi.connectionInfo?.ssid?.trim('"') ?: return false
            ssid.equals(homeWifiSsid.trim(), ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun trackingNotification() =
        NotificationCompat.Builder(this, "smartfuel-alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("SmartFuel trip assist")
            .setContentText("Watching location for manual fuel trip suggestions.")
            .setOngoing(true)
            .build()
}
