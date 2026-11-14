package com.kinly.familytracker

import org.json.JSONObject
import org.json.JSONException
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {
    fun request(baseUrl: String, path: String, method: String = "GET", body: JSONObject? = null, token: String? = null): JSONObject {
        require(!baseUrl.contains("REPLACE-WITH-YOUR", true)) {
            "Sumo server is not deployed/configured yet. The developer must set its HTTPS address in Config.kt."
        }
        val c = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            if (body != null) c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = c.responseCode
            val raw = (if (status in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }?.trim().orEmpty()
            val json = try { JSONObject(raw) } catch (_: JSONException) {
                throw IllegalStateException("Sumo server returned an invalid response (HTTP $status). Check backend deployment.")
            }
            if (status !in 200..299 || !json.optBoolean("ok", true)) {
                throw IllegalStateException(json.optString("error").ifBlank { "Sumo server error (HTTP $status)." })
            }
            return json
        } finally { c.disconnect() }
    }
}
