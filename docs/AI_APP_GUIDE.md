# FakeCall AI Implementation Guide

This document describes the current implementation of the FakeCall Android app for future AI agents and contributors. It is based on the code in this repository at the time of writing and focuses on how the app behaves, how features are wired, where state is stored, and which areas require extra care.

## 1. Purpose And Mental Model

FakeCall is a native Android app that simulates incoming phone calls through Android's real Telecom framework. The central idea is not to draw a fake call screen inside the app. Instead, the app registers a `PhoneAccount` and asks `TelecomManager` to add a new incoming call. The user's normal phone UI then handles ringing, answer, reject, audio routing, and call history integration as much as Android and the default dialer allow.

The core behavior pipeline is:

1. A user or external trigger chooses caller details and timing.
2. The app verifies phone permissions and that its calling account is enabled.
3. For immediate calls, the app directly calls `TelecomManager.addNewIncomingCall`.
4. For delayed calls, the app schedules an exact `AlarmManager` broadcast.
5. The broadcast receiver registers/verifies the phone account and triggers the incoming call.
6. `FakeCallConnectionService` creates a `FakeConnection`.
7. `FakeConnection` controls ringing timeout, answer/reject/disconnect behavior, playback, IVR, alarm TTS, snooze, audio routing, and optional microphone recording.

The app is a single-module Android project using Kotlin, Jetpack Compose, Material 3, AndroidX Navigation Compose, lifecycle ViewModel/state flows, Android Telecom, AlarmManager, Accessibility Service, Quick Settings tiles, dynamic launcher shortcuts, MediaPlayer, MediaRecorder, and TextToSpeech.

## 2. Project Layout

Top-level structure:

```text
.
|-- app/                         Main Android application module
|-- gradle/libs.versions.toml     Version catalog
|-- build.gradle.kts              Root Gradle plugin declarations
|-- settings.gradle.kts           Includes :app
|-- README.md                     User-facing project overview
|-- metadata/                     Store/listing metadata
|-- Screenshots/                  Screenshots
|-- docs/                         AI and developer documentation
```

Important source directories:

```text
app/src/main/java/com/upnp/fakeCall/
|-- MainActivity.kt
|-- FakeCallViewModel.kt
|-- TelecomHelper.kt
|-- FakeCallConnectionService.kt
|-- FakeConnection.kt
|-- FakeCallAlarmScheduler.kt
|-- FakeCallAlarmReceiver.kt
|-- FakeCallSchedulerService.kt
|-- QuickTriggerManager.kt
|-- QuickTriggerTileServices.kt
|-- QuickTriggerAccessibilityService.kt
|-- ShortcutTriggerActivity.kt
|-- ExternalTriggerReceiver.kt
|-- AlarmModeModels.kt
|-- AlarmModeRepository.kt
|-- AlarmModeScheduler.kt
|-- AlarmModeAlarmReceiver.kt
|-- CallRecordingForegroundService.kt
|-- BatterySetupNavigator.kt
|-- UpdateChecker.kt
|-- DelayFormatter.kt
|-- ivr/
|   |-- IvrModels.kt
|   |-- IvrConfigStore.kt
|   |-- IvrStateMachine.kt
|-- ui/
    |-- FakeCallApp.kt
    |-- components/Components.kt
    |-- screens/DashboardScreen.kt
    |-- screens/SettingsScreen.kt
    |-- screens/OnboardingScreen.kt
    |-- screens/AlarmModeScreen.kt
    |-- theme/
```

Resources:

```text
app/src/main/res/
|-- values/strings.xml            Main string resources and feature copy
|-- values-*/strings.xml          Localized strings
|-- raw/fake_voice.mp3            Bundled audio file; current call playback uses stored URIs and does not reference R.raw directly
|-- xml/accessibility_service_config.xml
|-- xml/backup_rules.xml
|-- xml/data_extraction_rules.xml
|-- drawable/                     Launcher icons, quick trigger icon, widget-style drawables
```

The code currently contains string resources and drawables for widget behavior, but there is no app widget provider class or manifest receiver in the current tree.

## 3. Build Configuration

Project:

- Root project name: `Fakecall`
- Included module: `:app`
- Application id and namespace: `com.upnp.fakeCall`
- Minimum SDK: 24
- Compile SDK: 36
- Target SDK: 36
- Version code: 24
- Version name: `2.4`
- Java compatibility: 11
- Compose enabled through the Kotlin Compose plugin
- Release minification is disabled

Main dependencies:

- Android Gradle Plugin `9.0.1`
- Kotlin Compose plugin `2.0.21`
- AndroidX Core KTX
- Lifecycle runtime, runtime compose, ViewModel KTX, ViewModel Compose
- Activity Compose
- Navigation Compose
- Compose BOM `2024.09.00`
- Compose UI, UI graphics, UI tooling
- Material 3
- Material icons extended
- JUnit, AndroidX test, Espresso, Compose UI tests

Test coverage is currently placeholder-only:

- `ExampleUnitTest.kt` checks `2 + 2 == 4`
- `ExampleInstrumentedTest.kt` checks package name

## 4. Android Manifest And Platform Surface

Declared permissions:

