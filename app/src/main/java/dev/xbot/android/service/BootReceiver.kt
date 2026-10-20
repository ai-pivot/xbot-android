package dev.xbot.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.xbot.android.XbotApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = (context.applicationContext as XbotApp).container.prefs
        if (!prefs.notificationsEnabled) return
        if (prefs.activeChatId.isBlank()) return
        runCatching { startNotificationService(context) }
    }
}
