package com.radwan.nova.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.radwan.nova.data.local.LanguageManager
import com.radwan.nova.data.remote.RemoteProfile
import com.radwan.nova.data.remote.SupabaseManager
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import java.io.File

// دوال حساب وحذف الكاش
private fun getCacheSizeFormatted(context: Context): String {
    return try {
        var size: Long = 0
        context.cacheDir?.let { size += getFolderSize(it) }
        context.externalCacheDir?.let { size += getFolderSize(it) }
        val sizeInMb = size.toDouble() / (1024 * 1024)
        String.format("%.1f MB", sizeInMb)
    } catch (e: Exception) {
        "0.0 MB"
    }
}

private fun getFolderSize(file: File): Long {
    var size: Long = 0
    if (file.isDirectory) {
        file.listFiles()?.forEach { size += getFolderSize(it) }
    } else {
        size += file.length()
    }
    return size
}

private fun clearAppCache(context: Context) {
    try {
        context.cacheDir?.deleteRecursively()
        context.externalCacheDir?.deleteRecursively()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("nova_settings_prefs", Context.MODE_PRIVATE) }

    var userProfile by remember { mutableStateOf<RemoteProfile?>(null) }
    var notificationsEnabled by remember {
        mutableStateOf(prefs.getBoolean("notifications_enabled", true))
    }
    var darkModeEnabled by remember {
        mutableStateOf(prefs.getBoolean("dark_mode_enabled", true))
    }

    // حالات الخصوصية والأمان
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var appLockEnabled by remember { mutableStateOf(prefs.getBoolean("app_lock_enabled", false)) }
    var hideLastSeen by remember { mutableStateOf(prefs.getBoolean("hide_last_seen", false)) }
    var readReceiptsEnabled by remember { mutableStateOf(prefs.getBoolean("read_receipts_enabled", true)) }

    // حالات التخزين والبيانات
    var showStorageDialog by remember { mutableStateOf(false) }
    var cacheSizeText by remember { mutableStateOf(getCacheSizeFormatted(context)) }
    var autoDownloadWifi by remember { mutableStateOf(prefs.getBoolean("auto_download_wifi", true)) }
    var autoDownloadMobile by remember { mutableStateOf(prefs.getBoolean("auto_download_mobile", false)) }
    var lowDataUsage by remember { mutableStateOf(prefs.getBoolean("low_data_usage", false)) }

    // حالة اختيار اللغة
    var showLanguageDialog by remember { mutableStateOf(false) }

    // حالات تعديل الملف الشخصي
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editUsername by remember { mutableStateOf("") }
    var editBio by remember { mutableStateOf("") }
    var editAvatarUrl by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    suspend fun loadUserProfile() {
        try {
            val myId = SupabaseManager.auth.currentUserOrNull()?.id
            if (myId != null) {
                val profile = SupabaseManager.postgrest.from("profiles")
                    .select {
                        filter {
                            eq("id", myId)
                        }
                    }.decodeSingleOrNull<RemoteProfile>()
                userProfile = profile
                if (profile != null) {
                    editName = profile.full_name.ifBlank { profile.name }
                    editUsername = profile.username
                    editBio = profile.bio ?: ""
                    editAvatarUrl = profile.avatar_url ?: ""
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(Unit) {
        LanguageManager.init(context)
        loadUserProfile()
    }

    val currentLangTitle = when (LanguageManager.currentLanguage) {
        "ar" -> "العربية (Arabic)"
        "fr" -> "Français (French)"
        else -> "English"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = LanguageManager.getString("settings_title"),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        containerColor = Color(0xFF0B1120)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1️⃣ كارت الملف الشخصي
            item {
                Text(
                    text = LanguageManager.getString("profile_section"),
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showEditProfileDialog = true
                            onProfileClick()
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(60.dp),
                            shape = CircleShape,
                            color = Color(0xFF2563EB)
                        ) {
                            if (!userProfile?.avatar_url.isNullOrBlank()) {
                                AsyncImage(
                                    model = userProfile?.avatar_url,
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Avatar",
                                    tint = Color.White,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            val name = userProfile?.full_name?.takeIf { it.isNotBlank() }
                                ?: userProfile?.name?.takeIf { it.isNotBlank() }
                                ?: "NOVA User"
                            val username = userProfile?.username?.takeIf { it.isNotBlank() } ?: "user"

                            Text(
                                text = name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "@$username",
                                fontSize = 13.sp,
                                color = Color(0xFF60A5FA)
                            )
                            if (!userProfile?.bio.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = userProfile?.bio ?: "",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1
                                )
                            }
                        }

                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 2️⃣ قائمة التفضيلات والخصوصية
            item {
                Text(
                    text = LanguageManager.getString("preferences_section"),
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        // لغة التطبيق
                        SettingsClickItem(
                            icon = Icons.Default.Language,
                            title = LanguageManager.getString("language_title"),
                            subtitle = currentLangTitle,
                            onClick = { showLanguageDialog = true }
                        )

                        // الإشعارات
                        SettingsToggleItem(
                            icon = Icons.Default.Notifications,
                            title = LanguageManager.getString("notifications_title"),
                            checked = notificationsEnabled,
                            onCheckedChange = {
                                notificationsEnabled = it
                                prefs.edit().putBoolean("notifications_enabled", it).apply()
                            }
                        )

                        // الوضع الليلي
                        SettingsToggleItem(
                            icon = Icons.Default.DarkMode,
                            title = LanguageManager.getString("dark_mode_title"),
                            checked = darkModeEnabled,
                            onCheckedChange = {
                                darkModeEnabled = it
                                prefs.edit().putBoolean("dark_mode_enabled", it).apply()
                            }
                        )

                        // 🔒 الخصوصية والأمان والتشفير
                        SettingsClickItem(
                            icon = Icons.Default.Lock,
                            title = LanguageManager.getString("privacy_title"),
                            subtitle = if (appLockEnabled) "القفل مفعل • تشفير تام" else "تشفير تام E2EE",
                            onClick = { showPrivacyDialog = true }
                        )

                        // 💾 التخزين والبيانات المؤقتة
                        SettingsClickItem(
                            icon = Icons.Default.Storage,
                            title = LanguageManager.getString("storage_title"),
                            subtitle = "الكاش: $cacheSizeText",
                            onClick = {
                                cacheSizeText = getCacheSizeFormatted(context)
                                showStorageDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // 🔒 نافذة الخصوصية والأمان والتشفير
    if (showPrivacyDialog) {
        Dialog(onDismissRequest = { showPrivacyDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.EnhancedEncryption,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "الخصوصية والأمان",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "محادثاتك محمية ومشفرة من طرف إلى طرف (E2EE) تلقائياً.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color(0xFF334155), thickness = 1.dp)

                    // قفل التطبيق
                    SettingsToggleItem(
                        icon = Icons.Default.Fingerprint,
                        title = "قفل التطبيق بالبصمة / الرمز",
                        checked = appLockEnabled,
                        onCheckedChange = {
                            appLockEnabled = it
                            prefs.edit().putBoolean("app_lock_enabled", it).apply()
                            Toast.makeText(context, if (it) "تم تفعيل حماية التطبيق" else "تم إيقاف قفل التطبيق", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // إخفاء آخر ظهور
                    SettingsToggleItem(
                        icon = Icons.Default.RemoveRedEye,
                        title = "إخفاء حالة الاتصال وآخر ظهور",
                        checked = hideLastSeen,
                        onCheckedChange = {
                            hideLastSeen = it
                            prefs.edit().putBoolean("hide_last_seen", it).apply()
                        }
                    )

                    // مؤشرات القراءة
                    SettingsToggleItem(
                        icon = Icons.Default.Check,
                        title = "مؤشرات قراءة الرسائل (صحين)",
                        checked = readReceiptsEnabled,
                        onCheckedChange = {
                            readReceiptsEnabled = it
                            prefs.edit().putBoolean("read_receipts_enabled", it).apply()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { showPrivacyDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(LanguageManager.getString("close"), color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // 💾 نافذة التخزين والبيانات المؤقتة
    if (showStorageDialog) {
        Dialog(onDismissRequest = { showStorageDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Storage,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "التخزين والبيانات",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // كارت حجم الكاش الفعلي مع زر التنظيف
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "الذاكرة المؤقتة (Cache)",
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = cacheSizeText,
                                    fontSize = 12.sp,
                                    color = Color(0xFF60A5FA)
                                )
                            }

                            Button(
                                onClick = {
                                    clearAppCache(context)
                                    cacheSizeText = getCacheSizeFormatted(context)
                                    Toast.makeText(context, "تم تنظيف الذاكرة المؤقتة بنجاح!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("مسح الكاش", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "التنزيل التلقائي للوسائط", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    SettingsToggleItem(
                        icon = Icons.Default.Wifi,
                        title = "تنزيل الوسائط عبر Wi-Fi تلقائياً",
                        checked = autoDownloadWifi,
                        onCheckedChange = {
                            autoDownloadWifi = it
                            prefs.edit().putBoolean("auto_download_wifi", it).apply()
                        }
                    )

                    SettingsToggleItem(
                        icon = Icons.Default.Storage,
                        title = "تنزيل الوسائط عبر بيانات الهاتف",
                        checked = autoDownloadMobile,
                        onCheckedChange = {
                            autoDownloadMobile = it
                            prefs.edit().putBoolean("auto_download_mobile", it).apply()
                        }
                    )

                    SettingsToggleItem(
                        icon = Icons.Default.CleaningServices,
                        title = "وضع توفير استهلاك البيانات",
                        checked = lowDataUsage,
                        onCheckedChange = {
                            lowDataUsage = it
                            prefs.edit().putBoolean("low_data_usage", it).apply()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { showStorageDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(LanguageManager.getString("close"), color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // 🌐 نافذة اختيار اللغة
    if (showLanguageDialog) {
        Dialog(onDismissRequest = { showLanguageDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = LanguageManager.getString("select_language"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val languages = listOf(
                        Triple("ar", "العربية", "🇸🇦"),
                        Triple("en", "English", "🇺🇸"),
                        Triple("fr", "Français", "🇫🇷")
                    )

                    languages.forEach { (code, name, flag) ->
                        val isSelected = LanguageManager.currentLanguage == code
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    LanguageManager.setLanguage(context, code)
                                    showLanguageDialog = false
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF2563EB).copy(alpha = 0.2f) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = flag, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = name,
                                    color = if (isSelected) Color(0xFF60A5FA) else Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF60A5FA),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { showLanguageDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(LanguageManager.getString("close"), color = Color(0xFF94A3B8))
                    }
                }
            }
        }
    }

    // 🌟 نافذة تعديل الملف الشخصي
    if (showEditProfileDialog) {
        Dialog(onDismissRequest = { if (!isSaving) showEditProfileDialog = false }) {
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
                        text = LanguageManager.getString("edit_profile"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = Color(0xFF2563EB)
                    ) {
                        if (editAvatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = editAvatarUrl,
                                contentDescription = "Avatar Preview",
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

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(LanguageManager.getString("full_name_label"), color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { editUsername = it },
                        label = { Text(LanguageManager.getString("username_label"), color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editAvatarUrl,
                        onValueChange = { editAvatarUrl = it },
                        label = { Text(LanguageManager.getString("avatar_url_label"), color = Color(0xFF94A3B8)) },
                        placeholder = { Text("https://example.com/avatar.jpg", color = Color.DarkGray) },
                        singleLine = true,
                        trailingIcon = {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color(0xFF60A5FA))
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it },
                        label = { Text(LanguageManager.getString("bio_label"), color = Color(0xFF94A3B8)) },
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showEditProfileDialog = false },
                            enabled = !isSaving
                        ) {
                            Text(LanguageManager.getString("cancel"), color = Color(0xFF94A3B8))
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                val myId = SupabaseManager.auth.currentUserOrNull()?.id
                                if (myId != null) {
                                    scope.launch {
                                        isSaving = true
                                        try {
                                            val updates = mutableMapOf<String, Any>()
                                            if (editName.isNotBlank()) updates["full_name"] = editName
                                            if (editUsername.isNotBlank()) updates["username"] = editUsername
                                            updates["bio"] = editBio
                                            updates["avatar_url"] = editAvatarUrl

                                            SupabaseManager.postgrest.from("profiles").update(updates) {
                                                filter {
                                                    eq("id", myId)
                                                }
                                            }
                                            loadUserProfile()
                                            showEditProfileDialog = false
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(LanguageManager.getString("save_changes"), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF0F172A)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF60A5FA),
                modifier = Modifier.padding(8.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF2563EB),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF0F172A)
            )
        )
    }
}

@Composable
fun SettingsClickItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF0F172A)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF60A5FA),
                modifier = Modifier.padding(8.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = Color(0xFF60A5FA),
                    fontSize = 12.sp
                )
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color(0xFF64748B)
        )
    }
}
