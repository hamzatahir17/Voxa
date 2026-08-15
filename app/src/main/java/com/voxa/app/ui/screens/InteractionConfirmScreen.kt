package com.voxa.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlin.math.abs
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.voxa.app.ui.components.VoxaBackgroundShader
import com.voxa.app.ui.theme.VoxaTheme
import com.voxa.app.ui.viewmodel.VoxaUiState
import com.voxa.app.ui.viewmodel.VoxaViewModel
import java.util.Calendar
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

@Composable
fun InteractionConfirmScreen(
    viewModel: VoxaViewModel,
    onConfirm: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    InteractionConfirmScreenContent(
        uiState = uiState,
        onConfirm = onConfirm,
        onUpdateTitle = { viewModel.updatePendingAction(title = it) },
        onUpdateDate = { viewModel.updatePendingAction(date = it) },
        onUpdateTime = { viewModel.updatePendingAction(time = it) },
        onUpdateLeadTime = { viewModel.updatePendingAction(leadTime = it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractionConfirmScreenContent(
    uiState: VoxaUiState,
    onConfirm: () -> Unit,
    onUpdateTitle: (String) -> Unit,
    onUpdateDate: (String) -> Unit,
    onUpdateTime: (String) -> Unit,
    onUpdateLeadTime: (Int) -> Unit
) {
    var isConfirmed by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showLeadTimePicker by remember { mutableStateOf(false) }
    
    val confirmAnimScale by animateFloatAsState(
        targetValue = if (isConfirmed) 0.8f else 1f,
        animationSpec = tween(400, easing = EaseInBack),
        label = "confirm_scale"
    )
    
    val confirmAnimAlpha by animateFloatAsState(
        targetValue = if (isConfirmed) 0f else 1f,
        animationSpec = tween(400),
        label = "confirm_alpha"
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        VoxaBackgroundShader(state = uiState.assistantState, volume = 0.2f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
                .graphicsLayer {
                    scaleX = confirmAnimScale
                    scaleY = confirmAnimScale
                    alpha = confirmAnimAlpha
                    translationY = (1f - confirmAnimAlpha) * (-200f)
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("READY TO SAVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.weight(0.8f))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Title", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                
                BasicTextField(
                    value = uiState.pendingActionTitle,
                    onValueChange = onUpdateTitle,
                    textStyle = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.Center) {
                            if (uiState.pendingActionTitle.isEmpty()) Text("Tap to name...", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            inner()
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.width(120.dp).height(3.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }

            Spacer(modifier = Modifier.height(48.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InteractionPill(
                        icon = Icons.Default.CalendarToday,
                        text = uiState.pendingActionDate,
                        modifier = Modifier.weight(1f)
                    ) { showDatePicker = true }
                    
                    InteractionPill(
                        icon = Icons.Default.Schedule,
                        text = uiState.pendingActionTime,
                        modifier = Modifier.weight(1f)
                    ) { showTimePicker = true }
                }

                InteractionPill(
                    icon = Icons.Default.NotificationsActive,
                    text = "Alert ${uiState.pendingLeadTime}m before",
                    modifier = Modifier.width(200.dp)
                ) { showLeadTimePicker = true }
            }

            Spacer(modifier = Modifier.weight(1.2f))

            Button(
                onClick = { isConfirmed = true },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Confirm Action", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Custom Glowing Pickers
        if (showDatePicker) {
            VoxaDatePicker(
                initialDate = uiState.pendingActionDate,
                onDateSelected = { 
                    onUpdateDate(it)
                    showDatePicker = false 
                },
                onDismiss = { showDatePicker = false }
            )
        }

        if (showTimePicker) {
            VoxaTimePicker(
                initialTime = uiState.pendingActionTime,
                onTimeSelected = { 
                    onUpdateTime(it)
                    showTimePicker = false 
                },
                onDismiss = { showTimePicker = false }
            )
        }

        if (showLeadTimePicker) {
            VoxaLeadTimePicker(
                currentInterval = uiState.pendingLeadTime,
                onIntervalSelected = {
                    onUpdateLeadTime(it)
                    showLeadTimePicker = false
                },
                onDismiss = { showLeadTimePicker = false }
            )
        }
    }
    
    LaunchedEffect(isConfirmed) {
        if (isConfirmed) {
            delay(400.milliseconds)
            onConfirm()
        }
    }
}

@Composable
fun VoxaDatePicker(initialDate: String, onDateSelected: (String) -> Unit, onDismiss: () -> Unit) {
    var currentMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var currentYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedDate by remember { 
        mutableStateOf<Calendar?>(
            try {
                // Simplified parser for common formats like "25 Oct, 2026"
                Calendar.getInstance().apply {
                    // Logic to parse initialDate if needed, otherwise default to now
                }
            } catch (e: Exception) {
                Calendar.getInstance()
            }
        )
    }

    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, currentYear)
        set(Calendar.MONTH, currentMonth)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    
    val days = (1..daysInMonth).toList()
    val paddingDays = List(firstDayOfWeek) { -1 }
    val allDays = paddingDays + days

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFF121317),
            border = BorderStroke(1.dp, Color(0xFF00FFCC).copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (currentMonth == 0) {
                            currentMonth = 11
                            currentYear--
                        } else {
                            currentMonth--
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Color(0xFF00FFCC))
                    }
                    
                    Text(
                        text = "${calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())} $currentYear",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = {
                        if (currentMonth == 11) {
                            currentMonth = 0
                            currentYear++
                        } else {
                            currentMonth++
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFF00FFCC))
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Weekdays
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                        Text(
                            text = day,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.height(240.dp),
                    userScrollEnabled = false
                ) {
                    items(allDays) { day ->
                        if (day == -1) {
                            Box(modifier = Modifier.aspectRatio(1f))
                        } else {
                            val todayCalendar = Calendar.getInstance()
                            val isToday = todayCalendar.get(Calendar.DAY_OF_MONTH) == day &&
                                    todayCalendar.get(Calendar.MONTH) == currentMonth &&
                                    todayCalendar.get(Calendar.YEAR) == currentYear
                            
                            val isSelected = selectedDate?.let {
                                it.get(Calendar.DAY_OF_MONTH) == day &&
                                it.get(Calendar.MONTH) == currentMonth &&
                                it.get(Calendar.YEAR) == currentYear
                            } ?: false
                            
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color(0xFF00FFCC) else Color.Transparent)
                                    .then(
                                        if (isToday && !isSelected) Modifier.border(
                                            width = 1.dp,
                                            color = Color(0xFF00FFCC).copy(alpha = 0.5f),
                                            shape = CircleShape
                                        ) else Modifier
                                    )
                                    .clickable {
                                        selectedDate = Calendar.getInstance().apply {
                                            set(currentYear, currentMonth, day)
                                        }
                                    }
                                    .then(
                                        if (isSelected) Modifier.shadow(
                                            elevation = 20.dp,
                                            shape = CircleShape,
                                            spotColor = Color(0xFF00FFCC),
                                            ambientColor = Color(0xFF00FFCC)
                                        ) else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day.toString(),
                                    color = when {
                                        isSelected -> Color(0xFF121317)
                                        isToday -> Color(0xFF00FFCC)
                                        else -> Color.White
                                    },
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.7f))
                    ) {
                        Text("CANCEL", style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp)
                    }
                    
                    Button(
                        onClick = {
                            selectedDate?.let {
                                val day = it.get(Calendar.DAY_OF_MONTH)
                                val month = it.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault())
                                val year = it.get(Calendar.YEAR)
                                onDateSelected("$day $month, $year")
                            } ?: onDismiss()
                        },
                        modifier = Modifier.weight(1.2f).height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("CONFIRM", color = Color(0xFF121317), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun VoxaTimePicker(initialTime: String, onTimeSelected: (String) -> Unit, onDismiss: () -> Unit) {
    val initialTimeParts = remember(initialTime) {
        try {
            if (initialTime == "Pending" || initialTime.isEmpty()) {
                val cal = Calendar.getInstance()
                val hour = if (cal.get(Calendar.HOUR_OF_DAY) % 12 == 0) 12 else cal.get(Calendar.HOUR_OF_DAY) % 12
                val amPm = if (cal.get(Calendar.HOUR_OF_DAY) < 12) "AM" else "PM"
                Triple(hour.toString().padStart(2, '0'), cal.get(Calendar.MINUTE).toString().padStart(2, '0'), amPm)
            } else {
                val timeOnly = initialTime.split(" ").first()
                val amPm = if (initialTime.uppercase().contains("PM")) "PM" else "AM"
                val hourMin = timeOnly.split(":")
                Triple(hourMin[0].padStart(2, '0'), if (hourMin.size > 1) hourMin[1].padStart(2, '0') else "00", amPm)
            }
        } catch (e: Exception) {
            val cal = Calendar.getInstance()
            Triple("12", "00", if (cal.get(Calendar.HOUR_OF_DAY) < 12) "AM" else "PM")
        }
    }

    var selectedHour by remember { mutableStateOf(initialTimeParts.first) }
    var selectedMinute by remember { mutableStateOf(initialTimeParts.second) }
    var selectedAmPm by remember { mutableStateOf(initialTimeParts.third) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(24.dp).fillMaxWidth().wrapContentHeight(),
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFF121317),
            border = BorderStroke(1.dp, Color(0xFF00FFCC).copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "SET TIME",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF00FFCC),
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Box(contentAlignment = Alignment.Center) {
                    // Glassmorphic selection highlight
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .padding(horizontal = 8.dp)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().height(250.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        WheelPicker(
                            items = (1..12).map { it.toString().padStart(2, '0') },
                            initialSelection = initialTimeParts.first,
                            onItemSelected = { selectedHour = it },
                            modifier = Modifier.weight(1f)
                        )
                        Text(":", style = MaterialTheme.typography.headlineMedium, color = Color.White.copy(alpha = 0.5f))
                        WheelPicker(
                            items = (0..59).map { it.toString().padStart(2, '0') },
                            initialSelection = initialTimeParts.second,
                            onItemSelected = { selectedMinute = it },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        WheelPicker(
                            items = listOf("AM", "PM"),
                            initialSelection = initialTimeParts.third,
                            onItemSelected = { selectedAmPm = it },
                            modifier = Modifier.weight(1f),
                            isInfinite = false
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.7f))
                    ) {
                        Text("CANCEL", style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp)
                    }
                    
                    Button(
                        onClick = {
                            onTimeSelected("$selectedHour:$selectedMinute $selectedAmPm")
                        },
                        modifier = Modifier.weight(1.2f).height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("CONFIRM", color = Color(0xFF121317), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun VoxaLeadTimePicker(
    currentInterval: Int,
    onIntervalSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val intervals = listOf(5, 10, 15, 20, 30)
    var selectedTempInterval by remember { mutableStateOf(currentInterval) }
    
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(24.dp).fillMaxWidth().wrapContentHeight(),
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFF121317),
            border = BorderStroke(1.dp, Color(0xFF00FFCC).copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "LEAD ALERT",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF00FFCC),
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    intervals.forEach { interval ->
                        val isSelected = selectedTempInterval == interval
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color(0xFF00FFCC) else Color.Transparent)
                                .clickable { selectedTempInterval = interval },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${interval}m",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) Color(0xFF121317) else Color.White.copy(alpha = 0.6f),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = { onIntervalSelected(selectedTempInterval) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("DONE", color = Color(0xFF121317), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }
}


@Composable
fun WheelPicker(
    items: List<String>,
    initialSelection: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    isInfinite: Boolean = true
) {
    val initialIndex = remember(initialSelection) {
        val baseIndex = items.indexOf(initialSelection).coerceAtLeast(0)
        if (isInfinite) (items.size * 50) + baseIndex else baseIndex
    }
    
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    
    val currentItem by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val center = layoutInfo.viewportStartOffset + (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2
            layoutInfo.visibleItemsInfo.minByOrNull { item -> 
                abs((item.offset + item.size / 2) - center) 
            }
        }
    }

    LaunchedEffect(currentItem) {
        currentItem?.let { info ->
            onItemSelected(items[info.index % items.size])
        }
    }

    Box(modifier = modifier.height(250.dp), contentAlignment = Alignment.Center) {
        LazyColumn(
            state = listState,
            flingBehavior = snapFlingBehavior,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 107.dp)
        ) {
            val itemCount = if (isInfinite) items.size * 100 else items.size
            items(itemCount) { index ->
                val item = items[index % items.size]
                val itemInfo = currentItem
                val isSelected = itemInfo?.index == index
                
                val scale by animateFloatAsState(if (isSelected) 1.2f else 0.8f)
                val alpha by animateFloatAsState(if (isSelected) 1f else 0.3f)

                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item,
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (isSelected) Color(0xFF00FFCC) else Color.White,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}



@Composable
fun InteractionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
        label = "pill_scale"
    )

    Surface(
        color = Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .height(56.dp)
            .scale(scale)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
fun InteractionConfirmScreenPreview() {
    VoxaTheme {
        InteractionConfirmScreenContent(
            uiState = VoxaUiState(pendingActionTitle = "Design Sync with Asif"), 
            onConfirm = {},
            onUpdateTitle = {},
            onUpdateDate = {},
            onUpdateTime = {},
            onUpdateLeadTime = {}
        )
    }
}
