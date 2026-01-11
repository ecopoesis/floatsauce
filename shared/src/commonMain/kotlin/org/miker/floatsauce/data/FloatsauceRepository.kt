package org.miker.floatsauce.data

import org.miker.floatsauce.domain.models.AuthService
import org.miker.floatsauce.domain.models.AuthState
import org.miker.floatsauce.domain.models.Creator
import org.miker.floatsauce.domain.models.Video

interface FloatsauceRepository {
    fun getServices(): List<AuthService>
    suspend fun getAuthState(service: AuthService): AuthState
    suspend fun getSubscriptions(service: AuthService): List<Creator>
    suspend fun getVideos(creatorId: String): List<Video>
    suspend fun requestDeviceAuth(service: AuthService)
    suspend fun pollForToken(service: AuthService): String?
    suspend fun saveToken(service: AuthService, token: String)
}

class MockFloatsauceRepository : FloatsauceRepository {
    override fun getServices(): List<AuthService> = AuthService.entries

    override suspend fun getAuthState(service: AuthService): AuthState {
        // For testing, let's say Floatplane is logged in, Sauce Plus is not
        return when (service) {
            AuthService.FLOATPLANE -> AuthState(isLoggedIn = true)
            AuthService.SAUCE_PLUS -> AuthState(isLoggedIn = false, qrCodeUrl = "https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=MockLogin")
        }
    }

    override suspend fun getSubscriptions(service: AuthService): List<Creator> {
        return if (service == AuthService.FLOATPLANE) {
            listOf(
                Creator("linustech", "Linus Tech Tips", "https://cdn.floatplane.com/avatars/ltt.png", "1.2M", 5, 1200),
                Creator("louisrossmann", "Louis Rossmann", "https://cdn.floatplane.com/avatars/rossmann.png", "250K", 1, 800)
            )
        } else {
            emptyList()
        }
    }

    override suspend fun getVideos(creatorId: String): List<Video> {
        return listOf(
            Video("v1", "Mock Video 1 for $creatorId", null, "10:00", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"),
            Video("v2", "Mock Video 2 for $creatorId", null, "15:30", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
        )
    }

    override suspend fun requestDeviceAuth(service: AuthService) {
        // Stub
    }

    override suspend fun pollForToken(service: AuthService): String? {
        return null
    }

    override suspend fun saveToken(service: AuthService, token: String) {
        // Stub
    }
}
