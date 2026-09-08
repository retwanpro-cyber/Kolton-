package com.radwan.nova.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment

import androidx.compose.ui.res.stringResource
import com.radwan.nova.R

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
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
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.radwan.nova.data.local.LanguageManager
import com.radwan.nova.data.remote.RemoteProfile
import com.radwan.nova.data.remote.SupabaseManager
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import java.io.File

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

    var showPrivacyDialog by remember { mutableStateOf(false) }
    var appLockEnabled by remember { mutableStateOf(prefs.getBoolean("app_lock_enabled", false)) }
    var hideLastSeen by remember { mutableStateOf(prefs.getBoolean("hide_last_seen", false)) }
    var readReceiptsEnabled by remember { mutableStateOf(prefs.getBoolean("read_receipts_enabled", true)) }

    var showStorageDialog by remember { mutableStateOf(false) }
    var cacheSizeText by remember { mutableStateOf(getCacheSizeFormatted(context)) }
    var autoDownloadWifi by remember { mutableStateOf(prefs.getBoolean("auto_download_wifi", true)) }
    var autoDownloadMobile by remember { mutableStateOf(prefs.getBoolean("auto_download_mobile", false)) }
    var lowDataUsage by remember { mutableStateOf(prefs.getBoolean("low_data_usage", false)) }

    var showLanguageDialog by remember { mutableStateOf(false) }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editUsername by remember { mutableStateOf("") }
    var editBio by remember { mutableStateOf("") }
    var editAvatarUrl by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                if (originalBitmap != null) {
                    val maxDim = 300
                    val width = originalBitmap.width
                    val height = originalBitmap.height
                    val ratio = width.toFloat() / height.toFloat()
                    val newW = if (width > height) maxDim else (maxDim * ratio).toInt()
                    val newH = if (height >= width) maxDim else (maxDim / ratio).toInt()
                    val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, newW, newH, true)

                    val outputStream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
                    val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                    editAvatarUrl = "data:image/jpeg;base64,$base64"
                }
            } catch (e: Exception) {
                Toast.makeText(context, "فشل تجهيز الصورة", Toast.LENGTH_SHORT).show()
            }
        }
    }
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
                    selectedImageUri = null
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
                                    model = parseImageModel(userProfile?.avatar_url),
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
                        SettingsClickItem(
                            icon = Icons.Default.Language,
                            title = LanguageManager.getString("language_title"),
                            subtitle = currentLangTitle,
                            onClick = { showLanguageDialog = true }
                        )

                        SettingsToggleItem(
                            icon = Icons.Default.Notifications,
                            title = LanguageManager.getString("notifications_title"),
                            checked = notificationsEnabled,
                            onCheckedChange = {
                                notificationsEnabled = it
                                prefs.edit().putBoolean("notifications_enabled", it).apply()
                            }
                        )

                        SettingsToggleItem(
                            icon = Icons.Default.DarkMode,
                            title = LanguageManager.getString("dark_mode_title"),
                            checked = darkModeEnabled,
                            onCheckedChange = {
                                darkModeEnabled = it
                                prefs.edit().putBoolean("dark_mode_enabled", it).apply()
                            }
                        )

                        SettingsClickItem(
                            icon = Icons.Default.Lock,
                            title = LanguageManager.getString("privacy_title"),
                            subtitle = if (appLockEnabled) LanguageManager.getString("lock_active_e2ee") else LanguageManager.getString("lock_inactive_e2ee"),
                            onClick = { showPrivacyDialog = true }
                        )

                        SettingsClickItem(
                            icon = Icons.Default.Storage,
                            title = LanguageManager.getString("storage_title"),
                            subtitle = "${stringResource(R.string.cache_prefix_label)}$cacheSizeText",
                            onClick = {
                                cacheSizeText = getCacheSizeFormatted(context)
                                showStorageDialog = true
                            }
                        )
                    }
                }
            }

            // 3️⃣ تواصل مع المطور
            item {
                Text(
                    text = stringResource(R.string.contact_developer),
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
                        SettingsClickItem(
                            icon = Icons.Default.Email,
                            title = stringResource(R.string.email_contact),
                            subtitle = "retwan.tech@gmail.com",
                            onClick = {
                                try {
                                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:retwan.tech@gmail.com")
                                        putExtra(Intent.EXTRA_SUBJECT, "NOVA Chat Support & Inquiry")
                                    }
                                    context.startActivity(emailIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.no_email_app), Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        SettingsClickItem(
                            icon = Icons.Default.Public,
                            title = stringResource(R.string.facebook_contact),
                            subtitle = "Ridwan Almasouri",
                            onClick = {
                                try {
                                    val fbUri = Uri.parse("https://www.facebook.com/ridwanalmasouri?mibextid=ZbWKwL")
                                    val fbIntent = Intent(Intent.ACTION_VIEW, fbUri)
                                    context.startActivity(fbIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.link_open_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 🔒 نافذة الخصوصية
    if (showPrivacyDialog) {
        Dialog(onDismissRequest = { showPrivacyDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EnhancedEncryption, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = stringResource(R.string.privacy_security_title), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringResource(R.string.privacy_security_desc), fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color(0xFF334155), thickness = 1.dp)

                    SettingsToggleItem(
                        icon = Icons.Default.Fingerprint,
                        title = stringResource(R.string.lock_app_title),
                        checked = appLockEnabled,
                        onCheckedChange = {
                            appLockEnabled = it
                            prefs.edit().putBoolean("app_lock_enabled", it).apply()
                            Toast.makeText(context, if (it) "تم تفعيل حماية التطبيق" else "تم إيقاف قفل التطبيق", Toast.LENGTH_SHORT).show()
                        }
                    )

                    SettingsToggleItem(
                        icon = Icons.Default.RemoveRedEye,
                        title = stringResource(R.string.hide_online_status),
                        checked = hideLastSeen,
                        onCheckedChange = {
                            hideLastSeen = it
                            prefs.edit().putBoolean("hide_last_seen", it).apply()
                        }
                    )

                    SettingsToggleItem(
                        icon = Icons.Default.Check,
                        title = stringResource(R.string.read_receipts),
                        checked = readReceiptsEnabled,
                        onCheckedChange = {
                            readReceiptsEnabled = it
                            prefs.edit().putBoolean("read_receipts_enabled", it).apply()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = { showPrivacyDialog = false }, modifier = Modifier.align(Alignment.End)) {
                        Text(LanguageManager.getString("close"), color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // 💾 نافذة التخزين
    if (showStorageDialog) {
        Dialog(onDismissRequest = { showStorageDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = stringResource(R.string.storage_data_title), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

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
                                Text(text = stringResource(R.string.cache_memory), fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = cacheSizeText, fontSize = 12.sp, color = Color(0xFF60A5FA))
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
                                Text(stringResource(R.string.clear_cache_action), fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = stringResource(R.string.auto_download_media), color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    SettingsToggleItem(
                        icon = Icons.Default.Wifi,
                        title = stringResource(R.string.download_wifi),
                        checked = autoDownloadWifi,
                        onCheckedChange = {
                            autoDownloadWifi = it
                            prefs.edit().putBoolean("auto_download_wifi", it).apply()
                        }
                    )

                    SettingsToggleItem(
                        icon = Icons.Default.Storage,
                        title = stringResource(R.string.download_mobile),
                        checked = autoDownloadMobile,
                        onCheckedChange = {
                            autoDownloadMobile = it
                            prefs.edit().putBoolean("auto_download_mobile", it).apply()
                        }
                    )

                    SettingsToggleItem(
                        icon = Icons.Default.CleaningServices,
                        title = stringResource(R.string.data_saver_mode),
                        checked = lowDataUsage,
                        onCheckedChange = {
                            lowDataUsage = it
                            prefs.edit().putBoolean("low_data_usage", it).apply()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = { showStorageDialog = false }, modifier = Modifier.align(Alignment.End)) {
                        Text(LanguageManager.getString("close"), color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // 🌐 نافذة اللغة
    if (showLanguageDialog) {
        Dialog(onDismissRequest = { showLanguageDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = LanguageManager.getString("select_language"), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
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
                                    (context as? android.app.Activity)?.recreate()
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
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = { showLanguageDialog = false }, modifier = Modifier.align(Alignment.End)) {
                        Text(LanguageManager.getString("close"), color = Color(0xFF94A3B8))
                    }
                }
            }
        }
    }

    // ========================================================
    // 🌟 شاشة الملف الشخصي الكاملة نمط الواتساب (WhatsApp Full Profile)
    // ========================================================
    if (showEditProfileDialog) {
        Dialog(
            onDismissRequest = { if (!isSaving) showEditProfileDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0B1120)
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = LanguageManager.getString("edit_profile"),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = { if (!isSaving) showEditProfileDialog = false },
                                    enabled = !isSaving
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                                }
                            },
                            actions = {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF2563EB),
                                        modifier = Modifier.size(22.dp).padding(end = 12.dp)
                                    )
                                } else {
                                    TextButton(
                                        onClick = {
                                            val myId = SupabaseManager.auth.currentUserOrNull()?.id
                                            if (myId != null) {
                                                scope.launch {
                                                    isSaving = true
                                                    try {
                                                        val updates = mutableMapOf<String, String>()
                                                        updates["id"] = myId
                                                        if (editName.isNotBlank()) updates["full_name"] = editName
                                                        if (editUsername.isNotBlank()) updates["username"] = editUsername
                                                        updates["bio"] = editBio
                                                        updates["avatar_url"] = editAvatarUrl
                                                        SupabaseManager.postgrest.from("profiles").upsert(updates)
                                                        Toast.makeText(context, "تم حفظ الملف الشخصي بنجاح!", Toast.LENGTH_SHORT).show()
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
                                        }
                                    ) {
                                        Text("حفظ", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
                        )
                    },
                    containerColor = Color(0xFF0B1120)
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))

                            // صورة البروفايل الكبيرة مع زر الكاميرا (WhatsApp Style)
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Surface(
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clickable { imagePickerLauncher.launch("image/*") },
                                    shape = CircleShape,
                                    color = Color(0xFF1E293B),
                                    border = BorderStroke(3.dp, Color(0xFF2563EB))
                                ) {
                                    if (editAvatarUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = selectedImageUri ?: parseImageModel(editAvatarUrl),
                                            contentDescription = "Avatar",
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.padding(28.dp)
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clickable { imagePickerLauncher.launch("image/*") },
                                    shape = CircleShape,
                                    color = Color(0xFF22C55E),
                                    border = BorderStroke(3.dp, Color(0xFF0B1120))
                                ) {
                                    Icon(
                                        Icons.Default.AddPhotoAlternate,
                                        contentDescription = "تغيير الصورة",
                                        tint = Color.White,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }

                        // بطاقة الحقول المرتبة
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    // الاسم الكامل
                                    OutlinedTextField(
                                        value = editName,
                                        onValueChange = { editName = it },
                                        label = { Text(LanguageManager.getString("full_name_label"), color = Color(0xFF94A3B8)) },
                                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp)) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2563EB),
                                            unfocusedBorderColor = Color(0xFF334155),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Text(
                                        text = "هذا ليس اسم المستخدم الخاص بك. سيكون هذا الاسم مرئياً لجهات اتصالك.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B),
                                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp, start = 4.dp)
                                    )

                                    // اسم المستخدم
                                    OutlinedTextField(
                                        value = editUsername,
                                        onValueChange = { editUsername = it },
                                        label = { Text(LanguageManager.getString("username_label"), color = Color(0xFF94A3B8)) },
                                        leadingIcon = {
                                            Text("@", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
                                        },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2563EB),
                                            unfocusedBorderColor = Color(0xFF334155),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // النبذة / الحالة
                                    OutlinedTextField(
                                        value = editBio,
                                        onValueChange = { editBio = it },
                                        label = { Text(LanguageManager.getString("bio_label"), color = Color(0xFF94A3B8)) },
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp)) },
                                        maxLines = 3,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2563EB),
                                            unfocusedBorderColor = Color(0xFF334155),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // زر حفظ أسفل الشاشة
                        item {
                            Button(
                                onClick = {
                                    val myId = SupabaseManager.auth.currentUserOrNull()?.id
                                    if (myId != null) {
                                        scope.launch {
                                            isSaving = true
                                            try {
                                                val updates = mutableMapOf<String, String>()
                                                updates["id"] = myId
                                                if (editName.isNotBlank()) updates["full_name"] = editName
                                                if (editUsername.isNotBlank()) updates["username"] = editUsername
                                                updates["bio"] = editBio
                                                updates["avatar_url"] = editAvatarUrl
                                                SupabaseManager.postgrest.from("profiles").upsert(updates)
                                                Toast.makeText(context, "تم حفظ الملف الشخصي بنجاح!", Toast.LENGTH_SHORT).show()
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
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                enabled = !isSaving
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                                } else {
                                    Text("حفظ التغييرات", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
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
