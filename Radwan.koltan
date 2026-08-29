package com.radwan.nova.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radwan.nova.data.models.Message
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _otherUserOnline = MutableStateFlow(false)
    val otherUserOnline: StateFlow<Boolean> = _otherUserOnline.asStateFlow()

    private var activeRoomId: String? = null
    private var targetOtherId: String? = null

    // توليد معرف موحد وصارم بين أي مستخدمين بالترتيب الأبجدي لمعرفاتهم
    fun generateRoomId(userA: String, userB: String): String {
        val a = userA.trim().lowercase()
        val b = userB.trim().lowercase()
        return if (a < b) "${a}__${b}" else "${b}__${a}"
    }

    fun loadMessages(otherUserIdOrRoom: String) {
        if (otherUserIdOrRoom.isBlank()) return

        val myId = SupabaseManager.auth.currentUserOrNull()?.id ?: ""
        
        // تحديد معرف الغرفة الحقيقي ومعرف الطرف الآخر
        val roomId = if (otherUserIdOrRoom.contains("__")) {
            val parts = otherUserIdOrRoom.split("__")
            targetOtherId = parts.firstOrNull { it != myId } ?: parts[0]
            otherUserIdOrRoom
        } else {
            targetOtherId = otherUserIdOrRoom
            if (myId.isNotBlank()) generateRoomId(myId, otherUserIdOrRoom) else otherUserIdOrRoom
        }

        activeRoomId = roomId

        viewModelScope.launch {
            _isLoading.value = true
            fetchMessages(roomId)
            checkOtherUserStatus()
            _isLoading.value = false

            // مزامنة حية كل 1.5 ثانية للرسائل وحالة الاتصال
            while (isActive && activeRoomId == roomId) {
                delay(1500)
                fetchMessages(roomId)
                checkOtherUserStatus()
            }
        }
    }

    private suspend fun checkOtherUserStatus() {
        val otherId = targetOtherId ?: return
        try {
            val profile = SupabaseManager.postgrest["profiles"]
                .select {
                    filter {
                        or {
                            eq("id", otherId)
                            eq("username", otherId)
                            eq("email", otherId)
                        }
                    }
                }
                .decodeSingleOrNull<RemoteProfile>()
            
            _otherUserOnline.value = profile?.is_online == true
        } catch (e: Exception) {
            _otherUserOnline.value = false
        }
    }

    private suspend fun fetchMessages(roomId: String) {
        try {
            val remoteList = SupabaseManager.postgrest["messages"]
                .select {
                    filter {
                        eq("chat_id", roomId)
                    }
                }
                .decodeList<RemoteMessage>()

            val mapped = remoteList.map { r ->
                Message(
                    id = r.id ?: UUID.randomUUID().toString(),
                    chatId = r.chat_id,
                    senderId = r.sender_id ?: "",
                    senderName = r.sender_name ?: "مستخدم",
                    text = r.text,
                    timestamp = r.created_at ?: ""
                )
            }
            _messages.value = mapped
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendMessage(otherUserIdOrRoom: String, text: String) {
        if (text.isBlank() || otherUserIdOrRoom.isBlank()) return

        val myUser = SupabaseManager.auth.currentUserOrNull()
        val myId = myUser?.id ?: "guest"
        val myName = myUser?.email?.substringBefore("@") ?: "مستخدم"

        val roomId = if (otherUserIdOrRoom.contains("__")) {
            otherUserIdOrRoom
        } else {
            generateRoomId(myId, otherUserIdOrRoom)
        }

        val newMsgId = UUID.randomUUID().toString()
        val currentTimeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val newMsg = Message(
            id = newMsgId,
            chatId = roomId,
            senderId = myId,
            senderName = myName,
            text = text.trim(),
            timestamp = currentTimeStr
        )
        
        _messages.value = _messages.value + newMsg

        viewModelScope.launch {
            try {
                val remote = RemoteMessage(
                    id = newMsgId,
                    chat_id = roomId,
                    sender_id = myId,
                    text = text.trim(),
                    sender_name = myName
                )
                SupabaseManager.postgrest["messages"].insert(remote)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
