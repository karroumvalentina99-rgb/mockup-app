package org.legalbet.mockupupload

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShareActivity : AppCompatActivity() {

    private lateinit var uris: List<Uri>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        uris = extractUris(intent)
        if (uris.isEmpty()) {
            Toast.makeText(this, "No image received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContentView(R.layout.activity_share)

        val thumb = findViewById<ImageView>(R.id.thumb)
        val sourceUrl = findViewById<TextInputEditText>(R.id.sourceUrl)
        val operator = findViewById<MaterialAutoCompleteTextView>(R.id.operator)
        val category = findViewById<MaterialAutoCompleteTextView>(R.id.category)
        val note = findViewById<TextInputEditText>(R.id.note)
        val status = findViewById<TextView>(R.id.shareStatus)
        val progress = findViewById<ProgressBar>(R.id.shareProgress)
        val title = findViewById<TextView>(R.id.title)
        val btnUpload = findViewById<MaterialButton>(R.id.btnDoUpload)
        val btnCancel = findViewById<MaterialButton>(R.id.btnCancel)

        if (uris.size > 1) {
            title.text = getString(R.string.share_label) + "  (${uris.size} images)"
        }

        sourceUrl.setText(prefilledSourceUrl())

        // Load a downsampled preview off the UI thread.
        Thread {
            val bmp = try {
                contentResolver.openInputStream(uris[0]).use { input ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeStream(input, null, opts)
                }
            } catch (e: Exception) {
                null
            }
            if (bmp != null) runOnUiThread { thumb.setImageBitmap(bmp) }
        }.start()

        // Populate operator/category dropdowns from the server (optional; blank is fine).
        Thread {
            val meta = Uploader.fetchMeta(this)
            if (meta != null) runOnUiThread {
                if (meta.operators.isNotEmpty()) operator.setSimpleItems(meta.operators.toTypedArray())
                if (meta.categories.isNotEmpty()) category.setSimpleItems(meta.categories.toTypedArray())
            }
        }.start()

        btnCancel.setOnClickListener { finish() }

        btnUpload.setOnClickListener {
            if (!Prefs.isConfigured(this)) {
                Toast.makeText(
                    this,
                    "Set the device token in Settings first.",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(Intent(this, SettingsActivity::class.java))
                return@setOnClickListener
            }
            val src = sourceUrl.text?.toString()?.trim().orEmpty()
            val op = operator.text?.toString()?.trim().orEmpty()
            val cat = category.text?.toString()?.trim().orEmpty()
            val n = note.text?.toString()?.trim().orEmpty()
            setBusy(true, btnUpload, btnCancel, progress, status)
            uploadAll(src, op, cat, n, status, progress, btnUpload, btnCancel)
        }
    }

    private fun uploadAll(
        source: String,
        operator: String,
        category: String,
        note: String,
        status: TextView,
        progress: ProgressBar,
        btnUpload: MaterialButton,
        btnCancel: MaterialButton
    ) {
        Thread {
            val results = ArrayList<UploadResult>()
            val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            for ((i, uri) in uris.withIndex()) {
                val base = if (uris.size == 1) "screenshot-$ts" else "screenshot-$ts-${i + 1}"

                runOnUiThread { status.text = "Sending ${i + 1}/${uris.size}…" }

                val bytes = try {
                    contentResolver.openInputStream(uri).use { it!!.readBytes() }
                } catch (e: Exception) {
                    null
                }

                if (bytes == null) {
                    results.add(UploadResult(false, null, "Could not read image"))
                } else {
                    results.add(Uploader.upload(this, bytes, base, source, operator, category, note))
                }
            }

            runOnUiThread {
                setBusy(false, btnUpload, btnCancel, progress, status)
                val ok = results.count { it.success }
                if (ok == uris.size) {
                    Toast.makeText(
                        this,
                        "Sent $ok/${uris.size} to editor inbox ✓",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    val firstErr = results.firstOrNull { !it.success }?.message ?: "Send failed"
                    status.text = "Sent $ok/${uris.size}. Error: $firstErr"
                }
            }
        }.start()
    }

    private fun setBusy(
        busy: Boolean,
        btnUpload: MaterialButton,
        btnCancel: MaterialButton,
        progress: ProgressBar,
        status: TextView
    ) {
        btnUpload.isEnabled = !busy
        btnCancel.isEnabled = !busy
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        if (busy) status.text = "Sending…"
    }

    /**
     * Source URL prefill. Prefer a full URL (with a path) from any source; otherwise
     * fall back to the accessibility-captured Chrome URL, then the share text, then clipboard.
     */
    private fun prefilledSourceUrl(): String {
        val extra = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            .let { if (it.startsWith("http://") || it.startsWith("https://")) it else "" }
        val clip = clipboardUrl()
        val acc = recentAccessibilityUrl()

        listOf(extra, clip, acc).firstOrNull { it.isNotEmpty() && hasPath(it) }?.let { return it }
        return acc.ifEmpty { extra.ifEmpty { clip } }
    }

    private fun clipboardUrl(): String = try {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val c = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
        if (c.startsWith("http://") || c.startsWith("https://")) c else ""
    } catch (e: Exception) {
        ""
    }

    private fun recentAccessibilityUrl(): String {
        val age = System.currentTimeMillis() - Prefs.lastChromeUrlTime(this)
        return if (age in 0..600_000) Prefs.lastChromeUrl(this) else ""
    }

    private fun hasPath(url: String): Boolean {
        val after = url.substringAfter("://", url)
        val slash = after.indexOf('/')
        return slash in 0 until (after.length - 1)
    }

    @Suppress("DEPRECATION")
    private fun extractUris(intent: Intent): List<Uri> {
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val u = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                listOfNotNull(u)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                list ?: emptyList()
            }
            else -> emptyList()
        }
    }
}
