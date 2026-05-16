package com.abetworks.abetcrm.sync

import android.content.Context
import android.util.Log
import com.abetworks.abetcrm.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ApiService(private val context: Context) {

    private val TAG = "AbetCRM_API"

    private val baseUrl: String get() = Prefs.get(context, Prefs.KEY_API_URL, "https://api.abetworks.in/v1")
    private val token: String   get() = Prefs.get(context, Prefs.KEY_AUTH_TOKEN, "")
    private val tenantId: String get() = Prefs.get(context, Prefs.KEY_TENANT_ID, "")

    // ── Leads ────────────────────────────────────────────────────────────────

    /**
     * Create or update a lead on the server.
     * @return remoteId string on success, null on failure
     */
    suspend fun upsertLead(remoteId: String?, body: JSONObject): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = if (remoteId != null) "$baseUrl/leads/$remoteId" else "$baseUrl/leads"
                val method = if (remoteId != null) "PUT" else "POST"
                val response = request(url, method, body)
                response?.getString("id")
            } catch (e: Exception) {
                Log.e(TAG, "upsertLead failed", e)
                null
            }
        }

    suspend fun postActivity(body: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            try {
                request("$baseUrl/activities", "POST", body) != null
            } catch (e: Exception) {
                Log.e(TAG, "postActivity failed", e)
                false
            }
        }

    // ── Auth ─────────────────────────────────────────────────────────────────

    suspend fun login(email: String, password: String): LoginResult =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply { put("email", email); put("password", password) }
                val response = request("$baseUrl/auth/login", "POST", body, skipAuth = true)
                    ?: return@withContext LoginResult.Error("No response from server")
                LoginResult.Success(
                    token    = response.getString("token"),
                    tenantId = response.getString("tenantId"),
                    name     = response.optString("name", "")
                )
            } catch (e: Exception) {
                LoginResult.Error(e.message ?: "Login failed")
            }
        }

    // ── Core HTTP ─────────────────────────────────────────────────────────────

    private fun request(
        urlStr: String,
        method: String,
        body: JSONObject? = null,
        skipAuth: Boolean = false
    ): JSONObject? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (!skipAuth && token.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (tenantId.isNotBlank()) {
                setRequestProperty("X-Tenant-Id", tenantId)
            }
            connectTimeout = 10_000
            readTimeout    = 15_000
            if (body != null) {
                doOutput = true
                OutputStreamWriter(outputStream).use { it.write(body.toString()) }
            }
        }

        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.readText() ?: ""
            Log.d(TAG, "$method $urlStr → $code")
            if (code in 200..299) JSONObject(responseText) else null
        } finally {
            conn.disconnect()
        }
    }
}

sealed class LoginResult {
    data class Success(val token: String, val tenantId: String, val name: String) : LoginResult()
    data class Error(val message: String) : LoginResult()
}
