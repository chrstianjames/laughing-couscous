package com.shortly.app.data.repository

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.shortly.app.platform.AppGraph
import com.shortly.app.platform.IoDispatcher
import com.shortly.app.platform.PickedFile
import com.shortly.app.data.api.ApiClient
import com.shortly.app.data.model.AuthRequest
import com.shortly.app.data.model.User
import com.shortly.app.data.prefs.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class UserRepository private constructor() {

    private val api: ApiClient get() = AppGraph.api
    private val tokenManager: TokenManager get() = AppGraph.tokenManager

    private val _currentUser = MutableStateFlow<User?>(null)

    /** Current logged-in user. Exposed as a [StateFlow] so Compose can `collectAsState()` it. */
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    suspend fun login(login: String, password: String): Result<User> = withContext(IoDispatcher) {
        try {
            val resp = api.login(AuthRequest(login = login, password = password))
            tokenManager.saveAuth(resp.token, resp.user.id)
            _currentUser.value = resp.user
            Result.success(resp.user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(username: String, email: String, password: String, displayName: String): Result<User> = withContext(IoDispatcher) {
        try {
            val resp = api.register(AuthRequest(username = username, email = email, password = password, displayName = displayName))
            tokenManager.saveAuth(resp.token, resp.user.id)
            _currentUser.value = resp.user
            Result.success(resp.user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() = withContext(IoDispatcher) {
        try { api.logout() } catch (_: Exception) {}
        tokenManager.clear()
        _currentUser.value = null
    }

    suspend fun loadMe(): Result<User> = withContext(IoDispatcher) {
        try {
            val resp = api.me()
            resp.user?.let { _currentUser.value = it }
            Result.success(resp.user!!)
        } catch (e: Exception) {
            tokenManager.clear()
            _currentUser.value = null
            Result.failure(e)
        }
    }

    suspend fun refreshIfNeeded() {
        if (tokenManager.getToken() != null && _currentUser.value == null) {
            try { loadMe() } catch (_: Exception) {}
        }
    }

    suspend fun editProfile(displayName: String? = null, bio: String? = null, username: String? = null): Result<User> = withContext(IoDispatcher) {
        try {
            val body = buildJsonObject {
                displayName?.let { put("display_name", it) }
                bio?.let { put("bio", it) }
                username?.let { put("username", it) }
            }
            val resp = api.editProfile(body)
            resp.user?.let { _currentUser.value = it }
            Result.success(resp.user ?: _currentUser.value!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadAvatar(file: PickedFile): Result<User> = withContext(IoDispatcher) {
        try {
            val resp = api.uploadAvatar(file)
            resp.user?.let { _currentUser.value = it }
            Result.success(resp.user ?: _currentUser.value!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateUserLocal(user: User) {
        _currentUser.value = user
    }

    companion object {
        val instance = UserRepository()
    }
}
