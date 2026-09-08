package com.radwan.nova.ui.screens.home

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.radwan.nova.data.remote.RemoteMessage
import com.radwan.nova.data.remote.RemoteProfile
import com.radwan.nova.data.remote.SupabaseManager
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

fun parseImageModel(data: String?): Any? {
    if (data.isNullOrBlank()) return null
    return if (data.startsWith("data:image") && data.contains(",")) {
        try {
            val base64Str = data.substringAfter(",")
            android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            data
        }
    } else {
        data
    }
}

data class HomeConversation(
    val roomId: String,
    val otherUserId: String,
    val otherUserName: String,
    val otherUserAvatar: String?,
    val lastMessage: String,
    val timestamp: String
)

private fun getDeletedChats(context: Context): Set<String> {
    val prefs = context.getSharedPreferences("nova_home_prefs", Context.MODE_PRIVATE)
    return prefs.getStringSet("deleted_chats", emptySet()) ?: emptySet()
}

private fun saveDeletedChats(context: Context, chatIds: Set<String>) {
    val prefs = context.getSharedPreferences("nova_home_prefs", Context.MODE_PRIVATE)
    val current = prefs.getStringSet("deleted_chats", emptySet())?.toMutableSet() ?: mutableSetOf()
    current.addAll(chatIds)
    prefs.edit().putStringSet("deleted_chats", current).apply()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onChatClick: (chatId: String, title: String) -> Unit,
    onSettingsClick: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = SupabaseManager.auth.currentUserOrNull()?.id ?: ""

    var conversationsList by remember { mutableStateOf<List<HomeConversation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var deletedChatIds by remember { mutableStateOf(getDeletedChats(context)) }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedChatIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    var menuExpanded by remember { mutableStateOf(false) }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedChatIds = emptySet()
    }

    fun loadAllConversations() {
        scope.launch {
            isLoading = true
            deletedChatIds = getDeletedChats(context)
            try {
                if (currentUserId.isNotBlank()) {
                    val messages = SupabaseManager.postgrest["messages"]
                        .select()
                        .decodeList<RemoteMessage>()

                    val myMessages = messages.filter { msg ->
                        val chatId = msg.chat_id ?: ""
                        chatId.contains(currentUserId)
                    }

                    val grouped = myMessages.groupBy { it.chat_id ?: "" }
                    val result = mutableListOf<HomeConversation>()

                    for ((chatRoom, msgs) in grouped) {
                        if (chatRoom.isBlank()) continue

                        val otherId = if (chatRoom.contains("__")) {
                            val parts = chatRoom.split("__")
                            parts.firstOrNull { it != currentUserId } ?: parts[0]
                        } else {
                            msgs.firstOrNull { it.sender_id != currentUserId }?.sender_id ?: chatRoom
                        }

                        if (deletedChatIds.contains(otherId) || deletedChatIds.contains(chatRoom)) {
                            continue
                        }

                        val lastMsg = msgs.lastOrNull()
                        val lastText = lastMsg?.text ?: ""
                        val lastTime = lastMsg?.created_at ?: ""

                        var otherName = "مستخدم"
                        var otherAvatar: String? = null
                        try {
                            val profile = SupabaseManager.postgrest["profiles"]
                                .select {
                                    filter {
                                        eq("id", otherId)
                                    }
                                }.decodeSingleOrNull<RemoteProfile>()

                            if (profile != null) {
                                otherName = profile.full_name.ifBlank { profile.username }
                                otherAvatar = profile.avatar_url
                            } else {
                                val senderName = msgs.firstOrNull { it.sender_id == otherId }?.sender_name
                                if (!senderName.isNullOrBlank()) {
                                    otherName = senderName
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        result.add(
                            HomeConversation(
                                roomId = chatRoom,
                                otherUserId = otherId,
                                otherUserName = otherName,
                                otherUserAvatar = otherAvatar,
                                lastMessage = lastText,
                                timestamp = lastTime
                            )
                        )
                    }
                    conversationsList = result
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadAllConversations()
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "تم تحديد ${selectedChatIds.size}",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 17.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedChatIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "إلغاء التحديد", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "حذف المحادثات",
                                tint = Color(0xFFEF4444)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B))
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = "NOVA Chat",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                    },
                    actions = {
                        IconButton(onClick = { loadAllConversations() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = Color.White)
                        }

                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "خيارات", tint = Color.White)
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier.background(Color(0xFF1E293B))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("الإعدادات", color = Color.White) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF60A5FA))
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onSettingsClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("تسجيل الخروج", color = Color(0xFFEF4444)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color(0xFFEF4444))
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        scope.launch {
                                            try {
                                                SupabaseManager.auth.signOut()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                            onLogout()
                                        }
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewChatDialog = true },
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.AddComment, contentDescription = "محادثة جديدة")
            }
        },
        containerColor = Color(0xFF0B1120)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF2563EB))
                }
            } else if (conversationsList.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.AddComment,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "لا توجد محادثات حتى الآن",
                        color = Color(0xFF94A3B8),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "اضغط على زر (+) لبدء محادثة جديدة",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(conversationsList, key = { it.roomId }) { conv ->
                        val isSelected = selectedChatIds.contains(conv.otherUserId)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) Color(0xFF2563EB) else Color.Transparent,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedChatIds = if (isSelected) {
                                                val newSet = selectedChatIds - conv.otherUserId
                                                if (newSet.isEmpty()) isSelectionMode = false
                                                newSet
                                            } else {
                                                selectedChatIds + conv.otherUserId
                                            }
                                        } else {
                                            onChatClick(conv.otherUserId, conv.otherUserName)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedChatIds = setOf(conv.otherUserId)
                                        }
                                    }
                                ),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF1E293B).copy(alpha = 0.9f) else Color(0xFF1E293B)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(52.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Surface(
                                            modifier = Modifier.fillMaxSize(),
                                            shape = CircleShape,
                                            color = Color(0xFF2563EB)
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = "محدد",
                                                tint = Color.White,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            modifier = Modifier.fillMaxSize(),
                                            shape = CircleShape,
                                            color = Color(0xFF2563EB).copy(alpha = 0.8f)
                                        ) {
                                            val avatar = parseImageModel(conv.otherUserAvatar)
                                            if (avatar != null) {
                                                AsyncImage(
                                                    model = avatar,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.padding(10.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = conv.otherUserName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = conv.lastMessage.ifBlank { "محادثة مشفرة" },
                                        fontSize = 13.sp,
                                        color = if (isSelected) Color(0xFF93C5FD) else Color(0xFF94A3B8),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isSelectionMode && isSelected) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF2563EB).copy(alpha = 0.2f),
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Text(
                                            text = "محدد",
                                            color = Color(0xFF60A5FA),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = if (selectedChatIds.size == 1) "حذف المحادثة" else "حذف المحادثات المحددة",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في حذف ${selectedChatIds.size} محادثة؟",
                    color = Color(0xFFCBD5E1)
                )
            },
            containerColor = Color(0xFF1E293B),
            confirmButton = {
                Button(
                    onClick = {
                        val idsToDelete = selectedChatIds
                        saveDeletedChats(context, idsToDelete)
                        deletedChatIds = getDeletedChats(context)

                        conversationsList = conversationsList.filter { !idsToDelete.contains(it.otherUserId) }

                        scope.launch {
                            idsToDelete.forEach { targetId ->
                                try {
                                    val rId = if (currentUserId < targetId) "${currentUserId}__${targetId}" else "${targetId}__${currentUserId}"
                                    SupabaseManager.postgrest["messages"].delete {
                                        filter {
                                            eq("chat_id", rId)
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }

                        isSelectionMode = false
                        selectedChatIds = emptySet()
                        showDeleteConfirmDialog = false
                        Toast.makeText(context, "تم حذف المحادثة بنجاح", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("حذف", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("إلغاء", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    // =======================================================
    // شاشة جهات الاتصال والمحادثة الجديدة الكاملة (Full Screen)
    // =======================================================
    if (showNewChatDialog) {
        var allUsers by remember { mutableStateOf<List<RemoteProfile>>(emptyList()) }
        var isFetchingUsers by remember { mutableStateOf(true) }
        var selectedFilterTab by remember { mutableStateOf(0) } // 0: المتصلون الآن, 1: غير المتصلين

        LaunchedEffect(Unit) {
            isFetchingUsers = true
            try {
                val users = SupabaseManager.postgrest["profiles"]
                    .select {
                        filter {
                            neq("id", currentUserId)
                        }
                    }.decodeList<RemoteProfile>()
                allUsers = users
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isFetchingUsers = false
            }
        }

        val filteredUsers = remember(allUsers, selectedFilterTab, searchQuery) {
            allUsers.filter { user ->
                val matchesTab = when (selectedFilterTab) {
                    0 -> user.is_online
                    else -> !user.is_online
                }
                val matchesSearch = if (searchQuery.isBlank()) true else {
                    user.full_name.contains(searchQuery, ignoreCase = true) ||
                    user.username.contains(searchQuery, ignoreCase = true)
                }
                matchesTab && matchesSearch
            }
        }

        val onlineCount = remember(allUsers) { allUsers.count { it.is_online } }
        val offlineCount = remember(allUsers) { allUsers.count { !it.is_online } }

        Dialog(
            onDismissRequest = {
                showNewChatDialog = false
                searchQuery = ""
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0F172A)
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        text = "جهات الاتصال",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${allUsers.size} جهة اتصال مسجلة",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = {
                                    showNewChatDialog = false
                                    searchQuery = ""
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "رجوع",
                                        tint = Color.White
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color(0xFF0F172A)
                            )
                        )
                    },
                    containerColor = Color(0xFF0B1120)
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // شريط البحث المباشر
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("بحث بالاسم أو اسم المستخدم...", color = Color(0xFF64748B), fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "مسح", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF2563EB),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF1E293B),
                                unfocusedContainerColor = Color(0xFF1E293B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        // الزرّان بالأعلى: المتصلون الآن | غير المتصلين
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // زر المتصلين الآن
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedFilterTab = 0 },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedFilterTab == 0) Color(0xFF166534) else Color(0xFF1E293B),
                                border = BorderStroke(1.dp, if (selectedFilterTab == 0) Color(0xFF22C55E) else Color(0xFF334155))
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFF22C55E), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "المتصلون ($onlineCount)",
                                        color = if (selectedFilterTab == 0) Color.White else Color(0xFF94A3B8),
                                        fontWeight = if (selectedFilterTab == 0) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            // زر غير المتصلين
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedFilterTab = 1 },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedFilterTab == 1) Color(0xFF1E3A8A) else Color(0xFF1E293B),
                                border = BorderStroke(1.dp, if (selectedFilterTab == 1) Color(0xFF3B82F6) else Color(0xFF334155))
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFF64748B), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "غير المتصلين ($offlineCount)",
                                        color = if (selectedFilterTab == 1) Color.White else Color(0xFF94A3B8),
                                        fontWeight = if (selectedFilterTab == 1) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // عرض القائمة الفورية
                        if (isFetchingUsers) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFF2563EB))
                            }
                        } else if (filteredUsers.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = if (selectedFilterTab == 0) Icons.Default.CheckCircle else Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (selectedFilterTab == 0) "لا يوجد أعضاء متصلون حالياً" else "لا يوجد أعضاء في هذه القائمة",
                                        color = Color(0xFF64748B),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredUsers) { user ->
                                    val name = user.full_name.ifBlank { user.username }
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showNewChatDialog = false
                                                searchQuery = ""
                                                val currentDeleted = getDeletedChats(context).toMutableSet()
                                                if (currentDeleted.remove(user.id)) {
                                                    context.getSharedPreferences("nova_home_prefs", Context.MODE_PRIVATE)
                                                        .edit().putStringSet("deleted_chats", currentDeleted).apply()
                                                    deletedChatIds = currentDeleted
                                                }
                                                onChatClick(user.id, name)
                                            },
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFF1E293B)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // الصورة الرمزية ومؤشر الاتصال
                                            Box {
                                                Surface(
                                                    modifier = Modifier.size(48.dp),
                                                    shape = CircleShape,
                                                    color = Color(0xFF2563EB)
                                                ) {
                                                    val avatarModel = parseImageModel(user.avatar_url)
                                                    if (avatarModel != null) {
                                                        AsyncImage(
                                                            model = avatarModel,
                                                            contentDescription = null,
                                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    } else {
                                                        Icon(
                                                            Icons.Default.Person,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.padding(10.dp)
                                                        )
                                                    }
                                                }
                                                if (user.is_online) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(14.dp)
                                                            .align(Alignment.BottomEnd)
                                                            .background(Color(0xFF22C55E), CircleShape)
                                                            .border(2.dp, Color(0xFF1E293B), CircleShape)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(14.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = name,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "@${user.username}",
                                                    color = Color(0xFF60A5FA),
                                                    fontSize = 12.sp
                                                )
                                                if (!user.bio.isNullOrBlank()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = user.bio,
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 12.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
