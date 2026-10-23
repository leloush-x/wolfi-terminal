package com.rk.terminal.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rk.resources.strings
import com.rk.settings.SettingsRepository
import com.rk.terminal.ui.screens.terminal.TerminalViewModel

/**
 * The microphone button.
 *
 * Recognition is Android's own [SpeechRecognizer]; the dictated text is
 * written straight into the running session, so it behaves like typing.
 */
@Composable
fun VoiceMicControl(
    terminalViewModel: TerminalViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state = remember { VoiceState() }
    DisposableEffect(Unit) { onDispose { state.destroy() } }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) state.start(context, terminalViewModel) }

    val micLabel = stringResource(strings.voice_mic)
    val stopLabel = stringResource(strings.voice_stop)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        IconButton(onClick = {
            when {
                state.listening -> state.stop()
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED -> state.start(context, terminalViewModel)
                else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }) {
            Icon(
                imageVector = if (state.listening) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (state.listening) stopLabel else micLabel
            )
        }

        if (state.lastInsertedCodePoints > 0) {
            IconButton(onClick = { state.undo(terminalViewModel) }) {
                Icon(
                    Icons.Filled.Undo,
                    contentDescription = stringResource(strings.voice_undo)
                )
            }
        }

        state.preview?.let { text ->
            AssistChip(
                onClick = { state.insert(terminalViewModel, text) },
                label = { Text(text.take(40)) }
            )
            if (SettingsRepository.isVoicePreview()) {
                Text(
                    text = stringResource(strings.voice_insert),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            IconButton(onClick = { state.preview = null }) {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = stringResource(strings.cancel)
                )
            }
        }
    }
}

/** Holds the recognizer and the state the mic button renders. */
private class VoiceState {
    var listening by mutableStateOf(false)
    var preview by mutableStateOf<String?>(null)
    var lastInsertedCodePoints by mutableIntStateOf(0)

    private var recognizer: SpeechRecognizer? = null

    fun start(context: Context, viewModel: TerminalViewModel) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(listener(viewModel))
        r.startListening(intent(context))
    }

    /** Stops listening but keeps the recognizer alive for the final result. */
    fun stop() {
        listening = false
        runCatching { recognizer?.stopListening() }
    }

    fun destroy() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        listening = false
    }

    fun insert(viewModel: TerminalViewModel, text: String) {
        val trailingSpace = SettingsRepository.isVoiceTrailingSpace()
        TextSender.send(viewModel.terminalView, text, trailingSpace)
        lastInsertedCodePoints = text.codePointCount(0, text.length) + (if (trailingSpace) 1 else 0)
        preview = null
    }

    fun undo(viewModel: TerminalViewModel) {
        val session = viewModel.terminalView?.mTermSession ?: return
        repeat(lastInsertedCodePoints) { session.write("\u007F") }
        lastInsertedCodePoints = 0
    }

    private fun intent(context: Context): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val language = SettingsRepository.getVoiceLanguage()
            if (language.isNotBlank()) putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            // Partial results only cost work when the preview is shown.
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, SettingsRepository.isVoicePreview())
        }

    private fun listener(viewModel: TerminalViewModel) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listening = true
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onEndOfSpeech() {
            listening = false
        }

        override fun onError(error: Int) {
            listening = false
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (text.isNullOrBlank()) return
            if (SettingsRepository.isVoicePreview()) {
                preview = text
            } else {
                insert(viewModel, text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) preview = text
        }
    }
}
