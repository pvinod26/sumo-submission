package com.kinly.familytracker

import android.content.Context

object Config {
    const val DEFAULT_BASE_URL = "https://trails-u5bo.onrender.com"
    const val PREFS = "sumo_device_prefs"
    const val TOKEN = "device_token"
    const val DEVICE_ID = "device_id"
    const val DEVICE_NAME = "device_name"
    const val FAMILY_CODE = "family_code"
    const val TRACKING = "tracking"

    fun baseUrl(context: Context): String = DEFAULT_BASE_URL.trimEnd('/')
}
