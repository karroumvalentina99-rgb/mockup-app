package org.legalbet.mockupupload

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Server + auth settings. The device token is a per-user secret, so everything is
 * stored in EncryptedSharedPreferences (with a plain-prefs fallback if the device
 * keystore is unavailable). No secrets are baked into the app.
 */
object Prefs {
    private const val SECURE_FILE = "mockup_secure_prefs"
    private const val PLAIN_FILE = "mockup_prefs"

    private const val KEY_BASE_URL = "base_url"
    private const val KEY_TOKEN = "device_token"
    private const val KEY_CF_ID = "cf_client_id"
    private const val KEY_CF_SECRET = "cf_client_secret"
    private const val KEY_LAST_URL = "last_chrome_url"
    private const val KEY_LAST_URL_TIME = "last_chrome_url_time"

    const val DEFAULT_BASE_URL = "https://img.lbtools.org"

    @Volatile private var cached: SharedPreferences? = null

    private fun sp(c: Context): SharedPreferences {
        cached?.let { return it }
        val app = c.applicationContext
        val prefs = try {
            val alias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                SECURE_FILE,
                alias,
                app,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Rare: keystore unavailable / corrupted. Fall back so the app still works.
            app.getSharedPreferences(PLAIN_FILE, Context.MODE_PRIVATE)
        }
        cached = prefs
        return prefs
    }

    fun baseUrl(c: Context): String =
        sp(c).getString(KEY_BASE_URL, DEFAULT_BASE_URL)!!.trim().trimEnd('/')

    /** The device token (mdt_…), sent as the Bearer credential. */
    fun token(c: Context): String = sp(c).getString(KEY_TOKEN, "")!!.trim()

    fun cfId(c: Context): String = sp(c).getString(KEY_CF_ID, "")!!.trim()

    fun cfSecret(c: Context): String = sp(c).getString(KEY_CF_SECRET, "")!!.trim()

    /** True once the device token is set (author is derived server-side from it). */
    fun isConfigured(c: Context): Boolean = token(c).isNotEmpty()

    /** Latest URL seen in Chrome's address bar (written by UrlAccessibilityService). */
    fun lastChromeUrl(c: Context): String = sp(c).getString(KEY_LAST_URL, "")!!

    fun lastChromeUrlTime(c: Context): Long = sp(c).getLong(KEY_LAST_URL_TIME, 0L)

    fun setLastChromeUrl(c: Context, url: String) {
        sp(c).edit()
            .putString(KEY_LAST_URL, url)
            .putLong(KEY_LAST_URL_TIME, System.currentTimeMillis())
            .apply()
    }

    fun save(c: Context, baseUrl: String, token: String, cfId: String, cfSecret: String) {
        sp(c).edit()
            .putString(KEY_BASE_URL, baseUrl.trim())
            .putString(KEY_TOKEN, token.trim())
            .putString(KEY_CF_ID, cfId.trim())
            .putString(KEY_CF_SECRET, cfSecret.trim())
            .apply()
    }
}
