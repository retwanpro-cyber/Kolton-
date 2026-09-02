package com.radwan.nova

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.radwan.nova.data.remote.SupabaseManager
import com.radwan.nova.ui.screens.auth.AuthScreen
import com.radwan.nova.ui.screens.chat.ChatScreen
import com.radwan.nova.ui.screens.home.HomeScreen
import com.radwan.nova.ui.screens.settings.SettingsScreen
import com.radwan.nova.ui.theme.NovaChatTheme
import io.github.jan.supabase.gotrue.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NovaChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val currentUser = SupabaseManager.auth.currentUserOrNull()
                    val startDestination = if (currentUser != null) "home" else "auth"

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable("auth") {
                            AuthScreen(
                                onAuthSuccess = {
                                    navController.navigate("home") {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("home") {
                            HomeScreen(
                                onChatClick = { chatId, title ->
                                    navController.navigate("chat/$chatId/$title")
                                },
                                onSettingsClick = {
                                    navController.navigate("settings")
                                },
                                onLogout = {
                                    navController.navigate("auth") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                onBackClick = {
                                    navController.popBackStack()
                                },
                                onProfileClick = {
                                    /* فتح تعديل الملف الشخصي لاحقاً */
                                }
                            )
                        }

                        composable(
                            route = "chat/{chatId}/{title}",
                            arguments = listOf(
                                navArgument("chatId") { type = NavType.StringType },
                                navArgument("title") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
                            val title = backStackEntry.arguments?.getString("title") ?: "Chat"
                            ChatScreen(
                                chatId = chatId,
                                chatTitle = title,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
