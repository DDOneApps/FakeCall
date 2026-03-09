package com.upnp.fakeCall.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.upnp.fakeCall.FakeCallViewModel
import com.upnp.fakeCall.ui.components.AnimatedIcon
import com.upnp.fakeCall.ui.components.ExpressiveButton
import com.upnp.fakeCall.ui.components.ExpressiveTextField
import com.upnp.fakeCall.ui.components.bounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: FakeCallViewModel,
    onBack: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.onAudioFileSelected(uri)
    }

    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            audioPickerLauncher.launch("audio/*")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    AnimatedIcon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.bounceClick(onBack)
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Phone Account",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AnimatedIcon(Icons.Rounded.Business, contentDescription = "Provider")
                                Text("Provider Name", style = MaterialTheme.typography.titleMedium)
                            }
                            AnimatedIcon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Forward")
                        }
                        ExpressiveTextField(
                            value = state.providerName,
                            onValueChange = viewModel::onProviderNameChange,
                            label = "Provider Name",
                            modifier = Modifier.fillMaxWidth()
                        )
                        ExpressiveButton(
                            text = "Save & Register",
                            icon = Icons.Rounded.PhoneInTalk,
                            onClick = viewModel::saveProvider,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.hasRequiredPermissions
                        )
                        ExpressiveButton(
                            text = "Enable in Calling Accounts",
                            icon = Icons.Rounded.PhoneInTalk,
                            onClick = { openCallingAccounts(context, viewModel) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.hasRequiredPermissions
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Audio",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AnimatedIcon(Icons.Rounded.AudioFile, contentDescription = "Audio")
                                Text("Audio File", style = MaterialTheme.typography.titleMedium)
                            }
                            AnimatedIcon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Forward")
                        }
                        Text(
                            text = "Selected: ${state.selectedAudioName.ifBlank { "Default" }}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ExpressiveButton(
                            text = "Select Audio",
                            icon = Icons.Rounded.AudioFile,
                            onClick = {
                                if (needsLegacyStoragePermission(context)) {
                                    legacyStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                } else {
                                    audioPickerLauncher.launch("audio/*")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        ExpressiveButton(
                            text = "Use Default Audio",
                            icon = Icons.Rounded.AudioFile,
                            onClick = viewModel::clearAudioSelection,
                            modifier = Modifier.fillMaxWidth()
                        )
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
                if (!state.hasRequiredPermissions) {
                    ExpressiveButton(
                        text = "Grant Phone Permissions",
                        icon = Icons.Rounded.PhoneInTalk,
                        onClick = onRequestPermissions,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun needsLegacyStoragePermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return false
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_EXTERNAL_STORAGE
    ) != PackageManager.PERMISSION_GRANTED
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
