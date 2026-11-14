package com.kinly.familytracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Config.TRACKING, false)) return
        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasLocation) return
        // Android versions/OEMs may prohibit starting a location foreground service from boot.
        // Catch and log the restriction; the user can reopen Kinly and resume sharing.
        try {
            ContextCompat.startForegroundService(context, Intent(context, LocationTrackingService::class.java))
        } catch (e: Exception) {
            Log.w("KinlyTracker", "Could not resume tracking after reboot; open Kinly to resume.", e)
        }
    }
}
