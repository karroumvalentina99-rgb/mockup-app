package org.legalbet.mockupupload

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private var promptedForSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val status = findViewById<TextView>(R.id.status)
        val progress = findViewById<ProgressBar>(R.id.progress)
        val btnTest = findViewById<MaterialButton>(R.id.btnTest)
        val btnSettings = findViewById<MaterialButton>(R.id.btnSettings)
        val btnAutoUrl = findViewById<MaterialButton>(R.id.btnAutoUrl)

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnAutoUrl.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Open Settings → Accessibility manually", Toast.LENGTH_LONG).show()
            }
        }

        btnTest.setOnClickListener {
            btnTest.isEnabled = false
            progress.visibility = View.VISIBLE
            status.text = "Testing…"
            Thread {
                val result = Uploader.testConnection(this)
                runOnUiThread {
                    progress.visibility = View.GONE
                    btnTest.isEnabled = true
                    status.text = if (result.success) {
                        "✓ Connected — inbox reachable"
                    } else {
                        "✗ " + result.message
                    }
                    Toast.makeText(
                        this,
                        if (result.success) "Connection OK" else "Failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.start()
        }
    }

    override fun onResume() {
        super.onResume()

        val status = findViewById<TextView>(R.id.status)
        if (!Prefs.isConfigured(this)) {
            status.text = "Set the device token in Settings."
            // On first launch, drop straight into Settings so nothing uploads
            // without a device token.
            if (!promptedForSettings) {
                promptedForSettings = true
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        } else {
            status.text = ""
        }

        val autoUrlStatus = findViewById<TextView>(R.id.autoUrlStatus)
        autoUrlStatus.setText(
            if (isAccessibilityEnabled()) R.string.auto_url_on else R.string.auto_url_off
        )
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = "$packageName/${UrlAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
