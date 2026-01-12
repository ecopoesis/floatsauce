package org.miker.floatsauce.domain.models

enum class AuthService(val displayName: String, val origin: String) {
    FLOATPLANE("Floatplane", "https://www.floatplane.com"),
    SAUCE_PLUS("Sauce+", "https://www.sauceplus.com")
}

data class AuthState(
    val isLoggedIn: Boolean,
    val qrCodeUrl: String? = null
)

data class Creator(
    val id: String,
    val name: String,
    val iconUrl: String?,
    val bannerUrl: String?,
    val channels: Int?,
    val service: AuthService
)

data class Video(
    val id: String,
    val postId: String,
    val title: String,
    val thumbnailUrl: String?,
    val duration: String,
    val releaseDate: String,
    val videoUrl: String,
    val progress: Int = 0
)
