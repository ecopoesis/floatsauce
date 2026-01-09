package org.miker.floatsauce

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.miker.floatsauce.domain.models.*
import org.miker.floatsauce.presentation.FloatsauceViewModel
import org.miker.floatsauce.presentation.Screen

@Composable
fun App(viewModel: FloatsauceViewModel = FloatsauceViewModel()) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.safeContentPadding()) {
                when (val screen = currentScreen) {
                    is Screen.ServiceSelection -> ServiceSelectionScreen(viewModel)
                    is Screen.QRLogin -> QRLoginScreen(screen.service, viewModel)
                    is Screen.Subscriptions -> SubscriptionsScreen(screen.service, viewModel)
                    is Screen.CreatorDetail -> CreatorDetailScreen(screen.creator, viewModel)
                }
            }
        }
    }
}

@Composable
fun ServiceSelectionScreen(viewModel: FloatsauceViewModel) {
    val services by viewModel.services.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Choose Service", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))
        Row {
            services.forEach { service ->
                Button(
                    onClick = { viewModel.selectService(service) },
                    modifier = Modifier.padding(16.dp).size(200.dp, 100.dp)
                ) {
                    Text(service.displayName)
                }
            }
        }
    }
}

@Composable
fun QRLoginScreen(service: AuthService, viewModel: FloatsauceViewModel) {
    val authState by viewModel.authState.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Login to ${service.displayName}", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Scan the QR code on your phone")
        Spacer(modifier = Modifier.height(32.dp))
        Box(modifier = Modifier.size(200.dp).background(Color.White)) {
            Text("QR CODE HERE\n${authState?.qrCodeUrl ?: ""}", color = Color.Black, modifier = Modifier.align(Alignment.Center))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { viewModel.goBack() }) {
            Text("Back")
        }
    }
}

@Composable
fun SubscriptionsScreen(service: AuthService, viewModel: FloatsauceViewModel) {
    val subscriptions by viewModel.subscriptions.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { viewModel.goBack() }) { Text("Back") }
            Spacer(modifier = Modifier.width(16.dp))
            Text("${service.displayName} Subscriptions", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(subscriptions) { creator ->
                ListItem(
                    headlineContent = { Text(creator.name) },
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    leadingContent = {
                        Box(modifier = Modifier.size(40.dp).background(Color.Gray))
                    },
                    trailingContent = {
                        Button(onClick = { viewModel.selectCreator(creator) }) {
                            Text("View")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CreatorDetailScreen(creator: Creator, viewModel: FloatsauceViewModel) {
    val videos by viewModel.videos.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { viewModel.goBack() }) { Text("Back") }
            Spacer(modifier = Modifier.width(16.dp))
            Text(creator.name, style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(videos) { video ->
                ListItem(
                    headlineContent = { Text(video.title) },
                    supportingContent = { Text(video.duration) },
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    trailingContent = {
                        Button(onClick = { /* Play Video */ println("Playing: ${video.videoUrl}") }) {
                            Text("Play")
                        }
                    }
                )
            }
        }
    }
}
