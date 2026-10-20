package dev.xbot.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import dev.xbot.android.MainActivity
import dev.xbot.android.R
import dev.xbot.android.XbotApp
import dev.xbot.android.data.SseState
import dev.xbot.android.data.XbotSseClient
import dev.xbot.android.data.WsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps one SSE connection alive while the app is backgrounded, and turns the
 * events into local notifications.
 *
 * This is the only way to get real-time notifications without server-side push:
 * xbot has no FCM/WebPush transport. The trade-off is the mandatory ongoing
 * notification and Android's/OEMs' background restrictions.
 */
class ChatNotificationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sse: XbotSseClient? = null
    private var eventsJob: Job? = null
    private var stateJob: Job? = null
    private var lastText: String = ""
    private var lastNotifyAt: Long = 0L

    private val prefs by lazy { (application as XbotApp).container.prefs }
    private val api by lazy { (application as XbotApp).container.api }

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val chatId = prefs.activeChatId
        if (chatId.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat(chatId)
        connect(chatId, prefs.activeChannel.ifBlank { "web" })
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        eventsJob?.cancel()
        stateJob?.cancel()
        sse?.stop()
        sse = null
        scope.cancel()
        super.onDestroy()
    }

    private fun connect(chatId: String, channel: String) {
        if (sse?.currentChatId() == chatId) return
        eventsJob?.cancel()
        stateJob?.cancel()
        sse?.stop()

        val client = XbotSseClient(api, prefs, scope)
        sse = client
        eventsJob = scope.launch {
            client.events.collect { message -> handleEvent(message) }
        }
        stateJob = scope.launch {
            client.state.collect { state ->
                if (state is SseState.Error && state.unauthorized) {
                    notify(
                        id = NOTIFY_AUTH,
                        title = "xbot 需要重新登录",
                        body = "登录会话已失效，打开应用重新登录后通知才会恢复。",
                    )
                }
            }
        }
        client.start(chatId, channel)
    }

    private fun handleEvent(message: WsMessage) {
        when (message.type) {
            "ask_user" -> {
                val question = message.progress?.questions?.firstOrNull()?.question
                    ?: "Agent 有一个问题需要你回答"
                notify(
                    id = NOTIFY_ASK,
                    title = "xbot 需要你的回答",
                    body = question,
                    highPriority = true,
                )
            }

            "text" -> {
                if (message.content.isNotBlank()) {
                    lastText = message.content
                }
            }

            "session" -> {
                val action = message.session?.action
                if (action == "idle" && message.session?.chatId == prefs.activeChatId) {
                    val body = lastText.ifBlank { "本轮回复已完成。" }
                    lastText = ""
                    notify(
                        id = NOTIFY_DONE,
                        title = "xbot 回复完成",
                        body = body.take(180),
                    )
                }
            }
        }
    }

    private fun notify(
        id: Int,
        title: String,
        body: String,
        highPriority: Boolean = false,
    ) {
        if (AppVisibility.isForeground) return
        // POST_NOTIFICATIONS is runtime-granted on Android 13+. The toggle asks
        // for it before enabling the service; re-check here because the user can
        // revoke it in system settings at any time.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt < 800L) return
        lastNotifyAt = now

        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chat_id", prefs.activeChatId)
        }
        val pending = PendingIntent.getActivity(
            this,
            id,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(
                if (highPriority) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT,
            )
            .build()
        runCatching {
            NotificationManagerCompat.from(this).notify(id, notification)
        }
    }
    private fun startForegroundCompat(chatId: String) {
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chat_id", chatId)
        }
        val pending = PendingIntent.getActivity(
            this,
            FG_ID,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("xbot 正在保持连接")
            .setContentText("会话：${chatId.take(40)}")
            .setContentIntent(pending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        } else {
            0
        }
        ServiceCompat.startForeground(this, FG_ID, notification, type)
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                getString(R.string.notification_service_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_service_channel_desc)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGES,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.notification_channel_desc)
            },
        )
    }

    private companion object {
        const val CHANNEL_SERVICE = "xbot_service"
        const val CHANNEL_MESSAGES = "xbot_messages"
        const val FG_ID = 1001
        const val NOTIFY_DONE = 2001
        const val NOTIFY_ASK = 2002
        const val NOTIFY_AUTH = 2003
    }
}
