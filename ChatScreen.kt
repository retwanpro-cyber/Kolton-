package com.radwan.nova.ui.screens.chat

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.radwan.nova.data.remote.RemoteProfile
import com.radwan.nova.data.remote.SupabaseManager
import com.radwan.nova.viewmodel.ChatViewModel
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import java.time.Instant

// دوال إدارة الحظر محلياً وبشكل دائم
private fun isUserBlocked(context: Context, userId: String): Boolean {
    val prefs = context.getSharedPreferences("nova_chat_prefs", Context.MODE_PRIVATE)
    val blockedSet = prefs.getStringSet("blocked_users", emptySet()) ?: emptySet()
    return blockedSet.contains(userId)
}

private fun setUserBlocked(context: Context, userId: String, blocked: Boolean) {
    val prefs = context.getSharedPreferences("nova_chat_prefs", Context.MODE_PRIVATE)
    val blockedSet = prefs.getStringSet("blocked_users", emptySet())?.toMutableSet() ?: mutableSetOf()
    if (blocked) {
        blockedSet.add(userId)
    } else {
        blockedSet.remove(userId)
    }
    prefs.edit().putStringSet("blocked_users", blockedSet).apply()
}

// دوال حفظ توقيت مسح المحادثة محلياً
private fun getClearedChatTime(context: Context, chatId: String): Long {
    val prefs = context.getSharedPreferences("nova_chat_prefs", Context.MODE_PRIVATE)
    return prefs.getLong("cleared_$chatId", 0L)
}

private fun setClearedChatTime(context: Context, chatId: String, timestamp: Long) {
    val prefs = context.getSharedPreferences("nova_chat_prefs", Context.MODE_PRIVATE)
    prefs.edit().putLong("cleared_$chatId", timestamp).apply()
}

