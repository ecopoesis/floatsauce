package org.miker.floatsauce

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import floatsauce.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.miker.floatsauce.data.AndroidSecureStorage
import org.miker.floatsauce.data.FloatsauceRepositoryImpl
import org.miker.floatsauce.getPlatform
import org.miker.floatsauce.domain.models.*
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
    BackHandler {
        viewModel.goBack()
    }
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
fun AuthFailedScreen(service: AuthService, viewModel: FloatsauceViewModel) {
    BackHandler {
        viewModel.goBack()
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Authorization failed", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { viewModel.selectService(service) }) {
            Text("Try again?")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.goBack() }) {
            Text("Back")
        }
    }
}

@Composable
fun SubscriptionsScreen(service: AuthService, viewModel: FloatsauceViewModel) {
    val subscriptions by viewModel.subscriptions.collectAsState()

    BackHandler {
        viewModel.goBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val banner = when (service) {
            AuthService.FLOATPLANE -> painterResource(Res.drawable.floatplane)
            AuthService.SAUCE_PLUS -> painterResource(Res.drawable.sauceplus)
        }

        Image(
            painter = banner,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(3840f / 720f),
            contentScale = ContentScale.FillWidth
        )

        if (subscriptions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val site = when (service) {
                        AuthService.FLOATPLANE -> "floatplane.com"
                        AuthService.SAUCE_PLUS -> "sauceplus.com"
                    }
                    Text(
                        text = "No subscriptions found. Please add subscriptions at $site",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.goBack() }) {
                        Text("Back")
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(subscriptions) { creator ->
                    CreatorCard(creator, viewModel) {
                        viewModel.selectCreator(creator)
                    }
                }
            }
        }
    }
}

@Composable
fun CreatorCard(creator: Creator, viewModel: FloatsauceViewModel, onClick: () -> Unit) {
    LaunchedEffect(creator.id) {
        viewModel.fetchCreatorDetails(creator)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E1E1E)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = creator.iconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                val channels = creator.channels
                Text(
                    text = creator.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    maxLines = 1
                )
                if (channels != null && channels > 1) {
                    Text(
                        text = "$channels Channels",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun CreatorDetailScreen(creator: Creator, viewModel: FloatsauceViewModel) {
    val videos by viewModel.videos.collectAsState()
    BackHandler {
        viewModel.goBack()
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
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
                        Button(onClick = { viewModel.playVideo(video, creator) }) {
                            Text("Play")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun VideoPlaybackScreen(video: Video, url: String, cookieName: String, cookieValue: String, origin: String, viewModel: FloatsauceViewModel) {
    println("[DEBUG_LOG] Android VideoPlaybackScreen: playing $url with cookie $cookieName and origin $origin")
    val context = LocalContext.current
    val exoPlayer = remember {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(getPlatform().userAgent)
            .setDefaultRequestProperties(mapOf(
                "Cookie" to "$cookieName=$cookieValue",
                "Origin" to origin
            ))

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
    }

    BackHandler {
        viewModel.goBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
