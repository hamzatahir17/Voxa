package com.voxa.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.voxa.app.ui.components.VoxaBackgroundShader
import com.voxa.app.ui.theme.VoxaTheme
import com.voxa.app.ui.viewmodel.VoxaViewModel

@Composable
fun OnboardingScreen(
    viewModel: VoxaViewModel,
    onPermissionGranted: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.RECORD_AUDIO)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isMicGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (isMicGranted) {
            viewModel.grantMicPermission()
        }
    }

    // Check if permissions are already granted
    LaunchedEffect(Unit) {
        val allGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            viewModel.grantMicPermission()
        }
    }
    
    LaunchedEffect(uiState.isMicPermissionGranted) {
        if (uiState.isMicPermissionGranted) {
            onPermissionGranted()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        VoxaBackgroundShader()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LogoSection()

            Spacer(modifier = Modifier.weight(1f))

            OnboardingCard { 
                launcher.launch(permissionsToRequest)
            }
        }
    }
}

@Composable
fun LogoSection() {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(240.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .blur(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    shape = CircleShape
                )
        )

        AsyncImage(
            model = "https://lh3.googleusercontent.com/aida-public/AB6AXuArypJJjndzEeMxTk3NL2blt9m-iRsi91D5TurlFUnJpdMbTvud9b13i5e_CkdRjYRnga2E_JN6YPeOueP56ZkKbLARchrlzRo_5yvfkFBBYlWazC-IlavFXoxi9cr96FmB1Ov4n_K1GBcnrSq-jFozZjHNKFAs2jQLpKpN4CdIW0Am90owBG5v5m7jP167dSq9UGc8NjImwXAvglYkbyuMMEg1EHlcXAzaOIe3vdJU7Ix01PYPy5SiYQ",
            contentDescription = "Voxa AI Logo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun OnboardingCard(onGrantAccess: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 48.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = CircleShape
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Enable Voice Access",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Voxa needs microphone access to hear your commands and provide an immersive voice experience.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onGrantAccess,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                border = androidx.compose.foundation.BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "Grant Access",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }

            TextButton(
                onClick = { /* Handle Later */ },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = "MAYBE LATER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OnboardingScreenPreview() {
    VoxaTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121317))) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LogoSection()
                Spacer(modifier = Modifier.weight(1f))
                OnboardingCard {}
            }
        }
    }
}
