package com.radwan.nova.ui.screens.home

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.radwan.nova.R
import com.radwan.nova.data.locale.LanguageManager
import com.radwan.nova.data.remote.RemoteMessage
import com.radwan.nova.data.remote.RemoteProfile
import com.radwan.nova.data.remote.SupabaseManager
import com.radwan.nova.viewmodel.HomeViewModel
import io.github.jan.supabase.auth.auth
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

private fun getDeletedChats(context: Context): Set<String> {
    val prefs = context.getSharedPreferences("nova_home_prefs", Context.MODE_PRIVATE)
    return prefs.getStringSet("deleted_chats", emptySet()) ?: emptySet()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onChatClick: (chatId: String, chatName: String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val conversations by viewModel.conversations.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentUserId = SupabaseManager.auth.currentUserOrNull()?.id ?: ""

    var deletedChatIds by remember { mutableStateOf(getDeletedChats(context)) }
    val conversationsList = remember(conversations, deletedChatIds) {
        conversations.filter { it.otherUserId !in deletedChatIds }
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text(
                            text = "${selectedChatIds.size} " + LanguageManager.getString("selected"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    } else {
                        Text(
                            text = LanguageManager.getString("app_name"),
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedChatIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            if (selectedChatIds.size == conversationsList.size) {
                                selectedChatIds = emptySet()
                            } else {
                                selectedChatIds = conversationsList.map { it.otherUserId }.toSet()
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = Color.White)
                        }
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                        }
                    } else {
                        IconButton(onClick = { viewModel.loadConversations() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier.background(Color(0xFF1E293B))
                            ) {
                                DropdownMenuItem(
                                    text = { Text(LanguageManager.getString("settings"), color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF60A5FA)) },
                                    onClick = {
                                        menuExpanded = false
                                        onSettingsClick()
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewChatDialog = true },
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.AddComment, contentDescription = LanguageManager.getString("new_chat"))
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
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = LanguageManager.getString("no_conversations"),
                        color = Color(0xFF64748B),
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(conversationsList) { conv ->
                        val isSelected = conv.otherUserId in selectedChatIds
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedChatIds = if (isSelected) {
                                                selectedChatIds - conv.otherUserId
                                            } else {
                                                selectedChatIds + conv.otherUserId
                                            }
                                            if (selectedChatIds.isEmpty()) isSelectionMode = false
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
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFF1E293B)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    Surface(
                                        modifier = Modifier.size(52.dp),
                                        shape = CircleShape,
                                        color = Color(0xFF2563EB)
                                    ) {
                                        val avatarModel = parseImageModel(conv.otherUserAvatarUrl)
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
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }
                                    if (conv.isOnline) {
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
                                        text = conv.otherUserName,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = conv.lastMessage,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isSelectionMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null,
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Color(0xFF2563EB),
                                            uncheckedColor = Color(0xFF64748B)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // تأكيد الحذف
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(LanguageManager.getString("delete_chat"), color = Color.White) },
            text = { Text(LanguageManager.getString("delete_chat_confirm"), color = Color(0xFF94A3B8)) },
            containerColor = Color(0xFF1E293B),
            confirmButton = {
                Button(
                    onClick = {
                        val idsToDelete = selectedChatIds.toSet()
                        val currentDeleted = getDeletedChats(context).toMutableSet()
                        currentDeleted.addAll(idsToDelete)
                        context.getSharedPreferences("nova_home_prefs", Context.MODE_PRIVATE)
                            .edit().putStringSet("deleted_chats", currentDeleted).apply()
                        deletedChatIds = currentDeleted

                        scope.launch {
                            idsToDelete.forEach { targetId ->
                                try {
                                    val rId = if (currentUserId < targetId) "${currentUserId}__${targetId}" else "${targetId}__${currentUserId}"
                                    SupabaseManager.postgrest["messages"].delete { filter { eq("chat_id", rId) } }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        isSelectionMode = false
                        selectedChatIds = emptySet()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text(LanguageManager.getString("delete"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(LanguageManager.getString("cancel"), color = Color(0xFF94A3B8))
                }
            }
        )
    }

    // ==========================================
    // شاشة المحادثة الجديدة الكاملة (Full Screen Contacts)
    // ==========================================
    if (showNewChatDialog) {
        var allUsers by remember { mutableStateOf<List<RemoteProfile>>(emptyList()) }
        var isFetchingUsers by remember { mutableStateOf(true) }
        var selectedFilterTab by remember { mutableStateOf(0) } // 0: المتصلون, 1: غير المتصلين

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
                                        text = LanguageManager.getString("new_chat"),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${allUsers.size} " + LanguageManager.getString("contacts"),
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
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
                        )
                    },
                    containerColor = Color(0xFF0B1120)
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // حقل البحث
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(LanguageManager.getString("search_hint"), color = Color(0xFF64748B), fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
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

                        // الزرّان: المتصلون الآن | غير المتصلين
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // زر المتصلين
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedFilterTab = 0 },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedFilterTab == 0) Color(0xFF166534) else Color(0xFF1E293B),
                                border = BorderStroke(1.dp, if (selectedFilterTab == 0) Color(0xFF22C55E) else Color(0xFF334155))
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
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
                                        text = LanguageManager.getString("online") + " ($onlineCount)",
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
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
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
                                        text = LanguageManager.getString("offline") + " ($offlineCount)",
                                        color = if (selectedFilterTab == 1) Color.White else Color(0xFF94A3B8),
                                        fontWeight = if (selectedFilterTab == 1) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // عرض القائمة
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
                                        text = if (selectedFilterTab == 0)
                                            LanguageManager.getString("no_online_users")
                                        else
                                            LanguageManager.getString("no_users_found"),
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
