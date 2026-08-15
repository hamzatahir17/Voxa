package com.voxa.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxa.app.ui.components.VoxaBackgroundShader
import com.voxa.app.ui.components.VoxaVoiceOrbShader
import com.voxa.app.ui.components.VoxaWaveformShader
import com.voxa.app.ui.theme.VoxaTheme
import com.voxa.app.ui.viewmodel.AssistantState
import com.voxa.app.ui.viewmodel.VoxaUiState
import com.voxa.app.ui.viewmodel.VoxaViewModel

@Composable
fun RecordingScreen(
    viewModel: VoxaViewModel,
    onTranscriptionComplete: () -> Unit,
    onClose: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Fallback launcher for devices without integrated SpeechRecognizer service
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = data?.get(0) ?: ""
            if (text.isNotEmpty()) {
                viewModel.processWithAIExternally(text)
            }
        } else {
            onClose()
        }
    }

    LaunchedEffect(uiState.transcription) {
        if (uiState.transcription == "RECOGNIZER_INTENT_FALLBACK") {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "How can I help?")
            }
            try {
                voiceLauncher.launch(intent)
            } catch (e: Exception) {
                viewModel.resetToError("Voice recognition not supported.")
            }
        }
    }

    RecordingScreenContent(
        uiState = uiState,
        onTranscriptionComplete = onTranscriptionComplete,
        onClose = {
            viewModel.stopListening()
            onClose()
        }
    )
}

@Composable
fun RecordingScreenContent(
    uiState: VoxaUiState,
    onTranscriptionComplete: () -> Unit,
    onClose: () -> Unit
) {
    LaunchedEffect(uiState.assistantState) {
        if (uiState.assistantState == AssistantState.IDLE && uiState.transcription.isNotEmpty() && uiState.transcription != "RECOGNIZER_INTENT_FALLBACK") {
            onTranscriptionComplete()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        VoxaBackgroundShader(state = uiState.assistantState, volume = uiState.volume)

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            VoxaVoiceOrbShader(
                modifier = Modifier.fillMaxSize(),
                state = uiState.assistantState,
                volume = uiState.volume
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            InteractionHeader(uiState.assistantState, onClose)
            
            Spacer(modifier = Modifier.height(60.dp))
            
            TranscriptionArea(uiState.transcription)
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Middle Waveform removed as requested
            
            Spacer(modifier = Modifier.weight(1f))
            
            InteractionControls(uiState.assistantState, onMicClick = onClose)
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun InteractionHeader(state: AssistantState, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val statusColor = when(state) {
                AssistantState.THINKING -> Color(0xFFC6BFFF)
                AssistantState.PROCESSING -> Color(0xFF24FFCD)
                else -> MaterialTheme.colorScheme.primary
            }
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = state.name,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(44.dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun TranscriptionArea(transcription: String) {
    if (transcription == "RECOGNIZER_INTENT_FALLBACK") return

    val entities = listOf("Sarah", "Asif", "tomorrow", "3 PM", "2 PM", "Friday", "New York")
    
    val annotatedString = buildAnnotatedString {
        transcription.split(" ").forEach { word ->
            val cleanWord = word.replace(Regex("[^A-Za-z0-9]"), "")
            val isEntity = entities.contains(cleanWord)
            
            if (isEntity) {
                withStyle(style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 32.sp,
                    lineHeight = 48.sp, // Increased line height to prevent overlapping
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                ).toSpanStyle()) {
                    append("$word ")
                }
            } else {
                withStyle(style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 32.sp,
                    lineHeight = 48.sp, // Increased line height to prevent overlapping
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                ).toSpanStyle()) {
                    append("$word ")
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp) // More space for text
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = annotatedString,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            lineHeight = 48.sp // Explicitly set line height for the whole text block
        )
    }
}

@Composable
fun InteractionControls(state: AssistantState, onMicClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onMicClick,
            color = if (state == AssistantState.LISTENING) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            modifier = Modifier.size(88.dp),
            shadowElevation = 20.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (state == AssistantState.LISTENING) Icons.Default.Close else Icons.Default.Mic, 
                    contentDescription = null, 
                    tint = if (state == AssistantState.LISTENING) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary, 
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecordingScreenPreview() {
    VoxaTheme {
        RecordingScreenContent(
            uiState = VoxaUiState(
                assistantState = AssistantState.LISTENING, 
                transcription = "Schedule a meeting with Sarah for tomorrow morning",
                volume = 0.6f
            ),
            onTranscriptionComplete = {},
            onClose = {}
        )
    }
}
