package dev.xbot.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.xbot.android.ui.XbotNavHost
import dev.xbot.android.ui.theme.XbotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XbotTheme {
                XbotNavHost()
            }
        }
    }
}
