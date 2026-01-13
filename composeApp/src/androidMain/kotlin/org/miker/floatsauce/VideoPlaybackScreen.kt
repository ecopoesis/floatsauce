package org.miker.floatsauce

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import co.touchlab.kermit.Logger
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.focusable
// Removed Icons imports for now
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import android.view.KeyEvent
import org.miker.floatsauce.domain.models.Video
import org.miker.floatsauce.presentation.FloatsauceViewModel
import org.miker.floatsauce.getPlatform

@OptIn(UnstableApi::class)
@Composable
fun VideoPlaybackScreen(video: Video, creatorName: String, url: String, cookieName: String, cookieValue: String, origin: String, viewModel: FloatsauceViewModel) {
    Logger.d { "Android VideoPlaybackScreen: playing $url with cookie $cookieName and origin $origin" }
    val context = LocalContext.current

    val exoPlayer = remember(url, cookieValue, origin) {
        val dataSourceFactory = DataSource.Factory {
            val upstream = DefaultHttpDataSource.Factory()
                .setUserAgent(getPlatform().userAgent)
                .createDataSource()
            FloatsauceDataSource(
                upstream = upstream,
                cookieName = cookieName,
                cookieValue = cookieValue,
                origin = origin,
                userAgent = getPlatform().userAgent
            )
        }

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    val focusRequester = remember { FocusRequester() }

    var lastSentProgress by remember { mutableIntStateOf(-1) }
    var lastUpdateTimestamp by remember { mutableLongStateOf(0L) }

    val sendProgress: (Int?, Boolean) -> Unit = { progressSeconds, force ->
        val currentProgress = progressSeconds ?: (exoPlayer.currentPosition / 1000).toInt()

        if (force || (currentProgress != lastSentProgress && currentProgress >= 0)) {
            Logger.d { "Sending progress update: $currentProgress seconds for video ${video.id}" }
            viewModel.updateVideoProgress(video, currentProgress)
            lastSentProgress = currentProgress
            lastUpdateTimestamp = System.currentTimeMillis()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingParam: Boolean) {
                isPlaying = isPlayingParam
                if (!isPlayingParam && exoPlayer.playbackState != Player.STATE_ENDED) {
                    sendProgress(null, true)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                duration = exoPlayer.duration.coerceAtLeast(0L)
                if (state == Player.STATE_ENDED) {
                    sendProgress((exoPlayer.duration / 1000).toInt(), true)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition

            val now = System.currentTimeMillis()
            if (now - lastUpdateTimestamp >= 10000) {
                sendProgress(null, false)
            }

            delay(500)
        }
    }

    LaunchedEffect(showControls, lastInteractionTime, isPlaying) {
        if (showControls && isPlaying) {
            delay(5000)
            showControls = false
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(exoPlayer) {
        val customUrl = Uri.parse(url).buildUpon().scheme("floatsauce").build()
        exoPlayer.setMediaItem(
            MediaItem.Builder()
                .setUri(customUrl)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()
        )
        exoPlayer.prepare()
        exoPlayer.play()
    }

    BackHandler {
        if (showControls) {
            showControls = false
        } else {
            viewModel.goBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    showControls = true
                    lastInteractionTime = System.currentTimeMillis()
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                            currentPosition = exoPlayer.currentPosition
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                            currentPosition = exoPlayer.currentPosition
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(48.dp)
            ) {
                Column(modifier = Modifier.align(Alignment.BottomStart)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = creatorName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Progress Bar
                    val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(Color.White)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = formatTime(duration),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                /*
                if (!isPlaying) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier
                            .size(100.dp)
                            .align(Alignment.Center),
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                }
                */
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    val secondsStr = seconds.toString().padStart(2, '0')
    val minutesStr = minutes.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$minutesStr:$secondsStr" else "$minutesStr:$secondsStr"
}
