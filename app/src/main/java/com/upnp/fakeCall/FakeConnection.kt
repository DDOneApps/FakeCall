package com.upnp.fakeCall

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.ContextCompat
import com.upnp.fakeCall.ivr.IvrConfigStore
import com.upnp.fakeCall.ivr.IvrStateMachine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FakeConnection(
    private val context: Context,
    private val callerName: String,
    private val callerNumber: String
) : Connection() {

    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var mediaRecorder: MediaRecorder? = null
    private var recordingTempFile: File? = null
    private var recordingDestination: RecordingDestination? = null
    private val audioManager: AudioManager = context.getSystemService(AudioManager::class.java)
    private val ivrStore = IvrConfigStore()
    private var ivrStateMachine: IvrStateMachine? = null
    private var ivrAudioAttributes: AudioAttributes? = null
    private var ttsEngine: TextToSpeech? = null
    private var pendingTtsMessage: String? = null
    private val folderNavStack = mutableListOf<FolderNavState>()

    init {
        val displayName = callerName.ifBlank { callerNumber }
        setAddress(Uri.fromParts(PhoneAccount.SCHEME_TEL, callerNumber, null), TelecomManager.PRESENTATION_ALLOWED)
        setCallerDisplayName(displayName, TelecomManager.PRESENTATION_ALLOWED)
        setConnectionCapabilities(CAPABILITY_MUTE)
        setAudioModeIsVoip(true)
        setInitializing()
        setRinging()
    }

    override fun onAnswer() {
        setActive()
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        }
        runCatching {
            // Start on earpiece, but allow system UI to switch routes afterward.
            setAudioRoute(CallAudioState.ROUTE_EARPIECE)
            audioManager.isSpeakerphoneOn = false
            runCatching { audioManager.stopBluetoothSco() }
            runCatching { audioManager.isBluetoothScoOn = false }
        }
        startVoicePlayback()
        maybeStartMicRecording()
    }

    override fun onReject() {
        disconnectWithCause(DisconnectCause.REJECTED)
    }

    override fun onDisconnect() {
        disconnectWithCause(DisconnectCause.LOCAL)
    }

    override fun onAbort() {
        disconnectWithCause(DisconnectCause.CANCELED)
    }

    override fun onCallAudioStateChanged(state: CallAudioState) {
        super.onCallAudioStateChanged(state)
        runCatching {
            // Keep the mic live while we are actively recording to avoid unexpected dropouts.
            audioManager.isMicrophoneMute = if (mediaRecorder != null) false else state.isMuted
        }
        runCatching {
            // Respect the system route (earpiece vs. speaker) so the phone app toggle works.
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            applyAudioRoute(state.route)
        }
    }

    private fun applyAudioRoute(route: Int) {
        when {
            route and CallAudioState.ROUTE_BLUETOOTH != 0 -> {
                audioManager.isSpeakerphoneOn = false
                runCatching { audioManager.startBluetoothSco() }
                runCatching { audioManager.isBluetoothScoOn = true }
            }
            route and CallAudioState.ROUTE_WIRED_HEADSET != 0 -> {
                audioManager.isSpeakerphoneOn = false
                runCatching { audioManager.stopBluetoothSco() }
                runCatching { audioManager.isBluetoothScoOn = false }
            }
            route and CallAudioState.ROUTE_SPEAKER != 0 -> {
                runCatching { audioManager.stopBluetoothSco() }
                runCatching { audioManager.isBluetoothScoOn = false }
                audioManager.isSpeakerphoneOn = true
            }
            else -> {
                // Default to earpiece.
                runCatching { audioManager.stopBluetoothSco() }
                runCatching { audioManager.isBluetoothScoOn = false }
                audioManager.isSpeakerphoneOn = false
            }
        }
    }

    override fun onPlayDtmfTone(c: Char) {
        super.onPlayDtmfTone(c)
        if (handleFolderModeDtmf(c)) return
        val machine = ivrStateMachine ?: return
        val next = machine.handleDtmf(c) ?: return
        val uriString = next.audioUri
        if (uriString.isBlank()) {
            stopAndReleasePlayer()
            return
        }
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        val attrs = ivrAudioAttributes ?: buildVoiceAudioAttributes()
        switchToAudio(uri, attrs)
    }

    private fun disconnectWithCause(code: Int) {
        stopAndReleasePlayer()
        shutdownTts()
        folderNavStack.clear()
        stopAndReleaseRecording()
        runCatching {
            audioManager.mode = AudioManager.MODE_NORMAL
        }
        setDisconnected(DisconnectCause(code))
        destroy()
    }

    private fun startVoicePlayback() {
        stopAndReleasePlayer()

        requestAudioFocus()

        val audioAttributes = buildVoiceAudioAttributes()
        ivrAudioAttributes = audioAttributes
        if (startFolderModeIfEnabled()) {
            return
        }

        val ivrConfig = ivrStore.load(context)
        ivrStateMachine = ivrConfig?.let { IvrStateMachine(it) }

        val ivrAudio = ivrStateMachine
            ?.currentNode()
            ?.audioUri
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }

        if (ivrAudio != null) {
            val started = startPlayerFromUri(ivrAudio, audioAttributes)
            if (started) return
        }

        val selectedUri = loadSelectedAudioUri()
        if (selectedUri == null) {
            Log.i(TAG, "No audio file selected; skipping call playback.")
            return
        }

        val started = startPlayerFromUri(selectedUri, audioAttributes)
        if (!started) {
            Log.e(TAG, "Failed to start call playback for uri=$selectedUri")
        }
    }

    private fun startFolderModeIfEnabled(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_MP3_IVR_MODE_ENABLED, false)
        if (!enabled) return false

        ivrStateMachine = null
        val rootUri = prefs.getString(KEY_MP3_IVR_FOLDER_URI, "")
            .orEmpty()
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (rootUri == null) {
            folderNavStack.clear()
            speakFolderPrompt(context.getString(R.string.status_select_mp3_ivr_folder))
            return true
        }

        val rootName = prefs.getString(
            KEY_MP3_IVR_FOLDER_NAME,
            context.getString(R.string.settings_mp3_ivr_no_folder_selected)
        ).orEmpty().ifBlank { context.getString(R.string.settings_mp3_ivr_no_folder_selected) }

        val entries = listFolderEntries(rootUri)
        folderNavStack.clear()
        folderNavStack.add(
            FolderNavState(
                folderUri = rootUri,
                folderName = rootName,
                entries = entries
            )
        )
        if (entries.isEmpty()) {
            speakFolderPrompt(context.getString(R.string.tts_mp3_ivr_empty_folder))
            return true
        }

        speakCurrentFolderMenu()
        return true
    }

    private fun handleFolderModeDtmf(digit: Char): Boolean {
        val current = folderNavStack.lastOrNull() ?: return false
        when (digit) {
            '#' -> {
                val hasNext = (current.pageIndex + 1) * FOLDER_PAGE_SIZE < current.entries.size
                if (hasNext) {
                    current.pageIndex += 1
                } else {
                    speakFolderPrompt(context.getString(R.string.tts_mp3_ivr_no_next_page))
                }
                speakCurrentFolderMenu()
                return true
            }
            '*' -> {
                if (current.pageIndex > 0) {
                    current.pageIndex -= 1
                } else {
                    speakFolderPrompt(context.getString(R.string.tts_mp3_ivr_no_previous_page))
                }
                speakCurrentFolderMenu()
                return true
            }
            '0' -> {
                if (folderNavStack.size > 1) {
                    folderNavStack.removeLast()
                    speakCurrentFolderMenu()
                } else {
                    speakFolderPrompt(context.getString(R.string.tts_mp3_ivr_root_folder))
                }
                return true
            }
            in '1'..'9' -> {
                val item = currentPageEntries(current).getOrNull(digit - '1')
                if (item == null) {
                    speakFolderPrompt(context.getString(R.string.tts_mp3_ivr_invalid_selection))
                    return true
                }
                if (item.isDirectory) {
                    openFolderEntry(item)
                } else {
                    playFolderAudioEntry(item)
                }
                return true
            }
            else -> return true
        }
    }

    private fun openFolderEntry(entry: FolderNavEntry) {
        val nestedEntries = listFolderEntries(entry.uri)
        folderNavStack.add(
            FolderNavState(
                folderUri = entry.uri,
                folderName = entry.displayName,
                entries = nestedEntries
            )
        )
        if (nestedEntries.isEmpty()) {
            speakFolderPrompt(context.getString(R.string.tts_mp3_ivr_empty_folder))
        }
        speakCurrentFolderMenu()
    }

    private fun playFolderAudioEntry(entry: FolderNavEntry) {
        val attrs = ivrAudioAttributes ?: buildVoiceAudioAttributes()
        runCatching { ttsEngine?.stop() }
        stopAndReleasePlayer()
        val started = startPlayerFromUri(
            uri = entry.uri,
            audioAttributes = attrs,
            loop = false,
            onCompletion = { speakCurrentFolderMenu() }
        )
        if (!started) {
            speakFolderPrompt(context.getString(R.string.tts_mp3_ivr_audio_failed))
        }
    }

    private fun speakCurrentFolderMenu() {
        val state = folderNavStack.lastOrNull() ?: return
        val entries = currentPageEntries(state)
        if (entries.isEmpty()) {
            speakFolderPrompt(context.getString(R.string.tts_mp3_ivr_empty_folder))
            return
        }

        val promptParts = mutableListOf<String>()
        promptParts += context.getString(R.string.tts_mp3_ivr_menu_intro, state.folderName)
        val totalPages = ((state.entries.size - 1) / FOLDER_PAGE_SIZE) + 1
        if (totalPages > 1) {
            promptParts += context.getString(
                R.string.tts_mp3_ivr_page_announcement,
                state.pageIndex + 1,
                totalPages
            )
        }

        entries.forEachIndexed { index, entry ->
            val key = index + 1
            promptParts += if (entry.isDirectory) {
                context.getString(R.string.tts_mp3_ivr_menu_item_folder, key, entry.displayName)
            } else {
                context.getString(R.string.tts_mp3_ivr_menu_item_audio, key, entry.displayName)
            }
        }

        if ((state.pageIndex + 1) * FOLDER_PAGE_SIZE < state.entries.size) {
            promptParts += context.getString(R.string.tts_mp3_ivr_next_page_hint)
        }
        if (state.pageIndex > 0) {
            promptParts += context.getString(R.string.tts_mp3_ivr_previous_page_hint)
        }
        if (folderNavStack.size > 1) {
            promptParts += context.getString(R.string.tts_mp3_ivr_back_folder_hint)
        }

        speakFolderPrompt(promptParts.joinToString(" "))
    }

    private fun currentPageEntries(state: FolderNavState): List<FolderNavEntry> {
        val from = state.pageIndex * FOLDER_PAGE_SIZE
        if (from >= state.entries.size) return emptyList()
        return state.entries.drop(from).take(FOLDER_PAGE_SIZE)
    }

    private fun listFolderEntries(folderUri: Uri): List<FolderNavEntry> {
        val resolver = context.contentResolver
        val folderId = runCatching { DocumentsContract.getDocumentId(folderUri) }.getOrElse {
            DocumentsContract.getTreeDocumentId(folderUri)
        }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, folderId)

        val entries = mutableListOf<FolderNavEntry>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        runCatching {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val docId = if (idCol >= 0) cursor.getString(idCol) else null
                    val name = if (nameCol >= 0) cursor.getString(nameCol) else null
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                    if (docId.isNullOrBlank()) continue
                    val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    val isAudio = !isDirectory && looksLikeAudio(mime, name)
                    if (!isDirectory && !isAudio) continue

                    val childUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                    entries += FolderNavEntry(
                        uri = childUri,
                        displayName = name.orEmpty().ifBlank { context.getString(R.string.label_unknown) },
                        isDirectory = isDirectory
                    )
                }
            }
        }

        return entries.sortedWith(
            compareByDescending<FolderNavEntry> { it.isDirectory }
                .thenBy { it.displayName.lowercase(Locale.getDefault()) }
        )
    }

    private fun looksLikeAudio(mimeType: String?, displayName: String?): Boolean {
        if (mimeType?.startsWith("audio/") == true) return true
        val lowerName = displayName.orEmpty().lowercase(Locale.getDefault())
        return lowerName.endsWith(".mp3") ||
            lowerName.endsWith(".wav") ||
            lowerName.endsWith(".m4a") ||
            lowerName.endsWith(".aac") ||
            lowerName.endsWith(".ogg") ||
            lowerName.endsWith(".flac")
    }

    private fun speakFolderPrompt(message: String) {
        if (message.isBlank()) return
        val existing = ttsEngine
        if (existing != null) {
            runCatching {
                existing.stop()
                existing.speak(message, TextToSpeech.QUEUE_FLUSH, null, "mp3_ivr_${System.currentTimeMillis()}")
            }
            return
        }
        pendingTtsMessage = message
        initializeTtsIfNeeded()
    }

    private fun initializeTtsIfNeeded() {
        if (ttsEngine != null) return
        var newEngine: TextToSpeech? = null
        newEngine = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                runCatching { newEngine?.shutdown() }
                return@TextToSpeech
            }
            ttsEngine = newEngine
            runCatching {
                newEngine?.language = Locale.getDefault()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                runCatching {
                    newEngine?.setAudioAttributes(buildVoiceAudioAttributes())
                }
            }
            pendingTtsMessage?.let { text ->
                runCatching {
                    newEngine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mp3_ivr_init")
                }
            }
            pendingTtsMessage = null
        }
    }

    private fun shutdownTts() {
        pendingTtsMessage = null
        ttsEngine?.let { tts ->
            runCatching { tts.stop() }
            runCatching { tts.shutdown() }
        }
        ttsEngine = null
    }

    private fun maybeStartMicRecording() {
        if (!isRecordingEnabled()) return
        if (!hasRecordAudioPermission()) return

        runCatching { CallRecordingForegroundService.start(context) }
            .onFailure { Log.e(TAG, "Failed to start recording foreground service.", it) }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "fake_call_$timestamp.m4a"
        val destination = createRecordingDestination(filename) ?: return
        val tempFile = buildTempRecordingFile(filename)

        runCatching {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                // MIC is more stable for continuous capture during self-managed calls.
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(256_000)
                setAudioSamplingRate(48_000)
                setAudioChannels(1)
                setOutputFile(tempFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            recordingTempFile = tempFile
            recordingDestination = destination
        }.onFailure {
            runCatching { tempFile.delete() }
            cleanupRecordingDestination(destination)
            stopAndReleaseRecording()
        }
    }

    private fun startPlayerFromUri(
        uri: Uri,
        audioAttributes: AudioAttributes,
        loop: Boolean = true,
        onCompletion: (() -> Unit)? = null
    ): Boolean {
        return runCatching {
            val player = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(context, uri)
                isLooping = loop
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra for uri=$uri")
                    stopAndReleasePlayer()
                    true
                }
                if (onCompletion != null) {
                    setOnCompletionListener { onCompletion() }
                }
                prepare()
                start()
            }
            mediaPlayer = player
            true
        }.getOrElse {
            Log.e(TAG, "MediaPlayer setup failed for uri=$uri", it)
            false
        }
    }

    private fun switchToAudio(uri: Uri, audioAttributes: AudioAttributes) {
        stopAndReleasePlayer()
        startPlayerFromUri(uri, audioAttributes)
    }

    private fun buildVoiceAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }

    private fun buildInternalRecordingFile(filename: String): File {
        val recordingsDir = File(context.filesDir, "recordings").apply { mkdirs() }
        return File(recordingsDir, filename)
    }

    private fun buildTempRecordingFile(filename: String): File {
        val tempDir = File(context.cacheDir, "recordings_tmp").apply { mkdirs() }
        return File(tempDir, filename)
    }

    private fun createRecordingDestination(filename: String): RecordingDestination? {
        val resolver = context.contentResolver

        val selectedTreeUri = loadRecordingsTreeUri()
        if (selectedTreeUri != null) {
            val fileUri = runCatching {
                DocumentsContract.createDocument(
                    resolver,
                    selectedTreeUri,
                    "audio/mp4",
                    filename
                )
            }.getOrNull()

            if (fileUri != null) {
                return RecordingDestination(uri = fileUri, file = null)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + File.separator + "FakeCall"
                )
            }
            val mediaStoreUri = runCatching {
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            }.getOrNull()

            if (mediaStoreUri != null) {
                return RecordingDestination(uri = mediaStoreUri, file = null)
            }
        }

        return RecordingDestination(
            uri = null,
            file = buildInternalRecordingFile(filename)
        )
    }

    private fun isRecordingEnabled(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_RECORDING_ENABLED, true)
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun stopAndReleaseRecording() {
        var stoppedCleanly = false
        mediaRecorder?.run {
            stoppedCleanly = runCatching { stop() }.isSuccess
            runCatching { reset() }
            runCatching { release() }
        }
        mediaRecorder = null
        finalizeRecordingDestination(stoppedCleanly)
        runCatching { CallRecordingForegroundService.stop(context) }
    }

    private fun loadSelectedAudioUri(): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasRuntimeOverride = prefs.getBoolean(KEY_RUNTIME_AUDIO_OVERRIDE_ENABLED, false)
        if (hasRuntimeOverride) {
            val runtimeValue = prefs.getString(KEY_RUNTIME_AUDIO_OVERRIDE_URI, "").orEmpty()
            prefs.edit()
                .putBoolean(KEY_RUNTIME_AUDIO_OVERRIDE_ENABLED, false)
                .remove(KEY_RUNTIME_AUDIO_OVERRIDE_URI)
                .remove(KEY_RUNTIME_AUDIO_OVERRIDE_NAME)
                .apply()
            if (runtimeValue.isBlank()) return null
            return runCatching { Uri.parse(runtimeValue) }.getOrNull()
        }
        val value = prefs.getString(KEY_AUDIO_URI, "").orEmpty()
        if (value.isBlank()) return null
        return runCatching { Uri.parse(value) }.getOrNull()
    }

    private fun loadRecordingsTreeUri(): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = prefs.getString(KEY_RECORDINGS_TREE_URI, "").orEmpty()
        if (value.isBlank()) return null
        return runCatching { Uri.parse(value) }.getOrNull()
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { }
                .build()
            audioFocusRequest = request
            runCatching { audioManager.requestAudioFocus(request) }
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    private fun stopAndReleasePlayer() {
        mediaPlayer?.run {
            runCatching {
                if (isPlaying) {
                    stop()
                }
            }
            reset()
            release()
        }
        mediaPlayer = null
        abandonAudioFocus()
    }

    companion object {
        private const val TAG = "FakeConnection"
        private const val PREFS_NAME = "fake_call_prefs"
        private const val KEY_AUDIO_URI = "audio_uri"
        private const val KEY_RUNTIME_AUDIO_OVERRIDE_ENABLED = "runtime_audio_override_enabled"
        private const val KEY_RUNTIME_AUDIO_OVERRIDE_URI = "runtime_audio_override_uri"
        private const val KEY_RUNTIME_AUDIO_OVERRIDE_NAME = "runtime_audio_override_name"
        private const val KEY_RECORDING_ENABLED = "recording_enabled"
        private const val KEY_RECORDINGS_TREE_URI = "recordings_tree_uri"
        private const val KEY_MP3_IVR_MODE_ENABLED = "mp3_ivr_mode_enabled"
        private const val KEY_MP3_IVR_FOLDER_URI = "mp3_ivr_folder_uri"
        private const val KEY_MP3_IVR_FOLDER_NAME = "mp3_ivr_folder_name"
        private const val FOLDER_PAGE_SIZE = 9
    }

    private fun finalizeRecordingDestination(stoppedCleanly: Boolean) {
        val tempFile = recordingTempFile
        val destination = recordingDestination

        recordingTempFile = null
        recordingDestination = null

        if (tempFile == null || destination == null) {
            runCatching { tempFile?.delete() }
            return
        }

        if (!stoppedCleanly || !tempFile.exists()) {
            runCatching { tempFile.delete() }
            cleanupRecordingDestination(destination)
            Log.w(TAG, "Recorder stop failed; discarded broken recording.")
            return
        }

        val moved = runCatching {
            when {
                destination.uri != null -> {
                    context.contentResolver.openOutputStream(destination.uri, "w")?.use { out ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    } ?: error("Could not open output stream for destination Uri.")
                }
                destination.file != null -> {
                    destination.file.parentFile?.mkdirs()
                    tempFile.copyTo(destination.file, overwrite = true)
                }
                else -> error("No recording destination.")
            }
            true
        }.getOrElse {
            Log.e(TAG, "Failed to export recording to destination.", it)
            false
        }

        runCatching { tempFile.delete() }

        if (!moved) {
            cleanupRecordingDestination(destination)
        }
    }

    private fun cleanupRecordingDestination(destination: RecordingDestination) {
        destination.uri?.let { uri ->
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
        destination.file?.let { file ->
            runCatching { file.delete() }
        }
    }
}

private data class RecordingDestination(
    val uri: Uri?,
    val file: File?
)

private data class FolderNavEntry(
    val uri: Uri,
    val displayName: String,
    val isDirectory: Boolean
)

private data class FolderNavState(
    val folderUri: Uri,
    val folderName: String,
    val entries: List<FolderNavEntry>,
    var pageIndex: Int = 0
)
