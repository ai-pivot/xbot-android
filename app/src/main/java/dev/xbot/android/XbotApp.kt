package dev.xbot.android

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import android.os.Build
import dev.xbot.android.data.AppPrefs
import dev.xbot.android.data.XbotApi
import dev.xbot.android.logs.AppLog
import dev.xbot.android.logs.BuildInfo
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
        BuildInfo.versionName = BuildConfig.VERSION_NAME
        BuildInfo.versionCode = BuildConfig.VERSION_CODE.toLong()
        BuildInfo.androidRelease = Build.VERSION.RELEASE.orEmpty()
        BuildInfo.sdkInt = Build.VERSION.SDK_INT
        AppLog.install(this)
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
