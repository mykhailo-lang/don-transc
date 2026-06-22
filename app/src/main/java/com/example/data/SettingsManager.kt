package com.example.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("transcriber_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MODE = "pref_mode" // "LOCAL" or "SERVER"
        private const val KEY_LANGUAGE = "pref_language" // "uk" or "ru"
        private const val KEY_SERVER_URL = "pref_server_url"
        private const val KEY_USER_ID = "pref_user_id"
        private const val KEY_CLIENT_NAME = "pref_client_name"
    }

    var mode: String
        get() = prefs.getString(KEY_MODE, "LOCAL") ?: "LOCAL"
        set(value) = prefs.edit().putString(KEY_MODE, value).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "uk") ?: "uk"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "https://api.example.com/") ?: "https://api.example.com/"
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var userId: String
        get() = prefs.getString(KEY_USER_ID, "Менеджер_01") ?: "Менеджер_01"
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var lastClientName: String
        get() = prefs.getString(KEY_CLIENT_NAME, "Клієнт") ?: "Клієнт"
        set(value) = prefs.edit().putString(KEY_CLIENT_NAME, value).apply()
}
