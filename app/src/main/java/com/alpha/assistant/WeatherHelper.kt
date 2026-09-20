package com.alpha.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Fetches live weather for the user's current location using Open-Meteo
 * (https://open-meteo.com) - completely free, no API key required.
 */
object WeatherHelper {

    private val client = OkHttpClient()

    fun isWeatherQuery(text: String): Boolean {
        val t = text.lowercase()
        return t.contains("mausam") || t.contains("मौसम") || t.contains("weather") ||
            t.contains("temperature") || t.contains("तापमान") || t.contains("baarish") || t.contains("बारिश")
    }

    fun getWeather(context: Context, callback: (String) -> Unit) {
        val hasFine = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            callback("मुझे location की permission नहीं मिली है, इसलिए मौसम नहीं बता पाऊँगी। कृपया app settings में location allow कर दीजिए।")
            return
        }

        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            fusedClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location == null) {
                    callback("अभी location नहीं मिल पाई, थोड़ी देर बाद फिर कोशिश कीजिए।")
                } else {
                    fetchWeatherFor(location.latitude, location.longitude, callback)
                }
            }.addOnFailureListener {
                callback("Location लेने में दिक्कत आई।")
            }
        } catch (e: SecurityException) {
            callback("मुझे location की permission नहीं मिली है।")
        }
    }

    private fun fetchWeatherFor(lat: Double, lon: Double, callback: (String) -> Unit) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("मौसम की जानकारी लाने में दिक्कत आई, internet चेक कीजिए।")
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val current = json.getJSONObject("current")
                    val temp = current.getDouble("temperature_2m")
                    val humidity = current.getInt("relative_humidity_2m")
                    val windSpeed = current.getDouble("wind_speed_10m")
                    val code = current.getInt("weather_code")
                    val condition = weatherCodeToHindi(code)

                    val reply = "अभी temperature $temp डिग्री सेल्सियस है, $condition, humidity $humidity% और हवा की speed $windSpeed किलोमीटर प्रति घंटा है।"
                    callback(reply)
                } catch (e: Exception) {
                    callback("मौसम का data समझने में दिक्कत आई।")
                }
            }
        })
    }

    private fun weatherCodeToHindi(code: Int): String = when (code) {
        0 -> "आसमान बिल्कुल साफ है"
        1, 2, 3 -> "थोड़े बादल छाए हुए हैं"
        45, 48 -> "कोहरा है"
        51, 53, 55, 56, 57 -> "हल्की बूंदाबांदी हो रही है"
        61, 63, 65, 66, 67 -> "बारिश हो रही है"
        71, 73, 75, 77 -> "बर्फबारी हो रही है"
        80, 81, 82 -> "तेज़ बारिश हो रही है"
        95, 96, 99 -> "आंधी-तूफान है"
        else -> "मौसम सामान्य है"
    }
}
