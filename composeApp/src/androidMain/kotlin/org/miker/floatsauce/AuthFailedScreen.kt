package org.miker.floatsauce

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.miker.floatsauce.domain.models.AuthService
import org.miker.floatsauce.presentation.FloatsauceViewModel

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
