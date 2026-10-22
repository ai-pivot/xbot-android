package dev.xbot.android.push

import android.util.Log
import dev.xbot.android.XbotApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Tells the server whether this device is currently visible, so it can skip
 * pushes to the device the user is already looking at. A 90s server-side lease
 * means a killed process simply stops refreshing and becomes eligible again.
 */
object PushPresence {
    private const val TAG = "PushPresence"
    private const val INTERVAL_MS = 30_000L
    private var job: Job? = null

    fun start(app: XbotApp) {
        stop(app, notifyServer = false)
        val token = app.container.prefs.fcmToken
        if (token.isBlank() || app.container.prefs.cookies.isEmpty()) return
        job = app.container.appScope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching {
                    app.container.api.pushPresence(PushRegistrar.PROVIDER, token, true)
                }.onFailure { Log.d(TAG, "presence true failed: ${it.message}") }
                delay(INTERVAL_MS)
            }
        }
    }

    fun stop(app: XbotApp) = stop(app, notifyServer = true)

    private fun stop(app: XbotApp, notifyServer: Boolean) {
        job?.cancel()
        job = null
        if (!notifyServer) return
        val token = app.container.prefs.fcmToken
        if (token.isBlank()) return
        app.container.appScope.launch(Dispatchers.IO) {
            runCatching {
                app.container.api.pushPresence(PushRegistrar.PROVIDER, token, false)
            }
        }
    }
}
