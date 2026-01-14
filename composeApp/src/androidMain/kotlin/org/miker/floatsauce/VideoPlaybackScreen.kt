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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Size
import coil3.asDrawable
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
import coil3.request.maxBitmapSize
import coil3.size.Dimension
import org.miker.floatsauce.domain.models.Video
import org.miker.floatsauce.presentation.FloatsauceViewModel
import org.miker.floatsauce.getPlatform

@OptIn(UnstableApi::class)
@Composable
fun VideoPlaybackScreen(
    video: Video,
    creatorName: String,
    url: String,
    resumeProgressSeconds: Int,
    cookieName: String,
    cookieValue: String,
    origin: String,
    thumbnailUrl: String?,
    thumbnailWidth: Int,
    thumbnailHeight: Int,
    thumbnailFrameCount: Int,
    viewModel: FloatsauceViewModel
) {
    Logger.d { "Android VideoPlaybackScreen: playing $url (resume at $resumeProgressSeconds) with cookie $cookieName and origin $origin" }
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
    var isScrubbing by remember { mutableStateOf(false) }
    var lastScrubTime by remember { mutableLongStateOf(0L) }
    var spriteBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl != null) {
            try {
                val loader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .maxBitmapSize(Size(Dimension.Undefined, Dimension.Undefined))
                    .size(thumbnailWidth, thumbnailHeight)
                    .precision(Precision.EXACT)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.image.asDrawable(context.resources) as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        Logger.d { "Loaded sprite bitmap: ${bitmap.width}x${bitmap.height} (Expected: ${thumbnailWidth}x${thumbnailHeight}) for $thumbnailUrl" }
                    }
                    spriteBitmap = bitmap
                }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to load sprite" }
            }
        } else {
            spriteBitmap = null
        }
    }

    LaunchedEffect(isScrubbing, lastScrubTime) {
        if (isScrubbing) {
            while (System.currentTimeMillis() - lastScrubTime < 2000) {
                delay(500)
            }
            isScrubbing = false
        }
    }

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
        if (resumeProgressSeconds > 0) {
            exoPlayer.seekTo(resumeProgressSeconds * 1000L)
        }
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
                    if (event.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_BACK) {
                        showControls = true
                        lastInteractionTime = System.currentTimeMillis()
                    }
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                            currentPosition = exoPlayer.currentPosition
                            isScrubbing = true
                            lastScrubTime = System.currentTimeMillis()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                            currentPosition = exoPlayer.currentPosition
                            isScrubbing = true
                            lastScrubTime = System.currentTimeMillis()
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

                    // Thumbnail Preview and Progress Bar
                    val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f

                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val maxWidth = maxWidth
                        if (showControls && isScrubbing && thumbnailUrl != null && thumbnailFrameCount > 0 && duration > 0) {
                            val thumbHeight = 120.dp
                            val thumbWidth = thumbHeight * (160f / 90f)
                            val centerX = maxWidth * progress
                            val halfThumbWidth = thumbWidth / 2
                            val thumbX = (centerX - halfThumbWidth).coerceIn(0.dp, maxWidth - thumbWidth)

                            Box(
                                modifier = Modifier
                                    .offset(x = thumbX)
                                    .padding(bottom = 16.dp)
                                    .size(thumbWidth, thumbHeight)
                                    .background(Color.Black)
                                    .clipToBounds()
                            ) {
                                val frameIndex = (progress * thumbnailFrameCount).toInt().coerceIn(0, thumbnailFrameCount - 1)
                                val framesPerRow = thumbnailWidth / 160
                                if (framesPerRow > 0) {
                                    val column = frameIndex % framesPerRow
                                    val row = frameIndex / framesPerRow

                                    val sprite = spriteBitmap
                                    if (sprite != null && thumbnailWidth > 0 && thumbnailHeight > 0) {
                                        val frameBitmap = remember(sprite, frameIndex) {
                                            try {
                                                val scaleX = sprite.width.toFloat() / thumbnailWidth
                                                val scaleY = sprite.height.toFloat() / thumbnailHeight

                                                val srcX = (column * 160 * scaleX).toInt().coerceIn(0, (sprite.width - 1).coerceAtLeast(0))
                                                val srcY = (row * 90 * scaleY).toInt().coerceIn(0, (sprite.height - 1).coerceAtLeast(0))
                                                val srcWidth = (160 * scaleX).toInt().coerceAtLeast(1)
                                                val srcHeight = (90 * scaleY).toInt().coerceAtLeast(1)

                                                // Adjust srcWidth/srcHeight to stay within bounds
                                                val safeSrcWidth = srcWidth.coerceAtMost(sprite.width - srcX)
                                                val safeSrcHeight = srcHeight.coerceAtMost(sprite.height - srcY)

                                                Bitmap.createBitmap(sprite, srcX, srcY, safeSrcWidth, safeSrcHeight)
                                            } catch (e: Exception) {
                                                Logger.e(e) { "Failed to extract frame at $frameIndex" }
                                                null
                                            }
                                        }

                                        if (frameBitmap != null) {
                                            Image(
                                                bitmap = frameBitmap.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.FillBounds
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.3f))
                                .align(Alignment.BottomStart)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress)
                                    .background(Color.White)
                            )
                        }
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
