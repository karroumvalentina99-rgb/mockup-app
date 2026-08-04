package org.legalbet.mockupupload

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Reads Chrome's address bar (omnibox) and remembers the current page URL, so a
 * shared screenshot can be tagged with its source URL automatically. Scoped to
 * com.android.chrome and only ever reads the `url_bar` node — nothing else.
 */
class UrlAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName != CHROME_PKG) return
        val root = rootInActiveWindow ?: return
        try {
            val nodes = root.findAccessibilityNodeInfosByViewId("$CHROME_PKG:id/url_bar")
            if (nodes.isNullOrEmpty()) return
            val normalized = normalize(nodes[0].text?.toString().orEmpty()) ?: return
            if (normalized != lastSeen) {
                lastSeen = normalized
                Prefs.setLastChromeUrl(this, normalized)
            }
        } catch (e: Exception) {
            // Ignore — best-effort capture.
        }
    }

    override fun onInterrupt() {}

    /** Turn an omnibox string into a URL, or null if it's a search hint / not a URL. */
    private fun normalize(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty() || t.contains(' ')) return null // URLs have no spaces; hints/searches do
        if (t.startsWith("http://") || t.startsWith("https://")) return t
        if (!t.contains('.')) return null               // not a domain
        return "https://$t"
    }

    companion object {
        private const val CHROME_PKG = "com.android.chrome"

        @Volatile
        var lastSeen: String? = null
    }
}
