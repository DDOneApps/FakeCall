package com.upnp.fakeCall.ui.components

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeUp
import com.upnp.fakeCall.CallContact
import com.upnp.fakeCall.CallerInputMode
import com.upnp.fakeCall.CustomPreset
import com.upnp.fakeCall.FakeCallViewModel
import com.upnp.fakeCall.R

fun <T> expressiveSpring() = spring<T>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)

val ExpressiveCardShape = RoundedCornerShape(32.dp)
val ExpressiveAsymmetricShape = RoundedCornerShape(topStart = 40.dp, topEnd = 24.dp, bottomEnd = 40.dp, bottomStart = 24.dp)
val ExpressiveSoftShape = RoundedCornerShape(18.dp)

fun Modifier.bounceClick(
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.92f else 1f,
        animationSpec = expressiveSpring(),
        label = "bounceScale"
    )

    val semanticsModifier = if (onClick != null) {
        Modifier.semantics(mergeDescendants = true) {
            role = Role.Button
            this.onClick {
                if (enabled) {
                    onClick()
                }
                true
            }
        }
    } else {
        Modifier
    }

    val pointerModifier = if (enabled) {
        Modifier.pointerInput(onClick) {
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    val up = waitForUpOrCancellation()
                    pressed = false
                    if (up != null && onClick != null) {
                        onClick()
                    }
                }
            }
        }
    } else {
        Modifier
    }

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(pointerModifier)
        .then(semanticsModifier)
}

@Composable
fun AnimatedIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = ExpressiveSoftShape,
    backgroundColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    tint: Color = MaterialTheme.colorScheme.onSurface,
    isRinging: Boolean = false,
    isActive: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val rotation = remember { Animatable(0f) }
    val pulse by animateFloatAsState(
        targetValue = if (isActive || isRinging) 1.05f else 1f,
        animationSpec = expressiveSpring(),
        label = "iconPulse"
    )

    LaunchedEffect(isRinging) {
        if (isRinging) {
            while (isActive) {
                rotation.animateTo(16f, animationSpec = expressiveSpring())
                rotation.animateTo(-12f, animationSpec = expressiveSpring())
                rotation.animateTo(0f, animationSpec = expressiveSpring())
            }
        } else {
            rotation.animateTo(0f, animationSpec = expressiveSpring())
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(backgroundColor)
            .graphicsLayer {
                rotationZ = rotation.value
                scaleX = pulse
                scaleY = pulse
            }
            .bounceClick(enabled = onClick != null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}

@Composable
fun ExpressiveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = imeAction)
    )
}

