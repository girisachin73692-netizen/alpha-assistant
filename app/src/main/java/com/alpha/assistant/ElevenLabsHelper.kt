package com.alpha.assistant

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Handles text-to-speech using the ElevenLabs API (female voice).
 *
 * SETUP REQUIRED:
 * 1. Sign up at https://elevenlabs.io and get an API key.
 * 2. Pick a female voice from your ElevenLabs "Voices" library and copy its Voice ID.
 * 3. Paste both values below.
 *
 * If API_KEY is left blank, the app automatically falls back to Android's
 * built-in TextToSpeech engine (see MainActivity) so it still works out of the box.
 */
object ElevenLabsHelper {

    // ---- FILL THESE IN ----
    private const val API_KEY = "" // <-- paste your ElevenLabs API key here
    private const val VOICE_ID = "21m00Tcm4TlvDq8ikWAM" // default female voice ("Rachel"); replace with your chosen voice ID
    // ------------------------

    private const val TAG = "ElevenLabsHelper"
    private val client = OkHttpClient()

    fun isConfigured(): Boolean = API_KEY.isNotBlank()

    /**
     * Speaks [text] using ElevenLabs and calls [onDone] when playback finishes
     * (or immediately if something goes wrong, so the app never hangs).
     */
    fun speak(context: Context, text: String, onDone: () -> Unit) {
        if (!isConfigured()) {
            onDone()
            return
        }

        val json = JSONObject().apply {
            put("text", text)
            put("model_id", "eleven_multilingual_v2") // supports Hindi + English
            put("voice_settings", JSONObject().apply {
                put("stability", 0.5)
                put("similarity_boost", 0.75)
            })
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$VOICE_ID")
            .addHeader("xi-api-key", API_KEY)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "ElevenLabs request failed: ${e.message}")
                onDone()
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        Log.e(TAG, "ElevenLabs error: ${response.code}")
                        onDone()
                        return
                    }
                    val audioFile = File(context.cacheDir, "alpha_tts.mp3")
                    FileOutputStream(audioFile).use { out ->
                        response.body?.byteStream()?.copyTo(out)
                    }
                    val player = MediaPlayer()
                    player.setDataSource(audioFile.absolutePath)
                    player.setOnCompletionListener {
                        it.release()
                        onDone()
                    }
                    player.prepare()
                    player.start()
                } catch (e: Exception) {
                    Log.e(TAG, "Playback failed: ${e.message}")
                    onDone()
                }
            }
        })
    }
}