private fun parseMessageTime(ts: String?): Long {
    if (ts.isNullOrBlank()) return 0L
    return try {
        Instant.parse(ts).toEpochMilli()
    } catch (e: Exception) {
        try {
            ts.toLong()
        } catch (e2: Exception) {
            0L
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    chatTitle: String,
    onBackClick: () -> Unit = {},
    viewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allMessages by viewModel.messages.collectAsState()
    val isOtherOnline by viewModel.otherUserOnline.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val currentUserId = SupabaseManager.auth.currentUserOrNull()?.id ?: ""

    // حالات الحظر ومسح المحادثة
    var isBlocked by remember { mutableStateOf(isUserBlocked(context, chatId)) }
    var lastClearedTime by remember { mutableLongStateOf(getClearedChatTime(context, chatId)) }

    // القائمة المنسدلة والحوارات
    var menuExpanded by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var showUnblockConfirmDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var otherUserProfile by remember { mutableStateOf<RemoteProfile?>(null) }

    // تصفية الرسائل بحيث لا تظهر الرسائل الممسوحة سابقاً
    val displayedMessages = remember(allMessages, lastClearedTime) {
        if (lastClearedTime == 0L) {
            allMessages
        } else {
            allMessages.filter { msg ->
                val time = parseMessageTime(msg.timestamp)
                time == 0L || time > lastClearedTime
            }
        }
    }

    LaunchedEffect(chatId) {
        if (chatId.isNotBlank()) {
            viewModel.loadMessages(chatId)
            isBlocked = isUserBlocked(context, chatId)
            lastClearedTime = getClearedChatTime(context, chatId)
            try {
                val profile = SupabaseManager.postgrest.from("profiles")
                    .select {
                        filter {
                            eq("id", chatId)
                        }
                    }.decodeSingleOrNull<RemoteProfile>()
                otherUserProfile = profile
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(displayedMessages.size) {
        if (displayedMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayedMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = chatTitle,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (isBlocked) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "محظور",
                                        color = Color(0xFFEF4444),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isBlocked) "تم حظر هذا المستخدم" else if (isOtherOnline) "متصل الآن" else "غير متصل",
                            fontSize = 11.sp,
                            color = if (isBlocked) Color(0xFFEF4444) else if (isOtherOnline) Color(0xFF10B981) else Color.LightGray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { if (chatId.isNotBlank()) viewModel.loadMessages(chatId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }

                    // أيقونة المزيد من الخيارات (⋮)
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "المزيد من الخيارات",
                                tint = Color.White
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(Color(0xFF1E293B))
                        ) {
                            // 1. عرض الملف الشخصي
                            DropdownMenuItem(
                                text = { Text("عرض الملف الشخصي", color = Color.White, fontSize = 14.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF60A5FA))
                                },
                                onClick = {
                                    menuExpanded = false
                                    showProfileDialog = true
                                }
                            )

                            // 2. زر الحظر / فك الحظر التفاعلي
                            if (isBlocked) {
                                DropdownMenuItem(
                                    text = { Text("إلغاء حظر المستخدم", color = Color(0xFF10B981), fontSize = 14.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color(0xFF10B981))
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        showUnblockConfirmDialog = true
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("حظر المستخدم", color = Color(0xFFEF4444), fontSize = 14.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFEF4444))
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        showBlockConfirmDialog = true
                                    }
                                )
                            }

                            // 3. مسح محتوى المحادثة
                            DropdownMenuItem(
                                text = { Text("مسح محتوى المحادثة", color = Color(0xFFF87171), fontSize = 14.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFF87171))
                                },
                                onClick = {
                                    menuExpanded = false
                                    showClearChatDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B))
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (displayedMessages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا توجد رسائل حالياً في هذه المحادثة",
                                color = Color(0xFF64748B),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    items(displayedMessages) { msg ->
                        val isMe = msg.senderId == currentUserId
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isMe) 16.dp else 4.dp,
                                    bottomEnd = if (isMe) 4.dp else 16.dp
                                ),
                                color = if (isMe) Color(0xFF2563EB) else Color(0xFF334155),
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = msg.text ?: "",
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                    if (!msg.timestamp.isNullOrBlank()) {
                                        Text(
                                            text = msg.timestamp.orEmpty().takeLast(8),
                                            color = Color.LightGray,
                                            fontSize = 10.sp,
                                            modifier = Modifier.align(Alignment.End)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // الشريط السفلي: إما حقل الكتابة أو شريط التنبيه بالحظر مع زر فك الحظر
            Surface(
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isBlocked) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "لقد قمت بحظر هذا المستخدم",
                                color = Color(0xFFEF4444),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Button(
                            onClick = {
                                setUserBlocked(context, chatId, false)
                                isBlocked = false
                                Toast.makeText(context, "تم إلغاء حظر $chatTitle بنجاح", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("إلغاء الحظر", color = Color(0xFF60A5FA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text(text = "اكتب رسالة...") },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    viewModel.sendMessage(chatId, messageText)
                                    messageText = ""
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    // 👤 1. نافذة عرض الملف الشخصي
    if (showProfileDialog) {
        Dialog(onDismissRequest = { showProfileDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        color = Color(0xFF2563EB)
                    ) {
                        if (!otherUserProfile?.avatar_url.isNullOrBlank()) {
                            AsyncImage(
                                model = otherUserProfile?.avatar_url,
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val displayName = otherUserProfile?.full_name?.takeIf { it.isNotBlank() }
                        ?: otherUserProfile?.name?.takeIf { it.isNotBlank() }
                        ?: chatTitle

                    val displayUsername = otherUserProfile?.username?.takeIf { it.isNotBlank() } ?: "user"

                    Text(
                        text = displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "@$displayUsername",
                        fontSize = 14.sp,
                        color = Color(0xFF60A5FA)
                    )

                    if (!otherUserProfile?.bio.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = otherUserProfile?.bio ?: "",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { showProfileDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إغلاق", color = Color.White)
                    }
                }
            }
        }
    }

    // 🚫 2. نافذة تأكيد الحظر
    if (showBlockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmDialog = false },
            title = { Text("حظر المستخدم", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من رغبتك في حظر $chatTitle؟ لن تتمكن من إرسال أو استلام رسائل منه.", color = Color(0xFFCBD5E1)) },
            containerColor = Color(0xFF1E293B),
            confirmButton = {
                Button(
                    onClick = {
                        setUserBlocked(context, chatId, true)
                        isBlocked = true
                        showBlockConfirmDialog = false
                        Toast.makeText(context, "تم حظر $chatTitle بنجاح", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("حظر", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmDialog = false }) {
                    Text("إلغاء", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    // 🔓 3. نافذة تأكيد فك الحظر
    if (showUnblockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showUnblockConfirmDialog = false },
            title = { Text("إلغاء حظر المستخدم", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("هل تريد إلغاء حظر $chatTitle والتمكن من التواصل معه مجدداً؟", color = Color(0xFFCBD5E1)) },
            containerColor = Color(0xFF1E293B),
            confirmButton = {
                Button(
                    onClick = {
                        setUserBlocked(context, chatId, false)
                        isBlocked = false
                        showUnblockConfirmDialog = false
                        Toast.makeText(context, "تم إلغاء حظر $chatTitle بنجاح", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("إلغاء الحظر", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnblockConfirmDialog = false }) {
                    Text("رجوع", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    // 🗑️ 4. نافذة تأكيد مسح المحادثة الفعلي
    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = { Text("مسح محتوى المحادثة", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من رغبتك في حذف جميع الرسائل في هذه المحادثة؟ لا يمكن التراجع عن هذا الإجراء.", color = Color(0xFFCBD5E1)) },
            containerColor = Color(0xFF1E293B),
            confirmButton = {
                Button(
                    onClick = {
                        val now = System.currentTimeMillis()
                        setClearedChatTime(context, chatId, now)
                        lastClearedTime = now
                        showClearChatDialog = false

                        scope.launch {
                            // حذف الرسائل من جانب المستخدم الحالي في Supabase
                            try {
                                SupabaseManager.postgrest.from("messages").delete {
                                    filter {
                                        eq("sender_id", currentUserId)
                                        eq("receiver_id", chatId)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            try {
                                SupabaseManager.postgrest.from("messages").delete {
                                    filter {
                                        eq("sender_id", chatId)
                                        eq("receiver_id", currentUserId)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            Toast.makeText(context, "تم مسح محتوى المحادثة بنجاح", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("مسح الآن", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) {
                    Text("إلغاء", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}
