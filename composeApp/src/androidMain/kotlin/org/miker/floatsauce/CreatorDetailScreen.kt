package org.miker.floatsauce

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import org.miker.floatsauce.domain.models.Creator
import org.miker.floatsauce.domain.models.Video
import org.miker.floatsauce.presentation.FloatsauceViewModel

@Composable
fun CreatorDetailScreen(creator: Creator, viewModel: FloatsauceViewModel) {
    val videos by viewModel.videos.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val currentCreator = subscriptions.find { it.id == creator.id } ?: creator

    BackHandler {
        viewModel.goBack()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
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
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = currentCreator.name,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                fontWeight = FontWeight.Bold
            )
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
            items(videos) { video ->
                VideoCard(video, creator, viewModel)
            }
        }
    }
}

@Composable
fun VideoCard(video: Video, creator: Creator, viewModel: FloatsauceViewModel) {
    Surface(
        modifier = Modifier
            .padding(16.dp)
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
