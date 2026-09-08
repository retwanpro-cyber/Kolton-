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

    const val LANG_SYSTEM = "system"
    const val LANG_AR = "ar"
    const val LANG_EN = "en"
    const val LANG_FR = "fr"

    var selectedLanguagePreference by mutableStateOf(LANG_SYSTEM)
        private set

    var currentLanguage by mutableStateOf("ar")
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LANG, LANG_SYSTEM) ?: LANG_SYSTEM
        selectedLanguagePreference = saved
        resolveAndApplyLanguage(context, saved)
    }

    fun onConfigurationChanged(context: Context, newConfig: Configuration) {
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
        
        // شاشة تعديل الملف الشخصي
        "edit_profile" to mapOf("ar" to "تعديل الملف الشخصي", "en" to "Edit Profile", "fr" to "Modifier le Profil"),
        "full_name_label" to mapOf("ar" to "الاسم الكامل", "en" to "Full Name", "fr" to "Nom Complet"),
        "username_label" to mapOf("ar" to "اسم المستخدم", "en" to "Username", "fr" to "Nom d'utilisateur"),
        "bio_label" to mapOf("ar" to "الحالة / النبذة", "en" to "Bio / Status", "fr" to "Bio / Statut"),
        "cancel" to mapOf("ar" to "إلغاء", "en" to "Cancel", "fr" to "Annuler"),
        "save" to mapOf("ar" to "حفظ", "en" to "Save", "fr" to "Enregistrer"),
        "save_changes" to mapOf("ar" to "حفظ التغييرات", "en" to "Save Changes", "fr" to "Enregistrer"),
        "profile_hint" to mapOf("ar" to "هذا ليس اسم المستخدم الخاص بك. سيكون هذا الاسم مرئياً لجهات اتصالك.", "en" to "This is not your username. This name will be visible to your contacts.", "fr" to "Ce n'est pas votre nom d'utilisateur. Il sera visible par vos contacts."),
        "close" to mapOf("ar" to "إغلاق", "en" to "Close", "fr" to "Fermer"),

        // شاشة معلومات جهة الاتصال
        "contact_info" to mapOf("ar" to "معلومات جهة الاتصال", "en" to "Contact Info", "fr" to "Infos du contact"),
        "online" to mapOf("ar" to "متصل الآن", "en" to "Online", "fr" to "En ligne"),
        "offline" to mapOf("ar" to "غير متصل", "en" to "Offline", "fr" to "Hors ligne"),
        "message" to mapOf("ar" to "مراسلة", "en" to "Message", "fr" to "Message"),
        "call" to mapOf("ar" to "اتصال", "en" to "Call", "fr" to "Appel"),
        "block" to mapOf("ar" to "حظر", "en" to "Block", "fr" to "Bloquer"),
        "unblock" to mapOf("ar" to "فك الحظر", "en" to "Unblock", "fr" to "Débloquer"),
        "about_section" to mapOf("ar" to "النبذة التعريفية", "en" to "About", "fr" to "Actu"),
        "encryption_title" to mapOf("ar" to "التشفير التام بين الطرفين", "en" to "End-to-end encryption", "fr" to "Chiffrement de bout en bout"),
        "encryption_desc" to mapOf("ar" to "الرسائل والمكالمات في هذه المحادثة مشفرة تماماً، ولا يمكن لأحد خارج هذه المحادثة قراءتها.", "en" to "Messages and calls in this chat are end-to-end encrypted. Nobody outside can read them.", "fr" to "Les messages et appels sont chiffrés de bout en bout."),
        "clear_chat" to mapOf("ar" to "مسح محتوى المحادثة", "en" to "Clear chat history", "fr" to "Effacer la discussion"),

        // شاشة جهات الاتصال
        "contacts" to mapOf("ar" to "جهات الاتصال", "en" to "Contacts", "fr" to "Contacts"),
        "registered_contacts" to mapOf("ar" to "جهة اتصال مسجلة", "en" to "contacts registered", "fr" to "contacts enregistrés"),
        "search_contacts_hint" to mapOf("ar" to "بحث بالاسم أو اسم المستخدم...", "en" to "Search by name or username...", "fr" to "Rechercher par nom..."),
        "online_tab" to mapOf("ar" to "المتصلون", "en" to "Online", "fr" to "En ligne"),
        "offline_tab" to mapOf("ar" to "غير المتصلين", "en" to "Offline", "fr" to "Hors ligne"),

        // شاشة المحادثة والشات
        "type_message_hint" to mapOf("ar" to "اكتب رسالة...", "en" to "Type a message...", "fr" to "Écrivez un message..."),
        "blocked_user_notice" to mapOf("ar" to "لقد قمت بحظر هذا المستخدم", "en" to "You have blocked this user", "fr" to "Vous avez bloqué cet utilisateur"),

        // شريط التحديد وحذف المحادثات
        "selected_count" to mapOf("ar" to "تم تحديد", "en" to "Selected", "fr" to "Sélectionné"),
        "delete_chat_title" to mapOf("ar" to "حذف المحادثة", "en" to "Delete Chat", "fr" to "Supprimer la discussion"),
        "delete_confirm_msg" to mapOf("ar" to "هل أنت متأكد من رغبتك في حذف هذه المحادثة؟", "en" to "Are you sure you want to delete this chat?", "fr" to "Voulez-vous vraiment supprimer cette discussion ?"),
        "delete_action" to mapOf("ar" to "حذف", "en" to "Delete", "fr" to "Supprimer"),

        // القوائم وتواصل مع المطور
        "logout" to mapOf("ar" to "تسجيل الخروج", "en" to "Sign Out", "fr" to "Se déconnecter"),
        "contact_developer" to mapOf("ar" to "تواصل مع المطور", "en" to "Contact Developer", "fr" to "Contacter le développeur"),
        "email_contact" to mapOf("ar" to "البريد الإلكتروني", "en" to "Email", "fr" to "E-mail"),
        "facebook_contact" to mapOf("ar" to "فيسبوك (Facebook)", "en" to "Facebook", "fr" to "Facebook")
    )

    fun getString(key: String): String {
        return strings[key]?.get(currentLanguage) ?: strings[key]?.get("en") ?: key
    }
}
