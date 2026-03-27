package com.upnp.fakeCall.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.upnp.fakeCall.FakeCallViewModel
import com.upnp.fakeCall.R
import com.upnp.fakeCall.ReleaseInfo
import com.upnp.fakeCall.ui.screens.DashboardScreen
import com.upnp.fakeCall.ui.screens.OnboardingScreen
import com.upnp.fakeCall.ui.screens.SettingsScreen

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_SETTINGS = "settings"

private val RequiredPermissions = arrayOf(
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.READ_PHONE_NUMBERS,
    Manifest.permission.RECORD_AUDIO
)

@Composable
fun FakeCallApp(
    viewModel: FakeCallViewModel = viewModel(),
    startInSettings: Boolean = false
) {
    val navController = rememberNavController()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = navController.context

    val slideSpec = tween<IntOffset>(
        durationMillis = 380,
        easing = FastOutSlowInEasing
    )
    val fadeSpec = tween<Float>(
        durationMillis = 180,
        easing = FastOutSlowInEasing
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.onPermissionStateChanged(hasAllPermissions(navController.context))
    }

    LaunchedEffect(Unit) {
        val granted = hasAllPermissions(navController.context)
        viewModel.onPermissionStateChanged(granted)
        if (!granted) {
            permissionLauncher.launch(RequiredPermissions)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = when {
                    startInSettings && state.isOnboardingComplete -> ROUTE_SETTINGS
                    state.isOnboardingComplete -> ROUTE_DASHBOARD
                    else -> ROUTE_ONBOARDING
                },
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = slideSpec
                    ) + fadeIn(animationSpec = fadeSpec)
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = slideSpec
                    ) + fadeOut(animationSpec = fadeSpec)
                },
                popEnterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = slideSpec
                    ) + fadeIn(animationSpec = fadeSpec)
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = slideSpec
                    ) + fadeOut(animationSpec = fadeSpec)
                }
            ) {
                composable(route = ROUTE_ONBOARDING) {
                    OnboardingScreen(
                        viewModel = viewModel,
                        onRequestPermissions = { permissionLauncher.launch(RequiredPermissions) },
                        onFinish = {
                            navController.navigate(ROUTE_DASHBOARD) {
                                popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                            }
                        }
                    )
                }

                composable(route = ROUTE_DASHBOARD) {
                    DashboardScreen(
                        viewModel = viewModel,
                        onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
                    )
                }

                composable(route = ROUTE_SETTINGS) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(ROUTE_DASHBOARD) {
                                    popUpTo(ROUTE_SETTINGS) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        },
                        onRequestPermissions = { permissionLauncher.launch(RequiredPermissions) }
                    )
                }
            }

            val startupUpdate = state.startupUpdate
            AnimatedVisibility(
                visible = startupUpdate != null,
                enter = expandVertically(animationSpec = tween(320, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = fadeSpec),
                exit = shrinkVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = fadeSpec),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                val release = startupUpdate ?: return@AnimatedVisibility
                UpdateBanner(
                    release = release,
                    onDownload = { openUpdateUrl(context, release.htmlUrl) },
                    onDismiss = viewModel::dismissStartupUpdate
                )
            }
        }
    }
}

@Composable
private fun UpdateBanner(
    release: ReleaseInfo,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.14f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Text(
                text = stringResource(R.string.update_available_banner, release.tagName),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = onDownload,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Text(stringResource(R.string.action_download))
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.cd_dismiss_update),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

private fun hasAllPermissions(context: Context): Boolean {
    return RequiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

private fun openUpdateUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        if (it is ActivityNotFoundException) {
            // Ignore silently if no browser app is available.
        }
    }
}
