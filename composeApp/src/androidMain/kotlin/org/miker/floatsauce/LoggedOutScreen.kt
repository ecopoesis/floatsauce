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
fun LoggedOutScreen(service: AuthService, viewModel: FloatsauceViewModel) {
    BackHandler {
        viewModel.goBack()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val serviceName = when (service) {
                AuthService.FLOATPLANE -> "Floatplane"
                AuthService.SAUCE_PLUS -> "Sauce+"
            }

            Text(
                text = "You have been logged out of $serviceName",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = {
                viewModel.selectService(service)
            }) {
                Text("Login to $serviceName")
            }
        }
    }
}
