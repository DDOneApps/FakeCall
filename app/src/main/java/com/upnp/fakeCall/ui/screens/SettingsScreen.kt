package com.upnp.fakeCall.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upnp.fakeCall.BuildConfig
import com.upnp.fakeCall.FakeCallViewModel
import com.upnp.fakeCall.ReleaseInfo
import com.upnp.fakeCall.UpdateCheckResult
import com.upnp.fakeCall.ivr.IvrNode
import com.upnp.fakeCall.ui.components.AnimatedIcon
import com.upnp.fakeCall.ui.components.ExpressiveTextField
import com.upnp.fakeCall.ui.components.bounceClick
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: FakeCallViewModel,
    onBack: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val ivrConfig = state.ivrConfig
    val ivrNodes = ivrConfig?.nodes?.values?.sortedBy { it.title } ?: emptyList()

    var showAddNodeDialog by rememberSaveable { mutableStateOf(false) }
    var mappingNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAudioNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    var isCheckingUpdates by rememberSaveable { mutableStateOf(false) }
    var quickTriggerDelayExpanded by rememberSaveable { mutableStateOf(false) }
    var updateDialogRelease by remember { mutableStateOf<ReleaseInfo?>(null) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        viewModel.onAudioFileSelected(uri)
    }
    val recordingsFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        viewModel.onRecordingFolderSelected(uri)
    }

    val ivrAudioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val nodeId = pendingAudioNodeId
        if (nodeId != null) {
            viewModel.onIvrNodeAudioSelected(nodeId, uri)
        }
        pendingAudioNodeId = null
    }

    val ivrExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/xml")
    ) { uri ->
        viewModel.exportIvrConfig(uri)
    }

    val ivrImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        viewModel.importIvrConfig(uri)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnimatedIcon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        shape = CircleShape,
                        backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = onBack
                    )
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 8.dp,
                    bottom = 24.dp
                )
            ) {
                item {
                    PreferenceCategoryHeader("Provider")
                }

                if (!state.hasRequiredPermissions) {
                    item {
                        PreferenceCard(
                            icon = Icons.Outlined.Phone,
                            title = "Phone permissions required",
                            subtitle = "Grant access to register the call provider.",
                            onClick = onRequestPermissions,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                item {
                    PreferenceCard(
                        icon = Icons.Outlined.Phone,
                        title = "Provider name",
                        subtitle = "Shown in Calling Accounts",
                        onClick = null,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        ExpressiveTextField(
                            value = state.providerName,
                            onValueChange = viewModel::onProviderNameChange,
                            label = "Provider name",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    PreferenceCard(
                        icon = Icons.Outlined.CheckCircle,
                        title = "Save & register provider",
                        subtitle = "Make this account available for incoming calls.",
                        onClick = viewModel::saveProvider
                    )
                }

                item {
                    PreferenceCard(
                        icon = Icons.Outlined.Settings,
                        title = "Enable provider in system",
                        subtitle = if (state.isProviderEnabled) {
                            "Provider is enabled."
                        } else {
                            "Open Calling Accounts to enable it."
                        },
                        onClick = { openCallingAccounts(context, viewModel) }
                    )
                }

                item {
                    PreferenceCategoryHeader("Audio")
                }

                item {
                    PreferenceCard(
                        icon = Icons.Outlined.MusicNote,
                        title = "Select audio file",
                        subtitle = "Current: ${state.selectedAudioName.ifBlank { "Default" }}",
                        onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) }
                    )
                }

                item {
                    PreferenceCard(
                        icon = Icons.Outlined.VolumeOff,
                        title = "Use default audio",
                        subtitle = "Disable custom audio playback.",
                        onClick = viewModel::clearAudioSelection
                    )
                }

                item {
                    PreferenceCard(
                        icon = Icons.Outlined.Mic,
                        title = "Microphone recording",
                        subtitle = if (state.isRecordingEnabled) "Enabled" else "Disabled",
                        onClick = null,
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = state.isRecordingEnabled,
                                    onCheckedChange = viewModel::onRecordingEnabledChange
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }

                item {
                    PreferenceCategoryHeader("Storage")
                }

                item {
                    PreferenceCard(
                        icon = Icons.Outlined.Folder,
                        title = "Recording folder",
                        subtitle = "Save to: ${state.recordingsFolderName}",
                        onClick = { recordingsFolderLauncher.launch(null) }
                    )
                }

                item {
                    PreferenceCard(
                        icon = Icons.Outlined.Refresh,
                        title = "Reset recording folder",
                        subtitle = "Use Downloads/FakeCall",
                        onClick = viewModel::clearRecordingFolderSelection
                    )
                }

                item {
                    PreferenceCategoryHeader("Automation")
                }

                item {
                    PreferenceCard(
                        icon = Icons.Outlined.AccessTime,
                        title = "Automation & Quick Trigger Defaults",
                        subtitle = "Used by external intents and the accessibility shortcut.",
                        onClick = null,
                        trailingContent = null
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ExpressiveTextField(
                                value = state.quickTriggerCallerName,
                                onValueChange = viewModel::onQuickTriggerCallerNameChange,
                                label = "Default caller name",
                                modifier = Modifier.fillMaxWidth()
                            )
                            ExpressiveTextField(
                                value = state.quickTriggerCallerNumber,
                                onValueChange = viewModel::onQuickTriggerCallerNumberChange,
                                label = "Default caller number",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(modifier = Modifier.fillMaxWidth()) {
                                androidx.compose.material3.OutlinedTextField(
                                    value = FakeCallViewModel.formatDelay(state.quickTriggerDelaySeconds),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Default delay") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = quickTriggerDelayExpanded)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            quickTriggerDelayExpanded = !quickTriggerDelayExpanded
                                        }
                                )
                                DropdownMenu(
                                    expanded = quickTriggerDelayExpanded,
                                    onDismissRequest = { quickTriggerDelayExpanded = false }
                                ) {
                                    viewModel.delayOptionsSeconds.forEach { delaySeconds ->
                                        DropdownMenuItem(
                                            text = { Text(FakeCallViewModel.formatDelay(delaySeconds)) },
                                            onClick = {
                                                viewModel.onQuickTriggerDelayChange(delaySeconds)
                                                quickTriggerDelayExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Quick triggers reuse the audio configured in the Audio section above, including your selected file or default playback behavior.",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(
                                onClick = { openAccessibilitySettings(context) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bounceClick()
                            ) {
                                Text("Open Accessibility Settings")
                            }
                            Text(
                                text = "Enable the FakeCall accessibility service there to use the system accessibility button or shortcut for instant scheduling.",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Action: `com.upnp.fakeCall.TRIGGER` with extras `caller_name`, `caller_number`, and `delay`.",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    PreferenceCategoryHeader("Mailbox (IVR)")
                }

                item {
                    PreferenceCard(
                        icon = Icons.Outlined.Folder,
                        title = "Import mailbox XML",
                        subtitle = "Load a saved IVR tree.",
                        onClick = { ivrImportLauncher.launch(arrayOf("text/xml", "application/xml")) }
                    )
                }

                item {
                    PreferenceCard(
                        icon = Icons.Outlined.Refresh,
                        title = "Export mailbox XML",
                        subtitle = "Share your current IVR tree.",
                        onClick = { ivrExportLauncher.launch("fakecall_mailbox.xml") }
                    )
                }

                item {
                    PreferenceCard(
                        icon = Icons.Outlined.Add,
                        title = "Add menu node",
                        subtitle = "Create a new IVR menu.",
                        onClick = { showAddNodeDialog = true }
                    )
                }

                if (ivrNodes.isEmpty()) {
                    item {
                        Text(
                            text = "No mailbox nodes yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    items(ivrNodes) { node ->
                        MailboxNodeCard(
                            node = node,
                            nodes = ivrNodes,
                            isRoot = ivrConfig?.rootId == node.id,
                            onSetRoot = { viewModel.setIvrRoot(node.id) },
                            onSelectAudio = {
                                pendingAudioNodeId = node.id
                                ivrAudioPicker.launch(arrayOf("audio/*"))
                            },
                            onClearAudio = { viewModel.clearIvrNodeAudio(node.id) },
                            onAddMapping = { mappingNodeId = node.id },
                            onRemoveMapping = { digit -> viewModel.removeIvrRoute(node.id, digit) },
                            onDelete = { viewModel.removeIvrNode(node.id) }
                        )
                    }
                }

                item {
                    if (state.statusMessage.isNotBlank()) {
                        Surface(
                            tonalElevation = 1.dp,
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = state.statusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "About & Updates",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Current Version: ${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .bounceClick(onClick = { openUrl(context, GITHUB_REPO_URL) })
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "GitHub Repository",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "DDOneApps/FakeCall",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    if (isCheckingUpdates) return@FilledTonalButton
                                    coroutineScope.launch {
                                        isCheckingUpdates = true
                                        when (val result = viewModel.checkForUpdatesManual()) {
                                            is UpdateCheckResult.UpdateAvailable -> {
                                                updateDialogRelease = result.release
                                            }
                                            UpdateCheckResult.UpToDate -> {
                                                snackbarHostState.showSnackbar("You are on the latest version.")
                                            }
                                            UpdateCheckResult.RateLimited -> {
                                                snackbarHostState.showSnackbar("GitHub rate limit reached. Try again later.")
                                            }
                                            UpdateCheckResult.Unavailable -> {
                                                snackbarHostState.showSnackbar("Couldn't check for updates right now.")
                                            }
                                        }
                                        isCheckingUpdates = false
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bounceClick(enabled = !isCheckingUpdates)
                            ) {
                                if (isCheckingUpdates) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .width(18.dp)
                                            .height(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Checking...")
                                } else {
                                    Text("Check for Updates")
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    if (showAddNodeDialog) {
        AddNodeDialog(
            onDismiss = { showAddNodeDialog = false },
            onConfirm = { name ->
                viewModel.addIvrNode(name)
                showAddNodeDialog = false
            }
        )
    }

    val mappingNode = ivrNodes.firstOrNull { it.id == mappingNodeId }
    if (mappingNode != null) {
        MappingDialog(
            node = mappingNode,
            nodes = ivrNodes,
            onDismiss = { mappingNodeId = null },
            onConfirm = { digit, targetId ->
                viewModel.addIvrRoute(mappingNode.id, digit, targetId)
                mappingNodeId = null
            }
        )
    }

    val release = updateDialogRelease
    if (release != null) {
        AlertDialog(
            onDismissRequest = { updateDialogRelease = null },
            title = { Text("Update Available") },
            text = {
                Text("Version ${release.tagName} is available on GitHub Releases.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        openUrl(context, release.htmlUrl)
                        updateDialogRelease = null
                    },
                    modifier = Modifier.bounceClick()
                ) {
                    Text("Update Now")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { updateDialogRelease = null },
                    modifier = Modifier.bounceClick()
                ) {
                    Text("Later")
                }
            }
        )
    }
}

@Composable
private fun PreferenceCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun PreferenceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    trailingContent: (@Composable () -> Unit)? = {
        androidx.compose.material3.Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    },
    content: (@Composable () -> Unit)? = null
) {
    val cardModifier = if (onClick != null) {
        modifier.bounceClick(onClick = onClick)
    } else {
        modifier
    }

    ElevatedCard(
        modifier = cardModifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedIcon(
                    imageVector = icon,
                    contentDescription = null,
                    shape = CircleShape,
                    backgroundColor = contentColor.copy(alpha = 0.12f),
                    tint = contentColor
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
                trailingContent?.invoke()
            }
            content?.invoke()
        }
    }
}

@Composable
private fun MailboxNodeCard(
    node: IvrNode,
    nodes: List<IvrNode>,
    isRoot: Boolean,
    onSetRoot: () -> Unit,
    onSelectAudio: () -> Unit,
    onClearAudio: () -> Unit,
    onAddMapping: () -> Unit,
    onRemoveMapping: (Char) -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = node.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isRoot) {
                        androidx.compose.material3.AssistChip(
                            onClick = {},
                            label = { Text("Root menu") },
                            leadingIcon = { androidx.compose.material3.Icon(Icons.Outlined.Star, contentDescription = null) }
                        )
                    } else {
                        TextButton(onClick = onSetRoot, modifier = Modifier.bounceClick()) {
                            androidx.compose.material3.Icon(Icons.Outlined.StarBorder, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Set as root")
                        }
                    }
                }
                androidx.compose.material3.IconButton(onClick = onDelete, modifier = Modifier.bounceClick()) {
                    androidx.compose.material3.Icon(Icons.Outlined.Delete, contentDescription = "Delete node")
                }
            }

            PreferenceCard(
                icon = Icons.Outlined.MusicNote,
                title = "Node audio",
                subtitle = node.audioLabel.ifBlank { "No audio selected" },
                onClick = null,
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.IconButton(onClick = onSelectAudio, modifier = Modifier.bounceClick()) {
                            androidx.compose.material3.Icon(Icons.Outlined.Folder, contentDescription = "Select audio")
                        }
                        androidx.compose.material3.IconButton(onClick = onClearAudio, modifier = Modifier.bounceClick()) {
                            androidx.compose.material3.Icon(Icons.Outlined.Close, contentDescription = "Clear audio")
                        }
                    }
                }
            )

            Text(
                text = "Digit mappings (0 = back)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (node.routes.isEmpty()) {
                Text(
                    text = "No mappings yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    node.routes.toSortedMap().forEach { (digit, target) ->
                        val title = nodes.firstOrNull { it.id == target }?.title ?: "Unknown"
                        androidx.compose.material3.InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text("$digit → $title") },
                            trailingIcon = {
                                androidx.compose.material3.IconButton(
                                    onClick = { onRemoveMapping(digit) },
                                    modifier = Modifier.bounceClick()
                                ) {
                                    androidx.compose.material3.Icon(Icons.Outlined.Close, contentDescription = "Remove mapping")
                                }
                            },
                            shape = RoundedCornerShape(999.dp)
                        )
                    }
                }
            }

            Button(onClick = onAddMapping, modifier = Modifier.bounceClick()) {
                androidx.compose.material3.Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add mapping")
            }
        }
    }
}

@Composable
private fun AddNodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New mailbox node") },
        text = {
            ExpressiveTextField(
                value = name,
                onValueChange = { name = it },
                label = "Node title",
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, modifier = Modifier.bounceClick()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.bounceClick()) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MappingDialog(
    node: IvrNode,
    nodes: List<IvrNode>,
    onDismiss: () -> Unit,
    onConfirm: (Char, String) -> Unit
) {
    val availableTargets = nodes.filter { it.id != node.id }
    var selectedDigit by rememberSaveable { mutableStateOf('1') }
    var selectedTargetId by rememberSaveable { mutableStateOf(availableTargets.firstOrNull()?.id.orEmpty()) }
    var digitExpanded by remember { mutableStateOf(false) }
    var targetExpanded by remember { mutableStateOf(false) }
    val digitClickSource = remember { MutableInteractionSource() }
    val targetClickSource = remember { MutableInteractionSource() }
    val canConfirm = availableTargets.isNotEmpty() && selectedTargetId.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add mapping") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.OutlinedTextField(
                        value = selectedDigit.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Digit") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = digitExpanded) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = digitClickSource,
                                indication = null
                            ) { digitExpanded = !digitExpanded }
                    )
                    DropdownMenu(
                        expanded = digitExpanded,
                        onDismissRequest = { digitExpanded = false }
                    ) {
                        listOf('1','2','3','4','5','6','7','8','9','*','#').forEach { digit ->
                            DropdownMenuItem(
                                text = { Text(digit.toString()) },
                                onClick = {
                                    selectedDigit = digit
                                    digitExpanded = false
                                }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.OutlinedTextField(
                        value = availableTargets.firstOrNull { it.id == selectedTargetId }?.title.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target node") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = availableTargets.isNotEmpty()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = targetClickSource,
                                indication = null,
                                enabled = availableTargets.isNotEmpty()
                            ) { targetExpanded = !targetExpanded }
                    )
                    DropdownMenu(
                        expanded = targetExpanded,
                        onDismissRequest = { targetExpanded = false }
                    ) {
                        availableTargets.forEach { target ->
                            DropdownMenuItem(
                                text = { Text(target.title) },
                                onClick = {
                                    selectedTargetId = target.id
                                    targetExpanded = false
                                }
                            )
                        }
                    }
                }

                if (availableTargets.isEmpty()) {
                    Text(
                        text = "Add another node to create mappings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedDigit, selectedTargetId) },
                enabled = canConfirm,
                modifier = Modifier.bounceClick(enabled = canConfirm)
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.bounceClick()) {
                Text("Cancel")
            }
        }
    )
}

private fun openCallingAccounts(context: Context, viewModel: FakeCallViewModel) {
    val intent = viewModel.openCallingAccountsIntent()
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        if (it is ActivityNotFoundException) {
            // Ignore silently on unsupported devices.
        }
    }
}

private fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        if (it is ActivityNotFoundException) {
            // Ignore silently on unsupported devices.
        }
    }
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        if (it is ActivityNotFoundException) {
            // Ignore silently if no browser is available.
        }
    }
}

private const val GITHUB_REPO_URL = "https://github.com/DDOneApps/FakeCall"
