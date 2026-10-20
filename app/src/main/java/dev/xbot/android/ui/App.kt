package dev.xbot.android.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.xbot.android.XbotApp
import dev.xbot.android.auth.LoginScreen
import dev.xbot.android.sessions.SessionsScreen

@Composable
fun XbotNavHost() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val prefs = (context.applicationContext as XbotApp).container.prefs
    val startDestination = remember {
        if (prefs.serverUrl.isBlank() || prefs.cookies.isEmpty()) "login" else "sessions"
    }

    NavHost(navController = nav, startDestination = startDestination) {
        composable("login") {
            LoginScreen(
                onLoggedIn = {
                    nav.navigate("sessions") {
                        popUpTo("login") { inclusive = true }
                    }
                },
            )
        }

        composable("sessions") {
            SessionsScreen(
                onOpenChat = { chat ->
                    nav.navigate(
                        "chat/${Uri.encode(chat.chatId)}/" +
                            "${Uri.encode(chat.channel.ifBlank { "web" })}/" +
                            "${Uri.encode(chat.label.ifBlank { chat.chatId })}",
                    )
                },
                onSettings = { nav.navigate("settings") },
                onLogin = {
                    nav.navigate("login") {
                        popUpTo("sessions") { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = "chat/{chatId}/{channel}/{title}",
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("channel") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
            ),
        ) { entry ->
            val chatId = entry.arguments?.getString("chatId").orEmpty()
            val channel = entry.arguments?.getString("channel").orEmpty().ifBlank { "web" }
            val title = Uri.decode(entry.arguments?.getString("title").orEmpty())
            ChatScreen(
                chatId = chatId,
                channel = channel,
                title = title,
                onBack = { nav.popBackStack() },
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenTools = { nav.navigate("tools") },
                onOpenModels = { nav.navigate("models") },
                onLogout = {
                    nav.navigate("login") {
                        popUpTo("sessions") { inclusive = true }
                    }
                },
            )
        }

        composable("tools") {
            ToolsScreen(
                onBack = { nav.popBackStack() },
            )
        }

        composable("models") {
            ModelSettingsScreen(
                onBack = { nav.popBackStack() },
            )
        }
    }
}
