package com.shortly.app.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.JsonObject
import com.shortly.app.ShortlyApp
import com.shortly.app.data.api.ApiService
import com.shortly.app.data.model.AuthRequest
import com.shortly.app.data.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class UserRepository private constructor() {

    private val api: ApiService get() = ShortlyApp.instance.apiService
    private val tokenManager: TokenManager get() = ShortlyApp.instance.tokenManager

    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

    suspend fun login(login: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val resp = api.login(AuthRequest(login = login, password = password))
            tokenManager.saveAuth(resp.token, resp.user.id)
            _currentUser.postValue(resp.user)
            Result.success(resp.user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(username: String, email: String, password: String, displayName: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val resp = api.register(AuthRequest(username = username, email = email, password = password, displayName = displayName))
            tokenManager.saveAuth(resp.token, resp.user.id)
            _currentUser.postValue(resp.user)
            Result.success(resp.user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        try { api.logout() } catch (_: Exception) {}
        tokenManager.clear()
        _currentUser.postValue(null)
    }

    suspend fun loadMe(): Result<User> = withContext(Dispatchers.IO) {
        try {
            val resp = api.me()
            resp.user?.let { _currentUser.postValue(it) }
            Result.success(resp.user!!)
        } catch (e: Exception) {
            tokenManager.clear()
            _currentUser.postValue(null)
            Result.failure(e)
        }
    }

    suspend fun refreshIfNeeded() {
        if (tokenManager.getToken() != null && _currentUser.value == null) {
            try { loadMe() } catch (_: Exception) {}
        }
    }

    suspend fun editProfile(displayName: String? = null, bio: String? = null, username: String? = null): Result<User> = withContext(Dispatchers.IO) {
        try {
            val body = JsonObject()
            displayName?.let { body.addProperty("display_name", it) }
            bio?.let { body.addProperty("bio", it) }
            username?.let { body.addProperty("username", it) }
            val resp = api.editProfile(body)
            resp.user?.let { _currentUser.postValue(it) }
            Result.success(resp.user ?: _currentUser.value!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadAvatar(file: File): Result<User> = withContext(Dispatchers.IO) {
        try {
            val reqFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", file.name, reqFile)
            val resp = api.uploadAvatar(part)
            resp.user?.let { _currentUser.postValue(it) }
            Result.success(resp.user ?: _currentUser.value!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateUserLocal(user: User) {
        _currentUser.postValue(user)
    }

    companion object {
        val instance = UserRepository()
    }
}
