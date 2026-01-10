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
import org.miker.floatsauce.data.FloatsauceRepository
import org.miker.floatsauce.domain.models.*

sealed class Screen {
    object ServiceSelection : Screen()
    data class QRLogin(val service: AuthService) : Screen()
    data class AuthFailed(val service: AuthService) : Screen()
    data class Subscriptions(val service: AuthService) : Screen()
    data class CreatorDetail(val creator: Creator) : Screen()
}

class FloatsauceViewModel(
    private val repository: FloatsauceRepository
) : ViewModel() {

    private val _currentScreen = MutableStateFlow<Screen>(Screen.ServiceSelection)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _services = MutableStateFlow(repository.getServices())
    val services: StateFlow<List<AuthService>> = _services.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<Creator>>(emptyList())
    val subscriptions: StateFlow<List<Creator>> = _subscriptions.asStateFlow()

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()

    private val _authState = MutableStateFlow<AuthState?>(null)
    val authState: StateFlow<AuthState?> = _authState.asStateFlow()

    fun selectService(service: AuthService) {
        viewModelScope.launch {
            _authState.value = null // Reset auth state before checking
            val auth = repository.getAuthState(service)
            _authState.value = auth
            if (auth.isLoggedIn) {
                _subscriptions.value = repository.getSubscriptions(service)
                _currentScreen.value = Screen.Subscriptions(service)
            } else {
                _currentScreen.value = Screen.QRLogin(service)
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
                        _currentScreen.value = Screen.Subscriptions(service)
                        success = true
                        break
                    }
                }
            }

            if (!success) {
                _currentScreen.value = Screen.AuthFailed(service)
            }
        }
    }

    fun selectCreator(creator: Creator) {
        viewModelScope.launch {
            _videos.value = repository.getVideos(creator.id)
            _currentScreen.value = Screen.CreatorDetail(creator)
        }
    }

    fun goBack() {
        val current = _currentScreen.value
        _currentScreen.value = when (current) {
            is Screen.ServiceSelection -> Screen.ServiceSelection
            is Screen.QRLogin -> Screen.ServiceSelection
            is Screen.AuthFailed -> Screen.ServiceSelection
            is Screen.Subscriptions -> Screen.ServiceSelection
            is Screen.CreatorDetail -> {
                // Determine which subscriptions to go back to based on context or just previous state
                // For now, simpler:
                Screen.ServiceSelection // Or track stack
            }
        }
        if (_currentScreen.value == Screen.ServiceSelection) {
            _authState.value = null
        }
    }

    fun watchCurrentScreen(onEach: (Screen) -> Unit) = currentScreen.onEach { onEach(it) }.launchIn(viewModelScope)
    fun watchServices(onEach: (List<AuthService>) -> Unit) = services.onEach { onEach(it) }.launchIn(viewModelScope)
    fun watchSubscriptions(onEach: (List<Creator>) -> Unit) = subscriptions.onEach { onEach(it) }.launchIn(viewModelScope)
    fun watchVideos(onEach: (List<Video>) -> Unit) = videos.onEach { onEach(it) }.launchIn(viewModelScope)
    fun watchAuthState(onEach: (AuthState?) -> Unit) = authState.onEach { onEach(it) }.launchIn(viewModelScope)
}
