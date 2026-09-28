package com.shortly.app.platform

import com.shortly.app.data.api.ApiClient
import com.shortly.app.data.prefs.TokenManager

/**
 * Process-wide singletons shared by Android and Web (replaces the old
 * `ShortlyApp.instance` Application accessor).
 */
object AppGraph {
    val store: KeyValueStore by lazy { KeyValueStore() }
    val tokenManager: TokenManager by lazy { TokenManager(store) }
    val api: ApiClient by lazy { ApiClient { tokenManager.getToken() } }
}