- `READ_PHONE_STATE`
- `READ_PHONE_NUMBERS`
- `READ_CONTACTS`
- `RECORD_AUDIO`
- `INTERNET`
- `MODIFY_AUDIO_SETTINGS`
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MICROPHONE`
- `SCHEDULE_EXACT_ALARM`
- `READ_EXTERNAL_STORAGE` with `maxSdkVersion=32`

Declared components:

- `MainActivity`
  - Launcher activity.
  - Also handles `android.service.quicksettings.action.QS_TILE_PREFERENCES` so Quick Settings preferences can open the app.

- `ShortcutTriggerActivity`
  - Exported, no-history, excluded from recents.
  - Used by dynamic launcher shortcuts to execute quick trigger presets.

- `FakeCallConnectionService`
  - Exported and protected by `android.permission.BIND_TELECOM_CONNECTION_SERVICE`.
  - Has `android.telecom.ConnectionService` intent filter.
  - Creates `FakeConnection` for incoming Telecom calls.

- `FakeCallSchedulerService`
  - Foreground short service for coroutine-based countdown scheduling.
  - The current ViewModel path uses exact alarms for scheduled calls; this service still exists and can schedule a delayed call while showing a notification.

- `CallRecordingForegroundService`
  - Foreground service with microphone type.
  - Keeps a notification visible while `FakeConnection` records microphone audio.

- `QuickTriggerAccessibilityService`
  - Exported and protected by `android.permission.BIND_ACCESSIBILITY_SERVICE`.
  - Requests the accessibility button and uses it as a quick trigger entry point.

- `QuickTriggerTile1Service` through `QuickTriggerTile5Service`
  - Exported Quick Settings tile services.
  - One tile slot per preset.

- `FakeCallAlarmReceiver`
  - Non-exported receiver for one-off/delayed fake calls.

- `AlarmModeAlarmReceiver`
  - Non-exported receiver for alarm-mode calls and snooze/repeat scheduling.

- `ExternalTriggerReceiver`
  - Exported receiver.
  - Accepts `com.upnp.fakeCall.TRIGGER` and legacy `com.ddone.fakecall.TRIGGER`.
  - Used by Tasker, MacroDroid, ADB, etc.

## 5. Main Runtime Architecture

### 5.1 Activity And Compose Entry

`MainActivity` enables edge-to-edge system bars, determines whether the app should start in settings, and sets Compose content:

- `FakecallTheme`
- `FakeCallApp(startInSettings = startInSettings)`

`startInSettings` is true when the incoming intent action is either:

- `com.upnp.fakeCall.action.OPEN_SETTINGS`
- `android.service.quicksettings.action.QS_TILE_PREFERENCES`

### 5.2 Navigation

`FakeCallApp` owns the `NavHost` and top-level route graph:

- `onboarding`
- `dashboard`
- `alarm`
- `alarm_create`
- `alarm_edit/{alarmId}`
- `settings`

Start destination:

- Settings if launched from settings intent and onboarding is complete.
- Dashboard if onboarding is complete.
- Onboarding otherwise.

Bottom mode navigation is visible only on:

- `dashboard`
- `alarm`

The mode bar switches between normal call mode and alarm mode. It is implemented as a custom Compose surface, not the standard `NavigationBar`.

### 5.3 Permission Bootstrapping

`FakeCallApp` requests these runtime permissions:

- `READ_PHONE_STATE`
- `READ_PHONE_NUMBERS`
- `RECORD_AUDIO`

`READ_CONTACTS` is declared in the manifest but is not included in the top-level `RequiredPermissions` array. Contact picking uses Android contact pick intents and contact resolver access; any future contact-related change should check whether and when `READ_CONTACTS` needs to be requested.

On launch, `FakeCallApp`:

1. Computes whether all required permissions are granted.
2. Calls `viewModel.onPermissionStateChanged(granted)`.
3. If not granted, launches the permission request.

When permissions become available, the ViewModel registers or updates the Telecom phone account and refreshes provider status.

## 6. ViewModel And App State

`FakeCallViewModel` is the central state owner. It extends `AndroidViewModel` because it needs an application context for prefs, system services, resources, URI grants, and schedulers.

The public state is:

```kotlin
val uiState: StateFlow<FakeCallUiState>
```

`FakeCallUiState` includes:

- Onboarding completion
- Provider name and provider enabled state
- Caller name/number
- Caller input mode: manual or contact
- Selected contact
- Pinned and recent contacts
- Delay and schedule kind
- Custom countdown/exact-time values
- Saved custom timing presets
- IVR config
- Selected audio URI/name
- Required permission status
- Timer running state and trigger timestamp
- Status message
- Recording enabled and recording folder label
- Quick trigger defaults
- Quick trigger presets
- Quick trigger default preset slot
- Normal call ring timeout
- Alarm ring timeout
- Alarm mode items
- MP3 IVR mode settings
- Startup update info

On initialization the ViewModel:

1. Loads most state from `SharedPreferences`.
2. Loads IVR XML through `IvrConfigStore`.
3. Loads quick trigger defaults and presets through `QuickTriggerManager`.
4. Loads alarm mode items through `AlarmModeRepository`.
5. Starts a coroutine loop every second to:
   - sync timer running state
   - refresh provider status if permissions are present
   - sync alarm mode state
6. Starts a startup update check coroutine.
7. Refreshes launcher shortcuts.

The ViewModel is deliberately broad. Most user actions from Compose screens call direct ViewModel methods rather than separate use-case classes.

## 7. SharedPreferences Storage

The app uses `SharedPreferences` heavily. There is no Room database or DataStore.

Main prefs file:

```text
fake_call_prefs
```

IVR prefs file:

```text
fake_call_ivr
```

Important `fake_call_prefs` keys used across classes:

```text
provider_name
caller_name
caller_number
caller_input_mode
selected_contact
pinned_contacts
recent_contacts
delay_seconds
schedule_kind
custom_countdown_minutes
custom_countdown_seconds
custom_exact_hour
custom_exact_minute
custom_presets
timer_ends_at
quick_trigger_active_preset_slot
audio_uri
audio_name
recording_enabled
recordings_tree_uri
recordings_folder_name
mp3_ivr_mode_enabled
mp3_ivr_folder_uri
mp3_ivr_folder_name
onboarding_complete
quick_trigger_preset_name
call_ring_timeout_seconds
alarm_ring_timeout_seconds
quick_trigger_caller_name
quick_trigger_caller_number
quick_trigger_delay_seconds
quick_trigger_use_custom_audio
quick_trigger_custom_audio_uri
quick_trigger_custom_audio_name
quick_trigger_presets_v1
quick_trigger_default_preset_slot
alarm_mode_items
```

Runtime override keys:

```text
runtime_audio_override_enabled
runtime_audio_override_uri
runtime_audio_override_name
runtime_message_mode
runtime_tts_message
runtime_speaker_default
runtime_snooze_enabled
runtime_snooze_minutes
runtime_snooze_alarm_id
runtime_snooze_caller_name
runtime_snooze_caller_number
runtime_snooze_provider_name
```

Runtime overrides are a critical cross-component handoff. Receivers write them immediately before calling Telecom. `FakeConnection` reads and clears them when it is constructed. Any new feature that relies on per-call metadata must be careful because these values are global and transient, not scoped by a call id.

Important `fake_call_ivr` key:

```text
ivr_config_xml
```

## 8. Telecom Integration

### 8.1 Phone Account

`TelecomHelper` wraps Telecom operations:

- Builds a `PhoneAccountHandle` with:
  - component: `FakeCallConnectionService`
  - account id: `fake_call_provider_account`

- Registers a `PhoneAccount` with:
  - label from settings/default provider name
  - `PhoneAccount.CAPABILITY_CALL_PROVIDER`
  - supported URI scheme: `tel`

- Checks enabled state:
  - `telecomManager.getPhoneAccount(accountHandle())?.isEnabled == true`

The account must be enabled by the user in Android Calling Accounts. Registering it is not enough.

### 8.2 Triggering A Call

`TelecomHelper.triggerIncomingCall(...)`:

1. Reads the appropriate ring timeout from prefs:
   - normal call: `call_ring_timeout_seconds`, default 45
   - alarm call: `alarm_ring_timeout_seconds`, default 60
2. Builds incoming call extras:
   - `extra_fake_caller_name`
   - `extra_fake_caller_number`
   - `extra_fake_call_source`
   - `extra_ring_timeout_seconds`
3. Builds Telecom extras with:
   - `TelecomManager.EXTRA_INCOMING_CALL_ADDRESS`
   - `TelecomManager.EXTRA_INCOMING_CALL_EXTRAS`
4. Calls `telecomManager.addNewIncomingCall(accountHandle(), extras)`.

### 8.3 Creating The Connection

`FakeCallConnectionService.onCreateIncomingConnection(...)` extracts:

- caller number from `request.address.schemeSpecificPart` or extras
- caller name from extras
- source from extras
- ring timeout from extras

It returns:

```kotlin
FakeConnection(context = this, callerName, callerNumber, ringTimeoutSeconds)
```

## 9. FakeConnection Behavior

`FakeConnection` is the runtime representation of the fake call. It extends `android.telecom.Connection`.

Initialization:

- Sets the address to `tel:<callerNumber>`
- Sets caller display name to caller name or number
- Adds mute capability
- Enables VoIP audio mode
- Transitions to initializing and ringing
- Starts a ring timeout if configured

Answer:

1. Cancels ring timeout.
2. Marks `wasAnswered = true`.
3. Calls `setActive()`.
4. Sets `AudioManager.MODE_IN_COMMUNICATION`.
5. Applies default route:
   - earpiece by default
   - speaker if alarm runtime override says speaker
6. Starts voice playback.
7. Starts microphone recording if enabled and permitted.

Reject:

- Cancels ring timeout.
- Disconnects with `DisconnectCause.REJECTED`.
- If not answered and snooze is enabled, `disconnectWithCause` can trigger snooze.

Disconnect:

- Cancels timeout.
- Disconnects with `DisconnectCause.LOCAL`.

Abort:

- Cancels timeout.
- Disconnects with `DisconnectCause.CANCELED`.

Audio route changes:

- Maintains microphone state if recording.
- Keeps `MODE_IN_COMMUNICATION`.
- Applies Bluetooth, wired headset, speaker, or earpiece route using `AudioManager`.

DTMF:

- If digit `1` is pressed and snooze is enabled:
  - schedule snooze
  - disconnect locally
- Else MP3-folder IVR handles digits first if active.
- Else custom IVR state machine handles digits.
- Digit `0` in custom IVR returns to the IVR root.

Disconnect cleanup:

- Cancels ring timeout.
- Optionally schedules snooze if the call was not answered.
- Stops media playback.
- Shuts down TTS.
- Clears folder navigation stack.
- Stops microphone recording and exports/deletes temp file.
- Resets audio mode to normal.
- Calls `setDisconnected(...)`.
- Calls `destroy()`.

### 9.1 Ring Timeout

Ring timeout seconds:

- `0` means unlimited ringing.
- Positive values schedule a handler callback.
- If the user does not answer before timeout, the connection disconnects with `DisconnectCause.MISSED`.

Normal calls and alarm calls use separate timeout prefs.

### 9.2 Playback Priority

When the call is answered, `startVoicePlayback()` chooses content in this order:

1. Alarm runtime TTS override if `runtime_message_mode == tts`.
2. MP3-folder IVR mode if enabled.
3. Custom IVR root node audio if an IVR config exists.
4. Per-call runtime custom audio override.
5. Global selected audio URI from settings.
6. No playback if no URI is available.

Audio playback uses `MediaPlayer`, `USAGE_VOICE_COMMUNICATION`, and loops by default except MP3-folder item playback, which returns to the menu on completion.

### 9.3 MP3 Folder IVR Mode

MP3-folder IVR is enabled by prefs:

- `mp3_ivr_mode_enabled`
- `mp3_ivr_folder_uri`
- `mp3_ivr_folder_name`

Behavior:

- The selected folder is treated as a navigable menu.
- Subfolders and audio files are listed.
- Directories sort before files, then by localized lowercase display name.
- Up to 9 items are announced per page.
- TTS announces menu choices.

DTMF mapping:

- `1` through `9`: open/play the corresponding item on the current page
- `#`: next page
- `*`: previous page
- `0`: back one folder, or announce root-folder message if already at root

