package com.upnp.fakeCall.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upnp.fakeCall.FakeCallViewModel
import com.upnp.fakeCall.ui.components.AnimatedIcon
import com.upnp.fakeCall.ui.components.CallerInputCard
import com.upnp.fakeCall.ui.components.ExpressiveButton
import com.upnp.fakeCall.ui.components.TimingSelectionCard
import com.upnp.fakeCall.ui.components.bounceClick
import java.util.Calendar

private val BouncySpec = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: FakeCallViewModel,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showTimePicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val now = Calendar.getInstance()
    val timePickerState = rememberTimePickerState(
        initialHour = now.get(Calendar.HOUR_OF_DAY),
        initialMinute = now.get(Calendar.MINUTE)
    )

    val hasActiveSchedule = state.isTimerRunning || state.exactScheduledAtMillis > 0L
    val canTrigger = state.hasRequiredPermissions && state.isProviderEnabled

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 0.dp
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = "FakeCall",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    AnimatedIcon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Open settings",
                        modifier = Modifier.bounceClick(onOpenSettings)
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
                tonalElevation = 8.dp
            ) {
                AnimatedContent(
                    targetState = hasActiveSchedule,
                    label = "schedule_button_content"
                ) { active ->
                    ExpressiveButton(
                        text = if (active) "Cancel Scheduled Call" else "Schedule Call",
                        icon = Icons.Rounded.Phone,
                        onClick = {
                            if (canTrigger || active) {
                                viewModel.onTriggerOrCancelClicked()
                            }
                        },
                        enabled = canTrigger || active,
                        errorState = active,
                        animateIcon = active,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CallerInputCard(
                    callerName = state.callerName,
                    callerNumber = state.callerNumber,
                    onNameChanged = viewModel::onCallerNameChange,
                    onNumberChanged = viewModel::onCallerNumberChange
                )
            }

            item {
                TimingSelectionCard(
                    selectedDelay = state.selectedDelaySeconds,
                    onDelaySelected = viewModel::onDelaySelected,
                    onExactTimeClick = { showTimePicker = true },
                    scheduledExactLabel = if (state.exactScheduledAtMillis > 0L) {
                        "Call scheduled for ${FakeCallViewModel.formatExactTime(state.exactScheduledAtMillis)}"
                    } else {
                        ""
                    },
                    onCancelExact = viewModel::cancelExactSchedule
                )
            }

            item {
                AnimatedVisibility(
                    visible = hasActiveSchedule,
                    enter = fadeIn(animationSpec = BouncySpec) +
                        expandVertically(animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )),
                    exit = fadeOut(animationSpec = BouncySpec)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        tonalElevation = 4.dp
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            AnimatedIcon(
                                imageVector = Icons.Rounded.AccessTime,
                                contentDescription = "Scheduled",
                                animateRinging = true
                            )
                            Text(
                                text = if (state.exactScheduledAtMillis > 0L) {
                                    "Exact call at ${FakeCallViewModel.formatExactTime(state.exactScheduledAtMillis)}"
                                } else {
                                    "Relative countdown active"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            item {
                if (state.statusMessage.isNotBlank()) {
                    Text(
                        text = state.statusMessage,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showTimePicker) {
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(36.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Schedule Exact Time",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TimePicker(state = timePickerState)
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        androidx.compose.material3.TextButton(onClick = { showTimePicker = false }) {
                            Text("Cancel")
                        }
                        androidx.compose.material3.TextButton(onClick = {
                            val scheduled = viewModel.scheduleExactCall(
                                hourOfDay = timePickerState.hour,
                                minute = timePickerState.minute
                            )
                            if (!scheduled && viewModel.needsExactAlarmPermission()) {
                                openExactAlarmSettings(context, viewModel)
                            }
                            showTimePicker = false
                        }) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }
}

private fun openExactAlarmSettings(context: Context, viewModel: FakeCallViewModel) {
    val intent = viewModel.openExactAlarmSettingsIntent()
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        if (it is ActivityNotFoundException) {
            // Ignore silently on unsupported devices.
        }
    }
}

