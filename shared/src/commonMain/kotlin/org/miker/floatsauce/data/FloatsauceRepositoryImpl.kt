package org.miker.floatsauce.data

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.miker.floatsauce.api.CreatorV3Api
import org.miker.floatsauce.api.SubscriptionsV3Api
import org.miker.floatsauce.domain.models.*
import org.openapitools.client.auth.ApiKeyAuth
import org.openapitools.client.infrastructure.ApiClient
import org.openapitools.client.infrastructure.HttpResponse

class FloatsauceRepositoryImpl(
    private val secureStorage: SecureStorage
) : FloatsauceRepository {

    private fun createSubscriptionsApi(service: AuthService): SubscriptionsV3Api {
        val baseUrl = when (service) {
            AuthService.FLOATPLANE -> "https://www.floatplane.com"
            AuthService.SAUCE_PLUS -> "https://www.sauceplus.com"
        }
        val cookieName = when (service) {
            AuthService.FLOATPLANE -> "sails.sid"
            AuthService.SAUCE_PLUS -> "__Host-sp-sess"
        }
        val api = SubscriptionsV3Api(baseUrl = baseUrl)
        val auth = api.getAuthentication("CookieAuth") as? ApiKeyAuth
        auth?.let {
            it.paramName = cookieName
            it.apiKey = secureStorage.get("cookie_${service.name}")
        }
        return api
    }

    private fun createCreatorApi(service: AuthService): CreatorV3Api {
        val baseUrl = when (service) {
            AuthService.FLOATPLANE -> "https://www.floatplane.com"
            AuthService.SAUCE_PLUS -> "https://www.sauceplus.com"
        }
        val cookieName = when (service) {
            AuthService.FLOATPLANE -> "sails.sid"
            AuthService.SAUCE_PLUS -> "__Host-sp-sess"
        }
        val api = CreatorV3Api(baseUrl = baseUrl)
        val auth = api.getAuthentication("CookieAuth") as? ApiKeyAuth
        auth?.let {
            it.paramName = cookieName
            it.apiKey = secureStorage.get("cookie_${service.name}")
        }
        return api
    }

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
        val creatorApi = createCreatorApi(service)
        return try {
            val subsResponse = subsApi.listUserSubscriptionsV3()
            val subscriptions = subsResponse.body()
            val creatorIds = subscriptions.map { it.creator }

            if (creatorIds.isEmpty()) return emptyList()

            // Fetch full creator info to get subscriber count and channels
            val creatorsResponse = creatorApi.getCreatorByName(creatorIds)
            val creatorModels = creatorsResponse.body().associateBy { it.id }

            subscriptions.map { sub ->
                val creatorModel = creatorModels[sub.creator]
                Creator(
                    id = sub.creator,
                    name = creatorModel?.title ?: sub.plan.title,
                    iconUrl = creatorModel?.icon?.path ?: sub.plan.logo,
                    subscribers = creatorModel?.subscriberCountDisplay ?: "0",
                    channels = creatorModel?.channels?.size ?: 0,
                    posts = 0
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getVideos(creatorId: String): List<Video> {
        // Implement if needed, but the focus is on auth now.
        return emptyList()
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
