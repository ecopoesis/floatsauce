package org.miker.floatsauce.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.miker.floatsauce.data.FloatsauceRepository
import org.miker.floatsauce.data.MockFloatsauceRepository
import org.miker.floatsauce.domain.models.*

sealed class Screen {
    object ServiceSelection : Screen()
    data class QRLogin(val service: AuthService) : Screen()
    data class Subscriptions(val service: AuthService) : Screen()
    data class CreatorDetail(val creator: Creator) : Screen()
}

class FloatsauceViewModel(
    private val repository: FloatsauceRepository = MockFloatsauceRepository()
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
        val auth = repository.getAuthState(service)
        _authState.value = auth
        if (auth.isLoggedIn) {
            _subscriptions.value = repository.getSubscriptions(service)
            _currentScreen.value = Screen.Subscriptions(service)
        } else {
            _currentScreen.value = Screen.QRLogin(service)
        }
    }

    fun selectCreator(creator: Creator) {
        _videos.value = repository.getVideos(creator.id)
        _currentScreen.value = Screen.CreatorDetail(creator)
    }

    fun goBack() {
        val current = _currentScreen.value
        _currentScreen.value = when (current) {
            is Screen.ServiceSelection -> Screen.ServiceSelection
            is Screen.QRLogin -> Screen.ServiceSelection
            is Screen.Subscriptions -> Screen.ServiceSelection
            is Screen.CreatorDetail -> {
                // Determine which subscriptions to go back to based on context or just previous state
                // For now, simpler:
                Screen.ServiceSelection // Or track stack
            }
        }
    }
}