Audio file recognition:

- MIME type starts with `audio/`, or
- extension is `.mp3`, `.wav`, `.m4a`, `.aac`, `.ogg`, or `.flac`

### 9.4 Custom IVR Mode

Custom IVR is stored as XML by `IvrConfigStore`.

`IvrConfig`:

- root node id
- map of node id to `IvrNode`

`IvrNode`:

- id
- title
- audio URI
- audio label
- route map from DTMF char to target node id

`IvrStateMachine`:

- Tracks `currentNodeId`.
- `currentNode()` returns the active node.
- `handleDtmf(digit)`:
  - digit `0` moves to root
  - other digits use current node routes
  - returns the new node or null

When the active IVR node has audio, `FakeConnection` switches playback to that node's audio.

### 9.5 TTS

TTS is used for:

- Alarm app-voice messages
- MP3-folder IVR menu announcements and errors

The engine is lazily initialized. A pending message is stored if TTS initialization is still in progress. TTS language is set to the default locale. On Android Lollipop and newer, TTS audio attributes are set to voice communication.

### 9.6 Recording

Recording starts only after answering a call.

Requirements:

- `recording_enabled` preference must be true.
- `RECORD_AUDIO` permission must be granted.

Runtime behavior:

1. Start `CallRecordingForegroundService`.
2. Create a timestamped filename: `fake_call_yyyyMMdd_HHmmss.m4a`.
3. Record to a temp file in `cacheDir/recordings_tmp`.
4. Use `MediaRecorder`:
   - source: `MIC`
   - format: `MPEG_4`
   - encoder: `AAC`
   - bitrate: 256000
   - sample rate: 48000
   - channels: 1
