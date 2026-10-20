package dev.xbot.android.service

import android.content.Context
import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/** Tracks whether any Activity is visible (foreground service does not count). */
object AppVisibility : DefaultLifecycleObserver {
    @Volatile
    var isForeground: Boolean = false
        private set

    fun install() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        isForeground = true
    }

    override fun onStop(owner: LifecycleOwner) {
        isForeground = false
    }
}

fun startNotificationService(context: Context) {
    val intent = Intent(context, ChatNotificationService::class.java)
    androidx.core.content.ContextCompat.startForegroundService(context, intent)
}

fun stopNotificationService(context: Context) {
    context.stopService(Intent(context, ChatNotificationService::class.java))
}