@Composable
fun ExpressiveButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    maxLines: Int = 2,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    shape: Shape = RoundedCornerShape(24.dp)
) {
    val background = if (enabled) containerColor else MaterialTheme.colorScheme.surfaceContainerHigh
    val foreground = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier
            .bounceClick(enabled = enabled, onClick = onClick),
        color = background,
        contentColor = foreground,
        shape = shape,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                androidx.compose.material3.Icon(
                    imageVector = leadingIcon,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = maxLines,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = ExpressiveCardShape,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

@Composable
fun CallerInputCard(
    callerInputMode: CallerInputMode,
    onCallerInputModeChange: (CallerInputMode) -> Unit,
    callerName: String,
    callerNumber: String,
    onCallerNameChange: (String) -> Unit,
    onCallerNumberChange: (String) -> Unit,
    selectedContact: CallContact?,
    pinnedContacts: List<CallContact>,
    recentContacts: List<CallContact>,
    onPickContact: () -> Unit,
    onSelectContact: (CallContact) -> Unit,
    onTogglePinned: (CallContact) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = stringResource(R.string.caller_details_title),
        modifier = modifier,
        shape = ExpressiveCardShape
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = callerInputMode == CallerInputMode.MANUAL,
                onClick = { onCallerInputModeChange(CallerInputMode.MANUAL) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.bounceClick(),
                label = { Text(stringResource(R.string.caller_mode_manual)) }
            )
            SegmentedButton(
                selected = callerInputMode == CallerInputMode.CONTACT,
                onClick = { onCallerInputModeChange(CallerInputMode.CONTACT) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.bounceClick(),
                label = { Text(stringResource(R.string.caller_mode_contact)) }
            )
        }

        if (callerInputMode == CallerInputMode.MANUAL) {
            ExpressiveTextField(
                value = callerName,
                onValueChange = onCallerNameChange,
                label = stringResource(R.string.label_caller_name),
                modifier = Modifier.fillMaxWidth(),
                imeAction = ImeAction.Next
            )
            ExpressiveTextField(
                value = callerNumber,
                onValueChange = onCallerNumberChange,
                label = stringResource(R.string.label_caller_number),
                modifier = Modifier.fillMaxWidth(),
                imeAction = ImeAction.Done
            )
            Text(
                text = stringResource(R.string.hint_shown_on_incoming_call),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ExpressiveButton(
                label = stringResource(R.string.action_select_contact),
                onClick = onPickContact,
                leadingIcon = Icons.Outlined.Person,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )

            val availableRecent = recentContacts
                .filterNot { recent -> pinnedContacts.any { sameContact(it, recent) } }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val minCardWidth = 108.dp
                val spacing = 8.dp
                val columns = ((maxWidth + spacing) / (minCardWidth + spacing))
                    .toInt()
                    .coerceIn(1, 3)
                val recentLimit = if (pinnedContacts.isNotEmpty()) 1 else 3
                val recentLimited = availableRecent.takeLast(recentLimit)
                ContactSelectionGrid(
                    columns = columns,
                    selectedContact = selectedContact,
                    pinnedContacts = pinnedContacts,
                    recentContacts = recentLimited,
                    onSelect = onSelectContact,
                    onTogglePinned = onTogglePinned,
                    modifier = Modifier.animateContentSize(animationSpec = expressiveSpring())
                )
            }
        }
    }
}

@Composable
private fun ContactSelectionGrid(
    columns: Int,
    selectedContact: CallContact?,
    pinnedContacts: List<CallContact>,
    recentContacts: List<CallContact>,
    onSelect: (CallContact) -> Unit,
    onTogglePinned: (CallContact) -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedContact == null && pinnedContacts.isEmpty() && recentContacts.isEmpty()) {
        return
    }

    val selectedHandledInGroups = selectedContact?.let { selected ->
        pinnedContacts.any { sameContact(it, selected) } ||
            recentContacts.any { sameContact(it, selected) }
    } ?: true
    val orphanSelectedContact = if (!selectedHandledInGroups) selectedContact else null

    val displayContacts = buildList {
        addAll(pinnedContacts)
        addAll(recentContacts)
        if (orphanSelectedContact != null) add(orphanSelectedContact)
    }
    val rows = displayContacts.chunked(columns)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { rowContacts ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowContacts.forEach { contact ->
                    ContactChip(
                        contact = contact,
                        isSelected = selectedContact?.let { sameContact(it, contact) } == true,
                        isPinned = pinnedContacts.any { sameContact(it, contact) },
                        modifier = Modifier.weight(1f),
                        onSelect = onSelect,
                        onTogglePinned = onTogglePinned
                    )
                }
                repeat(columns - rowContacts.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ContactChip(
    contact: CallContact,
    isSelected: Boolean,
    isPinned: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (CallContact) -> Unit,
    onTogglePinned: (CallContact) -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = expressiveSpring(),
        label = "contactCardColor"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.98f,
        animationSpec = expressiveSpring(),
        label = "contactCardScale"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        border = if (isSelected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        tonalElevation = if (isSelected) 2.dp else 1.dp,
        modifier = modifier
            .height(162.dp)
            .clip(RoundedCornerShape(22.dp))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable { onSelect(contact) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            IconButton(
                onClick = { onTogglePinned(contact) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = if (isPinned) {
                        Icons.Outlined.Star
                    } else {
                        Icons.Outlined.StarBorder
                    },
                    contentDescription = null,
                    tint = if (isPinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val avatarSize = when {
                    maxWidth < 90.dp -> 42.dp
                    maxWidth < 104.dp -> 48.dp
                    else -> if (isSelected) 58.dp else 52.dp
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ContactAvatar(
                        name = contact.displayName,
                        photoUri = contact.photoUri,
                        avatarBase64 = contact.avatarBase64,
                        size = avatarSize
                    )
                    Text(
                        text = contact.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isSelected) {
                        Text(
                            text = stringResource(R.string.selected_short),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    } else {
                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactAvatar(
    name: String,
    photoUri: String,
    avatarBase64: String,
    size: Dp
) {
    val context = LocalContext.current
    val imageBitmap: ImageBitmap? = remember(avatarBase64, photoUri) {
        val fromBase64 = runCatching {
            if (avatarBase64.isBlank()) return@runCatching null
            val bytes = android.util.Base64.decode(avatarBase64, android.util.Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
        if (fromBase64 != null) {
            fromBase64
        } else if (photoUri.isBlank()) {
            null
        } else {
            runCatching {
                val uri = Uri.parse(photoUri)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(size)
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = initialsForName(name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

private fun initialsForName(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts.first().take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

private fun sameContact(a: CallContact, b: CallContact): Boolean {
    return if (a.id > 0 && b.id > 0) a.id == b.id else a.phoneNumber == b.phoneNumber
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimingSelectionCard(
    scheduleTitle: String,
    scheduleSubtitle: String,
    selectedDelaySeconds: Int,
    presetOptions: List<Int>,
    onPresetSelected: (Int) -> Unit,
    manualTimeLabel: String,
    manualTimeHelper: String,
    onOpenCustom: () -> Unit,
    customPresets: List<CustomPreset>,
    onCustomPresetSelected: (CustomPreset) -> Unit,
    onRemovePreset: (CustomPreset) -> Unit,
    formatPreset: (CustomPreset) -> String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    SectionCard(
        title = stringResource(R.string.timing_title),
        modifier = modifier
    ) {
        Text(
            text = scheduleTitle,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = scheduleSubtitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            val count = presetOptions.size
            presetOptions.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = selectedDelaySeconds == option,
                    onClick = { onPresetSelected(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = count),
                    modifier = Modifier.bounceClick(),
                    label = { Text(FakeCallViewModel.formatDelay(context, option)) }
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .bounceClick(onClick = onOpenCustom),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = ExpressiveCardShape,
            tonalElevation = 1.dp
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
                        text = manualTimeLabel,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = manualTimeHelper,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedIcon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = null,
                    size = 36.dp,
                    shape = ExpressiveSoftShape,
                    backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (customPresets.isNotEmpty()) {
            Text(
                text = stringResource(R.string.label_saved_presets),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                customPresets.forEach { preset ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .bounceClick(onClick = { onCustomPresetSelected(preset) })
                            .padding(horizontal = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(formatPreset(preset), style = MaterialTheme.typography.labelLarge)
                            AnimatedIcon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.cd_remove_preset),
                                size = 28.dp,
                                shape = CircleShape,
                                backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = { onRemovePreset(preset) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioPreviewCard(
    modeLabel: String,
    audioLabel: String,
    helperText: String,
    previewUri: String,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    val stopPlayback: () -> Unit = {
        player?.run {
            runCatching { stop() }
            runCatching { release() }
        }
        player = null
        isPlaying = false
    }

    val startPlayback: () -> Unit = {
        if (previewUri.isNotBlank()) {
            val uri = runCatching { Uri.parse(previewUri) }.getOrNull()
            if (uri != null) {
                stopPlayback()
                val newPlayer = MediaPlayer()
                val started = runCatching {
                    newPlayer.setDataSource(context, uri)
                    newPlayer.prepare()
                    newPlayer.setOnCompletionListener {
                        isPlaying = false
                        runCatching { it.release() }
                        if (player === it) {
                            player = null
                        }
                    }
                    newPlayer.start()
                }.onFailure {
                    runCatching { newPlayer.release() }
                }.isSuccess
                if (started) {
                    player = newPlayer
                    isPlaying = true
                }
            }
        }
    }

    DisposableEffect(previewUri) {
        onDispose {
            stopPlayback()
        }
    }

    SectionCard(
        title = stringResource(R.string.audio_preview_title),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedIcon(
                imageVector = Icons.Outlined.VolumeUp,
                contentDescription = null,
                shape = CircleShape,
                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    tonalElevation = 1.dp
                ) {
                    Text(
                        text = modeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Text(
                    text = audioLabel,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = helperText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useVerticalButtons = maxWidth < 460.dp
            val hasPreview = previewUri.isNotBlank()
            val hasSecondary = secondaryActionLabel != null && onSecondaryAction != null
            if (useVerticalButtons) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (hasPreview) {
                        ExpressiveButton(
                            label = if (isPlaying) stringResource(R.string.action_stop) else stringResource(R.string.action_play),
                            onClick = {
                                if (isPlaying) {
                                    stopPlayback()
                                } else {
                                    startPlayback()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = if (isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    ExpressiveButton(
                        label = primaryActionLabel,
                        onClick = {
                            stopPlayback()
                            onPrimaryAction()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = Icons.Outlined.Tune,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (hasSecondary) {
                        ExpressiveButton(
                            label = secondaryActionLabel.orEmpty(),
                            onClick = {
                                stopPlayback()
                                onSecondaryAction?.invoke()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = Icons.Outlined.Close,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (hasPreview) {
                        ExpressiveButton(
                            label = if (isPlaying) stringResource(R.string.action_stop) else stringResource(R.string.action_play),
                            onClick = {
                                if (isPlaying) {
                                    stopPlayback()
                                } else {
                                    startPlayback()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            leadingIcon = if (isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    ExpressiveButton(
                        label = primaryActionLabel,
                        onClick = {
                            stopPlayback()
                            onPrimaryAction()
                        },
                        modifier = Modifier.weight(1f),
                        leadingIcon = Icons.Outlined.Tune,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (hasSecondary) {
                        ExpressiveButton(
                            label = secondaryActionLabel.orEmpty(),
                            onClick = {
                                stopPlayback()
                                onSecondaryAction?.invoke()
                            },
                            modifier = Modifier.weight(1f),
                            leadingIcon = Icons.Outlined.Close,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
