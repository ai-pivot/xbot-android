package dev.xbot.android

import android.app.Application
import dev.xbot.android.data.AppPrefs
import dev.xbot.android.data.XbotApi
import dev.xbot.android.service.AppVisibility
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
        AppVisibility.install()
    }
}
