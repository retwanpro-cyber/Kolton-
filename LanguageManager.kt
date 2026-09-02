package com.radwan.nova.data.local

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object LanguageManager {
    private const val PREFS_NAME = "nova_lang_prefs"
    private const val KEY_LANG = "selected_language"

    var currentLanguage by mutableStateOf("ar")
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentLanguage = prefs.getString(KEY_LANG, "ar") ?: "ar"
    }

    fun setLanguage(context: Context, langCode: String) {
        currentLanguage = langCode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, langCode).apply()
    }

    // قاموس الترجمة الفورية للشاشات
    fun getString(key: String): String {
        val strings = mapOf(
            "settings_title" to mapOf("ar" to "الإعدادات", "en" to "Settings", "fr" to "Paramètres"),
            "profile_section" to mapOf("ar" to "الملف الشخصي", "en" to "Profile", "fr" to "Profil"),
            "preferences_section" to mapOf("ar" to "التفضيلات والخصوصية", "en" to "Preferences & Privacy", "fr" to "Préférences et Confidentialité"),
            "language_title" to mapOf("ar" to "لغة التطبيق", "en" to "App Language", "fr" to "Langue de l'application"),
            "notifications_title" to mapOf("ar" to "الإشعارات التنبيهية", "en" to "Push Notifications", "fr" to "Notifications Push"),
            "dark_mode_title" to mapOf("ar" to "الوضع الليلي الفاخر", "en" to "Dark Mode", "fr" to "Mode Sombre"),
            "privacy_title" to mapOf("ar" to "الخصوصية والأمان والتشفير", "en" to "Privacy, Security & Encryption", "fr" to "Confidentialité et Sécurité"),
            "storage_title" to mapOf("ar" to "التخزين والبيانات المؤقتة", "en" to "Storage & Data", "fr" to "Stockage et Données"),
            "edit_profile" to mapOf("ar" to "تعديل الملف الشخصي", "en" to "Edit Profile", "fr" to "Modifier le Profil"),
            "full_name_label" to mapOf("ar" to "الاسم الكامل", "en" to "Full Name", "fr" to "Nom Complet"),
            "username_label" to mapOf("ar" to "اسم المستخدم (Username)", "en" to "Username", "fr" to "Nom d'utilisateur"),
            "avatar_url_label" to mapOf("ar" to "رابط الصورة الشخصية (URL)", "en" to "Avatar URL", "fr" to "URL de l'avatar"),
            "bio_label" to mapOf("ar" to "الحالة / النبذة التعريفية", "en" to "Bio / Status", "fr" to "Bio / Statut"),
            "cancel" to mapOf("ar" to "إلغاء", "en" to "Cancel", "fr" to "Annuler"),
            "save_changes" to mapOf("ar" to "حفظ التغييرات", "en" to "Save Changes", "fr" to "Enregistrer"),
            "select_language" to mapOf("ar" to "اختر لغة التطبيق", "en" to "Select Language", "fr" to "Choisir la langue"),
            "close" to mapOf("ar" to "إغلاق", "en" to "Close", "fr" to "Fermer")
        )
        return strings[key]?.get(currentLanguage) ?: strings[key]?.get("en") ?: key
    }
}
