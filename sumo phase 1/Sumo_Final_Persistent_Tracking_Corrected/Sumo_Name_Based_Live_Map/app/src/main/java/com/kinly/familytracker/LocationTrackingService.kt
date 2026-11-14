package com.kinly.familytracker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.json.JSONObject
import java.util.concurrent.Executors

class LocationTrackingService : Service() {
    private lateinit var fused: FusedLocationProviderClient
    private var callback: LocationCallback? = null

    private val channelId = "sumo_location_channel"
    private val notificationId = 901
    private val uploader = Executors.newSingleThreadExecutor()
    private val watchdogHandler = Handler(Looper.getMainLooper())

    @Volatile private var uploading = false
    @Volatile private var restartingUpdates = false

    private var lastLocationCallbackElapsed = 0L
    private var lastUploadSuccessElapsed = 0L

    private val updateRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        5_000L
    )
        .setMinUpdateIntervalMillis(2_000L)
        .setMinUpdateDistanceMeters(0f)
        .setMaxUpdateDelayMillis(5_000L)
        .setWaitForAccurateLocation(false)
        .build()

    private val watchdog = object : Runnable {
        override fun run() {
            if (!isSharingEnabled()) return

            val now = SystemClock.elapsedRealtime()
            val silence = now - lastLocationCallbackElapsed

            // If the location provider has been silent for too long while sharing
            // is still enabled, safely unregister and request updates again.
            if (lastLocationCallbackElapsed > 0L && silence > LOCATION_SILENCE_RESTART_MS) {
                Log.w(TAG, "No location callback for ${silence / 1000}s; re-registering updates")
                updateNotification("Reconnecting to GPS…")
                restartLocationUpdates()
            }

            watchdogHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startForeground(notificationId, notification("Starting location sharing…"))

        fused = LocationServices.getFusedLocationProviderClient(this)

        if (isSharingEnabled()) {
            beginUpdates()
            watchdogHandler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)
        } else {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isSharingEnabled()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // onCreate handles the first registration. If Android recreates the
        // service, ensure the watchdog/location request is present again.
        if (callback == null) {
            beginUpdates()
            watchdogHandler.removeCallbacks(watchdog)
            watchdogHandler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)
        }

        return START_STICKY
    }

    private fun isSharingEnabled(): Boolean =
        getSharedPreferences(Config.PREFS, MODE_PRIVATE)
            .getBoolean(Config.TRACKING, false)

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    private fun beginUpdates() {
        if (!isSharingEnabled()) {
            stopSelf()
            return
        }

        if (!hasLocationPermission()) {
            Log.e(TAG, "Location permission is missing")
            updateNotification("Location permission is required")
            stopSelf()
            return
        }

        if (callback == null) {
            callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    lastLocationCallbackElapsed = SystemClock.elapsedRealtime()

                    updateNotification(
                        "Sharing location · accuracy ±${loc.accuracy.toInt()} m"
                    )

                    uploadLocation(
                        loc.latitude,
                        loc.longitude,
                        loc.accuracy,
                        loc.time
                    )
                }
            }
        }

        try {
            fused.lastLocation.addOnSuccessListener { loc ->
                if (
                    loc != null &&
                    isSharingEnabled() &&
                    lastLocationCallbackElapsed == 0L
                ) {
                    // This is only an initial fallback upload. Live callbacks
                    // remain the primary source of tracking updates.
                    uploadLocation(
                        loc.latitude,
                        loc.longitude,
                        loc.accuracy,
                        loc.time
                    )
                }
            }

            fused.requestLocationUpdates(
                updateRequest,
                callback!!,
                Looper.getMainLooper()
            )

            // Start measuring silence only after registration succeeds.
            lastLocationCallbackElapsed = SystemClock.elapsedRealtime()
            Log.i(TAG, "High-accuracy location updates requested")
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing", e)
            updateNotification("Location permission is required")
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Could not request location updates", e)
            updateNotification("Could not start GPS; retrying…")
        }
    }

    private fun restartLocationUpdates() {
        if (restartingUpdates || !isSharingEnabled() || !hasLocationPermission()) return
        restartingUpdates = true

        try {
            callback?.let { fused.removeLocationUpdates(it) }
                ?.addOnCompleteListener {
                    if (isSharingEnabled() && hasLocationPermission()) {
                        try {
                            fused.requestLocationUpdates(
                                updateRequest,
                                callback!!,
                                Looper.getMainLooper()
                            )
                            lastLocationCallbackElapsed = SystemClock.elapsedRealtime()
                            updateNotification("GPS reconnected · sharing location")
                            Log.i(TAG, "Location updates re-registered")
                        } catch (e: SecurityException) {
                            Log.e(TAG, "Permission missing while restarting GPS", e)
                            updateNotification("Location permission is required")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to re-register location updates", e)
                            updateNotification("GPS reconnect failed; will retry")
                        } finally {
                            restartingUpdates = false
                        }
                    } else {
                        restartingUpdates = false
                    }
                }
        } catch (e: Exception) {
            restartingUpdates = false
            Log.e(TAG, "Failed while restarting location updates", e)
        }
    }

    private fun uploadLocation(
        lat: Double,
        lon: Double,
        accuracy: Float,
        timestamp: Long
    ) {
        if (!isSharingEnabled() || uploading) return

        val prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE)
        val token = prefs.getString(Config.TOKEN, null) ?: return

        uploading = true
        uploader.execute {
            try {
                ApiClient.request(
                    Config.baseUrl(this@LocationTrackingService),
                    "/api/location",
                    "POST",
                    JSONObject()
                        .put("latitude", lat)
                        .put("longitude", lon)
                        .put("accuracy", accuracy.toDouble())
                        .put("timestamp", timestamp),
                    token
                )

                lastUploadSuccessElapsed = SystemClock.elapsedRealtime()
                Log.i(TAG, "Location uploaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Location upload failed: ${e.message}", e)
                updateNotification(
                    "GPS active · server upload failed; will retry on next update"
                )
            } finally {
                uploading = false
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Sumo location sharing",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(notificationId, notification(text))
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("SUMO location sharing")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the Activity away from Recents must not disable sharing.
        // The foreground service continues while TRACKING remains true.
        Log.i(TAG, "Task removed from Recents; sharing remains enabled")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        watchdogHandler.removeCallbacksAndMessages(null)

        callback?.let {
            try {
                fused.removeLocationUpdates(it)
            } catch (e: Exception) {
                Log.w(TAG, "Could not remove location updates cleanly", e)
            }
        }

        callback = null
        uploader.shutdownNow()
        Log.i(TAG, "Location service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "SumoTracker"

        // Check periodically; there is intentionally NO overall tracking timeout.
        private const val WATCHDOG_INTERVAL_MS = 30_000L

        // If callbacks disappear for more than 90 seconds while sharing is
        // enabled, re-register the location request.
        private const val LOCATION_SILENCE_RESTART_MS = 90_000L
    }
}
