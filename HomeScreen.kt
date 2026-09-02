package com.radwan.nova.ui.screens.home

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
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
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.radwan.nova.data.remote.RemoteProfile
import com.radwan.nova.data.remote.SupabaseManager
import com.radwan.nova.viewmodel.ChatViewModel
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

// دوال حفظ واسترجاع المحادثات المحذوفة محلياً
private fun getDeletedChats(context: Context): Set<String> {
    val prefs = context.getSharedPreferences("nova_home_prefs", Context.MODE_PRIVATE)
    return prefs.getStringSet("deleted_chats", emptySet()) ?: emptySet()
}

private fun addDeletedChats(context: Context, chatIds: Set<String>) {
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
    onLogout: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val conversations by viewModel.conversations.collectAsState()
    val isLoading by viewModel.isLoadingConversations.collectAsState()
    val currentUserId = SupabaseManager.auth.currentUserOrNull()?.id ?: ""

    // قائمة المحادثات المحذوفة
    var deletedChatIds by remember { mutableStateOf(getDeletedChats(context)) }

    // وضع التحديد المتعدد (Selection Mode)
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedChatIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // حالات القوائم والبحث
    var menuExpanded by remember { mutableStateOf(false) }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<RemoteProfile>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // زر الرجوع في الهاتف يلغي وضع التحديد أولاً
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedChatIds = emptySet()
    }

    LaunchedEffect(Unit) {
        deletedChatIds = getDeletedChats(context)
        viewModel.loadConversations()
    }

    // تصفية المحادثات لإخفاء المحذوفة
    val activeConversations = remember(conversations, deletedChatIds) {
        conversations.filter { conv ->
            val otherId = if (conv.otherUserId.isNotBlank()) conv.otherUserId else conv.id
            !deletedChatIds.contains(otherId) && !deletedChatIds.contains(conv.id)
        }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // شريط التحديد والحذف
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
                // الشريط العادي
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
                        IconButton(onClick = { viewModel.loadConversations() }) {
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
            } else if (activeConversations.isEmpty()) {
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
                        text = "اضغط على زر (+) بالأسفل لبدء دردشة جديدة",
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
                    items(activeConversations, key = { it.id }) { conv ->
                        val targetId = if (conv.otherUserId.isNotBlank()) conv.otherUserId else conv.id
                        val targetTitle = if (conv.otherUserName.isNotBlank()) conv.otherUserName else conv.name
                        val isSelected = selectedChatIds.contains(targetId)

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
                                                val newSet = selectedChatIds - targetId
                                                if (newSet.isEmpty()) isSelectionMode = false
                                                newSet
                                            } else {
                                                selectedChatIds + targetId
                                            }
                                        } else {
                                            onChatClick(targetId, targetTitle)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedChatIds = setOf(targetId)
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
                                // الصورة الشخصية أو أيقونة التحديد
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
                                            if (!conv.otherUserAvatar.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = conv.otherUserAvatar,
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
                                        text = targetTitle,
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

    // 🗑️ نافذة تأكيد حذف المحادثات المحددة
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
                    text = "هل أنت متأكد من حذف ${selectedChatIds.size} محادثة من قائمتك؟",
                    color = Color(0xFFCBD5E1)
                )
            },
            containerColor = Color(0xFF1E293B),
            confirmButton = {
                Button(
                    onClick = {
                        val idsToDelete = selectedChatIds
                        addDeletedChats(context, idsToDelete)
                        deletedChatIds = getDeletedChats(context)
                        
                        // حذف الرسائل من قاعدة البيانات في الخلفية
                        scope.launch {
                            idsToDelete.forEach { targetId ->
                                try {
                                    SupabaseManager.postgrest.from("messages").delete {
                                        filter {
                                            eq("sender_id", currentUserId)
                                            eq("receiver_id", targetId)
                                        }
                                    }
                                    SupabaseManager.postgrest.from("messages").delete {
                                        filter {
                                            eq("sender_id", targetId)
                                            eq("receiver_id", currentUserId)
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

    // ➕ نافذة بدء محادثة جديدة
    if (showNewChatDialog) {
        Dialog(onDismissRequest = { showNewChatDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "بدء محادثة جديدة",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            if (it.isNotBlank()) {
                                isSearching = true
                                scope.launch {
                                    try {
                                        val users = SupabaseManager.postgrest.from("profiles")
                                            .select {
                                                filter {
                                                    neq("id", currentUserId)
                                                    ilike("username", "%$it%")
                                                }
                                            }.decodeList<RemoteProfile>()
                                        searchResults = users
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    } finally {
                                        isSearching = false
                                    }
                                }
                            } else {
                                searchResults = emptyList()
                            }
                        },
                        placeholder = { Text("ابحث باسم المستخدم...", color = Color(0xFF64748B)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF60A5FA)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isSearching) {
                        CircularProgressIndicator(color = Color(0xFF2563EB), modifier = Modifier.size(28.dp))
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(searchResults) { user ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showNewChatDialog = false
                                            val name = user.full_name.ifBlank { user.username }
                                            // إزالة من المحذوفة إن وجدت
                                            val currentDeleted = getDeletedChats(context).toMutableSet()
                                            if (currentDeleted.remove(user.id)) {
                                                context.getSharedPreferences("nova_home_prefs", Context.MODE_PRIVATE)
                                                    .edit().putStringSet("deleted_chats", currentDeleted).apply()
                                                deletedChatIds = currentDeleted
                                            }
                                            onChatClick(user.id, name)
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF0F172A)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(40.dp),
                                            shape = CircleShape,
                                            color = Color(0xFF2563EB)
                                        ) {
                                            if (!user.avatar_url.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = user.avatar_url,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.padding(8.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = user.full_name.ifBlank { user.username },
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "@${user.username}",
                                                color = Color(0xFF60A5FA),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { showNewChatDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("إغلاق", color = Color(0xFF94A3B8))
                    }
                }
            }
        }
    }
}
