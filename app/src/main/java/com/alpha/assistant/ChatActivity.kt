package com.alpha.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class ChatActivity : AppCompatActivity() {

    private lateinit var messageContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var etMessage: EditText
    private lateinit var btnMic: ImageButton
    private var mediaPlayer: MediaPlayer? = null
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var serviceStopped = false
    private val history = mutableListOf<Pair<String, String>>()
    private val prefs by lazy { getSharedPreferences("alpha_chat", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))

        messageContainer = findViewById(R.id.messageContainer)
        scrollView = findViewById(R.id.scrollView)
        etMessage = findViewById(R.id.etMessage)
        btnMic = findViewById(R.id.btnMic)

        loadHistory()
        if (history.isEmpty()) {
            addBubble("नमस्ते बॉस! मैं Alpha हूँ। टाइप कीजिए या माइक दबाकर बोलिए।", false)
        } else {
            history.forEach { addBubble(it.second, it.first == "user") }
        }

        findViewById<ImageButton>(R.id.btnSend).setOnClickListener {
            val t = etMessage.text.toString().trim()
            if (t.isNotEmpty()) {
                etMessage.setText("")
                sendText(t)
            }
        }
        btnMic.setOnClickListener { toggleMic() }
    }

    // ---------- chat + memory ----------

    private fun sendText(text: String) {
        addBubble(text, true)
        history.add("user" to text)
        saveHistory()

        // Sirf akela "Alpha" bola ho to recorded "haan ji boss" bajao, baaki sab AI ko.
        if (text.trim().length <= 8 && AlphaBrain.containsWakeWord(text)) {
            val res = AlphaBrain.voiceReplies["wake"]
            if (res != null) {
                playAudioReply(res)
                return
            }
        }

        if (AlphaServerClient.isConfigured()) {
            val thinking = addBubble("सोच रही हूँ...", false)
            val recent = history.takeLast(20).toList()
            AlphaServerClient.sendHistory(recent) { reply ->
                runOnUiThread {
                    val answer = reply?.trim()
                    if (answer.isNullOrEmpty()) {
                        thinking.text = "Server se jawab nahi mila - Termux mein server chal raha hai na check kar lo."
                    } else {
                        thinking.text = answer
                        history.add("assistant" to answer)
                        saveHistory()
                    }
                }
            }
        } else {
            val answer = AlphaBrain.textReplyFor(text)
            addBubble(answer, false)
            history.add("assistant" to answer)
            saveHistory()
        }
    }

    private fun saveHistory() {
        val arr = JSONArray()
        history.takeLast(60).forEach {
            arr.put(JSONObject().put("r", it.first).put("c", it.second))
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun loadHistory() {
        try {
            val arr = JSONArray(prefs.getString("history", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                history.add(o.getString("r") to o.getString("c"))
            }
        } catch (e: Exception) {
        }
    }

    // ---------- mic ----------

    private fun toggleMic() {
        if (listening) {
            stopMic()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 7)
            return
        }
        startMic()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startMic()
        } else if (requestCode == 7) {
            Toast.makeText(this, "Mic permission zaroori hai", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startMic() {
        // Background service mic pakde hoti hai, isliye pehle usse rok do.
        stopService(Intent(this, AlphaListenerService::class.java))
        serviceStopped = true
        btnMic.postDelayed({ beginRecognition() }, 500)
    }

    private fun beginRecognition() {
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle) {
                partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let { etMessage.setText(it) }
            }

            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                stopMic()
                etMessage.setText("")
                if (text.isNotEmpty()) sendText(text)
            }

            override fun onError(error: Int) {
                stopMic()
                etMessage.setText("")
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "Sunai nahi diya, phir se boliye"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Internet check kijiye"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Mic permission do"
                    else -> "Mic error ($error), dobara try kijiye"
                }
                Toast.makeText(this@ChatActivity, msg, Toast.LENGTH_SHORT).show()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        listening = true
        btnMic.setColorFilter(0xFFFF5252.toInt())
        etMessage.hint = "सुन रही हूँ..."
        r.startListening(i)
    }

    private fun stopMic() {
        listening = false
        btnMic.clearColorFilter()
        etMessage.hint = "Alpha se poochiye..."
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
        }
        recognizer = null
        resumeService()
    }

    private fun resumeService() {
        if (!serviceStopped) return
        serviceStopped = false
        try {
            val si = Intent(this, AlphaListenerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(si) else startService(si)
        } catch (e: Exception) {
        }
    }

    // ---------- UI ----------

    private fun playAudioReply(resId: Int) {
        addBubble("🔊 हाँ जी बॉस", false)
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, resId)
            mediaPlayer?.setOnCompletionListener { it.release() }
            mediaPlayer?.start()
        } catch (e: Exception) {
        }
    }

    private fun addBubble(text: String, isUser: Boolean): TextView {
        val bubble = TextView(this).apply {
            this.text = text
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(32, 20, 32, 20)
            textSize = 15f
            setTextIsSelectable(true)
            maxWidth = (resources.displayMetrics.widthPixels * 0.8).toInt()
            setBackgroundResource(if (isUser) R.drawable.bg_bubble_user else R.drawable.bg_bubble_alpha)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
            gravity = if (isUser) Gravity.END else Gravity.START
        }
        row.addView(bubble)
        messageContainer.addView(row)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        return bubble
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_clear_chat -> {
                history.clear()
                prefs.edit().remove("history").apply()
                messageContainer.removeAllViews()
                addBubble("Chat clear ho gayi. Kuch bhi poochiye।", false)
                true
            }
            R.id.menu_voice_mode -> {
                startActivity(Intent(this, MainActivity::class.java))
                true
            }
            R.id.menu_about -> {
                addBubble("Main Alpha hoon, mujhe Sachin Giri ne banaya hai.", false)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
        }
        mediaPlayer?.release()
        resumeService()
    }
}
