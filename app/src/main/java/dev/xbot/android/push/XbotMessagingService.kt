package dev.xbot.android.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.xbot.android.XbotApp

/**
 * Receives FCM data messages (mode=data) and renders them locally.
 *
 * Server payload (all values are strings):
 *   { kind, chat_id, title, body, request_id, ts }
 *
 * A notification message (mode=notification) is rendered by the OS before this
 * callback; the fallbacks below still work so both modes produce a sensible
 * notification during the transition.
 */
class XbotMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushRegistrar.onNewToken(application as XbotApp, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val kind = data["kind"].orEmpty()
        val title = data["title"].takeUnless { it.isNullOrBlank() }
            ?: message.notification?.title
            ?: "xbot"
        val body = data["body"].takeUnless { it.isNullOrBlank() }
            ?: message.notification?.body
            ?: "打开 xbot 查看详情。"
        val chatId = data["chat_id"].orEmpty()
        val requestId = data["request_id"].orEmpty()
        Notifications.show(this, kind, title, body, chatId, requestId)
    }
}
