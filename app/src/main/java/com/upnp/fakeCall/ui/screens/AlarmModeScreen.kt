package com.upnp.fakeCall.ui.screens

import android.content.Intent
import android.provider.MediaStore
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upnp.fakeCall.AlarmMessageMode
import com.upnp.fakeCall.AlarmModeDraft
import com.upnp.fakeCall.AlarmModeItem
import com.upnp.fakeCall.AlarmSpeakerDefault
import com.upnp.fakeCall.AlarmModeScheduler
import com.upnp.fakeCall.FakeCallViewModel
import com.upnp.fakeCall.R
import com.upnp.fakeCall.ui.components.AnimatedIcon
import com.upnp.fakeCall.ui.components.ExpressiveButton
import com.upnp.fakeCall.ui.components.ExpressiveTextField
import com.upnp.fakeCall.ui.components.SectionCard
import com.upnp.fakeCall.ui.components.bounceClick
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun AlarmOverviewScreen(
    viewModel: FakeCallViewModel,
    onOpenSettings: () -> Unit,
    onOpenCreateAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    var alarmPendingDelete by remember { mutableStateOf<AlarmModeItem?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.alarm_mode_title),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.alarm_mode_subtitle),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AnimatedIcon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.cd_open_settings),
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = onOpenSettings
                    )
                    AnimatedIcon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.alarm_add_new),
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = onOpenCreateAlarm
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 108.dp, top = 10.dp)
        ) {
            if (state.alarmModeItems.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AnimatedIcon(
                                imageVector = Icons.Outlined.Alarm,
                                contentDescription = null,
                                shape = CircleShape,
                                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                size = 60.dp
                            )
                            Text(
                                text = stringResource(R.string.alarm_empty_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.alarm_empty_subtitle),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            ExpressiveButton(
                                label = stringResource(R.string.alarm_add_new),
                                onClick = onOpenCreateAlarm,
                                leadingIcon = Icons.Outlined.Add
                            )
                        }
                    }
                }
            } else {
                items(state.alarmModeItems, key = { it.id }) { alarm ->
                    AlarmOverviewItem(
                        alarm = alarm,
                        is24Hour = is24Hour,
                        onEnabledChange = { enabled -> viewModel.onAlarmModeEnabledChanged(alarm.id, enabled) },
                        onEdit = { onEditAlarm(alarm.id) },
                        onDelete = { alarmPendingDelete = alarm }
                    )
                }
            }
        }
    }

    val toDelete = alarmPendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { alarmPendingDelete = null },
            title = { Text(stringResource(R.string.alarm_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.alarm_delete_message,
                        formatAlarmTime(toDelete.hour, toDelete.minute, is24Hour)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAlarmMode(toDelete.id)
                        alarmPendingDelete = null
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { alarmPendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun AlarmOverviewItem(
    alarm: AlarmModeItem,
    is24Hour: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val alpha by animateFloatAsState(
        targetValue = if (alarm.enabled) 1f else 0.62f,
        animationSpec = com.upnp.fakeCall.ui.components.expressiveSpring(),
        label = "alarmCardAlpha"
    )
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedIcon(
                imageVector = Icons.Outlined.Alarm,
                contentDescription = null,
                shape = CircleShape,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatAlarmTime(alarm.hour, alarm.minute, is24Hour),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                Text(
                    text = listOf(alarm.callerName, alarm.callerNumber).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
                Text(
                    text = alarmRepeatSummary(context, alarm),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
                AnimatedVisibility(visible = alarm.nextTriggerAtMillis > 0L && alarm.enabled) {
                    Text(
                        text = formatNextTrigger(alarm.nextTriggerAtMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = onEnabledChange
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.bounceClick()) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.cd_edit_alarm),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.bounceClick()) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.cd_delete_alarm),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AlarmCreateScreen(
    viewModel: FakeCallViewModel,
    onBack: () -> Unit,
    editAlarmId: Long? = null,
    modeNavigationBar: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val isEditMode = editAlarmId != null
    val initialDraft = remember(editAlarmId) {
        if (editAlarmId != null) {
            viewModel.draftForAlarm(editAlarmId) ?: viewModel.newAlarmModeDraft()
        } else {
            viewModel.newAlarmModeDraft()
        }
    }
    var draft by remember(editAlarmId) { mutableStateOf(initialDraft) }
    var showTimePicker by remember { mutableStateOf(false) }
    val canSaveAlarm = draft.callerNumber.trim().isNotBlank()

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            draft = draft.copy(
                customAudioUri = uri.toString(),
                customAudioName = resolveAudioLabel(context, uri)
            )
        }
    }

    val recorderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        draft = draft.copy(
            customAudioUri = uri.toString(),
            customAudioName = resolveAudioLabel(context, uri)
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isEditMode) {
                            stringResource(R.string.alarm_edit_title)
                        } else {
                            stringResource(R.string.alarm_create_title)
                        },
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isEditMode) {
                            stringResource(R.string.alarm_edit_subtitle)
                        } else {
                            stringResource(R.string.alarm_create_subtitle)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedIcon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        },
        bottomBar = {
            Column {
                Surface(
                    tonalElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)
                ) {
                    ExpressiveButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                        label = stringResource(R.string.action_save_call),
                        leadingIcon = Icons.Outlined.Call,
                        enabled = canSaveAlarm,
                        onClick = {
                            val saved = if (editAlarmId != null) {
                                viewModel.updateAlarmMode(editAlarmId, draft)
                            } else {
                                viewModel.saveAlarmMode(draft)
                            }
                            if (saved) onBack()
                        }
                    )
                }
                modeNavigationBar?.invoke()
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp, top = 10.dp)
        ) {
            item {
                SectionCard(title = stringResource(R.string.alarm_section_caller)) {
                    ExpressiveTextField(
                        value = draft.callerName,
                        onValueChange = { draft = draft.copy(callerName = it) },
                        label = stringResource(R.string.label_caller_name)
                    )
                    ExpressiveTextField(
                        value = draft.callerNumber,
                        onValueChange = { draft = draft.copy(callerNumber = it) },
                        label = stringResource(R.string.label_caller_number)
                    )
                    if (!canSaveAlarm) {
                        Text(
                            text = stringResource(R.string.status_enter_caller_number_scheduling),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.alarm_section_schedule)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick(onClick = { showTimePicker = true }),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AnimatedIcon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = null,
                                shape = CircleShape,
                                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Column {
                                Text(
                                    text = formatAlarmTime(draft.hour, draft.minute, DateFormat.is24HourFormat(context)),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.alarm_exact_time_only),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    WeekdaySelector(
                        selectedDays = draft.repeatDays,
                        onToggle = { day ->
                            draft = draft.copy(
                                repeatDays = if (draft.repeatDays.contains(day)) {
                                    draft.repeatDays - day
                                } else {
                                    draft.repeatDays + day
                                }
                            )
                        }
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.alarm_section_message)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = draft.messageMode == AlarmMessageMode.APP_VOICE_TTS,
                            onClick = { draft = draft.copy(messageMode = AlarmMessageMode.APP_VOICE_TTS) },
                            label = { Text(stringResource(R.string.alarm_message_mode_tts)) }
                        )
                        FilterChip(
                            selected = draft.messageMode == AlarmMessageMode.CUSTOM_AUDIO,
                            onClick = { draft = draft.copy(messageMode = AlarmMessageMode.CUSTOM_AUDIO) },
                            label = { Text(stringResource(R.string.alarm_message_mode_custom)) }
                        )
                    }
                    if (draft.messageMode == AlarmMessageMode.APP_VOICE_TTS) {
                        ExpressiveTextField(
                            value = draft.ttsMessage,
                            onValueChange = { draft = draft.copy(ttsMessage = it) },
                            label = stringResource(R.string.alarm_tts_message_label)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.alarm_tts_repeat_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(R.string.alarm_tts_repeat_subtitle),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = draft.repeatTtsMessage,
                                onCheckedChange = { draft = draft.copy(repeatTtsMessage = it) }
                            )
                        }
                    } else {
                        Text(
                            text = if (draft.customAudioName.isBlank()) {
                                stringResource(R.string.alarm_custom_audio_none)
                            } else {
                                draft.customAudioName
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ExpressiveButton(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.alarm_select_audio),
                                leadingIcon = Icons.Outlined.AudioFile,
                                onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) }
                            )
                            ExpressiveButton(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.alarm_record_audio),
                                leadingIcon = Icons.Outlined.Mic,
                                onClick = {
                                    val intent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
                                    recorderLauncher.launch(intent)
                                }
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.alarm_section_snooze)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.alarm_snooze_enable),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.alarm_snooze_hint),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = draft.snoozeEnabled,
                            onCheckedChange = { draft = draft.copy(snoozeEnabled = it) }
                        )
                    }
                    AnimatedVisibility(visible = draft.snoozeEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(3, 5, 10).forEach { minutes ->
                                FilterChip(
                                    selected = draft.snoozeMinutes == minutes,
                                    onClick = { draft = draft.copy(snoozeMinutes = minutes) },
                                    label = { Text(stringResource(R.string.alarm_snooze_minutes, minutes)) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.alarm_section_speaker)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = draft.speakerDefault == AlarmSpeakerDefault.EARPIECE,
                            onClick = { draft = draft.copy(speakerDefault = AlarmSpeakerDefault.EARPIECE) },
                            label = { Text(stringResource(R.string.alarm_speaker_earpiece)) },
                            leadingIcon = { Icon(Icons.Outlined.Call, contentDescription = null) }
                        )
                        FilterChip(
                            selected = draft.speakerDefault == AlarmSpeakerDefault.SPEAKER,
                            onClick = { draft = draft.copy(speakerDefault = AlarmSpeakerDefault.SPEAKER) },
                            label = { Text(stringResource(R.string.alarm_speaker_speaker)) },
                            leadingIcon = { Icon(Icons.Outlined.VolumeUp, contentDescription = null) }
                        )
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = draft.hour,
            initialMinute = draft.minute,
            is24Hour = DateFormat.is24HourFormat(context)
        )
        Dialog(onDismissRequest = { showTimePicker = false }) {
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
                        text = stringResource(R.string.alarm_pick_time),
                        style = MaterialTheme.typography.displaySmall
                    )
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ExpressiveButton(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.action_cancel),
                            onClick = { showTimePicker = false },
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                        ExpressiveButton(
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.action_apply),
                            onClick = {
                                draft = draft.copy(
                                    hour = timePickerState.hour,
                                    minute = timePickerState.minute
                                )
                                showTimePicker = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdaySelector(
    selectedDays: Set<Int>,
    onToggle: (Int) -> Unit
) {
    val context = LocalContext.current
    val days = listOf(
        DayOfWeek.MONDAY.value,
        DayOfWeek.TUESDAY.value,
        DayOfWeek.WEDNESDAY.value,
        DayOfWeek.THURSDAY.value,
        DayOfWeek.FRIDAY.value,
        DayOfWeek.SATURDAY.value,
        DayOfWeek.SUNDAY.value
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.alarm_repeat_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            days.forEach { day ->
                FilterChip(
                    selected = selectedDays.contains(day),
                    onClick = { onToggle(day) },
                    label = { Text(AlarmModeScheduler.dayLabel(context, day)) }
                )
            }
        }        
    }
}

private fun formatAlarmTime(hour: Int, minute: Int, is24Hour: Boolean): String {
    val locale = Locale.getDefault()
    val formatter = if (is24Hour) {
        DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "Hm"), locale)
    } else {
        DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "hm"), locale)
    }
    return java.time.LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)).format(formatter)
}

private fun alarmRepeatSummary(context: android.content.Context, alarm: AlarmModeItem): String {
    if (alarm.repeatDays.isEmpty()) return context.getString(R.string.alarm_repeat_once)
    val locale = Locale.getDefault()
    val labels = alarm.repeatDays.sorted().map {
        DayOfWeek.of(it).getDisplayName(java.time.format.TextStyle.SHORT, locale)
    }
    return labels.joinToString(", ")
}

private fun formatNextTrigger(triggerAtMillis: Long): String {
    if (triggerAtMillis <= 0L) return ""
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
    return Instant.ofEpochMilli(triggerAtMillis).atZone(ZoneId.systemDefault()).format(formatter)
}

private fun resolveAudioLabel(context: android.content.Context, uri: android.net.Uri): String {
    val resolver = context.contentResolver
    val label = runCatching {
        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
    }.getOrNull()
    return label ?: (uri.lastPathSegment ?: context.getString(R.string.default_selected_audio))
}
