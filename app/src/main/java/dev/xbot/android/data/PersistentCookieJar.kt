package dev.xbot.android.data

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * CookieJar backed by app-private SharedPreferences.
 *
 * xbot authenticates REST + SSE with the `xbot_session` cookie, so the cookie
 * must survive process death and be readable by the background notification
 * service. Cookies are stored as Set-Cookie strings (OkHttp's Cookie.toString
 * round-trips through Cookie.parse).
 */
class PersistentCookieJar(private val prefs: AppPrefs) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val stored = prefs.cookies.toMutableSet()
        val names = cookies.map { it.name }.toSet()
        // Replace stale values for the same cookie names.
        stored.removeAll { raw ->
            val parsed = parse(url, raw)
            parsed != null && parsed.name in names
        }
        cookies.forEach { stored.add(it.toString()) }
        prefs.cookies = stored
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return prefs.cookies.mapNotNull { raw -> parse(url, raw) }
            .filter { it.matches(url) && (!it.persistent || it.expiresAt > now) }
    }

    fun clear() {
        prefs.cookies = emptySet()
    }

    private fun parse(url: HttpUrl, raw: String): Cookie? =
        runCatching { Cookie.parse(url, raw) }.getOrNull()
}
