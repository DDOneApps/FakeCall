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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Phone
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.upnp.fakeCall.FakeCallViewModel
import com.upnp.fakeCall.R
import com.upnp.fakeCall.ReleaseInfo
import com.upnp.fakeCall.ui.screens.AlarmCreateScreen
import com.upnp.fakeCall.ui.screens.AlarmOverviewScreen
import com.upnp.fakeCall.ui.screens.DashboardScreen
import com.upnp.fakeCall.ui.screens.OnboardingScreen
import com.upnp.fakeCall.ui.screens.SettingsScreen

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_ALARM = "alarm"
private const val ROUTE_ALARM_CREATE = "alarm_create"
private const val ROUTE_ALARM_EDIT = "alarm_edit/{alarmId}"
private const val ROUTE_SETTINGS = "settings"
private const val ARG_ALARM_ID = "alarmId"

private fun alarmEditRoute(alarmId: Long): String = "alarm_edit/$alarmId"

private fun modeRouteIndex(route: String?): Int? {
    return when (route) {
        ROUTE_DASHBOARD -> 0
        ROUTE_ALARM -> 1
        else -> null
    }
}

private fun modeSlideDirection(fromRoute: String?, toRoute: String?): AnimatedContentTransitionScope.SlideDirection? {
    val fromIndex = modeRouteIndex(fromRoute) ?: return null
    val toIndex = modeRouteIndex(toRoute) ?: return null
    if (fromIndex == toIndex) return null
    return if (toIndex > fromIndex) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }
}

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
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
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
                    val direction = modeSlideDirection(initialState.destination.route, targetState.destination.route)
                    when {
                        initialState.destination.route == targetState.destination.route -> EnterTransition.None
                        direction != null -> {
                            slideIntoContainer(
                                towards = direction,
                                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))
                        }
                        else -> {
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = slideSpec
                            ) + fadeIn(animationSpec = fadeSpec)
                        }
                    }
                },
                exitTransition = {
                    val direction = modeSlideDirection(initialState.destination.route, targetState.destination.route)
                    when {
                        initialState.destination.route == targetState.destination.route -> ExitTransition.None
                        direction != null -> {
                            slideOutOfContainer(
                                towards = direction,
                                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                        }
                        else -> {
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = slideSpec
                            ) + fadeOut(animationSpec = fadeSpec)
                        }
                    }
                },
                popEnterTransition = {
                    val direction = modeSlideDirection(initialState.destination.route, targetState.destination.route)
                    when {
                        initialState.destination.route == targetState.destination.route -> EnterTransition.None
                        direction != null -> {
                            slideIntoContainer(
                                towards = direction,
                                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing))
                        }
                        else -> {
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = slideSpec
                            ) + fadeIn(animationSpec = fadeSpec)
                        }
                    }
                },
                popExitTransition = {
                    val direction = modeSlideDirection(initialState.destination.route, targetState.destination.route)
                    when {
                        initialState.destination.route == targetState.destination.route -> ExitTransition.None
                        direction != null -> {
                            slideOutOfContainer(
                                towards = direction,
                                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                        }
                        else -> {
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = slideSpec
                            ) + fadeOut(animationSpec = fadeSpec)
                        }
                    }
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
                        onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                        bottomFloatingInset = 76.dp,
                        modeNavigationBar = null
                    )
                }

                composable(route = ROUTE_ALARM) {
                    AlarmOverviewScreen(
                        viewModel = viewModel,
                        onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                        onOpenCreateAlarm = { navController.navigate(ROUTE_ALARM_CREATE) },
                        onEditAlarm = { alarmId ->
                            navController.navigate(alarmEditRoute(alarmId))
                        }
                    )
                }

                composable(route = ROUTE_ALARM_CREATE) {
                    AlarmCreateScreen(
                        viewModel = viewModel,
                        onBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(ROUTE_ALARM) { launchSingleTop = true }
                            }
                        }
                    )
                }

                composable(
                    route = ROUTE_ALARM_EDIT,
                    arguments = listOf(navArgument(ARG_ALARM_ID) { type = NavType.LongType })
                ) { entry ->
                    val alarmId = entry.arguments?.getLong(ARG_ALARM_ID) ?: return@composable
                    AlarmCreateScreen(
                        viewModel = viewModel,
                        editAlarmId = alarmId,
                        onBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(ROUTE_ALARM) { launchSingleTop = true }
                            }
                        }
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

            val showModeBar = currentRoute == ROUTE_DASHBOARD || currentRoute == ROUTE_ALARM
            AnimatedVisibility(
                visible = showModeBar,
                enter = fadeIn(animationSpec = tween(160, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(120, easing = FastOutSlowInEasing)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                HomeModeNavigationBar(
                    selectedRoute = if (currentRoute == ROUTE_ALARM) ROUTE_ALARM else ROUTE_DASHBOARD,
                    onSelectDashboard = {
                        if (currentRoute != ROUTE_DASHBOARD) {
                            navController.navigate(ROUTE_DASHBOARD) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onSelectAlarm = {
                        if (currentRoute != ROUTE_ALARM) {
                            navController.navigate(ROUTE_ALARM) {
                                launchSingleTop = true
                            }
                        }
                    }
                )
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

@Composable
private fun HomeModeNavigationBar(
    selectedRoute: String,
    onSelectDashboard: () -> Unit,
    onSelectAlarm: () -> Unit
) {
    val navContainerColor by animateColorAsState(
        targetValue = if (selectedRoute == ROUTE_ALARM) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "modeBarContainer"
    )
    val selectedIndex = if (selectedRoute == ROUTE_ALARM) 1 else 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 236.dp, max = 312.dp),
            shape = RoundedCornerShape(28.dp),
            color = navContainerColor,
            tonalElevation = 2.dp
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                val selectorOffsetTarget = if (selectedIndex == 0) 0.dp else maxWidth / 2
                val selectorOffset by animateDpAsState(
                    targetValue = selectorOffsetTarget,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "modeSelectorOffset"
                )
                val selectorWidth = maxWidth / 2

                Surface(
                    modifier = Modifier
                        .offset(x = selectorOffset)
                        .width(selectorWidth)
                        .height(46.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {}

                Row(modifier = Modifier.fillMaxWidth()) {
                    ModeSwitchItem(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.nav_mode_call),
                        icon = Icons.Outlined.Phone,
                        selected = selectedIndex == 0,
                        onClick = onSelectDashboard
                    )
                    ModeSwitchItem(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.nav_mode_alarm),
                        icon = Icons.Outlined.Alarm,
                        selected = selectedIndex == 1,
                        onClick = onSelectAlarm
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeSwitchItem(
    modifier: Modifier,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "modeSwitchContent"
    )

    Row(
        modifier = modifier
            .height(46.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            modifier = Modifier.padding(start = 8.dp)
        )
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
