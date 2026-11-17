package com.adhora.app.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NativeEngine {
    private val gson = Gson()
    private var isInitialized = false
    var httpPort: Int = 0
        private set

    init {
        try {
            System.loadLibrary("adhora_engine")
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    suspend fun init(context: Context): Int = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            val dataDir = context.filesDir.absolutePath
            val cacheDir = context.cacheDir.absolutePath
            httpPort = initEngine(dataDir, cacheDir)
            isInitialized = true
        }
        httpPort
    }

    suspend fun checkIsLoggedIn(): Boolean = withContext(Dispatchers.IO) {
        try {
            isLoggedIn()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun requestOtp(apiId: Int, apiHash: String, phone: String): AuthResult = withContext(Dispatchers.IO) {
        val json = requestLoginCode(apiId, apiHash, phone)
        try {
            gson.fromJson(json, AuthResult::class.java)
        } catch (e: Exception) {
            AuthResult("error", e.message, null, null, null)
        }
    }

    suspend fun verifyOtp(code: String): AuthResult = withContext(Dispatchers.IO) {
        val json = signIn(code)
        try {
            gson.fromJson(json, AuthResult::class.java)
        } catch (e: Exception) {
            AuthResult("error", e.message, null, null, null)
        }
    }

    suspend fun verify2fa(password: String): AuthResult = withContext(Dispatchers.IO) {
        val json = checkPassword(password)
        try {
            gson.fromJson(json, AuthResult::class.java)
        } catch (e: Exception) {
            AuthResult("error", e.message, null, null, null)
        }
    }

    suspend fun performLogout(): Boolean = withContext(Dispatchers.IO) {
        try {
            logout()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun loadDialogs(): List<DialogItem> = withContext(Dispatchers.IO) {
        val json = getDialogs()
        try {
            val type = object : TypeToken<List<DialogItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun loadMedia(channelIds: List<Long>, limit: Int = 50, offsetId: Int = 0): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val idsJson = gson.toJson(channelIds)
            val json = getMedia(idsJson, limit, offsetId)
            try {
                val type = object : TypeToken<List<MediaItem>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun downloadMediaFile(chatId: Long, msgId: Int, destPath: String): String =
        withContext(Dispatchers.IO) {
            downloadMediaToGallery(chatId, msgId, destPath)
        }

    // JNI Declarations
    private external fun initEngine(dataDir: String, cacheDir: String): Int
    private external fun isLoggedIn(): Boolean
    private external fun requestLoginCode(apiId: Int, apiHash: String, phone: String): String
    private external fun signIn(code: String): String
    private external fun checkPassword(password: String): String
    private external fun logout(): Boolean
    private external fun getDialogs(): String
    private external fun getMedia(channelIdsJson: String, limit: Int, offsetId: Int): String
    private external fun downloadMediaToGallery(chatId: Long, msgId: Int, destPath: String): String
}
