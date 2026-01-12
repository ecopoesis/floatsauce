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
    data class VideoPlayback(val video: Video, val creatorName: String, val url: String, val cookieName: String, val cookieValue: String, val origin: String) : Screen()
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

    private var currentOffset = 0
    private var canLoadMore = true
    private var isLoadingMore = false
    private val PAGE_SIZE = 20

    private val _authState = MutableStateFlow<AuthState?>(null)
    val authState: StateFlow<AuthState?> = _authState.asStateFlow()

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
        viewModelScope.launch {
            Logger.d { "playVideo: fetching stream URL for videoId=${video.id}" }
            val url = repository.getVideoStreamUrl(creator.service, video.id)
            if (url != null) {
                val cookie = repository.getCookie(creator.service)
                Logger.d { "playVideo: navigating to VideoPlayback with url=$url, cookieName=${cookie?.first}, origin=${creator.service.origin}" }
                navigateTo(Screen.VideoPlayback(
                    video = video,
                    creatorName = creator.name,
                    url = url,
                    cookieName = cookie?.first ?: "",
                    cookieValue = cookie?.second ?: "",
                    origin = creator.service.origin
                ))
            } else {
                Logger.d { "playVideo: failed to get stream URL" }
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

    fun goBack() {
        Logger.d { "goBack: current=${_currentScreen.value}, stackSize=${screenStack.size}" }
        if (screenStack.isNotEmpty()) {
            val previous = screenStack.removeAt(screenStack.size - 1)
            _currentScreen.value = previous
            if (previous == Screen.ServiceSelection) {
                _authState.value = null
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
    fun watchAuthState(onEach: (AuthState?) -> Unit) = authState.onEach { onEach(it) }.launchIn(viewModelScope)
}
