package com.shortly.app.data.prefs

import com.shortly.app.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Auth token + small settings, backed by the platform [KeyValueStore]. */
class TokenManager(private val store: KeyValueStore) {

    private companion object {
        const val TOKEN = "token"
        const val USER_ID = "user_id"
        const val DARK_MODE = "dark_mode"
    }

    private val _token = MutableStateFlow(store.get(TOKEN))
    private val _darkMode = MutableStateFlow(store.get(DARK_MODE)?.toBooleanStrictOrNull())

    val tokenFlow: StateFlow<String?> = _token
    /** null = follow system, true = dark, false = light */
    val darkModeFlow: StateFlow<Boolean?> = _darkMode

    fun getToken(): String? = _token.value
    fun getCachedUserId(): String? = store.get(USER_ID)

    fun saveAuth(token: String, userId: String) {
        store.put(TOKEN, token)
        store.put(USER_ID, userId)
        _token.value = token
    }

    fun clear() {
        val dark = store.get(DARK_MODE)
        store.clear()
        store.put(DARK_MODE, dark)
        _token.value = null
    }

    fun setDarkMode(enabled: Boolean?) {
        store.put(DARK_MODE, enabled?.toString())
        _darkMode.value = enabled
    }
}
