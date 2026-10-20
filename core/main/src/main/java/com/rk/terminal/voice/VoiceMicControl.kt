package com.rk.terminal.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.rk.settings.SettingsRepository
import com.rk.terminal.ui.screens.terminal.TerminalViewModel
import com.termux.view.TerminalView
import kotlinx.coroutines.CoroutineScope
@Composable
fun VoiceMicControl(
    terminalViewModel: TerminalViewModel,
    scope: CoroutineScope,
) {
    val context = LocalContext.current
    val visible = SettingsRepository.isVoiceMicButtonVisible() && SettingsRepository.isVoiceEnabled()
    if (!visible) return

    var listening by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }
    var lastInsertedCp by remember { mutableStateOf(0) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening(context, terminalViewModel, { listening = it }, { preview = it }, { lastInsertedCp = it }, { recognizer = it }, scope)
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizer?.destroy()
            recognizer = null
        }
    }

    Row {
        IconButton(onClick = {
            if (!listening) {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    startListening(context, terminalViewModel, { listening = it }, { preview = it }, { lastInsertedCp = it }, { recognizer = it }, scope)
                }
            } else {
                recognizer?.stopListening()
                listening = false
            }
        }) {
            Icon(Icons.Filled.Mic, contentDescription = "Mic")
        }

        if (lastInsertedCp > 0) {
            IconButton(onClick = {
                val view = terminalViewModel.terminalView
                val session = view?.mTermSession
                if (session != null) {
                    repeat(lastInsertedCp) { session.write("\u007F") }
                }
                lastInsertedCp = 0
            }) {
                Icon(Icons.Filled.Undo, contentDescription = "Undo last voice insert")
            }
        }

        preview?.let { text ->
            AssistChip(onClick = {}, label = { Text(text.take(40)) })
            // Insert
            IconButton(onClick = {
                val view = terminalViewModel.terminalView
                TextSender.send(view, text, SettingsRepository.isVoiceTrailingSpace())
                lastInsertedCp = text.codePointCount(0, text.length) + (if (SettingsRepository.isVoiceTrailingSpace()) 1 else 0)
                preview = null
            }) { Text("Insert") }
            IconButton(onClick = { preview = null }) { Text("Cancel") }
        }
    }
}

private fun startListening(
    context: android.content.Context,
    terminalViewModel: TerminalViewModel,
    setListening: (Boolean) -> Unit,
    setPreview: (String?) -> Unit,
    setLastInserted: (Int) -> Unit,
    setRecognizer: (SpeechRecognizer?) -> Unit,
    scope: CoroutineScope,
) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) return
    val r = SpeechRecognizer.createSpeechRecognizer(context)
    setRecognizer(r)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        val lang = SettingsRepository.getVoiceLanguage()
        if (lang.isNotBlank()) putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }
    r.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { setListening(true) }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { setListening(false) }
        override fun onError(error: Int) { setListening(false) }
        override fun onResults(results: Bundle?) {
            setListening(false)
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()
            if (text != null) {
                if (SettingsRepository.isVoicePreview()) {
                    setPreview(text)
                } else {
                    val view = terminalViewModel.terminalView
                    TextSender.send(view, text, SettingsRepository.isVoiceTrailingSpace())
                    setLastInserted(text.codePointCount(0, text.length) + (if (SettingsRepository.isVoiceTrailingSpace()) 1 else 0))
                }
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()
            if (text != null) setPreview(text)
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    })
    r.startListening(intent)
    // store recognizer? We can't easily; leak is okay for now but release on destroy. 
}