5. On stop, export the temp file.

Recording destination priority:

1. User-selected document tree URI from `recordings_tree_uri`.
2. `MediaStore.Downloads` relative path `Downloads/FakeCall` on Android Q+.
3. Internal app storage `filesDir/recordings`.

If recording stop or export fails, the temp file and destination placeholder are cleaned up.

## 10. Normal Call Scheduling

Normal dashboard calls are managed primarily by `FakeCallViewModel.scheduleFakeCall()`.

Validation before scheduling:

- Required phone permissions must be present.
- Calling account must be enabled.
- Caller number must not be blank.
- If caller input mode is contact, a contact must be selected.
- For exact-time scheduling, exact alarms must be allowed.

Schedule kinds:

- `PRESET`
  - Uses `selectedDelaySeconds`.
- `CUSTOM_COUNTDOWN`
  - Uses custom minutes and seconds.
- `CUSTOM_EXACT`
  - Computes the next occurrence of selected hour/minute. If the time has already passed today, schedules tomorrow.

Immediate call path:

1. Register/update phone account.
2. If account is enabled, call `TelecomHelper.triggerIncomingCall`.
3. Clear `timer_ends_at`.
4. Update status.

Delayed call path:

1. Compute `triggerAtMillis`.
2. Cancel any existing one-off fake call alarm.
3. Schedule exact alarm through `FakeCallAlarmScheduler`.
4. Save `timer_ends_at`.
5. Update UI state to running.

Cancel path:

1. Cancel `FakeCallAlarmScheduler`.
2. Cancel `FakeCallSchedulerService`.
3. Remove `timer_ends_at`.
4. Clear active quick trigger slot.
5. Refresh Quick Settings tiles.
6. Update status.

`FakeCallSchedulerService` is an alternate foreground-service countdown path. It registers the account, waits using coroutine `delay`, triggers Telecom if enabled, and stops. Current quick trigger and ViewModel delayed paths use `FakeCallAlarmScheduler`, not this service.

## 11. One-Off Alarm Receiver

`FakeCallAlarmReceiver` fires for normal delayed calls.

On receive:

