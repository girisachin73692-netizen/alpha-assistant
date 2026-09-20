package com.alpha.assistant

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Just a status screen now. All the actual listening happens in
 * AlphaListenerService, which keeps running even if this screen is closed.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvHeard: TextView

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val text = intent.getStringExtra(AlphaListenerService.EXTRA_STATUS_TEXT) ?: return
            tvHeard.text = text
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvHeard = findViewById(R.id.tvHeard)
        tvStatus.text = "Alpha background me chal raha hai"

        findViewById<android.widget.Button>(R.id.btnOpenChat).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        requestPermissionsIfNeeded()
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, storagePermission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(storagePermission)
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
        } else {
            startAlphaService()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Mic is the only permission that's truly required to start Alpha.
        // Location/storage are optional extras - Alpha still starts without them.
        val micIndex = permissions.indexOf(Manifest.permission.RECORD_AUDIO)
        val micGranted = micIndex == -1 || (grantResults.getOrNull(micIndex) == PackageManager.PERMISSION_GRANTED)
        if (requestCode == 1 && micGranted) {
            startAlphaService()
        } else {
            tvStatus.text = "माइक्रोफोन परमिशन ज़रूरी है"
        }
    }

    private fun startAlphaService() {
        val serviceIntent = Intent(this, AlphaListenerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(AlphaListenerService.ACTION_STATUS_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(statusReceiver)
    }
}
