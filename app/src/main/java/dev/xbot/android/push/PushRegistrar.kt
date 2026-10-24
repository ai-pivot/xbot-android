package dev.xbot.android.push

import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import dev.xbot.android.BuildConfig
import dev.xbot.android.XbotApp
import dev.xbot.android.data.UnauthorizedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Registers this device's FCM token with the xbot server (POST /api/push/register).
 *
 * Registration is best-effort: no server / no login / no GMS simply leaves
 * pushRegistered=false; the app keeps working through the foreground SSE.
 */
object PushRegistrar {
    private const val TAG = "PushRegistrar"
    const val PROVIDER = "fcm"
    const val PLATFORM = "android"

    /** Fetches the token and registers when a session exists. */
    fun onAppStart(app: XbotApp) {
        runCatching {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                val token = if (task.isSuccessful) task.result.orEmpty() else ""
                if (token.isBlank()) {
                    Log.w(TAG, "getToken failed: ${task.exception}")
                    return@addOnCompleteListener
                }
                app.container.prefs.fcmToken = token
                registerIfLoggedIn(app, token)
            }
        }.onFailure { Log.w(TAG, "FCM unavailable on this device", it) }
    }

    fun onNewToken(app: XbotApp, token: String) {
        app.container.prefs.fcmToken = token
        registerIfLoggedIn(app, token)
    }

    /** Called after a successful login (the cookie is now valid). */
    fun onLogin(app: XbotApp) {
        val token = app.container.prefs.fcmToken
        if (token.isNotBlank()) {
            registerIfLoggedIn(app, token)
        } else {
            onAppStart(app)
        }
    }

    /** Best-effort unregister before the cookie is cleared. */
    suspend fun onLogout(app: XbotApp) {
        val token = app.container.prefs.fcmToken
        app.container.prefs.pushRegistered = false
        if (token.isBlank()) return
        runCatching { app.container.api.pushUnregister(PROVIDER, token) }
    }

    /** Sends a test notification to every target of the current user. */
    suspend fun sendTest(app: XbotApp): Int {
        val api = app.container.api
        return try {
            api.pushTest()
        } catch (e: UnauthorizedException) {
            if (!api.trySilentLogin()) throw e
            api.pushTest()
        }
    }

    private fun registerIfLoggedIn(app: XbotApp, token: String) {
        if (token.isBlank() || app.container.prefs.cookies.isEmpty()) return
        app.container.appScope.launch(Dispatchers.IO) {
            runCatching { register(app, token) }
                .onSuccess {
                    app.container.prefs.pushRegistered = true
                    Log.i(TAG, "registered with server")
                }
                .onFailure {
                    app.container.prefs.pushRegistered = false
                    Log.w(TAG, "register failed: ${it.message}")
                }
        }
    }

    private suspend fun register(app: XbotApp, token: String) {
        val api = app.container.api
        val device = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
            .joinToString(" ").trim().ifBlank { "Android" }
        val call: suspend () -> Unit = {
            api.pushRegister(
                provider = PROVIDER,
                platform = PLATFORM,
                address = token,
                metadata = mapOf("device_name" to device),
                deviceName = device,
                appVersion = BuildConfig.VERSION_NAME,
            )
        }
        try {
            call()
        } catch (e: UnauthorizedException) {
            if (!api.trySilentLogin()) throw e
            call()
        }
    }
}
