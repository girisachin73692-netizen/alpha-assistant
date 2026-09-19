package com.alpha.assistant

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

/**
 * A simple text chat screen for talking to Alpha by typing instead of speaking.
 * Uses the exact same reply logic (AlphaBrain) as the background voice service,
 * so answers are consistent whether you talk to Alpha by voice or by text.
 *
 * This does NOT add a real AI/agent - that's intentionally left for later.
 * It's rule-based matching, same as the voice assistant.
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var messageContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var etMessage: EditText
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        messageContainer = findViewById(R.id.messageContainer)
        scrollView = findViewById(R.id.scrollView)
        etMessage = findViewById(R.id.etMessage)
        val btnSend = findViewById<ImageButton>(R.id.btnSend)

        addAlphaBubble("नमस्ते! मैं Alpha हूँ। कुछ भी टाइप करके पूछिए।")

        btnSend.setOnClickListener { sendMessage() }
    }

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty()) return

        addUserBubble(text)
        etMessage.setText("")

        // Same brain the voice service uses - checks for a bundled real-voice
        // reply first, else falls back to a text reply.
        val audioResId = AlphaBrain.findVoiceReply(text)
            ?: if (AlphaBrain.containsWakeWord(text)) AlphaBrain.voiceReplies["wake"] else null

        if (audioResId != null) {
            playAudioReply(audioResId)
            return
        }

        if (AlphaServerClient.isConfigured()) {
            val thinkingBubble = addAlphaBubble("सोच रही हूँ...")
            AlphaServerClient.sendMessage(text) { reply ->
                runOnUiThread {
                    thinkingBubble.text = reply ?: "Server se jawab nahi mila - server chal raha hai na check kar lo."
                }
            }
        } else {
            addAlphaBubble(AlphaBrain.textReplyFor(text))
        }
    }

    private fun playAudioReply(resId: Int) {
        addAlphaBubble("🔊 (voice reply)")
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, resId)
            mediaPlayer?.setOnCompletionListener { it.release() }
            mediaPlayer?.start()
        } catch (e: Exception) {
            // If playback fails for any reason, at least the bubble above already shown.
        }
    }

    private fun addUserBubble(text: String): TextView {
        return addBubble(text, isUser = true)
    }

    private fun addAlphaBubble(text: String): TextView {
        return addBubble(text, isUser = false)
    }

    private fun addBubble(text: String, isUser: Boolean): TextView {
        val bubble = TextView(this).apply {
            this.text = text
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(32, 20, 32, 20)
            textSize = 15f
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

        val bubbleParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        bubble.layoutParams = bubbleParams

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
                messageContainer.removeAllViews()
                addAlphaBubble("Chat clear ho gayi. Kuch bhi poochiye।")
                true
            }
            R.id.menu_voice_mode -> {
                startActivity(Intent(this, MainActivity::class.java))
                true
            }
            R.id.menu_about -> {
                addAlphaBubble("Main Alpha hoon - abhi rule-based reply deta hoon. Real AI agent baad mein add hoga.")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}
