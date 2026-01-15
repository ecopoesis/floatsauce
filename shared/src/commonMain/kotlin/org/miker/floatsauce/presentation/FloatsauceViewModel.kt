package org.miker.floatsauce.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import co.touchlab.kermit.Logger
import org.miker.floatsauce.data.FloatsauceRepository
import org.miker.floatsauce.domain.models.*

sealed class Screen {
    object ServiceSelection : Screen()
    data class QRLogin(val service: AuthService) : Screen()
    data class AuthFailed(val service: AuthService) : Screen()
    data class Subscriptions(val service: AuthService) : Screen()
    data class CreatorDetail(val creator: Creator) : Screen()
    data class VideoPlayback(
        val video: Video,
        val creatorName: String,
        val url: String,
        val resumeProgressSeconds: Int,
        val cookieName: String,
        val cookieValue: String,
        val origin: String,
        val thumbnailUrl: String? = null,
        val thumbnailWidth: Int = 0,
        val thumbnailHeight: Int = 0,
        val thumbnailFrameCount: Int = 0
    ) : Screen()
    data class LoggedOut(val service: AuthService) : Screen()
}

class FloatsauceViewModel(
    private val repository: FloatsauceRepository
) : ViewModel() {

    private val screenStack = mutableListOf<Screen>()

    private val _currentScreen = MutableStateFlow<Screen>(Screen.ServiceSelection)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _services = MutableStateFlow(repository.getServices())
    val services: StateFlow<List<AuthService>> = _services.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<Creator>>(emptyList())
    val subscriptions: StateFlow<List<Creator>> = _subscriptions.asStateFlow()

    private val _browseCreators = MutableStateFlow<List<Creator>>(emptyList())
    val browseCreators: StateFlow<List<Creator>> = _browseCreators.asStateFlow()

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()

    private val _selectedChannel = MutableStateFlow<Channel?>(null)
    val selectedChannel: StateFlow<Channel?> = _selectedChannel.asStateFlow()

    private val _lastPlayedVideoId = MutableStateFlow<String?>(null)
    val lastPlayedVideoId: StateFlow<String?> = _lastPlayedVideoId.asStateFlow()

    private var currentOffset = 0
    private var canLoadMore = true
    private var isLoadingMore = false
    private val PAGE_SIZE = 20

    private val _authState = MutableStateFlow<AuthState?>(null)
    val authState: StateFlow<AuthState?> = _authState.asStateFlow()

    private val _loginStatuses = MutableStateFlow<Map<AuthService, Boolean>>(emptyMap())
    val loginStatuses: StateFlow<Map<AuthService, Boolean>> = _loginStatuses.asStateFlow()

    init {
        updateLoginStatuses()
    }

    private fun updateLoginStatuses() {
        viewModelScope.launch {
            val statuses = mutableMapOf<AuthService, Boolean>()
            for (service in repository.getServices()) {
                statuses[service] = repository.getCookie(service) != null
            }
            _loginStatuses.value = statuses
        }
    }

    private fun navigateTo(screen: Screen) {
        if (_currentScreen.value != screen) {
            screenStack.add(_currentScreen.value)
            _currentScreen.value = screen
        }
    }

    private fun navigateToSubscriptions(service: AuthService) {
        _currentScreen.value = Screen.Subscriptions(service)
        screenStack.clear()
        screenStack.add(Screen.ServiceSelection)
    }

    fun selectService(service: AuthService) {
        viewModelScope.launch {
            _authState.value = null // Reset auth state before checking
            val auth = repository.getAuthState(service)
            _authState.value = auth
            if (auth.isLoggedIn) {
                _subscriptions.value = repository.getSubscriptions(service)
                _browseCreators.value = repository.getCreators(service)
                navigateToSubscriptions(service)
            } else {
                navigateTo(Screen.QRLogin(service))
                startPolling(service)
            }
        }
    }

    private fun startPolling(service: AuthService) {
        viewModelScope.launch {
            repository.requestDeviceAuth(service)
            val startTime = kotlinx.datetime.Clock.System.now()
            val timeoutSeconds = 15
            var success = false

            while (kotlinx.datetime.Clock.System.now().epochSeconds - startTime.epochSeconds < timeoutSeconds) {
                delay(2000)
                val token = repository.pollForToken(service)
                if (token != null) {
                    repository.saveToken(service, token)
                    val auth = repository.getAuthState(service)
                    if (auth.isLoggedIn) {
                        _authState.value = auth
                        _subscriptions.value = repository.getSubscriptions(service)
                        _browseCreators.value = repository.getCreators(service)
                        updateLoginStatuses()
                        navigateToSubscriptions(service)
                        success = true
                        break
                    }
                }
            }

            if (!success) {
                _currentScreen.value = Screen.AuthFailed(service) // Don't add AuthFailed to stack? Or maybe yes.
            }
        }
    }

    fun selectCreator(creator: Creator) {
        _selectedChannel.value = null
        _lastPlayedVideoId.value = null
        currentOffset = 0
        canLoadMore = true
        _videos.value = emptyList()
        viewModelScope.launch {
            loadPage(creator, null)
            navigateTo(Screen.CreatorDetail(creator))
        }
    }

    fun selectChannel(creator: Creator, channel: Channel?) {
        _selectedChannel.value = channel
        _lastPlayedVideoId.value = null
        currentOffset = 0
        canLoadMore = true
        _videos.value = emptyList()
        viewModelScope.launch {
            loadPage(creator, channel)
        }
    }

    fun loadMoreVideos(creator: Creator) {
        if (!canLoadMore || isLoadingMore) return
        viewModelScope.launch {
            loadPage(creator, _selectedChannel.value)
        }
    }

    private suspend fun loadPage(creator: Creator, channel: Channel?) {
        isLoadingMore = true
        try {
            val newVideos = repository.getVideos(creator.service, creator.id, channel?.id, limit = PAGE_SIZE, fetchAfter = currentOffset)
            if (newVideos.isEmpty()) {
                canLoadMore = false
            } else {
                val progressMap = if (newVideos.isNotEmpty()) {
                    repository.getVideosProgress(creator.service, newVideos.map { it.postId })
                } else {
                    emptyMap()
                }

                val updatedNewVideos = newVideos.map { video ->
                    val rawProgress = progressMap[video.postId] ?: 0
                    val progress = if (rawProgress >= 95) 100 else rawProgress
                    video.copy(progress = progress)
                }

                _videos.value = _videos.value + updatedNewVideos
                currentOffset += newVideos.size
                if (newVideos.size < PAGE_SIZE) {
                    canLoadMore = false
                }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Error loading videos" }
        } finally {
            isLoadingMore = false
        }
    }

    fun playVideo(video: Video, creator: Creator) {
        _lastPlayedVideoId.value = video.id
        viewModelScope.launch {
            Logger.d { "playVideo: fetching stream URL for videoId=${video.id}" }
            val info = repository.getVideoStreamUrl(creator.service, video.id)
            if (info != null) {
                val cookie = repository.getCookie(creator.service)
                Logger.d { "playVideo: navigating to VideoPlayback with url=${info.url}, resume=${info.resumeProgressSeconds}, cookieName=${cookie?.first}, origin=${creator.service.origin}" }
                navigateTo(Screen.VideoPlayback(
                    video = video,
                    creatorName = creator.name,
                    url = info.url,
                    resumeProgressSeconds = info.resumeProgressSeconds,
                    cookieName = cookie?.first ?: "",
                    cookieValue = cookie?.second ?: "",
                    origin = creator.service.origin,
                    thumbnailUrl = info.thumbnailUrl,
                    thumbnailWidth = info.thumbnailWidth,
                    thumbnailHeight = info.thumbnailHeight,
                    thumbnailFrameCount = info.thumbnailFrameCount
                ))
            } else {
                Logger.d { "playVideo: failed to get stream URL" }
            }
        }
    }

    fun updateVideoProgress(video: Video, progressSeconds: Int, progressPercent: Int? = null) {
        viewModelScope.launch {
            repository.updateVideoProgress(video.service, video.id, progressSeconds)
            if (progressPercent != null) {
                _videos.value = _videos.value.map {
                    if (it.id == video.id) it.copy(progress = progressPercent) else it
                }
            }
        }
    }

    fun fetchCreatorDetails(creator: Creator) {
        if (creator.channels != null) return
        viewModelScope.launch {
            val details = repository.getCreatorDetails(creator.service, creator.id)
            if (details != null) {
                _subscriptions.value = _subscriptions.value.map {
                    if (it.id == creator.id) details else it
                }
            }
        }
    }

    fun logout(service: AuthService) {
        viewModelScope.launch {
            repository.logout(service)
            updateLoginStatuses()
            val current = _currentScreen.value
            val shouldNavigateToLoggedOut = when (current) {
                is Screen.Subscriptions -> current.service == service
                is Screen.CreatorDetail -> current.creator.service == service
                else -> false
            }

            if (shouldNavigateToLoggedOut) {
                // Clear stack and go to LoggedOut
                screenStack.clear()
                screenStack.add(Screen.ServiceSelection)
                _currentScreen.value = Screen.LoggedOut(service)
            }
        }
    }

    private fun refreshVideosProgress(creator: Creator) {
        viewModelScope.launch {
            try {
                val postIds = _videos.value.map { it.postId }
                if (postIds.isEmpty()) return@launch

                val progressMap = repository.getVideosProgress(creator.service, postIds)
                _videos.value = _videos.value.map { video ->
                    val rawProgress = progressMap[video.postId] ?: video.progress
                    val progress = if (rawProgress >= 95) 100 else rawProgress
                    video.copy(progress = progress)
                }
            } catch (e: Exception) {
                Logger.e(e) { "Error refreshing videos progress" }
            }
        }
    }

    fun goBack() {
        Logger.d { "goBack: current=${_currentScreen.value}, stackSize=${screenStack.size}" }
        val current = _currentScreen.value
        if (screenStack.isNotEmpty()) {
            val previous = screenStack.removeAt(screenStack.size - 1)
            _currentScreen.value = previous
            if (previous == Screen.ServiceSelection) {
                _authState.value = null
            }

            if (current is Screen.VideoPlayback && previous is Screen.CreatorDetail) {
                refreshVideosProgress(previous.creator)
            }
        } else {
            // Root screen, could exit but we don't have that control here easily
            _currentScreen.value = Screen.ServiceSelection
            _authState.value = null
        }
    }

    fun watchCurrentScreen(onEach: (Screen) -> Unit) = currentScreen.onEach { onEach(it) }.launchIn(viewModelScope)
    fun watchServices(onEach: (List<AuthService>) -> Unit) = services.onEach { onEach(it) }.launchIn(viewModelScope)
    fun watchSubscriptions(onEach: (List<Creator>) -> Unit) = subscriptions.onEach { onEach(it) }.launchIn(viewModelScope)
    fun watchBrowseCreators(onEach: (List<Creator>) -> Unit) = browseCreators.onEach { onEach(it) }.launchIn(viewModelScope)
    fun watchVideos(onEach: (List<Video>) -> Unit) = videos.onEach { onEach(it) }.launchIn(viewModelScope)
    fun watchSelectedChannel(onEach: (Channel?) -> Unit) = selectedChannel.onEach { onEach(it) }.launchIn(viewModelScope)
    fun watchLastPlayedVideoId(onEach: (String?) -> Unit) = lastPlayedVideoId.onEach { onEach(it) }.launchIn(viewModelScope)
    fun watchAuthState(onEach: (AuthState?) -> Unit) = authState.onEach { onEach(it) }.launchIn(viewModelScope)
    fun watchLoginStatuses(onEach: (Map<AuthService, Boolean>) -> Unit) = loginStatuses.onEach { onEach(it) }.launchIn(viewModelScope)
}