1. Reads optional runtime audio override extras.
2. Writes or clears runtime audio override prefs.
3. Removes `timer_ends_at`.
4. Sets `quick_trigger_active_preset_slot` to `-1`.
5. Refreshes Quick Settings tiles.
6. Reads caller name, caller number, provider name.
7. Returns early if caller number is blank.
8. Registers/updates phone account.
9. If enabled, triggers incoming call with source `CALL`.

The receiver is non-exported; external apps trigger through `ExternalTriggerReceiver`, not this receiver.

## 12. Quick Triggers

Quick triggers are shared by:

- Settings defaults
- Quick Settings tiles
- Launcher shortcuts
- Accessibility shortcut
- External broadcast API fallback values

`QuickTriggerManager` is the central API.

### 12.1 Defaults

`QuickTriggerDefaults` stores:

- caller name
- caller number
- delay seconds
- whether to override audio
- custom audio URI/name

If quick-trigger caller name/number/delay are missing, defaults fall back to the main caller name/number/delay preferences.

### 12.2 Presets

Up to 5 `QuickTriggerPreset` entries are stored in JSON under `quick_trigger_presets_v1`.

Each preset has:

- id
- title
- caller name
- caller number
- delay seconds
- optional custom audio flag
- custom audio URI/name

Slots are positional: preset at index 0 is slot 1, etc. Removing a preset shifts later slots down. Code updates active/default slot references after removal.

### 12.3 Execution

Execution starts from one of:

- `executePreset(context, slot)`
- `executeFromInputs(context, callerName, callerNumber, delaySeconds, presetSlot)`
- `executeFromDefaults(context)`

Request resolution:

- Missing caller name/number/delay values fall back to quick trigger defaults.
- Provider name comes from prefs or default provider string.
- Blank resolved caller number fails.

Execution behavior:

- Delay `0` or nearly immediate:
  - cancel existing fake call alarm
  - write runtime audio override if present
  - register/update phone account
  - trigger Telecom if account enabled
  - save caller fields and clear timer
- Positive delay:
  - cancel existing fake call alarm
  - clear current runtime audio override
  - schedule exact alarm
  - save caller fields and `timer_ends_at`
  - save active preset slot if any

Return value:

- `IMMEDIATE`
- `SCHEDULED`
- `FAILED`

### 12.4 Launcher Shortcuts

On Android 7.1+ (`N_MR1`), presets become dynamic shortcuts:

- Shortcut id: `quick_trigger_preset_<slot>`
- Activity: `ShortcutTriggerActivity`
- Action: `com.upnp.fakeCall.action.TRIGGER_PRESET`
- Extra: `preset_slot`

Shortcut labels are shortened:

- short label max 10 chars
- title max 30 chars when saved

### 12.5 Quick Settings Tiles

There are five tile service classes, all extending `BaseQuickTriggerTileService`.

Tile states:

- No preset in that slot: `Tile.STATE_UNAVAILABLE`
- Preset exists and is active scheduled slot: `Tile.STATE_ACTIVE`
- Preset exists but not active: `Tile.STATE_INACTIVE`

Tile click behavior:

- If locked, runs after unlock.
- Executes the preset.
- Shows toast for immediate, scheduled, or missing preset.
- Refreshes the tile.

### 12.6 Accessibility Shortcut

`QuickTriggerAccessibilityService` registers an accessibility button callback on Android O+.

When clicked:

1. Loads defaults and default preset slot.
2. Calls `QuickTriggerManager.executeFromDefaults`.
3. Shows a toast based on result.

The service does not inspect accessibility events or window content.

### 12.7 External Broadcast API

`ExternalTriggerReceiver` is exported and accepts:

- `com.upnp.fakeCall.TRIGGER`
- `com.ddone.fakecall.TRIGGER`

Extras:

- `caller_name` as optional string
- `caller_number` as optional string
- `delay` as optional int seconds

Missing extras fall back to quick trigger defaults. Failure shows a toast.

ADB example:

```bash
adb shell am broadcast -a com.upnp.fakeCall.TRIGGER -p com.upnp.fakeCall --es caller_name "Boss" --es caller_number "+49123456789" --ei delay 30
```

## 13. Alarm Mode

Alarm mode is separate from normal dashboard calls. It schedules call-based alarms/reminders at exact clock times, optionally repeating on weekdays, playing TTS or custom audio, using a speaker default, and allowing snooze.

### 13.1 Data Model

`AlarmModeItem`:

- id
- caller name
- caller number
- hour
- minute
- repeat days
- message mode
- TTS message
- custom audio URI/name
- snooze enabled
- snooze minutes
- speaker default
- enabled
- next trigger timestamp

`AlarmModeDraft` mirrors editable fields used by the create/edit UI.

Message modes:

- `APP_VOICE_TTS`
- `CUSTOM_AUDIO`

Speaker defaults:

- `EARPIECE`
- `SPEAKER`

Repeat days use `java.time.DayOfWeek.value`:

- Monday = 1
- Tuesday = 2
- Wednesday = 3
- Thursday = 4
- Friday = 5
- Saturday = 6
- Sunday = 7

### 13.2 Persistence

`AlarmModeRepository` stores all alarms as a JSON array in `fake_call_prefs` under `alarm_mode_items`.

Parsing is defensive:

- skips id `0`
- skips blank caller numbers
- coerces hour/minute and snooze range
- ignores invalid repeat days
- defaults invalid enum values

Items are sorted by `hour * 60 + minute` when saved.

