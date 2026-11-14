package com.kinly.familytracker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val permissionRequest = 501
    private val executor = Executors.newSingleThreadExecutor()
    private val burgundy = Color.rgb(138, 0, 79)
    private lateinit var root: LinearLayout
    private var busy = false
    private var nameInput: EditText? = null
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE)
        if (!prefs.getString(Config.TOKEN, null).isNullOrBlank()) {
            if (hasLocation()) showDashboard() else showWelcome()
        } else showWelcome()
    }

    private fun shell(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(24), dp(24), dp(24))
        setBackgroundColor(Color.rgb(250, 247, 249))
    }

    private fun showWelcome(message: String = "Enter your name to join the live Sumo map.") {
        root = shell()
        root.addView(TextView(this).apply {
            text = "SUMO"
            textSize = 34f
            letterSpacing = .18f
            setTextColor(burgundy)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Stay close to the people who matter."
            textSize = 18f
            setTextColor(Color.rgb(43,36,42))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(Color.rgb(111,101,110))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        })
        nameInput = EditText(this).apply {
            hint = "Enter your name"
            setSingleLine(true)
            textSize = 16f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setText(getSharedPreferences(Config.PREFS, MODE_PRIVATE).getString(Config.DEVICE_NAME, ""))
        }
        root.addView(nameInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        root.addView(Button(this).apply {
            text = if (busy) "Please wait…" else "Let's Go!"
            isAllCaps = false
            isEnabled = !busy
            setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(burgundy))
            setOnClickListener {
                val name = nameInput?.text?.toString()?.trim().orEmpty()
                if (name.length < 2 || name.length > 40) {
                    showWelcome("Please enter a name between 2 and 40 characters.")
                } else {
                    getSharedPreferences(Config.PREFS, MODE_PRIVATE).edit().putString(Config.DEVICE_NAME, name).apply()
                    if (hasLocation()) registerAnonymousDevice(name) else requestLocationPermission()
                }
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        root.addView(TextView(this).apply {
            text = "Location sharing runs with a visible Android notification. You can stop it from the app or system settings."
            textSize = 11f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        })
        setContentView(root)
    }

    private fun hasLocation() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), permissionRequest)
    }

    private fun registerAnonymousDevice(name: String = getSharedPreferences(Config.PREFS, MODE_PRIVATE).getString(Config.DEVICE_NAME, "User") ?: "User") {
        if (busy) return
        busy = true
        showWelcome("Setting up your private Sumo device…")
        executor.execute {
            try {
                val prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE)
                val existingId = prefs.getString(Config.DEVICE_ID, null)
                val payload = JSONObject().put("name", name)
                if (!existingId.isNullOrBlank()) payload.put("device_id", existingId)
                val response = ApiClient.request(Config.baseUrl(this), "/api/device/register", "POST", payload)
                val token = response.optString("token")
                val id = response.optString("device_id")
                if (token.isBlank() || id.isBlank()) throw IllegalStateException("Sumo server did not return a device identity.")
                prefs.edit().putString(Config.TOKEN, token).putString(Config.DEVICE_ID, id)
                    .putString(Config.DEVICE_NAME, response.optString("device_name", "My device"))
                    .putString(Config.FAMILY_CODE, response.optString("family_code", "")).apply()
                runOnUiThread {
                    busy = false
                    prefs.edit().putBoolean(Config.TRACKING, true).apply()
                    startLocationService()
                    showDashboard()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    busy = false
                    showWelcome(e.message ?: "Could not connect to Sumo server.")
                }
            }
        }
    }

    private fun showDashboard() {
        val prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(10))
        }
        header.addView(TextView(this).apply {
            text = "SUMO"
            textSize = 24f
            setTextColor(burgundy)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        header.addView(TextView(this).apply {
            text = "Sharing as ${prefs.getString(Config.DEVICE_NAME, "You")}"
            textSize = 12f
            setTextColor(Color.DKGRAY)
        })
        root.addView(header)
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val token = prefs.getString(Config.TOKEN, "") ?: ""
                    view.evaluateJavascript("localStorage.setItem('sumo_token', ${JSONObject.quote(token)});", null)
                }
            }
            loadUrl(Config.baseUrl(this@MainActivity) + "/")
        }
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val stop = Button(this).apply {
            text = if (prefs.getBoolean(Config.TRACKING, false)) "Stop location sharing" else "Start location sharing"
            isAllCaps = false
            setOnClickListener {
                val active = prefs.getBoolean(Config.TRACKING, false)
                if (active) {
                    stopService(Intent(this@MainActivity, LocationTrackingService::class.java))
                    prefs.edit().putBoolean(Config.TRACKING, false).apply()
                    text = "Start location sharing"
                } else if (hasLocation()) {
                    prefs.edit().putBoolean(Config.TRACKING, true).apply()
                    startLocationService()
                    text = "Stop location sharing"
                } else requestLocationPermission()
            }
        }
        root.addView(stop)
        setContentView(root)
    }

    private fun startLocationService() {
        if (hasLocation()) ContextCompat.startForegroundService(this, Intent(this, LocationTrackingService::class.java))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequest) {
            if (hasLocation()) {
                val name = getSharedPreferences(Config.PREFS, MODE_PRIVATE).getString(Config.DEVICE_NAME, "") ?: ""
                if (name.length >= 2) registerAnonymousDevice(name)
                else showWelcome("Enter your name to continue.")
            } else showWelcome("Location permission is needed to share your location.")
        }
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized && hasLocation()) {
            val prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE)
            if (prefs.getString(Config.TOKEN, null).isNullOrBlank() && !busy) {
                val name = prefs.getString(Config.DEVICE_NAME, "") ?: ""
                if (name.length >= 2) registerAnonymousDevice(name)
            }
        }
    }

    override fun onBackPressed() { super.onBackPressed() }
}
