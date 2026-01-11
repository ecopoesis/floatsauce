package org.miker.floatsauce.data

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.miker.floatsauce.api.*
import org.miker.floatsauce.domain.models.*
import org.openapitools.client.auth.ApiKeyAuth
import org.openapitools.client.infrastructure.ApiClient
import org.openapitools.client.infrastructure.HttpResponse

class FloatsauceRepositoryImpl(
    private val secureStorage: SecureStorage
) : FloatsauceRepository {

    private fun <T : ApiClient> createApi(service: AuthService, apiFactory: (String) -> T): T {
        val baseUrl = when (service) {
            AuthService.FLOATPLANE -> "https://www.floatplane.com"
            AuthService.SAUCE_PLUS -> "https://www.sauceplus.com"
        }
        val cookieName = when (service) {
            AuthService.FLOATPLANE -> "sails.sid"
            AuthService.SAUCE_PLUS -> "__Host-sp-sess"
        }
        val api = apiFactory(baseUrl)
        val auth = api.getAuthentication("CookieAuth") as? ApiKeyAuth
        auth?.let {
            it.paramName = cookieName
            it.apiKey = secureStorage.get("cookie_${service.name}")
        }
        return api
    }

    private fun createSubscriptionsApi(service: AuthService) = createApi(service) { SubscriptionsV3Api(baseUrl = it) }
    private fun createCreatorApi(service: AuthService) = createApi(service) { CreatorV3Api(baseUrl = it) }
    private fun createContentApi(service: AuthService) = createApi(service) { ContentV3Api(baseUrl = it) }
    private fun createDeliveryApi(service: AuthService) = createApi(service) { DeliveryV3Api(baseUrl = it) }

    override fun getServices(): List<AuthService> = AuthService.entries

    override suspend fun getAuthState(service: AuthService): AuthState {
        val cookie = secureStorage.get("cookie_${service.name}")
        if (cookie == null) {
            return AuthState(isLoggedIn = false, qrCodeUrl = getQrCodeUrl(service))
        }

        val api = createSubscriptionsApi(service)
        return try {
            val response = api.listUserSubscriptionsV3()
            // If we get here, it's 200 OK (wrapped response)
            AuthState(isLoggedIn = true)
        } catch (e: Exception) {
            // Check for 403
            // In the generated ApiClient, exceptions might be thrown for non-2xx
            // Need to check how errors are handled.
            AuthState(isLoggedIn = false, qrCodeUrl = getQrCodeUrl(service))
        }
    }

    private fun getQrCodeUrl(service: AuthService): String {
        val data = when (service) {
            AuthService.FLOATPLANE -> "https://www.floatplane.com/login"
            AuthService.SAUCE_PLUS -> "https://www.sauceplus.com/login"
        }
        return "https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=$data"
    }

    override suspend fun getSubscriptions(service: AuthService): List<Creator> {
        val subsApi = createSubscriptionsApi(service)
        return try {
            val subsResponse = subsApi.listUserSubscriptionsV3()
            val subscriptions = subsResponse.body()

            subscriptions.map { sub ->
                Creator(
                    id = sub.creator,
                    name = sub.plan.title,
                    iconUrl = sub.plan.logo,
                    channels = null,
                    service = service
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getCreatorDetails(service: AuthService, id: String): Creator? {
        val creatorApi = createCreatorApi(service)
        return try {
            val response = creatorApi.getCreator(id)
            val creatorModel = response.body()
            Creator(
                id = creatorModel.id,
                name = creatorModel.title,
                iconUrl = creatorModel.icon.path,
                channels = creatorModel.channels.size,
                service = service
            )
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getVideos(service: AuthService, creatorId: String): List<Video> {
        val contentApi = createContentApi(service)
        return try {
            val response = contentApi.getCreatorBlogPosts(id = creatorId, hasVideo = true)
            val posts = response.body()
            posts.mapNotNull { post ->
                val videoId = post.videoAttachments?.firstOrNull() ?: return@mapNotNull null
                Video(
                    id = videoId,
                    title = post.title,
                    thumbnailUrl = post.thumbnail?.path,
                    duration = formatDuration(post.metadata.videoDuration),
                    videoUrl = videoId
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun formatDuration(seconds: Double): String {
        val s = seconds.toInt()
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) {
            "${h}:${m.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
        } else {
            "${m}:${sec.toString().padStart(2, '0')}"
        }
    }

    override suspend fun requestDeviceAuth(service: AuthService) {
        println("[DEBUG_LOG] POST to Device Authorization Endpoint for ${service.displayName}")
    }

    override suspend fun pollForToken(service: AuthService): String? {
        return when (service) {
            AuthService.FLOATPLANE -> AuthSecrets.FLOATPLANE_COOKIE
            AuthService.SAUCE_PLUS -> AuthSecrets.SAUCEPLUS_COOKIE
        }
    }

    override suspend fun saveToken(service: AuthService, token: String) {
        secureStorage.set("cookie_${service.name}", token)
    }
}
