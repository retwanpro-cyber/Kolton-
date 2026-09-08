package com.radwan.nova.data.local

import com.radwan.nova.utils.LocaleHelper
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

object LanguageManager {
    private const val PREFS_NAME = "nova_language_prefs"
    private const val KEY_LANG = "app_language"

    // اللغات المدعومة
    const val LANG_SYSTEM = "system"
    const val LANG_AR = "ar"
    const val LANG_EN = "en"
    const val LANG_FR = "fr"

    // اللغة المختارة في الإعدادات (system أو ar أو en أو fr)
    var selectedLanguagePreference by mutableStateOf(LANG_SYSTEM)
        private set

    // اللغة النشطة الفعلية في التطبيق (ar أو en أو fr)
    var currentLanguage by mutableStateOf("ar")
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LANG, LANG_SYSTEM) ?: LANG_SYSTEM
        selectedLanguagePreference = saved
        resolveAndApplyLanguage(context, saved)
    }

    fun onConfigurationChanged(context: Context, newConfig: Configuration) {
        // إذا كان المستخدم يختار لغة النظام، نحدثها فوراً عند تغيير لغة الهاتف
        if (selectedLanguagePreference == LANG_SYSTEM) {
            resolveAndApplyLanguage(context, LANG_SYSTEM)
        }
    }

    fun syncWithSystem(context: Context) {
        if (selectedLanguagePreference == LANG_SYSTEM) {
            resolveAndApplyLanguage(context, LANG_SYSTEM)
        }
    }

    private fun resolveAndApplyLanguage(context: Context, preference: String) {
        val resolvedLang = if (preference == LANG_SYSTEM) {
            val deviceLocale = Locale.getDefault()
            val lang = deviceLocale.language.lowercase()
            when {
                lang.startsWith("ar") -> LANG_AR
                lang.startsWith("fr") -> LANG_FR
                else -> LANG_EN
            }
        } else {
            preference
        }

        currentLanguage = resolvedLang
        try {
            LocaleHelper.setLocale(context, resolvedLang)
        } catch (e: Exception) {}
    }

    fun setLanguage(context: Context, langCode: String) {
        selectedLanguagePreference = langCode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, langCode).apply()
        resolveAndApplyLanguage(context, langCode)
    }

    private val strings = mapOf(
        "app_name" to mapOf("ar" to "NOVA Chat", "en" to "NOVA Chat", "fr" to "NOVA Chat"),
        "chats_tab" to mapOf("ar" to "المحادثات", "en" to "Chats", "fr" to "Discussions"),
        "contacts_tab" to mapOf("ar" to "جهات الاتصال", "en" to "Contacts", "fr" to "Contacts"),
        "settings_tab" to mapOf("ar" to "الإعدادات", "en" to "Settings", "fr" to "Paramètres"),
        "settings_title" to mapOf("ar" to "الإعدادات", "en" to "Settings", "fr" to "Paramètres"),
        "profile_section" to mapOf("ar" to "الملف الشخصي", "en" to "Profile", "fr" to "Profil"),
        "preferences_section" to mapOf("ar" to "التفضيلات والخصوصية", "en" to "Preferences & Privacy", "fr" to "Préférences et Confidentialité"),
        "language_title" to mapOf("ar" to "لغة التطبيق", "en" to "App Language", "fr" to "Langue de l'application"),
        "select_language" to mapOf("ar" to "اختر لغة التطبيق", "en" to "Select Language", "fr" to "Choisir la langue"),
        "lang_system_default" to mapOf("ar" to "تلقائي (لغة الهاتف)", "en" to "System Default", "fr" to "Par défaut du système"),
        "notifications_title" to mapOf("ar" to "إشعارات التطبيق", "en" to "Push Notifications", "fr" to "Notifications Push"),
        "dark_mode_title" to mapOf("ar" to "الوضع الليلي", "en" to "Dark Mode", "fr" to "Mode Sombre"),
        "privacy_title" to mapOf("ar" to "الخصوصية والأمان والتشفير", "en" to "Privacy, Security & Encryption", "fr" to "Confidentialité et Sécurité"),
        "storage_title" to mapOf("ar" to "التخزين والبيانات المؤقتة", "en" to "Storage & Data", "fr" to "Stockage et Données"),
        "edit_profile" to mapOf("ar" to "تعديل الملف الشخصي", "en" to "Edit Profile", "fr" to "Modifier le Profil"),
        "full_name_label" to mapOf("ar" to "الاسم الكامل", "en" to "Full Name", "fr" to "Nom Complet"),
        "username_label" to mapOf("ar" to "اسم المستخدم", "en" to "Username", "fr" to "Nom d'utilisateur"),
        "bio_label" to mapOf("ar" to "الحالة / النبذة", "en" to "Bio / Status", "fr" to "Bio / Statut"),
        "cancel" to mapOf("ar" to "إلغاء", "en" to "Cancel", "fr" to "Annuler"),
        "save_changes" to mapOf("ar" to "حفظ التغييرات", "en" to "Save Changes", "fr" to "Enregistrer"),
        "close" to mapOf("ar" to "إغلاق", "en" to "Close", "fr" to "Fermer"),
        "contact_developer" to mapOf("ar" to "تواصل مع المطور", "en" to "Contact Developer", "fr" to "Contacter le développeur"),
        "email_contact" to mapOf("ar" to "البريد الإلكتروني", "en" to "Email", "fr" to "E-mail"),
        "facebook_contact" to mapOf("ar" to "فيسبوك (Facebook)", "en" to "Facebook", "fr" to "Facebook"),
        "new_chat" to mapOf("ar" to "محادثة جديدة", "en" to "New Chat", "fr" to "Nouvelle discussion"),
        "search_hint" to mapOf("ar" to "البحث في المحادثات والمستخدمين...", "en" to "Search chats or users...", "fr" to "Rechercher des discussions..."),
        "logout" to mapOf("ar" to "تسجيل الخروج", "en" to "Sign Out", "fr" to "Se déconnecter")
    )

    fun getString(key: String): String {
        return strings[key]?.get(currentLanguage) ?: strings[key]?.get("en") ?: key
    }
}