### 13.3 Scheduling

`AlarmModeScheduler` computes the next trigger:

- No repeat days:
  - schedule today at hour/minute if future
  - otherwise tomorrow
- Repeat days:
  - schedule the next date whose day-of-week is in the repeat set
  - if today's time has passed, start checking from tomorrow

Scheduling uses:

- `AlarmManager.setExactAndAllowWhileIdle` on Android M+
- `AlarmManager.setExact` below M
- exact alarm permission check on Android S+

Request code:

```text
40000 + (abs(alarmId) % 1000000000)
```

### 13.4 Alarm Receiver

`AlarmModeAlarmReceiver`:

1. Reads the alarm id and caller number. Returns if invalid.
2. Reads provider name from prefs.
3. Reads alarm message mode, TTS, custom audio, snooze, and speaker settings from intent extras.
4. Writes runtime overrides for `FakeConnection`.
5. Registers/updates phone account.
6. If account is enabled, triggers incoming call with source `ALARM`.
7. Handles repeat behavior:
   - no repeat days: disable the alarm, set next trigger to 0, cancel pending intent
   - repeat days: compute/schedule next trigger and update repository

Important: alarm-mode TTS/custom-audio/snooze settings are passed to the connection through global runtime override prefs. `FakeConnection.consumeRuntimeOverrides()` clears those keys as soon as the connection is created.

### 13.5 Snooze

Snooze can be triggered by:

- Rejecting/missing a ringing alarm-mode call before it is answered
- Pressing DTMF `1` when snooze is enabled

`FakeConnection.triggerSnooze()`:

1. Guards against duplicate snooze.
2. Uses runtime override caller number and snooze settings.
3. Loads the original alarm item if `runtime_snooze_alarm_id` is non-zero.
4. Creates a one-time alarm copy with a new id and empty repeat days.
5. Schedules it for now plus snooze minutes.

Snooze alarms are scheduled but are not inserted into `AlarmModeRepository` in `triggerSnooze()`. The receiver receives all required fields from the PendingIntent extras generated by `AlarmModeScheduler.scheduleSnooze`.

## 14. UI Screens

### 14.1 Onboarding

`OnboardingScreen` guides initial setup:

- Feature overview
- Phone and microphone permissions
- Calling account setup
- Exact alarm setup
- Battery optimization and OEM background/autostart setup

Onboarding completion is stored in `onboarding_complete`.

### 14.2 Dashboard

`DashboardScreen` is the normal fake-call scheduling surface.

It includes:

- Main title and settings button
- Update/status surfaces
- Caller input card
- Schedule state card
- Timing controls
- Audio preview
- Bottom action bar
- Custom call sheet
- Countdown and exact-time pickers
- Custom timing preset handling

It calls ViewModel methods for all state changes and scheduling.

### 14.3 Settings

`SettingsScreen` is the main configuration hub.

Feature areas include:

- Provider setup
- Calling account status/action
- Audio file selection and default audio behavior
- Normal and alarm ring timeout
- Microphone recording toggle
- Recording folder selection/reset
- Automation and quick trigger defaults
- Quick trigger presets and per-preset audio
- Accessibility settings entry
- IVR custom mode and MP3-folder mode
- IVR import/export
- Add/delete/configure IVR nodes and routes
- About/update check

### 14.4 Alarm Mode

`AlarmOverviewScreen` lists alarms and allows:

- add alarm
- edit alarm
- delete alarm
- enable/disable alarm

`AlarmCreateScreen` handles both create and edit:

- caller name/number
- exact clock time
- repeat weekdays
- message mode
- TTS message
- custom audio selection
- snooze settings
- speaker default

## 15. Contacts

Caller input can be manual or contact-based.

Contact data model:

- id
- display name
- phone number
- photo URI
- avatar base64

The ViewModel resolves contacts from a picked URI by trying:

1. Phone projection on the URI.
2. Contact projection plus a lookup for primary phone number.

It also tries to encode a 128x128 PNG avatar as Base64, using direct photo URI first and contact lookup photo second.

Pinned and recent contacts are persisted as JSON arrays. Recent contacts are deduplicated and pruned:

- If pinned contacts exist, only one recent item is retained.
- Otherwise up to three recent items are retained by `pruneRecentContacts`.

Constants also define broader limits:

- `MAX_RECENT_CONTACTS = 12`
- `MAX_PINNED_CONTACTS = 8`

If changing contact behavior, inspect both UI pruning and ViewModel constants because not all declared limits are necessarily applied in the same place.

## 16. Audio And URI Permissions

The app stores persistent string versions of user-selected URIs.

User-selected audio:

- Stored as `audio_uri` and `audio_name`.
- Used as the fallback playback source for normal calls.

Per-preset audio:

- Stored inside quick trigger preset JSON.
- Copied into runtime override prefs before the call.

Alarm custom audio:

- Stored inside alarm JSON and alarm PendingIntent extras.
- Copied into runtime override prefs by `AlarmModeAlarmReceiver`.

MP3 IVR folder:

- Stored as `mp3_ivr_folder_uri` and `mp3_ivr_folder_name`.
- `FakeConnection` queries children through `DocumentsContract`.

Recording folder:

- Stored as `recordings_tree_uri` and `recordings_folder_name`.
- `FakeConnection` creates output documents with `DocumentsContract.createDocument`.

