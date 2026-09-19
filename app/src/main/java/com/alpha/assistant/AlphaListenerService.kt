package com.alpha.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.media.MediaPlayer
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Runs in the foreground (persistent notification) so Android does not kill it,
 * and keeps listening for the wake word "Alpha" continuously - even if the
 * MainActivity screen is closed or the app is in the background.
 */
class AlphaListenerService : Service(), RecognitionListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private enum class Mode { WAKE, COMMAND }
    private var mode = Mode.WAKE

    companion object {
        const val CHANNEL_ID = "alpha_service_channel"
        const val NOTIFICATION_ID = 101
        const val ACTION_STATUS_UPDATE = "com.alpha.assistant.STATUS_UPDATE"
        const val EXTRA_STATUS_TEXT = "status_text"
    }

    override fun onCreate() {
        super.onCreate()

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                val result = tts.setLanguage(Locale("hi", "IN"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.language = Locale.US
                }
                selectFemaleVoice()
            }
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("सुन रहा हूँ... ('Alpha' बोलिए)"))
        startListening()
        // If Android kills the service under memory pressure, restart it automatically.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startListening() {
        speechRecognizer.startListening(recognizerIntent)
    }

    /**
     * Restarting the recognizer immediately after it finishes often silently
     * fails ("busy") on many devices. Cancelling first and waiting a short
     * moment makes every restart (after wake word, after a command, after an
     * error) reliably work every single time, not just once.
     */
    private fun restartListening(delayMs: Long = 350) {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                speechRecognizer.cancel()
            } catch (e: Exception) {
                // ignore - recognizer may already be idle
            }
            try {
                speechRecognizer.startListening(recognizerIntent)
            } catch (e: Exception) {
                // if it still fails, try once more shortly after
                Handler(Looper.getMainLooper()).postDelayed({
                    try { speechRecognizer.startListening(recognizerIntent) } catch (e2: Exception) {}
                }, 500)
            }
        }, delayMs)
    }

    /**
     * Picks the best available female voice from the device's installed TTS voices.
     * Works with zero setup — no API key needed. Tries, in order:
     * 1. Any voice whose name literally says "female" (works on most engines).
     * 2. Known Google TTS female voice codes for Hindi, then English.
     * 3. Falls back to the engine's default voice if nothing else matches.
     */
    private fun selectFemaleVoice() {
        val voices = tts.voices ?: return

        val knownFemaleCodes = listOf(
            "hi-in-x-hia#female_1-local", // Hindi female (Google TTS)
            "hi-in-x-hie#female_2-local",
            "en-in-x-end#female_1-local", // Indian-English female
            "en-us-x-sfg#female_1-local", // US-English female
            "en-us-x-tpf#female_2-local"
        )

        val byNameMatch = voices.firstOrNull { it.name.contains("female", ignoreCase = true) }
        val byKnownCode = voices.firstOrNull { v -> knownFemaleCodes.any { v.name.equals(it, ignoreCase = true) } }
        val hindiFemaleGuess = voices.firstOrNull {
            it.locale?.language == "hi" && it.name.contains("hia", ignoreCase = true)
        }

        val chosen = byNameMatch ?: byKnownCode ?: hindiFemaleGuess
        if (chosen != null) {
            tts.voice = chosen
        }
        // If none matched, the engine's current default voice is used as-is.
    }

    private fun containsWakeWord(text: String): Boolean = AlphaBrain.containsWakeWord(text)

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
        // Also broadcast to MainActivity in case it's open, so the on-screen text updates too.
        sendBroadcast(Intent(ACTION_STATUS_UPDATE).putExtra(EXTRA_STATUS_TEXT, text))
    }

    private fun buildNotification(contentText: String): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Alpha Assistant", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Alpha")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    /**
     * Plays a bundled real-voice audio reply (from [voiceReplies]) instead of
     * robotic device TTS. No API key needed - audio is baked into the app.
     */
    private fun playBundledAudio(resId: Int, thenDo: () -> Unit) {
        try {
            val player = MediaPlayer.create(this, resId)
            if (player == null) {
                thenDo()
                return
            }
            player.setOnCompletionListener {
                it.release()
                thenDo()
            }
            player.start()
        } catch (e: Exception) {
            thenDo()
        }
    }

    private fun playWakeReplyAudio(thenDo: () -> Unit) {
        updateNotification("हाँ जी बॉस")
        val resId = AlphaBrain.voiceReplies["wake"]
        if (resId != null) {
            playBundledAudio(resId, thenDo)
        } else {
            thenDo()
        }
    }

    /**
     * Tries to match the command text against a keyword that has a real recorded
     * voice reply bundled. Returns true (and plays it) if found, else false so the
     * caller can fall back to device TTS / ElevenLabs API.
     */
    private fun tryPlayVoiceReply(command: String, thenDo: () -> Unit): Boolean {
        val resId = AlphaBrain.findVoiceReply(command) ?: return false
        updateNotification(command)
        playBundledAudio(resId, thenDo)
        return true
    }

    private fun speak(text: String, thenDo: () -> Unit = {}) {
        updateNotification(text)
        if (ElevenLabsHelper.isConfigured()) {
            ElevenLabsHelper.speak(this, text) { thenDo() }
        } else if (ttsReady) {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { thenDo() }
                override fun onError(utteranceId: String?) { thenDo() }
            })
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "alpha_utt")
        } else {
            thenDo()
        }
    }

    private fun handleCommand(command: String) {
        // First choice: a real recorded voice reply (same Kanika/ElevenLabs voice), if one matches.
        val playedRealVoice = tryPlayVoiceReply(command) {
            mode = Mode.WAKE
            updateNotification("सुन रहा हूँ... ('Alpha' बोलिए)")
            restartListening()
        }
        if (playedRealVoice) return

        // Second choice: your own Alpha AI server (real AI reply), if configured.
        if (AlphaServerClient.isConfigured()) {
            updateNotification("सोच रहा हूँ...")
            AlphaServerClient.sendMessage(command) { reply ->
                Handler(Looper.getMainLooper()).post {
                    val textToSpeak = reply ?: AlphaBrain.textReplyFor(command)
                    speak(textToSpeak) {
                        mode = Mode.WAKE
                        updateNotification("सुन रहा हूँ... ('Alpha' बोलिए)")
                        restartListening()
                    }
                }
            }
            return
        }

        // Fallback: local rule-based reply.
        speak(AlphaBrain.textReplyFor(command)) {
            mode = Mode.WAKE
            updateNotification("सुन रहा हूँ... ('Alpha' बोलिए)")
            restartListening()
        }
    }

    override fun onResults(results: Bundle) {
        val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
        when (mode) {
            Mode.WAKE -> {
                if (containsWakeWord(text)) {
                    mode = Mode.COMMAND
                    playWakeReplyAudio {
                        updateNotification("बोलिए, कमांड दीजिए...")
                        restartListening()
                    }
                } else {
                    restartListening()
                }
            }
            Mode.COMMAND -> handleCommand(text)
        }
    }

    override fun onPartialResults(partialResults: Bundle) {
        partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
            sendBroadcast(Intent(ACTION_STATUS_UPDATE).putExtra(EXTRA_STATUS_TEXT, it))
        }
    }

    override fun onError(error: Int) {
        // Timeout / no speech / busy -> just keep listening, service never stops on its own.
        // Using restartListening() (cancel + short delay) instead of an immediate
        // startListening() so the mic reliably comes back every time, not just once.
        restartListening()
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        tts.stop()
        tts.shutdown()
    }
}
