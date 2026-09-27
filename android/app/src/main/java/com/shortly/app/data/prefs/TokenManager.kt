package com.shortly.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "shortly_prefs")

class TokenManager(private val context: Context) {

    companion object {
        val TOKEN_KEY = stringPreferencesKey("auth_token")
        val USER_ID_KEY = stringPreferencesKey("user_id")
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
    }

    private val prefs = context.getSharedPreferences("shortly_sync", Context.MODE_PRIVATE)

    val tokenFlow: Flow<String?> = context.dataStore.data.map { p -> p[TOKEN_KEY] }
    val darkModeFlow: Flow<Boolean?> = context.dataStore.data.map { p ->
        if (p.contains(DARK_MODE_KEY)) p[DARK_MODE_KEY] else null
    }

    fun getToken(): String? = prefs.getString("token", null)
    fun getCachedUserId(): String? = prefs.getString("user_id", null)

    suspend fun saveAuth(token: String, userId: String) {
        prefs.edit().putString("token", token).putString("user_id", userId).apply()
        context.dataStore.edit { p ->
            p[TOKEN_KEY] = token
            p[USER_ID_KEY] = userId
        }
    }

    suspend fun clear() {
        prefs.edit().clear().apply()
        context.dataStore.edit { it.clear() }
    }

    suspend fun setDarkMode(enabled: Boolean?) {
        context.dataStore.edit { p ->
            if (enabled == null) p.remove(DARK_MODE_KEY)
            else p[DARK_MODE_KEY] = enabled
        }
    }
}
