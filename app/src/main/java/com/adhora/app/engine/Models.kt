package com.adhora.app.engine

import com.google.gson.annotations.SerializedName

data class DialogItem(
    val id: Long,
    val title: String,
    val username: String?,
    @SerializedName("is_channel") val isChannel: Boolean,
    @SerializedName("is_group") val isGroup: Boolean,
    @SerializedName("is_admin") val isAdmin: Boolean,
    @SerializedName("is_creator") val isCreator: Boolean,
    @SerializedName("unread_count") val unreadCount: Int,
    @SerializedName("thumb_url") val thumbUrl: String,
    var isSelected: Boolean = false
)

data class MediaItem(
    val id: Int,
    @SerializedName("chat_id") val chatId: Long,
    @SerializedName("chat_title") var chatTitle: String = "",
    @SerializedName("media_type") val mediaType: String, // "video" or "photo"
    @SerializedName("file_name") val fileName: String,
    @SerializedName("mime_type") val mimeType: String,
    val size: Long,
    val duration: Int,
    val width: Int,
    val height: Int,
    val date: Long,
    val caption: String,
    @SerializedName("stream_url") val streamUrl: String,
    @SerializedName("thumb_url") val thumbUrl: String
)

data class AuthResult(
    val status: String, // "ok" | "2fa_required" | "error"
    val message: String?,
    val hint: String?,
    @SerializedName("user_id") val userId: Long?,
    @SerializedName("user_name") val userName: String?
)
