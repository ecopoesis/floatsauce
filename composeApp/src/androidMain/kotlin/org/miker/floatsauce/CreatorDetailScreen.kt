package org.miker.floatsauce

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.miker.floatsauce.domain.models.Channel
import org.miker.floatsauce.domain.models.Creator
import org.miker.floatsauce.domain.models.Video
import org.miker.floatsauce.presentation.FloatsauceViewModel

@Composable
fun CreatorDetailScreen(creator: Creator, viewModel: FloatsauceViewModel) {
    val videos by viewModel.videos.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val selectedChannel by viewModel.selectedChannel.collectAsState()
    val lastPlayedVideoId by viewModel.lastPlayedVideoId.collectAsState()
    val currentCreator = subscriptions.find { it.id == creator.id } ?: creator
    val channels = currentCreator.channels

    val gridState = rememberLazyGridState()

    LaunchedEffect(videos, lastPlayedVideoId) {
        if (lastPlayedVideoId != null && videos.isNotEmpty()) {
            val index = videos.indexOfFirst { it.id == lastPlayedVideoId }
            if (index != -1) {
                val headerItems = 2 + (if (channels != null && channels.size > 1) 1 else 0)
                gridState.scrollToItem(index + headerItems)
            }
        }
    }

    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItemIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleItemIndex >= gridState.layoutInfo.totalItemsCount - 5
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadMoreVideos(creator)
        }
    }

    LaunchedEffect(creator.id) {
        viewModel.fetchCreatorDetails(creator)
    }

    var showSettings by remember { mutableStateOf(false) }

    BackHandler {
        if (showSettings) {
            showSettings = false
        } else {
            viewModel.goBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize()
                .focusProperties { canFocus = !showSettings },
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box {
                    // Banner
                    AsyncImage(
                        model = currentCreator.bannerUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3840f / 720f)
                            .background(Color.DarkGray),
                        contentScale = ContentScale.Crop
                    )

                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                        SettingsButton(onClick = { showSettings = true })
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = currentCreator.name,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            if (channels != null && channels.size > 1) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ChannelBar(currentCreator, selectedChannel, viewModel)
                }
            }

            if (videos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(onClick = { viewModel.goBack() }) {
                            Text("No videos found")
                        }
                    }
                }
            } else {
                items(videos, key = { it.id }) { video ->
                    VideoCard(video, creator, viewModel, isInitialFocus = video.id == lastPlayedVideoId)
                }
            }
        }

        SettingsOverlay(
            isVisible = showSettings,
            onDismiss = { showSettings = false },
            viewModel = viewModel
        )
    }
}

@Composable
fun ChannelBar(creator: Creator, selectedChannel: Channel?, viewModel: FloatsauceViewModel) {
    val channels = creator.channels ?: emptyList()

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ChannelButton(
                title = "All",
                iconUrl = creator.iconUrl,
                isSelected = selectedChannel == null,
                onClick = { viewModel.selectChannel(creator, null) }
            )
        }
        items(channels) { channel ->
            ChannelButton(
                title = channel.title,
                iconUrl = channel.iconUrl,
                isSelected = selectedChannel?.id == channel.id,
                onClick = { viewModel.selectChannel(creator, channel) }
            )
        }
    }
}

@Composable
fun ChannelButton(title: String, iconUrl: String?, isSelected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val brandPurple = Color(0xFF7337B5)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50), // Semi-circle ends
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = if (isFocused) {
            BorderStroke(4.dp, brandPurple)
        } else if (isSelected) {
            null
        } else {
            BorderStroke(1.dp, Color.Gray)
        },
        interactionSource = interactionSource,
        modifier = Modifier.height(40.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
            )
        }
    }
}

@Composable
fun VideoCard(video: Video, creator: Creator, viewModel: FloatsauceViewModel, isInitialFocus: Boolean = false) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isInitialFocus) {
        if (isInitialFocus) {
            focusRequester.requestFocus()
        }
    }

    Surface(
        modifier = Modifier
            .padding(16.dp)
            .focusRequester(focusRequester)
            .clickable { viewModel.playVideo(video, creator) },
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E1E1E)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray),
                    contentScale = ContentScale.Crop
                )
                if (video.progress > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(bottom = 0.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(video.progress / 100f)
                                .background(Color.Yellow)
                        )
                    }
                }
                // Duration overlay (Liquid Glass simulated with alpha)
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = video.duration,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = video.releaseDate,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
