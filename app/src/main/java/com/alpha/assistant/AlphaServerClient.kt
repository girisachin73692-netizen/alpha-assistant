package com.alpha.assistant

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to YOUR OWN Alpha backend (the FastAPI/Termux server you built -
 * main.py's POST /v1/chat/completions endpoint) for real AI-generated replies,
 * instead of the fixed rule-based matching in AlphaBrain.
 *
 * SETUP REQUIRED:
 * 1. Make sure your Alpha server (Termux, main.py) is running on this same phone.
 * 2. Paste your ALPHA_API_KEY below (same value as in your server's .env file).
 *
 * If API_KEY is left blank, isConfigured() returns false and the app falls
 * back to AlphaBrain's local rule-based replies - so it never breaks.
 */
object AlphaServerClient {

    // ---- FILL THIS IN ----
    private const val ALPHA_API_KEY = "lD5Qy7laCF9jODHCb7dqxl8jGhq6BiOHT4IR982GtVY"
    // -----------------------

    // Server runs in Termux on this same phone, so localhost works.
    private const val BASE_URL = "http://127.0.0.1:8000"

    private const val TAG = "AlphaServerClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile var lastError: String = ""

    fun isConfigured(): Boolean = ALPHA_API_KEY.isNotBlank()

    /**
     * Sends [userMessage] to your own Alpha server and returns the AI's reply text.
     * Calls [onResult] with the reply, or with null if something went wrong
     * (server not running, wrong key, no network, etc.) so the caller can
     * fall back to a local reply instead of leaving the user hanging.
     */
    fun sendHistory(history: List<Pair<String, String>>, onResult: (String?) -> Unit) {
        if (!isConfigured()) { onResult(null); return }
        val messages = JSONArray()
        for ((role, content) in history) {
            messages.put(JSONObject().put("role", role).put("content", content))
        }
        val body = JSONObject().put("messages", messages).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL/v1/chat/completions")
            .addHeader("x-api-key", ALPHA_API_KEY)
            .post(body)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { lastError = e.toString(); onResult(null) }
            override fun onResponse(call: Call, response: okhttp3.Response) {
                try {
                    if (!response.isSuccessful) { lastError = "HTTP " + response.code; onResult(null); return }
                    val raw = response.body?.string()
                    val content = JSONObject(raw ?: "{}").optJSONArray("choices")
                        ?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                    onResult(content)
                } catch (e: Exception) { lastError = "parse " + e.toString(); onResult(null) }
            }
        })
    }

    fun sendMessage(userMessage: String, onResult: (String?) -> Unit) {
        if (!isConfigured()) {
            onResult(null)
            return
        }

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
        }

        val bodyJson = JSONObject().apply {
            put("messages", messages)
        }

        val body = bodyJson.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$BASE_URL/v1/chat/completions")
            .addHeader("x-api-key", ALPHA_API_KEY)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Alpha server request failed: ${e.message}")
                onResult(null)
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                try {
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Alpha server error: ${response.code}")
                        onResult(null)
                        return
                    }
                    val raw = response.body?.string()
                    if (raw.isNullOrBlank()) {
                        onResult(null)
                        return
                    }
                    val json = JSONObject(raw)
                    val choices = json.optJSONArray("choices")
                    val content = choices
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                    onResult(content)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse Alpha server response: ${e.message}")
                    onResult(null)
                }
            }
        })
    }
}
