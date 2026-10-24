package dev.xbot.android.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.xbot.android.MainActivity
import dev.xbot.android.R

/**
 * Local notification rendering for FCM data messages. The server sends a
 * data-only message, so the app owns the channel, icon, deep link and grouping.
 */
object Notifications {
    const val CHANNEL_MESSAGES = "xbot_messages"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGES,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
            },
        )
    }

    fun show(
        context: Context,
        kind: String,
        title: String,
        body: String,
        chatId: String,
        requestId: String,
    ) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val id = notificationId(kind, chatId, requestId)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chat_id", chatId)
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val highPriority = kind == "ask_user"
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "xbot" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(
                if (highPriority) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT,
            )
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    /** Stable per prompt/chat so a re-delivered FCM message replaces, not stacks. */
    private fun notificationId(kind: String, chatId: String, requestId: String): Int {
        val key = if (kind == "ask_user" && requestId.isNotBlank()) {
            "ask:$requestId"
        } else {
            "chat:$chatId"
        }
        return key.hashCode() and 0x7fffffff
    }
}
