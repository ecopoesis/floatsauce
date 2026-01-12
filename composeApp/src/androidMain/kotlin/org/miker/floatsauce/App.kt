package org.miker.floatsauce

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.miker.floatsauce.data.AndroidSecureStorage
import org.miker.floatsauce.data.FloatsauceRepositoryImpl
import org.miker.floatsauce.presentation.FloatsauceViewModel
import org.miker.floatsauce.presentation.Screen

@Composable
fun App() {
    val context = LocalContext.current
    val viewModel: FloatsauceViewModel = viewModel<FloatsauceViewModel>(
        factory = viewModelFactory {
            initializer {
                val secureStorage = AndroidSecureStorage(context)
                val repository = FloatsauceRepositoryImpl(secureStorage)
                FloatsauceViewModel(repository)
            }
        }
    )
    val currentScreen by viewModel.currentScreen.collectAsState()

    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
            contentColor = Color.White
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (val screen = currentScreen) {
                    is Screen.ServiceSelection -> ServiceSelectionScreen(viewModel)
                    is Screen.QRLogin -> QRLoginScreen(screen.service, viewModel)
                    is Screen.AuthFailed -> AuthFailedScreen(screen.service, viewModel)
                    is Screen.Subscriptions -> SubscriptionsScreen(screen.service, viewModel)
                    is Screen.CreatorDetail -> CreatorDetailScreen(screen.creator, viewModel)
                    is Screen.VideoPlayback -> VideoPlaybackScreen(screen.video, screen.url, screen.cookieName, screen.cookieValue, screen.origin, viewModel)
                }
            }
        }
    }
}
