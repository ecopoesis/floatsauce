package org.miker.floatsauce

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.miker.floatsauce.domain.models.AuthService
import org.miker.floatsauce.presentation.FloatsauceViewModel

@Composable
fun SettingsOverlay(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    viewModel: FloatsauceViewModel
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            focusRequester.requestFocus()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(400.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    SettingsMenuButton(
                        text = "Logout of Floatplane",
                        onClick = {
                            viewModel.logout(AuthService.FLOATPLANE)
                            onDismiss()
                        },
                        modifier = Modifier.focusRequester(focusRequester)
                    )

                    SettingsMenuButton(
                        text = "Logout of Sauce+",
                        onClick = {
                            viewModel.logout(AuthService.SAUCE_PLUS)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val brandPurple = Color(0xFF7337B5)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent,
        border = if (isFocused) {
            BorderStroke(4.dp, brandPurple)
        } else {
            null
        },
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Start
        )
    }
}
