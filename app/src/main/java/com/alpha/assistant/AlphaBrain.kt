package com.alpha.assistant

import java.text.SimpleDateFormat
import java.util.*

/**
 * Single source of truth for "what should Alpha reply to X" - shared by the
 * background voice service (AlphaListenerService) and the text chat screen
 * (ChatActivity), so both behave consistently.
 *
 * NOTE: This is rule-based matching only (no real AI/agent yet, by design -
 * that gets added later). It checks bundled real-voice recordings first,
 * then a couple of dynamic built-in commands, then a generic fallback.
 */
object AlphaBrain {

    private val wakeWordVariants = listOf("alpha", "अल्फा", "एल्फा", "अल्फ़ा")

    /** Strips punctuation and extra spaces so matching isn't thrown off by "?", "।", double spaces, etc. */
    private fun normalize(text: String): String {
        return text.lowercase(Locale.getDefault())
            .replace(Regex("[?!.,।]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Every fixed reply that should play in the real recorded (Kanika/ElevenLabs) voice,
     * as: trigger keyword (what the user says) -> raw audio resource (Alpha's reply).
     *
     * HOW TO ACTIVATE EACH ONE (once you send the audio file):
     * 1. The file gets dropped into app/src/main/res/raw/ with the filename in the comment.
     * 2. That one line below gets uncommented.
     * No other code changes needed, no API key.
     */
    val voiceReplies: Map<String, Int> = mapOf(
        "wake" to R.raw.alpha_reply_yes // "Alpha" wake word -> "हाँ जी बॉस" (already added)

        // Greetings — trigger: user says hello/namaste/good morning
        // "नमस्ते" to R.raw.alpha_reply_namaste,        // reply: "नमस्ते बॉस"
        // "गुड मॉर्निंग" to R.raw.alpha_reply_morning,   // reply: "गुड मॉर्निंग बॉस"
        // "हेलो" to R.raw.alpha_reply_hello,             // reply: "जी बताइए" / "जी बॉस, बोलिए"

        // Acknowledgement — trigger: after a task-style command
        // "ठीक है" to R.raw.alpha_reply_ok,              // reply: "ठीक है बॉस"
        // "कर दो" to R.raw.alpha_reply_will_do,          // reply: "हो जाएगा बॉस"
        // "समझे" to R.raw.alpha_reply_understood,        // reply: "समझ गया"

        // Not understood / retry — used as fallback instead of the current default line
        // "not_understood" to R.raw.alpha_reply_sorry,   // reply: "माफ़ कीजिए, मुझे समझ नहीं आया"
        // "retry" to R.raw.alpha_reply_repeat,           // reply: "फिर से बोलिए प्लीज"

        // Closing — trigger: user says thanks/bye
        // "धन्यवाद" to R.raw.alpha_reply_thanks,         // reply: "धन्यवाद बॉस"
        // "बाय" to R.raw.alpha_reply_bye,                // reply: "अलविदा, फिर मिलेंगे"

        // Task status — trigger: before/after doing something
        // "अभी करता" to R.raw.alpha_reply_doing_now,     // reply: "अभी करता हूँ"
        // "हो गया" to R.raw.alpha_reply_done,            // reply: "हो गया बॉस"
        // "नहीं हुआ" to R.raw.alpha_reply_failed,        // reply: "नहीं हो पाया, फिर से try करते हैं"
    )

    fun containsWakeWord(text: String): Boolean {
        val norm = normalize(text)
        return wakeWordVariants.any { norm.contains(it) }
    }

    /** Returns the matching bundled audio resource id for [command], or null if none matches. */
    fun findVoiceReply(command: String): Int? {
        val norm = normalize(command)
        return voiceReplies.entries.firstOrNull { (keyword, _) ->
            keyword != "wake" && norm.contains(normalize(keyword))
        }?.value
    }

    /**
     * Text-only reply for anything that isn't a bundled voice recording -
     * dynamic content (like time/date) or the generic fallback. Used by the chat
     * screen directly, and mirrors what the voice service speaks via TTS.
     *
     * More keywords/synonyms per command = "smarter" without needing a real AI -
     * add more phrases to any line below anytime.
     */
    fun textReplyFor(command: String): String {
        val n = normalize(command)
        return when {
            // Time
            n.contains("time") || n.contains("समय") || n.contains("टाइम") ||
                n.contains("kitna baja") || n.contains("कितना बजा") -> {
                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                "अभी समय है ${sdf.format(Date())}"
            }
            // Date
            n.contains("date") || n.contains("डेट") || n.contains("तारीख") -> {
                val sdf = SimpleDateFormat("dd MMMM yyyy", Locale("hi", "IN"))
                "आज की तारीख है ${sdf.format(Date())}"
            }
            // Day of week
            n.contains("कौनसा दिन") || n.contains("कौन सा दिन") || n.contains("आज कौन") ||
                n.contains("what day") -> {
                val sdf = SimpleDateFormat("EEEE", Locale("hi", "IN"))
                "आज ${sdf.format(Date())} है"
            }
            // Name
            n.contains("तुम्हारा नाम") || n.contains("आपका नाम") || n.contains("your name") ||
                n.contains("kaun ho") || n.contains("कौन हो") -> "मेरा नाम Alpha है, मैं आपकी voice assistant हूँ"
            // How are you
            n.contains("कैसी हो") || n.contains("कैसे हो") || n.contains("how are you") ||
                n.contains("kaisi ho") || n.contains("kaise ho") -> "मैं बिल्कुल ठीक हूँ बॉस, आप बताइए"
            // Thanks (text fallback in case no bundled audio matched yet)
            n.contains("धन्यवाद") || n.contains("thank") || n.contains("shukriya") ||
                n.contains("शुक्रिया") -> "आपका स्वागत है बॉस"
            // Greeting fallback
            n.contains("नमस्ते") || n.contains("hello") || n.contains("हेलो") ||
                n.contains("hi ") || n == "hi" -> "नमस्ते बॉस, बताइए मैं क्या मदद कर सकती हूँ"
            else -> "माफ़ कीजिए, मुझे ये कमांड समझ नहीं आई"
        }
    }
}
