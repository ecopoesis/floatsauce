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
import org.miker.floatsauce.domain.models.Video
import org.miker.floatsauce.presentation.FloatsauceViewModel
import org.miker.floatsauce.getPlatform

@OptIn(UnstableApi::class)
@Composable
fun VideoPlaybackScreen(video: Video, url: String, cookieName: String, cookieValue: String, origin: String, viewModel: FloatsauceViewModel) {
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
        viewModel.goBack()
    }

    DisposableEffect(exoPlayer) {
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
