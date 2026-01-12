package org.miker.floatsauce

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.miker.floatsauce.presentation.FloatsauceViewModel

@Composable
fun ServiceSelectionScreen(viewModel: FloatsauceViewModel) {
    val services by viewModel.services.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Choose Service", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))
        Row {
            services.forEach { service ->
                Button(
                    onClick = { viewModel.selectService(service) },
                    modifier = Modifier.padding(16.dp).size(200.dp, 100.dp)
                ) {
                    Text(service.displayName)
                }
            }
        }
    }
}
