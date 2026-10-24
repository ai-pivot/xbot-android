package dev.xbot.android

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.xbot.android.data.AppPrefs
import dev.xbot.android.data.XbotApi
import dev.xbot.android.push.PushPresence
import dev.xbot.android.push.PushRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Application) {
    val prefs = AppPrefs(context)
    val api = XbotApi(prefs)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

class XbotApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // FCM data push: harmless when the device has no GMS (the task just fails).
        PushRegistrar.onAppStart(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                PushPresence.start(this@XbotApp)
            }

            override fun onStop(owner: LifecycleOwner) {
                PushPresence.stop(this@XbotApp)
            }
        })
    }
}
