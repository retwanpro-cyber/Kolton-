package com.radwan.nova.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radwan.nova.data.models.Chat
import com.radwan.nova.data.remote.SupabaseManager
import com.radwan.nova.data.remote.RemoteMessage
import com.radwan.nova.data.remote.RemoteProfile
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        setOnline(true)
        loadChats()
        startPeriodicSync()
    }

    fun loadChats() {
        viewModelScope.launch {
            _isLoading.value = true
            fetchChats()
            _isLoading.value = false
        }
    }

    fun fetchRemoteChats() {
        loadChats()
    }

    private fun setOnline(online: Boolean) {
        viewModelScope.launch {
            try {
                val myId = SupabaseManager.auth.currentUserOrNull()?.id ?: return@launch
                SupabaseManager.postgrest["profiles"].update(
                    mapOf("is_online" to online)
                ) {
                    filter { eq("id", myId) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun logout(onLoggedOut: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                // تعيين الحساب كغير متصل فوراً عند الخروج
                val myId = SupabaseManager.auth.currentUserOrNull()?.id
                if (!myId.isNullOrBlank()) {
                    SupabaseManager.postgrest["profiles"].update(
                        mapOf("is_online" to false)
                    ) {
                        filter { eq("id", myId) }
                    }
                }
                SupabaseManager.auth.signOut()
                onLoggedOut()
            } catch (e: Exception) {
                e.printStackTrace()
                onLoggedOut()
            }
        }
    }

    private fun startPeriodicSync() {
        viewModelScope.launch {
            while (isActive) {
                delay(2000)
                fetchChats()
            }
        }
    }

    private suspend fun fetchChats() {
        try {
            val myUser = SupabaseManager.auth.currentUserOrNull()
            val myId = myUser?.id?.lowercase() ?: return

            // 1. جلب الرسائل
            val allMessages = SupabaseManager.postgrest["messages"]
                .select()
                .decodeList<RemoteMessage>()

            // 2. تصفية الغرف التي ينتمي إليها المستخدم الحالي فقط
            val myMessages = allMessages.filter { msg ->
                val room = msg.chat_id.lowercase()
                room.contains("__") && room.split("__").contains(myId)
            }

            if (myMessages.isEmpty()) {
                _chats.value = emptyList()
                return
            }

            // 3. جلب جميع البروفايلات لمعرفة أسماء وصور الأطراف الأخرى
            val allProfiles = try {
                SupabaseManager.postgrest["profiles"]
                    .select()
                    .decodeList<RemoteProfile>()
            } catch (e: Exception) {
                emptyList()
            }
            val profileMap = allProfiles.associateBy { it.id.lowercase() }

            // 4. تجميع آخر رسالة لكل غرفة
            val roomMap = mutableMapOf<String, RemoteMessage>()
            for (msg in myMessages) {
                roomMap[msg.chat_id] = msg
            }

            val chatList = roomMap.map { (roomId, lastMsg) ->
                val participants = roomId.split("__")
                val otherUserId = participants.firstOrNull { it != myId } ?: participants[0]
                val otherProfile = profileMap[otherUserId]
                val displayName = otherProfile?.username ?: otherProfile?.full_name ?: (if (lastMsg.sender_id != myId) lastMsg.sender_name else "مستخدم") ?: "محادثة خاصة"

                Chat(
                    id = roomId, // نمرر الـ roomId بالكامل لفتح نفس الغرفة بالضبط!
                    name = displayName,
                    lastMessage = lastMsg.text,
                    lastMessageTime = lastMsg.created_at ?: "",
                    avatarUrl = otherProfile?.avatar_url ?: "",
                    unreadCount = 0
                )
            }

            _chats.value = chatList
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
