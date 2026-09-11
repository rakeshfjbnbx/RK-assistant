package com.example

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class RkController(private val context: Context) {

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private val geminiService = RkGeminiService()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _status = MutableStateFlow("আমি শুনছি...")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0.1f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(VoiceLanguage.AUTO)
    val selectedLanguage: StateFlow<VoiceLanguage> = _selectedLanguage.asStateFlow()

    private val _isTtsEnabled = MutableStateFlow(true)
    val isTtsEnabled: StateFlow<Boolean> = _isTtsEnabled.asStateFlow()

    private val _isWakeWordEnabled = MutableStateFlow(true)
    val isWakeWordEnabled: StateFlow<Boolean> = _isWakeWordEnabled.asStateFlow()

    private val _pendingAction = MutableStateFlow<PendingAction?>(null)
    val pendingAction: StateFlow<PendingAction?> = _pendingAction.asStateFlow()

    private val _history = MutableStateFlow<List<RkHistoryItem>>(emptyList())
    val history: StateFlow<List<RkHistoryItem>> = _history.asStateFlow()

    init {
        initSpeechRecognizer()
        initTextToSpeech()
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            } catch (e: Exception) {
                Log.e("RkController", "Error initializing SpeechRecognizer", e)
            }
        }
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                textToSpeech?.setSpeechRate(0.98f)
                textToSpeech?.setPitch(1.02f)
                applyTtsLanguage(Locale("bn", "IN"))

                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _assistantState.value = AssistantState.SPEAKING
                    }

                    override fun onDone(utteranceId: String?) {
                        if (_assistantState.value == AssistantState.SPEAKING) {
                            _assistantState.value = AssistantState.IDLE
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        if (_assistantState.value == AssistantState.SPEAKING) {
                            _assistantState.value = AssistantState.IDLE
                        }
                    }
                })
            }
        }
    }

    private fun applyTtsLanguage(locale: Locale) {
        if (!isTtsInitialized || textToSpeech == null) return
        val result = textToSpeech?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            textToSpeech?.setLanguage(Locale.US)
        }
    }

    fun setLanguage(language: VoiceLanguage) {
        _selectedLanguage.value = language
        val prompt = when (language) {
            VoiceLanguage.BENGALI -> "ভাষা পরিবর্তন: বাংলা"
            VoiceLanguage.HINDI -> "भाषा बदली: हिन्दी"
            VoiceLanguage.ENGLISH -> "Language set: English"
            VoiceLanguage.AUTO -> "অটো ভাষা সনাক্তকরণ চালু"
        }
        _status.value = prompt
    }

    fun toggleTts() {
        _isTtsEnabled.value = !_isTtsEnabled.value
        if (!_isTtsEnabled.value) {
            stopSpeaking()
        }
    }

    fun toggleWakeWord() {
        _isWakeWordEnabled.value = !_isWakeWordEnabled.value
        if (_isWakeWordEnabled.value) {
            _status.value = "ওয়েক ওয়ার্ড 'RK' সক্রিয় আছে"
        } else {
            _status.value = "ওয়েক ওয়ার্ড বন্ধ করা হয়েছে"
        }
    }

    // Voice interruption: stop any active voice when user taps or speaks
    fun interruptVoice() {
        stopSpeaking()
    }

    fun startListening() {
        interruptVoice()

        if (speechRecognizer == null) {
            initSpeechRecognizer()
        }

        val recognizer = speechRecognizer
        if (recognizer == null) {
            _status.value = "ভয়েস রিকগনিশন উপলব্ধ নেই। নিচে লিখে কমান্ড দিন।"
            return
        }

        _status.value = "Listening..."
        _assistantState.value = AssistantState.LISTENING
        _rmsLevel.value = 0.2f

        val locale = when (_selectedLanguage.value) {
            VoiceLanguage.BENGALI -> Locale("bn", "IN")
            VoiceLanguage.HINDI -> Locale("hi", "IN")
            VoiceLanguage.ENGLISH -> Locale.US
            VoiceLanguage.AUTO -> Locale("bn", "IN") // Default recognition locale, handles mixed
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toString())
            putExtra(RecognizerIntent.EXTRA_PROMPT, _selectedLanguage.value.speechPrompt)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _assistantState.value = AssistantState.LISTENING
            }

            override fun onBeginningOfSpeech() {
                interruptVoice()
                _status.value = "শুনছি..."
            }

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 14f).coerceIn(0.1f, 1f)
                _rmsLevel.value = normalized
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _rmsLevel.value = 0.1f
            }

            override fun onError(error: Int) {
                _assistantState.value = AssistantState.IDLE
                _rmsLevel.value = 0.1f
                _status.value = "আবার বলুন..."
            }

            override fun onResults(results: Bundle?) {
                _rmsLevel.value = 0.1f
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = list?.firstOrNull()

                if (!text.isNullOrBlank()) {
                    _recognizedText.value = text
                    processUserInput(text)
                } else {
                    _assistantState.value = AssistantState.IDLE
                    _status.value = "আবার বলুন..."
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    _status.value = text
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            _assistantState.value = AssistantState.IDLE
            _status.value = "ভয়েস চালু করতে সমস্যা হয়েছে।"
            Log.e("RkController", "Error starting listening", e)
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e("RkController", "Error stopping listening", e)
        }
        if (_assistantState.value == AssistantState.LISTENING) {
            _assistantState.value = AssistantState.IDLE
        }
        _rmsLevel.value = 0.1f
    }

    fun processUserInput(rawInput: String) {
        val input = rawInput.trim()
        if (input.isEmpty()) return

        _recognizedText.value = input
        interruptVoice()

        // Check if user is confirming an active pending action
        val currentPending = _pendingAction.value
        if (currentPending != null) {
            val lower = input.lowercase()
            if (lower.contains("হ্যাঁ") || lower.contains("yes") || lower.contains("कन्फर्म") || lower.contains("हाँ") || lower.contains("confirm") || lower.contains("করো") || lower.contains("কর")) {
                confirmPendingAction()
                return
            } else if (lower.contains("না") || lower.contains("no") || lower.contains("cancel") || lower.contains("বাতিল") || lower.contains("रद्द")) {
                cancelPendingAction()
                return
            }
        }

        // Set state to THINKING
        _assistantState.value = AssistantState.THINKING
        _status.value = "ভাবছি..."

        coroutineScope.launch {
            try {
                val result = geminiService.processQuery(input)
                executeParsedResult(input, result)
            } catch (e: Exception) {
                Log.e("RkController", "Error processing query", e)
                val fallback = geminiService.localSmartBrain(input)
                executeParsedResult(input, fallback)
            }
        }
    }

    private fun executeParsedResult(originalInput: String, result: GeminiParsedResult) {
        _status.value = result.spokenResponse

        // Set TTS language matching detected language
        val targetLocale = when (result.detectedLanguage) {
            "bn" -> Locale("bn", "IN")
            "hi" -> Locale("hi", "IN")
            else -> Locale.US
        }
        applyTtsLanguage(targetLocale)

        // Handle Sensitive Actions requiring user confirmation (Calls & SMS)
        if (result.requiresConfirmation) {
            speak(result.spokenResponse)
            _assistantState.value = AssistantState.IDLE

            val pending = PendingAction(
                type = result.actionType,
                title = if (result.actionType == ActionType.CALL) "ফোন কল কনফার্ম করুন" else "মেসেজ পাঠানো কনফার্ম করুন",
                description = result.spokenResponse,
                targetData = result.phoneNumber ?: result.smsBody,
                language = result.detectedLanguage,
                execute = {
                    when (result.actionType) {
                        ActionType.CALL -> executeCallDirect(result.phoneNumber)
                        ActionType.SMS -> executeSmsDirect(result.phoneNumber, result.smsBody)
                        else -> Unit
                    }
                    addHistory(originalInput, result.spokenResponse, true, result.actionType)
                }
            )
            _pendingAction.value = pending
            return
        }

        // Execute non-sensitive actions directly
        when (result.actionType) {
            ActionType.OPEN_APP -> {
                when (result.actionTarget?.lowercase()) {
                    "youtube" -> openApp("com.google.android.youtube", "https://www.youtube.com", "YouTube")
                    "facebook" -> openApp("com.facebook.katana", "https://www.facebook.com", "Facebook")
                    "instagram" -> openApp("com.instagram.android", "https://www.instagram.com", "Instagram")
                    "camera" -> openCamera()
                    "gallery" -> openGallery()
                    "settings" -> openSettings()
                    else -> openSettings()
                }
            }
            ActionType.SEARCH_GOOGLE -> {
                executeSearch(result.searchQuery ?: originalInput)
            }
            ActionType.SYSTEM_SETTING -> {
                openSettings()
            }
            ActionType.VOLUME_CONTROL -> {
                executeVolumeControl(result.volumeChange)
            }
            ActionType.CALL -> {
                executeCallDirect(result.phoneNumber)
            }
            ActionType.SMS -> {
                executeSmsDirect(result.phoneNumber, result.smsBody)
            }
            ActionType.CONVERSATION -> {
                // Conversational only
            }
        }

        speak(result.spokenResponse)
        addHistory(originalInput, result.spokenResponse, true, result.actionType)
    }

    fun confirmPendingAction() {
        val pending = _pendingAction.value ?: return
        _pendingAction.value = null
        val confirmMsg = when (pending.language) {
            "bn" -> "নিশ্চিত করা হয়েছে, কার্যক্রম সম্পন্ন হচ্ছে।"
            "hi" -> "पुष्टि हो गई, काम हो रहा है।"
            else -> "Confirmed, executing now."
        }
        _status.value = confirmMsg
        speak(confirmMsg)
        pending.execute()
    }

    fun cancelPendingAction() {
        val pending = _pendingAction.value ?: return
        _pendingAction.value = null
        val cancelMsg = when (pending.language) {
            "bn" -> "বাতিল করা হয়েছে।"
            "hi" -> "रद्द कर दिया गया।"
            else -> "Action cancelled."
        }
        _status.value = cancelMsg
        speak(cancelMsg)
        _assistantState.value = AssistantState.IDLE
    }

    private fun executeVolumeControl(change: String?) {
        val manager = audioManager ?: return
        try {
            when (change) {
                "UP" -> manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "DOWN" -> manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "MUTE" -> manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                "MAX" -> {
                    val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    manager.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI)
                }
                else -> manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            }
        } catch (e: Exception) {
            Log.e("RkController", "Volume control error", e)
        }
    }

    private fun openApp(packageName: String, fallbackUrl: String, appName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                _status.value = "অ্যাপ খুলতে সমস্যা হয়েছে।"
            }
        } else {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            } catch (e: Exception) {
                _status.value = "এই অ্যাপ ফোনে ইনস্টল নেই।"
            }
        }
    }

    private fun openCamera() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("RkController", "Camera launch error", e)
        }
    }

    private fun openGallery() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "image/*"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("RkController", "Gallery launch error", e)
        }
    }

    private fun openSettings() {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("RkController", "Settings launch error", e)
        }
    }

    private fun executeSearch(query: String) {
        val url = "https://www.google.com/search?q=" + Uri.encode(query)
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("RkController", "Search launch error", e)
        }
    }

    private fun executeCallDirect(phoneNumber: String?) {
        try {
            val uri = if (!phoneNumber.isNullOrBlank()) {
                Uri.parse("tel:$phoneNumber")
            } else {
                Uri.parse("tel:")
            }
            val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("RkController", "Dialer error", e)
        }
    }

    private fun executeSmsDirect(phoneNumber: String?, body: String?) {
        try {
            val uri = if (!phoneNumber.isNullOrBlank()) {
                Uri.parse("smsto:$phoneNumber")
            } else {
                Uri.parse("smsto:")
            }
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                if (!body.isNullOrBlank()) {
                    putExtra("sms_body", body)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("RkController", "SMS error", e)
        }
    }

    private fun addHistory(command: String, response: String, isSuccess: Boolean, actionType: ActionType) {
        val newItem = RkHistoryItem(
            command = command,
            response = response,
            isSuccess = isSuccess,
            actionType = actionType
        )
        _history.value = listOf(newItem) + _history.value.take(25)
    }

    private fun speak(text: String) {
        if (!_isTtsEnabled.value || !isTtsInitialized) return
        try {
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "RK_SPEECH_${System.currentTimeMillis()}")
            }
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "RK_SPEECH")
        } catch (e: Exception) {
            Log.e("RkController", "Error speaking TTS", e)
        }
    }

    fun stopSpeaking() {
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.e("RkController", "Error stopping TTS", e)
        }
        if (_assistantState.value == AssistantState.SPEAKING) {
            _assistantState.value = AssistantState.IDLE
        }
    }

    fun clearSession() {
        geminiService.clearSession()
        _history.value = emptyList()
        _status.value = "আমি শুনছি..."
        _recognizedText.value = ""
        _pendingAction.value = null
        _assistantState.value = AssistantState.IDLE
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("RkController", "Error destroying speechRecognizer", e)
        }
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            Log.e("RkController", "Error destroying textToSpeech", e)
        }
    }
}