Any code that accepts a URI should ensure the app takes persistable URI permissions when the URI comes from Storage Access Framework. The ViewModel currently has methods such as `onAudioFileSelected`, `onRecordingFolderSelected`, and `onMp3IvrFolderSelected` that are responsible for this handoff.

## 17. Update Checking

`UpdateChecker` calls:

```text
https://api.github.com/repos/DDOneApps/FakeCall/releases/latest
```

It sets:

- `Accept: application/vnd.github+json`
- `User-Agent: FakeCall-Android`

Response handling:

- 200: parse `tag_name` and `html_url`
- 403: rate limited
- other: unavailable

Version comparison:

- Strips leading `v` or `V`
- Splits on non-digits
- Compares numeric parts with missing parts treated as 0

`FakeCallApp` shows a top update banner when `startupUpdate` is present.

## 18. Battery Optimization Helpers

`BatterySetupNavigator` detects ROM families:

- Xiaomi/Redmi/Poco/HyperOS
- OnePlus/OxygenOS
- Oppo/Realme/ColorOS
- Samsung/One UI
- Generic

It can open:

- Android battery optimization exemption request/settings
- OEM-specific autostart/background settings
- App details as fallback

This is used in onboarding to help scheduled calls work while the screen is off or the app is backgrounded.

## 19. Localization

The project has many `values-*/strings.xml` resource folders and a Crowdin workflow. New user-visible strings should be added to `app/src/main/res/values/strings.xml`; if they need to avoid translation, mark `translatable="false"`.

The default strings include newer alarm-mode and widget-related strings. Be careful to keep localized resource completeness in mind, especially when adding required strings referenced from code.

## 20. Known Current Gaps And Implementation Notes

These are not necessarily bugs, but they matter for future AI work:

- The repo has placeholder tests only. Behavioral changes to scheduling, alarms, IVR, or recording should add real unit tests where possible.
- `SharedPreferences` keys are duplicated as private constants across several classes. Renaming a key in one place can silently break another feature.
- Runtime override prefs are global and transient. Concurrent calls or overlapping scheduled triggers could race because overrides are not scoped by call id.
- `FakeCallSchedulerService` still exists but current delayed scheduling paths use exact alarms. Confirm intended behavior before refactoring it away.
- Widget resources/strings exist, but no current app widget component is declared in the manifest.
- Contact access and `READ_CONTACTS` permission should be revisited before expanding contact features.
- Android 12+ exact alarm permission is essential for scheduled calls. Always preserve checks and user guidance.
- Telecom behavior varies by OEM, default dialer, and Android version. Test on physical devices when changing account registration or connection behavior.
- `RECORD_AUDIO` is requested at startup because recording and voice-call behavior depend on it. If making recording optional at permission time, test answered-call playback and recording startup thoroughly.
- Quick trigger preset slots are positional. Deleting or reordering presets affects launcher shortcuts and Quick Settings tile semantics.
- Alarm-mode snooze schedules a one-time PendingIntent but does not persist a visible alarm item. That appears intentional for transient snooze calls.
- `UpdateChecker` uses network calls with plain `HttpURLConnection`; failures are intentionally collapsed into `Unavailable`.

## 21. Common Change Recipes

### Add A New Setting

1. Add a field to `FakeCallUiState` if the UI needs to observe it.
2. Add a prefs key in `FakeCallViewModel` or a more appropriate owner.
3. Load the value in initial state.
4. Add an `on...Change` method that writes prefs and updates state.
5. Add UI in `SettingsScreen`.
6. Add string resources.
7. If another runtime component needs the setting, either:
   - read the stable pref directly, or
   - pass a one-time runtime override if it is per-call.

### Add Per-Call Metadata

Prefer passing data through Telecom incoming call extras if it only affects connection creation. If the data needs to be consumed by `FakeConnection` but is not currently in `FakeCallConnectionService`, consider adding it to `TelecomHelper.triggerIncomingCall` extras and reading it in the service.

Avoid adding more global runtime override prefs unless the metadata is truly transient and there is no cleaner Telecom extra path.

### Add A Quick Trigger Source

Use `QuickTriggerManager` rather than duplicating scheduling logic.

Call one of:

- `executePreset(context, slot)`
- `executeFromDefaults(context)`
- `executeFromInputs(context, callerName, callerNumber, delaySeconds)`

Then map `QuickTriggerExecution` to the source-specific feedback UI.

### Add A New Alarm Option

1. Add the field to `AlarmModeItem` and `AlarmModeDraft`.
2. Update `AlarmModeRepository` JSON save/parse.
3. Update `AlarmModeScheduler.scheduleAt` extras.
4. Update `AlarmModeAlarmReceiver` extra parsing.
5. Decide how `FakeConnection` receives the setting:
   - direct Telecom extra, preferred for connection-scoped data
   - runtime override pref, consistent with current alarm settings
6. Update `AlarmCreateScreen` and `AlarmOverviewScreen` if visible.
7. Add strings and tests for scheduling/persistence.

### Add Or Change IVR Behavior

Custom IVR:

- Update `IvrNode`/`IvrConfig` if data shape changes.
- Update XML serialization and parser.
- Update `IvrStateMachine`.
- Update settings UI import/export and node editor.
- Update `FakeConnection.onPlayDtmfTone`.

MP3-folder IVR:

- Update folder listing/filtering in `FakeConnection`.
- Update TTS strings.
- Preserve page navigation semantics unless intentionally changing the user contract.

