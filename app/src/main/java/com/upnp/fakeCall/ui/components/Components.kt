package com.upnp.fakeCall.ui.components

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeUp
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
    callerName: String,
    callerNumber: String,
    onCallerNameChange: (String) -> Unit,
    onCallerNumberChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = stringResource(R.string.caller_details_title),
        modifier = modifier,
        shape = ExpressiveAsymmetricShape
    ) {
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
    }
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
    audioLabel: String,
    audioUri: String,
    onOpenSettings: () -> Unit,
    onClearAudio: () -> Unit,
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

    DisposableEffect(audioUri) {
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
                Text(
                    text = audioLabel,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.audio_preview_hint),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useVerticalButtons = maxWidth < 460.dp
            if (useVerticalButtons) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExpressiveButton(
                        label = if (isPlaying) stringResource(R.string.action_stop) else stringResource(R.string.action_play),
                        onClick = {
                            if (audioUri.isBlank()) return@ExpressiveButton
                            if (isPlaying) {
                                stopPlayback()
                            } else {
                                val uri = runCatching { Uri.parse(audioUri) }.getOrNull() ?: return@ExpressiveButton
                                val newPlayer = MediaPlayer().apply {
                                    setDataSource(context, uri)
                                    prepare()
                                    start()
                                    setOnCompletionListener {
                                        isPlaying = false
                                        runCatching { release() }
                                        player = null
                                    }
                                }
                                player = newPlayer
                                isPlaying = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = if (isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                        enabled = audioUri.isNotBlank(),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    ExpressiveButton(
                        label = stringResource(R.string.action_open_settings),
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    ExpressiveButton(
                        label = stringResource(R.string.action_clear_audio),
                        onClick = {
                            stopPlayback()
                            onClearAudio()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExpressiveButton(
                        label = if (isPlaying) stringResource(R.string.action_stop) else stringResource(R.string.action_play),
                        onClick = {
                            if (audioUri.isBlank()) return@ExpressiveButton
                            if (isPlaying) {
                                stopPlayback()
                            } else {
                                val uri = runCatching { Uri.parse(audioUri) }.getOrNull() ?: return@ExpressiveButton
                                val newPlayer = MediaPlayer().apply {
                                    setDataSource(context, uri)
                                    prepare()
                                    start()
                                    setOnCompletionListener {
                                        isPlaying = false
                                        runCatching { release() }
                                        player = null
                                    }
                                }
                                player = newPlayer
                                isPlaying = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        leadingIcon = if (isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                        enabled = audioUri.isNotBlank(),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    ExpressiveButton(
                        label = stringResource(R.string.action_open_settings),
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    ExpressiveButton(
                        label = stringResource(R.string.action_clear_audio),
                        onClick = {
                            stopPlayback()
                            onClearAudio()
                        },
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
