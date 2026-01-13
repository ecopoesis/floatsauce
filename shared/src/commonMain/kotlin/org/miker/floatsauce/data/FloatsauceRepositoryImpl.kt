package org.miker.floatsauce.data

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import org.miker.floatsauce.api.*
import org.miker.floatsauce.models.*
import org.miker.floatsauce.domain.models.*
import org.miker.floatsauce.getPlatform
import org.openapitools.client.auth.ApiKeyAuth
import org.openapitools.client.infrastructure.ApiClient
import org.openapitools.client.infrastructure.HttpResponse

class FloatsauceRepositoryImpl(
    private val secureStorage: SecureStorage
) : FloatsauceRepository {

    init {
        Logger.setMinSeverity(Severity.Debug)
    }

    private fun <T : ApiClient> createApi(service: AuthService, apiFactory: (String, ((HttpClientConfig<*>) -> Unit)?) -> T): T {
        val baseUrl = service.origin
        val cookieName = when (service) {
            AuthService.FLOATPLANE -> "sails.sid"
            AuthService.SAUCE_PLUS -> "__Host-sp-sess"
        }

        val platform = getPlatform()
        val httpClientConfig: (HttpClientConfig<*>) -> Unit = { config ->
            config.install(Logging) {
                logger = object : io.ktor.client.plugins.logging.Logger {
                    override fun log(message: String) {
                        Logger.d { "Ktor: $message" }
                    }
                }
                level = LogLevel.ALL
                sanitizeHeader { header -> header == HttpHeaders.Authorization || header == "Cookie" || header == "Set-Cookie" }
            }
            config.install(DefaultRequest) {
                val userAgent = platform.userAgent
                Logger.d { "Setting User-Agent: $userAgent" }
                header(HttpHeaders.UserAgent, userAgent)
                Logger.d { "Setting Origin: $baseUrl" }
                header("Origin", baseUrl)
                val cookie = secureStorage.get("cookie_${service.name}")
                if (cookie != null) {
                    Logger.d { "Setting Cookie: $cookieName=$cookie" }
                }
            }
        }

        val api = apiFactory(baseUrl, httpClientConfig)
        val auth = api.getAuthentication("CookieAuth") as? ApiKeyAuth
        auth?.let {
            it.paramName = cookieName
            it.apiKey = secureStorage.get("cookie_${service.name}")
        }
        return api
    }

    private fun createSubscriptionsApi(service: AuthService) = createApi(service) { baseUrl, config -> SubscriptionsV3Api(baseUrl = baseUrl, httpClientConfig = config) }
    private fun createCreatorApi(service: AuthService) = createApi(service) { baseUrl, config -> CreatorV3Api(baseUrl = baseUrl, httpClientConfig = config) }
    private fun createContentApi(service: AuthService) = createApi(service) { baseUrl, config -> ContentV3Api(baseUrl = baseUrl, httpClientConfig = config) }
    private fun createDeliveryApi(service: AuthService) = createApi(service) { baseUrl, config -> DeliveryV3Api(baseUrl = baseUrl, httpClientConfig = config) }

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
                    bannerUrl = null,
                    channels = null,
                    service = service
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getCreators(service: AuthService): List<Creator> {
        if (service == AuthService.FLOATPLANE) return emptyList()
        val creatorApi = createCreatorApi(service)
        return try {
            val response = creatorApi.getCreators(search = "")
            val creators = response.body()

            creators.map { creator ->
                Creator(
                    id = creator.id,
                    name = creator.title,
                    iconUrl = creator.icon.path,
                    bannerUrl = creator.cover?.path,
                    channels = creator.channels.map { Channel(it.id, it.title, it.icon.path) },
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
                bannerUrl = creatorModel.cover?.path,
                channels = creatorModel.channels.map { Channel(it.id, it.title, it.icon.path) },
                service = service
            )
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getVideos(service: AuthService, creatorId: String, channelId: String?, limit: Int?, fetchAfter: Int?): List<Video> {
        val contentApi = createContentApi(service)
        return try {
            val response = contentApi.getCreatorBlogPosts(id = creatorId, channel = channelId, hasVideo = true, limit = limit, fetchAfter = fetchAfter)
            val posts = response.body()
            posts.mapNotNull { post ->
                val videoId = post.videoAttachments?.firstOrNull() ?: return@mapNotNull null
                Video(
                    id = videoId,
                    postId = post.id,
                    title = post.title,
                    thumbnailUrl = post.thumbnail?.path,
                    duration = formatDuration(post.metadata.videoDuration),
                    releaseDate = formatFriendlyDate(post.releaseDate),
                    videoUrl = videoId,
                    service = service
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getVideosProgress(service: AuthService, postIds: List<String>): Map<String, Int> {
        val contentApi = createContentApi(service)
        return try {
            val response = contentApi.getProgress(GetProgressRequest(ids = postIds, contentType = GetProgressRequest.ContentType.BLOG_POST))
            response.body().associate { it.id to it.progress }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    override suspend fun getVideoStreamUrl(service: AuthService, videoId: String): String? {
        val contentApi = createContentApi(service)
        val deliveryApi = createDeliveryApi(service)
        return try {
            // As requested, get video content first
            contentApi.getVideoContent(videoId)
            
            val response = deliveryApi.getDeliveryInfoV3(
                scenario = DeliveryV3Api.ScenarioGetDeliveryInfoV3.ON_DEMAND,
                entityId = videoId
            )
            val cdnResponse = response.body()
            val group = cdnResponse.groups.firstOrNull() ?: return null

            val platform = getPlatform()
            val targetHeight = platform.screenHeight
            Logger.d { "Device screen height: $targetHeight" }

            val variants = group.variants
            val variant = variants
                .filter { (it.meta?.video?.height ?: 0) >= targetHeight }
                .minByOrNull { it.meta?.video?.height ?: 0 }
                ?: variants.maxByOrNull { it.meta?.video?.height ?: 0 }
                ?: variants.firstOrNull()
                ?: return null

            Logger.d { "Selected variant: ${variant.name} (${variant.meta?.video?.width}x${variant.meta?.video?.height})" }
            
            val baseUrl = variant.origins?.firstOrNull()?.url 
                ?: group.origins?.firstOrNull()?.url 
                ?: service.origin
            
            val url = if (variant.url.startsWith("http")) {
                variant.url
            } else {
                "${baseUrl.removeSuffix("/")}/${variant.url.removePrefix("/")}"
            }
            Logger.d { "Resolved Video URL: $url" }
            url
        } catch (e: Exception) {
            Logger.e(e) { "Error getting video stream URL" }
            null
        }
    }

    private fun formatFriendlyDate(instant: Instant): String {
        val now = Clock.System.now()
        val period = now - instant
        val seconds = period.inWholeSeconds
        if (seconds < 60) return "Just now"
        val minutes = seconds / 60
        if (minutes < 60) return "$minutes minute${if (minutes > 1) "s" else ""} ago"
        val hours = minutes / 60
        if (hours < 24) return "$hours hour${if (hours > 1) "s" else ""} ago"
        val days = hours / 24
        if (days < 30) return "$days day${if (days > 1) "s" else ""} ago"
        val months = days / 30
        if (months < 12) return "$months month${if (months > 1) "s" else ""} ago"
        val years = months / 12
        return "$years year${if (years > 1) "s" else ""} ago"
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
        Logger.d { "POST to Device Authorization Endpoint for ${service.displayName}" }
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

    override suspend fun updateVideoProgress(service: AuthService, videoId: String, progressSeconds: Int) {
        val contentApi = createContentApi(service)
        try {
            contentApi.updateProgress(UpdateProgressRequest(id = videoId, contentType = UpdateProgressRequest.ContentType.VIDEO, progress = progressSeconds))
        } catch (e: Exception) {
            Logger.withTag("FloatsauceRepository").e(e) { "Failed to update video progress for $videoId" }
        }
    }

    override suspend fun getCookie(service: AuthService): Pair<String, String>? {
        val cookieName = when (service) {
            AuthService.FLOATPLANE -> "sails.sid"
            AuthService.SAUCE_PLUS -> "__Host-sp-sess"
        }
        val cookieValue = secureStorage.get("cookie_${service.name}") ?: return null
        return cookieName to cookieValue
    }

    override suspend fun logout(service: AuthService) {
        secureStorage.set("cookie_${service.name}", null)
    }
}
