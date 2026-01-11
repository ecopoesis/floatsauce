package org.miker.floatsauce.domain.models

enum class AuthService(val displayName: String) {
    FLOATPLANE("Floatplane"),
    SAUCE_PLUS("Sauce+")
}

data class AuthState(
    val isLoggedIn: Boolean,
    val qrCodeUrl: String? = null
)

data class Creator(
    val id: String,
    val name: String,
    val iconUrl: String?,
    val subscribers: String,
    val channels: Int?,
    val posts: Int
)

data class Video(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val duration: String,
    val videoUrl: String
)
