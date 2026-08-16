package com.voxa.app.ui.screens

import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
import android.content.Intent
import com.voxa.app.VoxaAlarmService
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxa.app.ui.components.VoxaWaveformShader
import com.voxa.app.ui.theme.VoxaTheme
import com.voxa.app.ui.viewmodel.ItineraryItem
import com.voxa.app.ui.viewmodel.VoxaViewModel
import kotlinx.coroutines.delay

@Composable
fun AlertsScreen(
    viewModel: VoxaViewModel,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // UI thread optimized: AlertsScreen no longer manages audio/vibration directly.
    LaunchedEffect(Unit) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.cancelAll() 
    }

    AlertsScreenContent(
        alertItem = uiState.activeAlertItem,
        snoozeLength = uiState.snoozeLengthMins,
        onDismiss = {
            val intent = Intent(context, VoxaAlarmService::class.java).apply {
                action = "ACTION_STOP_ALARM"
            }
            context.startService(intent)
            onDismiss()
        },
        onSnooze = {
            val intent = Intent(context, VoxaAlarmService::class.java).apply {
                action = "ACTION_STOP_ALARM"
            }
            context.startService(intent)
            onSnooze()
        }
    )
}

@Composable
fun AlertsScreenContent(
    alertItem: ItineraryItem?,
    snoozeLength: Int,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(modifier = Modifier.fillMaxSize()) {
            VoxaWaveformShader()
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f)))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            AlertHeader()
            AlertInfo(alertItem)
            AlertActions(
                onDismiss = onDismiss,
                onSnooze = onSnooze,
                snoozeLength = snoozeLength
            )
        }
    }
}

@Composable
fun AlertHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Alerts",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AlertInfo(item: ItineraryItem?) {
    val infiniteTransition = rememberInfiniteTransition(label = "alert_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = item?.time ?: "00:00 AM",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer { this.alpha = alpha }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = (item?.title ?: "ALERT").uppercase(),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 48.sp,
                lineHeight = 54.sp,
                fontWeight = FontWeight.ExtraBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.scale(0.95f + (alpha * 0.05f))
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = item?.subtitle ?: "Upcoming event scheduled in Voxa.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(250.dp)
        )
    }
}

@Composable
fun AlertActions(onDismiss: () -> Unit, onSnooze: () -> Unit, snoozeLength: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        DismissButton(onDismiss)
        
        Text(
            text = "HOLD TO DISMISS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            letterSpacing = 2.sp
        )

        SnoozeSlider(onSnooze, snoozeLength)
    }
}

@Composable
fun DismissButton(onDismiss: () -> Unit) {
    var isHolding by remember { mutableStateOf(false) }

    val animatedProgress by animateFloatAsState(
        targetValue = if (isHolding) 1f else 0f,
        animationSpec = tween(if (isHolding) 1500 else 300, easing = LinearEasing),
        label = "progress"
    )

    LaunchedEffect(animatedProgress) {
        if (animatedProgress >= 1f) {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .size(80.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHolding = true
                        tryAwaitRelease()
                        isHolding = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = Color.White.copy(alpha = 0.1f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFF24FFCD),
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.StopCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun SnoozeSlider(onSnooze: () -> Unit, snoozeLength: Int) {
    var dragX by remember { mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
            .padding(4.dp)
    ) {
        val scope = this
        val maxDragPx = with(density) { (scope.maxWidth - 56.dp).toPx() }
        
        val animatedDragX by animateFloatAsState(
            targetValue = dragX,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "drag_x"
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "SLIDE TO SNOOZE (${snoozeLength}M)",
                modifier = Modifier.graphicsLayer { 
                    alpha = (1f - (dragX / maxDragPx)).coerceIn(0.2f, 0.5f) 
                },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
        }
        
        Surface(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(animatedDragX.toInt(), 0) }
                .size(56.dp)
                .pointerInput(maxDragPx) {
                    detectDragGestures(
                        onDragEnd = {
                            if (dragX >= (maxDragPx * 0.9f)) {
                                onSnooze()
                            }
                            dragX = 0f
                        },
                        onDragCancel = { dragX = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragX = (dragX + dragAmount.x).coerceIn(0f, maxDragPx)
                        }
                    )
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Snooze,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AlertsScreenPreview() {
    VoxaTheme {
        AlertsScreenContent(
            alertItem = ItineraryItem(
                id = 1,
                time = "10:30 AM",
                title = "Design Sync",
                subtitle = "Discuss new UI shaders",
                isCompleted = false,
                isActive = true,
                leadTimeMins = 0
            ),
            snoozeLength = 10,
            onDismiss = {},
            onSnooze = {}
        )
    }
}
