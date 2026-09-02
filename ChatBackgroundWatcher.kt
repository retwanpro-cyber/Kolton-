package com.radwan.nova.utils

import android.content.Context
import com.radwan.nova.data.remote.RemoteMessage
import com.radwan.nova.data.remote.SupabaseManager
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object ChatBackgroundWatcher {
    private var watcherJob: Job? = null
    private var lastSeenTimestamp: String = ""

    fun startWatching(context: Context) {
        if (watcherJob?.isActive == true) return

        val prefs = context.getSharedPreferences("nova_notification_prefs", Context.MODE_PRIVATE)
        lastSeenTimestamp = prefs.getString("last_msg_time", "") ?: ""

        watcherJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val myId = SupabaseManager.auth.currentUserOrNull()?.id
                    if (!myId.isNullOrBlank()) {
                        val messages = SupabaseManager.postgrest["messages"]
                            .select()
                            .decodeList<RemoteMessage>()

                        // تصفية الرسائل التي وصلت للمستخدم الحالي ولم يرسلها هو
                        val incoming = messages.filter { msg ->
                            val isForMe = msg.chat_id?.contains(myId) == true || msg.receiver_id == myId
                            val notMine = msg.sender_id != myId
                            isForMe && notMine
                        }

                        if (lastSeenTimestamp.isBlank()) {
                            // أول تشغيل نحفظ أحدث توقيت لعدم تكرار الإشعارات القديمة
                            lastSeenTimestamp = incoming.lastOrNull()?.created_at ?: System.currentTimeMillis().toString()
                            prefs.edit().putString("last_msg_time", lastSeenTimestamp).apply()
                        } else {
                            // البحث عن أي رسائل جديدة بعد التوقيت الأخير
                            val newMessages = incoming.filter {
                                (it.created_at ?: "") > lastSeenTimestamp
                            }

                            for (newMsg in newMessages) {
                                val sender = if (!newMsg.sender_name.isNullOrBlank()) newMsg.sender_name else "رسالة جديدة"
                                val body = newMsg.text ?: "لديك رسالة جديدة"
                                val chatId = newMsg.sender_id ?: newMsg.chat_id ?: ""

                                NotificationHelper.showMessageNotification(
                                    context = context,
                                    senderName = sender,
                                    messageText = body,
                                    chatId = chatId
                                )
                                lastSeenTimestamp = newMsg.created_at ?: System.currentTimeMillis().toString()
                                prefs.edit().putString("last_msg_time", lastSeenTimestamp).apply()
                            }
                        }
                    }
                } catch (e: Exception) {
                    // في حال انقطاع النت يتم المحاولة بعد قليل بهدوء
                }
                delay(3000) // فحص دوري كل 3 ثوانٍ
            }
        }
    }

    fun stopWatching() {
        watcherJob?.cancel()
        watcherJob = null
    }
}
