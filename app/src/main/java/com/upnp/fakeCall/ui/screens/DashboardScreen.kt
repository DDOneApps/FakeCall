package com.upnp.fakeCall.ui.screens

import android.text.format.DateFormat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upnp.fakeCall.CustomPreset
import com.upnp.fakeCall.FakeCallUiState
import com.upnp.fakeCall.FakeCallViewModel
import com.upnp.fakeCall.R
import com.upnp.fakeCall.ScheduleKind
import com.upnp.fakeCall.ui.components.AnimatedIcon
import com.upnp.fakeCall.ui.components.AudioPreviewCard
import com.upnp.fakeCall.ui.components.CallerInputCard
import com.upnp.fakeCall.ui.components.ExpressiveCardShape
import com.upnp.fakeCall.ui.components.ExpressiveButton
import com.upnp.fakeCall.ui.components.expressiveSpring
import com.upnp.fakeCall.ui.components.TimingSelectionCard
import com.upnp.fakeCall.ui.components.bounceClick
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: FakeCallViewModel,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var showCustomSheet by rememberSaveable { mutableStateOf(false) }
    var sheetMode by rememberSaveable {
        mutableStateOf(
            if (state.scheduleKind == ScheduleKind.CUSTOM_EXACT) {
                ScheduleKind.CUSTOM_EXACT
            } else {
                ScheduleKind.CUSTOM_COUNTDOWN
            }
        )
    }

    val blurRadius by animateFloatAsState(
        targetValue = if (showCustomSheet) 16f else 0f,
        animationSpec = expressiveSpring(),
        label = "backgroundBlur"
    )

    val backgroundScale by animateFloatAsState(
        targetValue = if (showCustomSheet) 0.98f else 1f,
        animationSpec = expressiveSpring(),
        label = "backgroundScale"
    )

    val canTrigger = state.hasRequiredPermissions && state.isProviderEnabled
    val actionLabel = if (state.isTimerRunning) stringResource(R.string.action_cancel_call) else stringResource(R.string.action_schedule_call)
    val is24Hour = DateFormat.is24HourFormat(context)

    val actionContainerColor by animateColorAsState(
        targetValue = if (state.isTimerRunning) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "actionContainer"
    )

    val actionContentColor by animateColorAsState(
        targetValue = if (state.isTimerRunning) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimary
        },
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "actionContent"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = backgroundScale
                    scaleY = backgroundScale
                }
                .blur(blurRadius.dp)
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.app_title),
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.app_subtitle),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AnimatedIcon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.cd_open_settings),
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tint = MaterialTheme.colorScheme.onSurface,
                            onClick = onOpenSettings
                        )
                    }
                },
                bottomBar = {
                    BottomActionBar(
                        enabled = canTrigger || state.isTimerRunning,
                        label = actionLabel,
                        containerColor = actionContainerColor,
                        contentColor = actionContentColor,
                        isRinging = state.isTimerRunning,
                        onClick = {
                            if (canTrigger || state.isTimerRunning) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.onTriggerOrCancelClicked()
                            }
                        }
                    )
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = 8.dp,
                        bottom = 120.dp
                    )
                ) {
                    item {
                        ScheduleStateCard(
                            isTimerRunning = state.isTimerRunning,
                            scheduleLabel = scheduleDisplay(context, state, is24Hour),
                            scheduleSubtitle = scheduleSubtitle(state),
                            runningLabel = runningScheduleLabel(state.timerEndsAtMillis)
                        )
                    }

                    item {
                        AnimatedVisibility(
                            visible = state.isTimerRunning,
                            enter = expandVertically(animationSpec = expressiveSpring()) + fadeIn(animationSpec = expressiveSpring()),
                            exit = shrinkVertically(animationSpec = expressiveSpring()) + fadeOut(animationSpec = expressiveSpring())
                        ) {
                            ScheduledBanner(
                                runningLabel = runningScheduleLabel(state.timerEndsAtMillis)
                            )
                        }
                    }

                    item {
                        CallerInputCard(
                            callerName = state.callerName,
                            callerNumber = state.callerNumber,
                            onCallerNameChange = viewModel::onCallerNameChange,
                            onCallerNumberChange = viewModel::onCallerNumberChange
                        )
                    }

                    item {
                        val manualLabel = when (state.scheduleKind) {
                            ScheduleKind.PRESET -> stringResource(R.string.schedule_kind_set_custom_time)
                            else -> scheduleDisplay(context, state, is24Hour)
                        }
                        val manualHelper = when (state.scheduleKind) {
                            ScheduleKind.CUSTOM_EXACT -> stringResource(R.string.schedule_kind_exact_time)
                            ScheduleKind.CUSTOM_COUNTDOWN -> stringResource(R.string.schedule_kind_countdown_timer)
                            ScheduleKind.PRESET -> stringResource(R.string.schedule_kind_manual_override)
                        }
                        TimingSelectionCard(
                            scheduleTitle = scheduleDisplay(context, state, is24Hour),
                            scheduleSubtitle = scheduleSubtitle(state),
                            selectedDelaySeconds = if (state.scheduleKind == ScheduleKind.PRESET) {
                                state.selectedDelaySeconds
                            } else {
                                -1
                            },
                            presetOptions = listOf(0, 10, 30, 60, 120, 300),
                            onPresetSelected = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.onDelaySelected(it)
                            },
                            manualTimeLabel = manualLabel,
                            manualTimeHelper = manualHelper,
                            onOpenCustom = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                sheetMode = if (state.scheduleKind == ScheduleKind.CUSTOM_EXACT) {
                                    ScheduleKind.CUSTOM_EXACT
                                } else {
                                    ScheduleKind.CUSTOM_COUNTDOWN
                                }
                                showCustomSheet = true
                            },
                            customPresets = state.customPresets,
                            onCustomPresetSelected = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.onCustomPresetSelected(it)
                            },
                            onRemovePreset = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.removeCustomPreset(it)
                            },
                            formatPreset = { formatCustomPreset(context, it, is24Hour) }
                        )
                    }

                    item {
                        if (!viewModel.canScheduleExactAlarms()) {
                            Surface(
                                tonalElevation = 1.dp,
                                shape = RoundedCornerShape(32.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.error_exact_alarms_off_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = stringResource(R.string.error_exact_alarms_off_subtitle),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    ExpressiveButton(
                                        label = stringResource(R.string.permission_alarms_action),
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val intent = viewModel.openExactAlarmSettingsIntent()
                                            runCatching { context.startActivity(intent) }
                                        },
                                        containerColor = MaterialTheme.colorScheme.onErrorContainer,
                                        contentColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                }
                            }
                        }
                    }

                    item {
                        AudioPreviewCard(
                            audioLabel = state.selectedAudioName.ifBlank { stringResource(R.string.default_audio_name) },
                            audioUri = state.selectedAudioUri,
                            onOpenSettings = onOpenSettings,
                            onClearAudio = viewModel::clearAudioSelection
                        )
                    }

                    item {
                        if (state.statusMessage.isNotBlank()) {
                            Surface(
                                tonalElevation = 1.dp,
                                shape = RoundedCornerShape(32.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Text(
                                    text = state.statusMessage,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    item {
                        if (!state.hasRequiredPermissions || !state.isProviderEnabled) {
                            Surface(
                                tonalElevation = 1.dp,
                                shape = RoundedCornerShape(32.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.error_grant_permissions),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        CustomCallSheet(
            visible = showCustomSheet,
            scheduleKind = sheetMode,
            countdownMinutes = state.customCountdownMinutes,
            countdownSeconds = state.customCountdownSeconds,
            exactHour = state.customExactHour,
            exactMinute = state.customExactMinute,
            onScheduleKindChange = { sheetMode = it },
            onCountdownChange = viewModel::onCustomCountdownChange,
            onExactTimeChange = viewModel::onCustomExactTimeChange,
            onSavePreset = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.addCustomPreset(sheetMode)
            },
            onApply = {
                viewModel.onScheduleKindSelected(sheetMode)
                showCustomSheet = false
            },
            onDismiss = { showCustomSheet = false }
        )
    }
}

@Composable
private fun ScheduleStateCard(
    isTimerRunning: Boolean,
    scheduleLabel: String,
    scheduleSubtitle: String,
    runningLabel: String
) {
    AnimatedContent(
        targetState = isTimerRunning,
        transitionSpec = {
            fadeIn(animationSpec = expressiveSpring()) togetherWith fadeOut(animationSpec = expressiveSpring())
        },
        label = "scheduleState"
    ) { running ->
        Surface(
            shape = ExpressiveCardShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedIcon(
                    imageVector = if (running) Icons.Rounded.PhoneInTalk else Icons.Outlined.AccessTime,
                    contentDescription = null,
                    shape = CircleShape,
                    backgroundColor = if (running) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    tint = if (running) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    isRinging = running,
                    isActive = running
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (running) stringResource(R.string.schedule_state_running) else stringResource(R.string.schedule_state_ready),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (running) runningLabel else scheduleLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = scheduleSubtitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduledBanner(runningLabel: String) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedIcon(
                imageVector = Icons.Rounded.Phone,
                contentDescription = null,
                shape = CircleShape,
                backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                tint = MaterialTheme.colorScheme.primary,
                isRinging = true,
                isActive = true
            )
            Column {
                Text(
                    text = stringResource(R.string.schedule_state_running),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = runningLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    enabled: Boolean,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    isRinging: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .bounceClick(enabled = enabled, onClick = onClick),
            color = containerColor.copy(alpha = if (enabled) 0.96f else 0.6f),
            contentColor = contentColor,
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedIcon(
                    imageVector = Icons.Rounded.Phone,
                    contentDescription = null,
                    shape = CircleShape,
                    backgroundColor = contentColor.copy(alpha = 0.18f),
                    tint = contentColor,
                    isRinging = isRinging,
                    isActive = isRinging
                )
                AnimatedContent(
                    targetState = label,
                    transitionSpec = {
                        fadeIn(animationSpec = expressiveSpring()) togetherWith fadeOut(animationSpec = expressiveSpring())
                    },
                    label = "actionLabel"
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomCallSheet(
    visible: Boolean,
    scheduleKind: ScheduleKind,
    countdownMinutes: Int,
    countdownSeconds: Int,
    exactHour: Int,
    exactMinute: Int,
    onScheduleKindChange: (ScheduleKind) -> Unit,
    onCountdownChange: (Int, Int) -> Unit,
    onExactTimeChange: (Int, Int) -> Unit,
    onSavePreset: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    var backProgress by remember { mutableFloatStateOf(0f) }

    PredictiveBackHandler(enabled = visible) { progress ->
        try {
            progress.collect { event ->
                backProgress = event.progress
            }
            onDismiss()
            backProgress = 0f
        } catch (_: CancellationException) {
            backProgress = 0f
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = expressiveSpring(),
        label = "sheetProgress"
    )

    if (visible || progress > 0f) {
        val scrimAlpha = lerp(0f, 0.35f, progress)
        val cornerRadius = androidx.compose.ui.unit.lerp(40.dp, 22.dp, progress)
        val offsetY = androidx.compose.ui.unit.lerp(90.dp, 0.dp, progress)
        val scale = lerp(0.95f, 1f, progress) * lerp(1f, 0.92f, backProgress)
        val alpha = lerp(0.7f, 1f, progress) * lerp(1f, 0.6f, backProgress)

        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = expressiveSpring()),
                exit = fadeOut(animationSpec = expressiveSpring())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
                        .padding(bottom = 1.dp)
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .navigationBarsPadding()
                    .offset(y = offsetY)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    },
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shape = RoundedCornerShape(cornerRadius)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AnimatedIcon(
                            imageVector = Icons.Outlined.AccessTime,
                            contentDescription = null,
                            shape = CircleShape,
                            backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = stringResource(R.string.custom_call_sheet_title),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        androidx.compose.material3.FilterChip(
                            selected = scheduleKind == ScheduleKind.CUSTOM_COUNTDOWN,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onScheduleKindChange(ScheduleKind.CUSTOM_COUNTDOWN)
                            },
                            label = { Text(stringResource(R.string.filter_countdown)) },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.bounceClick()
                        )
                        androidx.compose.material3.FilterChip(
                            selected = scheduleKind == ScheduleKind.CUSTOM_EXACT,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onScheduleKindChange(ScheduleKind.CUSTOM_EXACT)
                            },
                            label = { Text(stringResource(R.string.filter_exact_time)) },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.bounceClick()
                        )
                    }

                    if (scheduleKind == ScheduleKind.CUSTOM_COUNTDOWN) {
                        CountdownPicker(
                            minutes = countdownMinutes,
                            seconds = countdownSeconds,
                            onChange = onCountdownChange
                        )
                    } else {
                        val is24Hour = DateFormat.is24HourFormat(context)
                        ExactTimePicker(
                            hour = exactHour,
                            minute = exactMinute,
                            is24Hour = is24Hour,
                            onChange = onExactTimeChange
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onSavePreset,
                            modifier = Modifier.weight(1f).bounceClick()
                        ) {
                            Text(stringResource(R.string.action_save_as_preset))
                        }
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).bounceClick()
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onApply()
                            },
                            modifier = Modifier.weight(1f).bounceClick()
                        ) {
                            Text(stringResource(R.string.action_use_this_time))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownPicker(
    minutes: Int,
    seconds: Int,
    onChange: (Int, Int) -> Unit
) {
    val currentMinutes by rememberUpdatedState(minutes)
    val currentSeconds by rememberUpdatedState(seconds)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WheelPicker(
            label = stringResource(R.string.wheel_picker_minutes),
            value = minutes,
            range = 0..59,
            onValueChange = {
                onChange(it, currentSeconds)
            }
        )
        Text(
            text = ":",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        WheelPicker(
            label = stringResource(R.string.wheel_picker_seconds),
            value = seconds,
            range = 0..59,
            onValueChange = {
                onChange(currentMinutes, it)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExactTimePicker(
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    onChange: (Int, Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = { showDialog = true }),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedIcon(
                imageVector = Icons.Outlined.AccessTime,
                contentDescription = null,
                shape = CircleShape,
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatExactTime(hour, minute, is24Hour),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.exact_time_picker_tap_hint),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = is24Hour
        )
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(
                shape = RoundedCornerShape(36.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.exact_time_picker_dialog_title),
                        style = MaterialTheme.typography.displaySmall
                    )
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(onClick = { showDialog = false }, modifier = Modifier.bounceClick()) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        Button(onClick = {
                            onChange(timePickerState.hour, timePickerState.minute)
                            showDialog = false
                        }, modifier = Modifier.bounceClick()) {
                            Text(stringResource(R.string.action_apply))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WheelPicker(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    val itemHeight = 40.dp
    val listState = rememberPickerState(value - range.first)
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(value) {
        val target = (value - range.first).coerceIn(0, range.last - range.first)
        if ((listState.state.firstVisibleItemIndex - target).absoluteValue > 1) {
            listState.state.scrollToItem(target)
        }
    }

    LaunchedEffect(listState, value) {
        snapshotFlow {
            val offset = listState.state.firstVisibleItemScrollOffset
            val index = listState.state.firstVisibleItemIndex +
                if (offset > listState.itemHeightPx / 2) 1 else 0
            index.coerceIn(0, range.last - range.first)
        }.collect { index ->
            val selected = range.first + index
            if (selected != value) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onValueChange(selected)
            }
        }
    }

    Box(
        modifier = Modifier
            .width(80.dp)
            .height(itemHeight * 3)
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState.state,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = itemHeight),
            flingBehavior = listState.flingBehavior
        ) {
            items(range.count()) { index ->
                val displayed = range.first + index
                val isSelected = displayed == value
                Text(
                    text = displayed.toString().padStart(2, '0'),
                    style = if (isSelected) {
                        MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .height(itemHeight)
                        .padding(vertical = 4.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .height(itemHeight)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

private data class PickerState(
    val state: androidx.compose.foundation.lazy.LazyListState,
    val flingBehavior: androidx.compose.foundation.gestures.FlingBehavior,
    val itemHeightPx: Int
)

@Composable
private fun rememberPickerState(initialIndex: Int): PickerState {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val itemHeight = 40.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.roundToPx() }
    val flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(listState)
    return remember(listState, flingBehavior, itemHeightPx) {
        PickerState(listState, flingBehavior, itemHeightPx)
    }
}

private fun scheduleDisplay(context: android.content.Context, state: FakeCallUiState, is24Hour: Boolean): String {
    return when (state.scheduleKind) {
        ScheduleKind.CUSTOM_EXACT -> formatExactTime(state.customExactHour, state.customExactMinute, is24Hour)
        ScheduleKind.CUSTOM_COUNTDOWN -> FakeCallViewModel.formatDelay(
            context,
            state.customCountdownMinutes * 60 + state.customCountdownSeconds
        )
        ScheduleKind.PRESET -> FakeCallViewModel.formatDelay(context, state.selectedDelaySeconds)
    }
}

@Composable
private fun scheduleSubtitle(state: FakeCallUiState): String {
    return when (state.scheduleKind) {
        ScheduleKind.CUSTOM_EXACT -> stringResource(R.string.schedule_kind_exact_time)
        ScheduleKind.CUSTOM_COUNTDOWN -> stringResource(R.string.schedule_kind_countdown_timer)
        ScheduleKind.PRESET -> stringResource(R.string.schedule_kind_quick_preset)
    }
}

private fun formatExactTime(hour: Int, minute: Int, is24Hour: Boolean): String {
    val locale = Locale.getDefault()
    val formatter = if (is24Hour) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "Hm")
        DateTimeFormatter.ofPattern(pattern, locale)
    } else {
        val pattern = DateFormat.getBestDateTimePattern(locale, "hm")
        DateTimeFormatter.ofPattern(pattern, locale)
    }
    val time = java.time.LocalTime.of(hour, minute)
    return time.format(formatter)
}

private fun runningScheduleLabel(triggerAtMillis: Long): String {
    if (triggerAtMillis <= 0L) return ""
    val time = Instant.ofEpochMilli(triggerAtMillis)
        .atZone(ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
    return time.format(formatter)
}

private fun formatCustomPreset(
    context: android.content.Context,
    preset: CustomPreset,
    is24Hour: Boolean
): String {
    return when (preset.kind) {
        ScheduleKind.CUSTOM_COUNTDOWN -> FakeCallViewModel.formatDelay(
            context,
            preset.minutes * 60 + preset.seconds
        )
        ScheduleKind.CUSTOM_EXACT -> formatExactTime(preset.hour, preset.minute, is24Hour)
        else -> ""
    }
}
