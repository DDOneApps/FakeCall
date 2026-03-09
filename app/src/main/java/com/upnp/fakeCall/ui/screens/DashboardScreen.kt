package com.upnp.fakeCall.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upnp.fakeCall.FakeCallViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: FakeCallViewModel,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showTimePicker by remember { mutableStateOf(false) }

    val now = Calendar.getInstance()
    val timePickerState = rememberTimePickerState(
        initialHour = now.get(Calendar.HOUR_OF_DAY),
        initialMinute = now.get(Calendar.MINUTE)
    )

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("FakeCall") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Open settings")
                    }
                }
            )
        },
        floatingActionButton = {
            val hasActiveSchedule = state.isTimerRunning || state.exactScheduledAtMillis > 0L
            val canTrigger = state.hasRequiredPermissions && state.isProviderEnabled
            val fabText = if (hasActiveSchedule) "Cancel Scheduled Call" else "Trigger Call"

            ExtendedFloatingActionButton(
                onClick = {
                    if (canTrigger || hasActiveSchedule) {
                        viewModel.onTriggerOrCancelClicked()
                    }
                },
                shape = RoundedCornerShape(32.dp),
                containerColor = if (canTrigger || hasActiveSchedule) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (canTrigger || hasActiveSchedule) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            ) {
                Text(fabText)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.callerName,
                onValueChange = viewModel::onCallerNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Target Caller Name") },
                singleLine = true
            )

            OutlinedTextField(
                value = state.callerNumber,
                onValueChange = viewModel::onCallerNumberChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Target Caller Number") },
                singleLine = true
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Call me in: ${FakeCallViewModel.formatDelay(state.selectedDelaySeconds)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        viewModel.delayOptionsSeconds.forEach { option ->
                            FilterChip(
                                selected = option == state.selectedDelaySeconds,
                                onClick = { viewModel.onDelaySelected(option) },
                                label = { Text(FakeCallViewModel.formatDelay(option)) },
                                shape = RoundedCornerShape(999.dp)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Schedule Exact Time")
                    }

                    if (state.exactScheduledAtMillis > 0L) {
                        Text(
                            text = "Call scheduled for ${FakeCallViewModel.formatExactTime(state.exactScheduledAtMillis)}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedButton(onClick = viewModel::cancelExactSchedule) {
                            Text("Cancel Exact Schedule")
                        }
                    }
                }
            }

            if (state.isTimerRunning) {
                Text(
                    text = "Countdown active. Tap Cancel Scheduled Call to stop it.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            if (state.statusMessage.isNotBlank()) {
                Text(
                    text = state.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!state.hasRequiredPermissions || !state.isProviderEnabled) {
                Text(
                    text = "Go to Settings to grant permissions and enable provider.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.width(1.dp))
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Pick exact call time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
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
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
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
