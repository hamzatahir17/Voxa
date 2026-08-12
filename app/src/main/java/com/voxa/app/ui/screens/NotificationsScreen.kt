package com.voxa.app.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxa.app.ui.components.VoxaBackgroundShader
import com.voxa.app.ui.theme.VoxaTheme
import com.voxa.app.ui.viewmodel.NotificationItem
import com.voxa.app.ui.viewmodel.VoxaUiState
import com.voxa.app.ui.viewmodel.VoxaViewModel

@Composable
fun NotificationsScreen(
    viewModel: VoxaViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    NotificationsScreenContent(
        uiState = uiState,
        onBack = onBack,
        onDismissNotification = { viewModel.dismissNotification(it) }
    )
}

@Composable
fun NotificationsScreenContent(
    uiState: VoxaUiState,
    onBack: () -> Unit,
    onDismissNotification: (Int) -> Unit
) {
    Scaffold { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                VoxaBackgroundShader()
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (uiState.notifications.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No new notifications", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(uiState.notifications, key = { it.id }) { item ->
                            NotificationCard(item) { onDismissNotification(item.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationCard(item: NotificationItem, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDismiss() }
            .border(1.dp, if (item.isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(if (item.isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = if (item.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text(text = item.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = item.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.graphicsLayer { alpha = 0.6f })
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NotificationsScreenPreview() {
    VoxaTheme {
        NotificationsScreenContent(
            uiState = VoxaUiState(),
            onBack = {},
            onDismissNotification = {}
        )
    }
}
