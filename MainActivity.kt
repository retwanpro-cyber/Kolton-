package com.radwan.nova

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("nova_auth_prefs", Context.MODE_PRIVATE)

        setContent {
            NovaChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    var isCheckingAuth by remember { mutableStateOf(true) }
                    var startRoute by remember { mutableStateOf("auth") }

                    LaunchedEffect(Unit) {
                        try {
                            var current = SupabaseManager.auth.currentUserOrNull()
                            if (current == null) {
                                delay(300)
                                current = SupabaseManager.auth.currentUserOrNull()
                            }

                            val localSaved = prefs.getString("saved_user_id", null)
                            if (current != null || !localSaved.isNullOrBlank()) {
                                startRoute = "home"
                            } else {
                                startRoute = "auth"
                            }
                        } catch (e: Exception) {
                            val localSaved = prefs.getString("saved_user_id", null)
                            startRoute = if (!localSaved.isNullOrBlank()) "home" else "auth"
                        } finally {
                            isCheckingAuth = false
                        }
                    }

                    if (isCheckingAuth) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0F172A)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF2563EB),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    } else {
                        NavHost(
                            navController = navController,
                            startDestination = startRoute
                        ) {
                            composable("auth") {
                                AuthScreen(
                                    onAuthSuccess = {
                                        val uid = SupabaseManager.auth.currentUserOrNull()?.id ?: "logged_in_user"
                                        prefs.edit().putString("saved_user_id", uid).putBoolean("is_logged_in", true).commit()
                                        navController.navigate("home") {
                                            popUpTo("auth") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable("home") {
                                HomeScreen(
                                    onChatClick = { chatId: String, title: String ->
                                        navController.navigate("chat/$chatId/$title")
                                    },
                                    onSettingsClick = {
                                        navController.navigate("settings")
                                    },
                                    onLogout = {
                                        prefs.edit().clear().commit()
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
                                    onProfileClick = {}
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
}
