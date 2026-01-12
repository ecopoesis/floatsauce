package org.miker.floatsauce

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.miker.floatsauce.domain.models.AuthService
import org.miker.floatsauce.presentation.FloatsauceViewModel

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
