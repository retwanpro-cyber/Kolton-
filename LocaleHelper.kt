package com.radwan.nova.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    private const val PREFS_NAME = "nova_language_prefs"
    private const val KEY_LANGUAGE = "app_language"

    fun onAttach(context: Context): Context {
        val lang = getLanguage(context)
        return updateResources(context, lang)
    }

    /**
     * إرجاع لغة التطبيق:
     * إذا اختار المستخدم لغة مخصصة يدوياً (غير system) نرجعها.
     * إذا لم يختر أو اختار تلقائي نرجع لغة الهاتف الفعلية فوراً!
     */
    fun getLanguage(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedLang = prefs.getString(KEY_LANGUAGE, null)
        
        if (!savedLang.isNullOrBlank() && savedLang != "system") {
            return savedLang
        }

        // قراءة لغة الهاتف الحالية من النظام مباشرة
        val deviceLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales.get(0) ?: Locale.getDefault()
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale ?: Locale.getDefault()
        }

        val langCode = deviceLocale.language.lowercase()
        return when {
            langCode.startsWith("ar") -> "ar"
            langCode.startsWith("fr") -> "fr"
            else -> "en"
        }
    }

    fun getPersistedLanguage(context: Context): String {
        return getLanguage(context)
    }

    fun setLocale(context: Context, language: String): Context {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
        return updateResources(context, language)
    }

    fun resetToSystem(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_LANGUAGE).apply()
    }

    private fun updateResources(context: Context, language: String): Context {
        val targetLang = if (language == "system") getLanguage(context) else language
        val locale = Locale(targetLang)
        Locale.setDefault(locale)

        val resources = context.resources
        val configuration = Configuration(resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
        }

        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }
}
