package dev.xbot.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import dev.xbot.android.ui.XbotNavHost
import dev.xbot.android.ui.theme.XbotTheme

class MainActivity : ComponentActivity() {
    /** Set by a push notification tap; consumed by XbotNavHost. */
    private val deepLinkChatId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkChatId.value = intent?.getStringExtra("chat_id")
        setContent {
            XbotTheme {
                XbotNavHost(deepLinkChatId = deepLinkChatId.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkChatId.value = intent.getStringExtra("chat_id")
    }
}
