package org.miker.floatsauce

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import floatsauce.composeapp.generated.resources.Res
import floatsauce.composeapp.generated.resources.floatplane
import floatsauce.composeapp.generated.resources.sauceplus
import org.jetbrains.compose.resources.painterResource
import org.miker.floatsauce.domain.models.AuthService
import org.miker.floatsauce.domain.models.Creator
import org.miker.floatsauce.presentation.FloatsauceViewModel

@Composable
fun SubscriptionsScreen(service: AuthService, viewModel: FloatsauceViewModel) {
    val subscriptions by viewModel.subscriptions.collectAsState()
    val allCreators by viewModel.browseCreators.collectAsState()
    val browseCreators = allCreators.filter { creator -> subscriptions.none { it.id == creator.id } }

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

        if (subscriptions.isEmpty() && browseCreators.isEmpty()) {
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
            val showHeaders = subscriptions.isNotEmpty() && browseCreators.isNotEmpty()
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(32.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (subscriptions.isNotEmpty()) {
                    if (showHeaders) {
                        item(span = { GridItemSpan(5) }) {
                            Text(
                                text = "Your subscriptions",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                    items(subscriptions) { creator ->
                        CreatorCard(creator, viewModel) {
                            viewModel.selectCreator(creator)
                        }
                    }
                }

                if (browseCreators.isNotEmpty()) {
                    if (showHeaders) {
                        item(span = { GridItemSpan(5) }) {
                            Text(
                                text = "Browse creators",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                            )
                        }
                    }
                    items(browseCreators) { creator ->
                        CreatorCard(creator, viewModel) {
                            viewModel.selectCreator(creator)
                        }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = creator.iconUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(Color.DarkGray),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = creator.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
