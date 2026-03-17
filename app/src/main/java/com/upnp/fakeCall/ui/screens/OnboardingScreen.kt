package com.upnp.fakeCall.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upnp.fakeCall.FakeCallViewModel
import com.upnp.fakeCall.ui.components.AnimatedIcon
import com.upnp.fakeCall.ui.components.ExpressiveButton
import com.upnp.fakeCall.ui.components.ExpressiveCardShape
import com.upnp.fakeCall.ui.components.expressiveSpring

@Composable
fun OnboardingScreen(
    viewModel: FakeCallViewModel,
    onRequestPermissions: () -> Unit,
    onFinish: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val permissionsReady = state.hasRequiredPermissions
    val callingAccountReady = state.isProviderEnabled
    val exactAlarmsReady = viewModel.canScheduleExactAlarms()
    val canFinish = permissionsReady && callingAccountReady

    val heroScale by animateFloatAsState(
        targetValue = if (canFinish) 1.02f else 1f,
        animationSpec = expressiveSpring(),
        label = "heroScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 16.dp,
                bottom = 32.dp
            )
        ) {
            item {
                Column(
                    modifier = Modifier.graphicsLayer {
                        scaleX = heroScale
                        scaleY = heroScale
                    },
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Welcome to FakeCall",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "A premium fake call experience with expressive motion and instant scheduling.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                FeatureCard()
            }

            item {
                PermissionCard(
                    title = "Phone + Microphone permissions",
                    subtitle = "Required to simulate calls and record audio.",
                    icon = Icons.Outlined.Mic,
                    isReady = permissionsReady,
                    actionLabel = "Grant permissions",
                    onAction = {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onRequestPermissions()
                    }
                )
            }

            item {
                PermissionCard(
                    title = "Make & manage phone calls",
                    subtitle = "Enable FakeCall in system Calling Accounts.",
                    icon = Icons.Outlined.Phone,
                    isReady = callingAccountReady,
                    actionLabel = "Open calling accounts",
                    onAction = {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        val intent = viewModel.openCallingAccountsIntent()
                        runCatching { context.startActivity(intent) }
                    }
                )
            }

            item {
                PermissionCard(
                    title = "Precise alarms & reminders",
                    subtitle = "Needed for exact-time calls on Android 12+.",
                    icon = Icons.Outlined.AccessTime,
                    isReady = exactAlarmsReady,
                    actionLabel = "Enable precise alarms",
                    onAction = {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        val intent = viewModel.openExactAlarmSettingsIntent()
                        runCatching { context.startActivity(intent) }
                    }
                )
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = ExpressiveCardShape,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AnimatedIcon(
                                imageVector = Icons.Outlined.AccountCircle,
                                contentDescription = null,
                                shape = androidx.compose.foundation.shape.CircleShape,
                                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Can’t open Calling Accounts settings?",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Some builds hide the screen. You can open it via ADB using the standard intent (more robust on Android 17+):",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = ExpressiveCardShape,
                            tonalElevation = 1.dp
                        ) {
                            Text(
                                text = "adb shell am start -a android.telecom.action.CHANGE_PHONE_ACCOUNTS",
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = canFinish,
                    enter = expandVertically(animationSpec = expressiveSpring()) + fadeIn(animationSpec = expressiveSpring()),
                    exit = shrinkVertically(animationSpec = expressiveSpring()) + fadeOut(animationSpec = expressiveSpring())
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = ExpressiveCardShape,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AnimatedIcon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                shape = androidx.compose.foundation.shape.CircleShape,
                                backgroundColor = MaterialTheme.colorScheme.primary,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = "All set! You’re ready to schedule your first fake call.",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            item {
                ExpressiveButton(
                    label = if (canFinish) "Finish setup" else "Finish setup (needs permissions)",
                    onClick = {
                        viewModel.completeOnboarding()
                        onFinish()
                    },
                    enabled = canFinish,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ExpressiveCardShape)
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun FeatureCard() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = ExpressiveCardShape,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Main features",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            FeatureRow(
                icon = Icons.Outlined.Phone,
                title = "Realistic incoming calls",
                subtitle = "Expressive call screens with smart scheduling."
            )
            FeatureRow(
                icon = Icons.Outlined.AccessTime,
                title = "Quick presets + exact time",
                subtitle = "Schedule instantly or pick a precise time."
            )
            FeatureRow(
                icon = Icons.Outlined.Settings,
                title = "Audio + IVR routing",
                subtitle = "Play custom audio and route with keypad tones."
            )
        }
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnimatedIcon(
            imageVector = icon,
            contentDescription = null,
            shape = androidx.compose.foundation.shape.CircleShape,
            backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            tint = MaterialTheme.colorScheme.primary,
            isActive = true
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isReady: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    val statusColor = if (isReady) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val statusIcon = if (isReady) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = ExpressiveCardShape,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedIcon(
                    imageVector = icon,
                    contentDescription = null,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedIcon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    backgroundColor = statusColor,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            ExpressiveButton(
                label = if (isReady) "Completed" else actionLabel,
                onClick = onAction,
                enabled = !isReady,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
