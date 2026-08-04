package org.legalbet.mockupupload

import android.content.Context
import android.os.Build
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UploadResult(val success: Boolean, val id: String?, val message: String)

data class InboxMeta(
    val operators: List<String>,
    val categories: List<String>,
    val themes: List<String>
)

/**
 * Sends a screenshot to the Mockup Inbox (multipart/form-data). The editor at
 * /phone-mockup auto-pulls new inbox items every ~20s. Blocking — call off the UI thread.
 */
object Uploader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun addCfHeaders(ctx: Context, b: Request.Builder) {
        val id = Prefs.cfId(ctx)
        val secret = Prefs.cfSecret(ctx)
        if (id.isNotEmpty() && secret.isNotEmpty()) {
            b.header("CF-Access-Client-Id", id)
            b.header("CF-Access-Client-Secret", secret)
        }
    }

    /** Detect image type from magic bytes → (mime, extension). Defaults to PNG. */
    private fun detectMime(b: ByteArray): Pair<String, String> {
        fun u(i: Int) = if (i < b.size) b[i].toInt() and 0xFF else -1
        if (u(0) == 0x89 && u(1) == 0x50 && u(2) == 0x4E && u(3) == 0x47) return "image/png" to "png"
        if (u(0) == 0xFF && u(1) == 0xD8) return "image/jpeg" to "jpg"
        if (u(0) == 0x52 && u(1) == 0x49 && u(2) == 0x46 && u(3) == 0x46 &&
            u(8) == 0x57 && u(9) == 0x45 && u(10) == 0x42 && u(11) == 0x50
        ) return "image/webp" to "webp"
        return "image/png" to "png"
    }

    fun upload(
        ctx: Context,
        imageBytes: ByteArray,
        filenameBase: String,
        sourceUrl: String,
        operator: String,
        category: String,
        note: String
    ): UploadResult {
        if (Prefs.token(ctx).isEmpty()) {
            return UploadResult(false, null, "Set the device token in Settings first.")
        }

        val (mime, ext) = detectMime(imageBytes)
        val fname = (filenameBase.ifBlank { "screenshot" }) + "." + ext
        val fileBody = imageBytes.toRequestBody(mime.toMediaType())

        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", fname, fileBody)
            .addFormDataPart("source", "android")
            .addFormDataPart("device", Build.MODEL ?: "android")
        if (sourceUrl.isNotEmpty()) form.addFormDataPart("source_url", sourceUrl)
        if (operator.isNotEmpty()) form.addFormDataPart("operator", operator)
        if (category.isNotEmpty()) form.addFormDataPart("category", category)
        if (note.isNotEmpty()) form.addFormDataPart("note", note)

        val req = Request.Builder()
            .url(Prefs.baseUrl(ctx) + "/mockup-upload/inbox/upload")
            .header("Authorization", "Bearer " + Prefs.token(ctx))
            .post(form.build())
        addCfHeaders(ctx, req)

        return try {
            client.newCall(req.build()).execute().use { resp ->
                val ct = resp.header("Content-Type") ?: ""
                val body = resp.body?.string().orEmpty()
                val looksLikeCf = ct.contains("html", true) ||
                    body.contains("Cloudflare Access", true) ||
                    body.contains("<html", true)

                when {
                    looksLikeCf -> UploadResult(
                        false, null,
                        "Blocked by Cloudflare Access — check the CF service token in Settings."
                    )
                    resp.isSuccessful -> {
                        val id = runCatching { JSONObject(body).optString("id") }
                            .getOrNull()?.takeIf { it.isNotEmpty() }
                        UploadResult(true, id, "Sent to editor inbox")
                    }
                    resp.code == 401 -> UploadResult(
                        false, null,
                        "401 — device token invalid or missing. Update it in Settings."
                    )
                    resp.code == 413 -> UploadResult(false, null, "413 — image too large (max 25 MB).")
                    resp.code == 400 -> UploadResult(false, null, "400 — " + detail(body))
                    else -> UploadResult(false, null, "HTTP ${resp.code} — " + detail(body))
                }
            }
        } catch (e: Exception) {
            UploadResult(false, null, "Network error: ${e.message}")
        }
    }

    private fun detail(body: String): String {
        val d = runCatching { JSONObject(body).optString("detail") }.getOrNull().orEmpty()
        return d.ifEmpty { body.take(140) }
    }

    /** Fetches dropdown options. GET /inbox/meta needs no device token, only the CF edge. */
    fun fetchMeta(ctx: Context): InboxMeta? {
        val req = Request.Builder()
            .url(Prefs.baseUrl(ctx) + "/mockup-upload/inbox/meta")
            .get()
        addCfHeaders(ctx, req)
        return try {
            client.newCall(req.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                if (body.contains("<html", true)) return null
                val j = JSONObject(body)
                InboxMeta(
                    toList(j.optJSONArray("operators")),
                    toList(j.optJSONArray("categories")),
                    toList(j.optJSONArray("themes"))
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun toList(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        val out = ArrayList<String>(a.length())
        for (i in 0 until a.length()) out.add(a.optString(i))
        return out
    }

    /** Posts a tiny 1x1 PNG to the inbox to verify both the CF edge and the device token. */
    fun testConnection(ctx: Context): UploadResult {
        val onePx = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
            Base64.DEFAULT
        )
        return upload(ctx, onePx, "android-connection-test", "", "", "", "connection test")
    }
}