### Change Recording

Touchpoints:

- `FakeConnection.maybeStartMicRecording`
- `FakeConnection.createRecordingDestination`
- `FakeConnection.stopAndReleaseRecording`
- `CallRecordingForegroundService`
- Settings recording toggle/folder UI
- Manifest foreground service permissions

Always test:

- answer and hang up normally
- reject before answer
- recorder stop failure path
- Android Q+ MediaStore destination
- SAF selected folder destination
- no `RECORD_AUDIO` permission

## 22. Suggested Tests To Add

High-value unit tests:

- `AlarmModeScheduler.computeNextTriggerAtMillis`
  - one-time future today
  - one-time past schedules tomorrow
  - repeat day today before time
  - repeat day today after time
  - multi-day repeats

- `AlarmModeRepository`
  - JSON round trip
  - invalid enum fallback
  - invalid repeat day filtering
  - blank caller number skipped

- `QuickTriggerManager`
  - defaults fallback
  - preset save limit
  - removing slots shifts active/default slots
  - immediate vs scheduled execution branches with fakes or wrappers

- `IvrConfigStore`
  - XML round trip
  - missing root falls back to first node
  - route parsing

- `IvrStateMachine`
  - digit routing
  - `0` returns to root
  - unknown digit no-op

- `DelayFormatter`
  - zero, seconds-only, minutes-only, mixed minutes/seconds

Instrumentation/manual tests:

- Calling account registration and enabled-state flow
- Real incoming fake call UI through the default phone app
- Answer playback
- Reject/missed timeout
- Alarm TTS call
- Alarm custom audio call
- Snooze by reject and by DTMF `1`
- Quick Settings tile execution
- Launcher shortcut execution
- Accessibility button execution
- External ADB broadcast
- Recording export to Downloads and SAF folder
- MP3-folder IVR navigation

## 23. Important Files By Responsibility

```text
MainActivity.kt
  Compose entry point and start-in-settings intent handling.

ui/FakeCallApp.kt
  Navigation graph, permission launcher, update banner, bottom mode switch.

FakeCallViewModel.kt
  Main app state, prefs loading/saving, scheduling, provider status, contact handling,
  IVR config operations, quick trigger settings, alarm mode orchestration.

TelecomHelper.kt
  PhoneAccount registration/status and Telecom incoming call trigger.

FakeCallConnectionService.kt
  Converts Telecom incoming connection requests into FakeConnection instances.

FakeConnection.kt
  Call lifecycle, ringing timeout, audio playback, IVR, TTS, snooze, recording,
  audio route handling, cleanup.

FakeCallAlarmScheduler.kt / FakeCallAlarmReceiver.kt
  One-off exact alarm scheduling and delayed normal fake call trigger.

QuickTriggerManager.kt
  Quick trigger defaults, presets, dynamic shortcuts, tile refresh, execution logic.

QuickTriggerTileServices.kt
  Five Quick Settings tile services backed by preset slots.

QuickTriggerAccessibilityService.kt
  Accessibility button as quick trigger.

ShortcutTriggerActivity.kt
  Dynamic launcher shortcut trampoline.

ExternalTriggerReceiver.kt
  Exported automation API receiver.

AlarmModeModels.kt
  Alarm-mode data models and enums.

AlarmModeRepository.kt
  Alarm-mode JSON persistence.

AlarmModeScheduler.kt
  Alarm-mode exact scheduling and next-trigger calculation.

AlarmModeAlarmReceiver.kt
  Alarm trigger handling, runtime override setup, repeat rescheduling.

ivr/IvrConfigStore.kt
  IVR XML persistence/import/export parser.

ivr/IvrStateMachine.kt
  Custom IVR DTMF state transitions.

CallRecordingForegroundService.kt
  Foreground notification while microphone recording is active.

BatterySetupNavigator.kt
  Battery optimization and OEM settings navigation helpers.

UpdateChecker.kt
  GitHub latest-release update check.

DelayFormatter.kt
  Locale-aware duration formatting.
```

## 24. Safe Development Guidance For AI Agents

- Read `FakeCallViewModel.kt`, `FakeConnection.kt`, and `QuickTriggerManager.kt` before changing behavior. They contain most cross-feature contracts.
- Treat prefs keys as public internal API. Search all usages before renaming, deleting, or changing defaults.
- Prefer central helpers:
  - use `TelecomHelper` for phone account and incoming calls
  - use `QuickTriggerManager` for quick trigger execution
  - use `AlarmModeScheduler` for alarm-mode scheduling
  - use `IvrConfigStore` for IVR serialization
- Keep feature-specific behavior in the existing owner unless there is a strong reason to refactor.
- Do not remove manifest permissions without checking all platform entry points.
- Test exact alarms on Android 12+ behavior whenever scheduling changes.
- Test Telecom changes on a real device when possible. Emulators and OEM devices vary.
- Avoid making the in-app UI imitate the phone call screen. The defining behavior is integration with the real dialer via Telecom.
- Preserve user-selected URI permission handling when touching audio, folders, contacts, or recordings.
- Be conservative with foreground services because Android target SDK 36 restrictions may be strict.
- Be careful with exported components:
  - `ExternalTriggerReceiver` is intentionally exported.
  - Telecom, accessibility, and Quick Settings services are permission-protected platform integrations.
  - Receivers for internal alarms are non-exported.
