package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.LockscreenService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Build
import java.util.Locale

enum class AppScreen { Setup, Practice, Writing }

sealed class FeedbackState {
    object Idle : FeedbackState()
    data class Correct(val nextCount: Int) : FeedbackState()
    data class Incorrect(val userAnswer: String, val correctAnswer: String) : FeedbackState()
    data class Revealed(val correctAnswer: String) : FeedbackState()
}

const val MIN_AUTO_REPEAT = 1
const val MAX_AUTO_REPEAT = 10
const val MIN_AUTO_SPEED = 0.5f
const val MAX_AUTO_SPEED = 2.0f

class TranslationViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val database = AppDatabase.getDatabase(application)
    private val repository = StudySetRepository(database.studySetDao())

    // UI Navigation State
    private val _currentScreen = MutableStateFlow(AppScreen.Setup)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Room Database Study Sets
    val allStudySets: StateFlow<List<StudySet>> = repository.allStudySets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Room Database Folders
    val allFolders: StateFlow<List<Folder>> = repository.allFolders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Room Database Starred Pairs
    val allStarredPairs: StateFlow<List<StarredPair>> = repository.allStarredPairs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val practiceOnlyStarred = mutableStateOf(false)

    // Auto-scroll ("Tự cuộn"): số lần đọc lặp lại cho mỗi câu hỏi / đáp án
    val autoRepeatCount = mutableStateOf(2)
    // Tốc độ Tự cuộn: 0.5x..2.0x (ảnh hưởng thời gian nghỉ và tốc độ đọc)
    val autoSpeed = mutableStateOf(1f)

    // Writing practice (Tập viết chữ Hán)
    val writingFreeMode = mutableStateOf(false)
    val writingSpeed = mutableStateOf(1.5f)
    // Làm mờ khi Viết tự do (0 = tắt làm mờ / hiện cả chữ Hán & Tiếng Việt, 1 = mờ chữ Hán, 2 = che Tiếng Việt/phiên âm & nghĩa, 3 = che cả hai)
    val writingBlurMode = mutableStateOf(1)
    val writingBlur = mutableStateOf(true)
    // Tự động đọc chữ Hán khi chuyển từ/chữ trong Luyện Viết
    val writingAutoSpeak = mutableStateOf(true)
    val writingWord = mutableStateOf("")
    private var screenBeforeWriting = AppScreen.Setup

    // Version counter to trigger UI recomposition when personal tags, notes, or saved dates change
    val vocabStatusVersion = mutableStateOf(0)

    // Last horizontal swipe direction in Quizlet mode (-1 = left/Chưa nhớ, 1 = right/Đã nhớ, 0 = vertical/default)
    val lastQuizletSwipeDir = mutableStateOf(0)

    // Set of indices that the user has seen or practiced during current session
    private val _practicedIndices = MutableStateFlow<Set<Int>>(emptySet())
    val practicedIndices: StateFlow<Set<Int>> = _practicedIndices.asStateFlow()

    // Số lượng từ học mỗi phiên/bài (0 = Tất cả, >0 = giới hạn số từ lấy theo thứ tự hoặc ngẫu nhiên)
    val studyWordCountLimit = mutableStateOf(0)

    fun setStudyWordCountLimit(limit: Int) {
        val clean = limit.coerceAtLeast(0)
        studyWordCountLimit.value = clean
        getApplication<Application>().getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
            .edit().putInt("study_word_count_limit", clean).apply()
    }

    fun createFolder(name: String, onCreated: ((Long) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = repository.insertFolder(Folder(name = name))
            withContext(Dispatchers.Main) {
                onCreated?.invoke(id)
            }
        }
    }

    fun deleteFolder(folder: Folder, onResult: ((Boolean, String) -> Unit)? = null) {
        val app = getApplication<Application>()
        if (BuiltinLibraryCatalog.isProtectedFolder(folder.name)) {
            val msg = "Không thể xóa thư mục mặc định '${folder.name}'. Bộ dữ liệu sách & từ vựng có sẵn được bảo vệ!"
            LockscreenService.sendSystemAlertNotification(app, "Bảo vệ dữ liệu gốc", msg)
            onResult?.invoke(false, msg)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteFolder(folder)
            val msg = "Đã xóa thư mục '${folder.name}' khỏi hệ thống."
            withContext(Dispatchers.Main) {
                LockscreenService.sendSystemAlertNotification(app, "Đã xóa thư mục", msg)
                onResult?.invoke(true, msg)
            }
        }
    }

    fun renameFolder(folder: Folder, newName: String) {
        if (BuiltinLibraryCatalog.isProtectedFolder(folder.name)) {
            val app = getApplication<Application>()
            LockscreenService.sendSystemAlertNotification(
                app,
                "Bảo vệ thư mục gốc",
                "Thư mục mặc định '${folder.name}' không thể đổi tên."
            )
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateFolder(folder.copy(name = newName, lastModified = System.currentTimeMillis()))
        }
    }

    fun assignSetToFolder(setId: Long, folderId: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.assignStudySetToFolder(setId, folderId)
        }
    }

    fun toggleStar(vi: String, foreign: String, language: String) {
        recordWordSavedTimestamp(foreign, vi)
        viewModelScope.launch(Dispatchers.IO) {
            val existing = allStarredPairs.value.find { 
                it.vi.trim().lowercase() == vi.trim().lowercase() && 
                it.foreign.trim().lowercase() == foreign.trim().lowercase() 
            }
            if (existing != null) {
                repository.deleteStarredPair(existing.vi, existing.foreign)
            } else {
                repository.insertStarredPair(StarredPair(vi = vi, foreign = foreign, language = language))
            }
        }
    }

    /** Gắn/bỏ sao hàng loạt cho danh sách (vi, foreign) bất kỳ, ví dụ danh sách luyện viết. */
    fun setStarForPairs(pairs: List<Pair<String, String>>, language: String, star: Boolean) {
        if (star) {
            pairs.forEach { (vi, foreign) -> recordWordSavedTimestamp(foreign, vi) }
        }
        viewModelScope.launch(Dispatchers.IO) {
            fun key(a: String, b: String) = a.trim().lowercase() + "\u0000" + b.trim().lowercase()
            val existing = allStarredPairs.value.associateBy { key(it.vi, it.foreign) }
            pairs.forEach { (vi, foreign) ->
                val found = existing[key(vi, foreign)]
                if (star && found == null) {
                    repository.insertStarredPair(StarredPair(vi = vi, foreign = foreign, language = language))
                } else if (!star && found != null) {
                    repository.deleteStarredPair(found.vi, found.foreign)
                }
            }
        }
    }

    fun starPairOnly(vi: String, foreign: String, language: String) {
        recordWordSavedTimestamp(foreign, vi)
        viewModelScope.launch(Dispatchers.IO) {
            val existing = allStarredPairs.value.any { 
                it.vi.trim().lowercase() == vi.trim().lowercase() && 
                it.foreign.trim().lowercase() == foreign.trim().lowercase() 
            }
            if (!existing) {
                repository.insertStarredPair(StarredPair(vi = vi, foreign = foreign, language = language))
            }
        }
    }

    fun starAllPracticePairs() {
        viewModelScope.launch(Dispatchers.IO) {
            val lang = targetLanguage.value
            _practicePairs.value.forEach { pair ->
                val existing = allStarredPairs.value.any { 
                    it.vi.trim().lowercase() == pair.vi.trim().lowercase() && 
                    it.foreign.trim().lowercase() == pair.foreign.trim().lowercase() 
                }
                if (!existing) {
                    repository.insertStarredPair(StarredPair(vi = pair.vi, foreign = pair.foreign, language = lang))
                }
            }
        }
    }

    fun unstarAllPracticePairs() {
        viewModelScope.launch(Dispatchers.IO) {
            _practicePairs.value.forEach { pair ->
                repository.deleteStarredPair(pair.vi, pair.foreign)
            }
        }
    }

    // TTS Engine
    private var tts: TextToSpeech? = null
    private val activeCallbacks = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()
    private val _isTtsReady = MutableStateFlow(false)
    val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

    // Lockscreen & Passive Learning Toggle States
    val isLockscreenEnabled = mutableStateOf(false)
    val isPassiveNotifEnabled = mutableStateOf(false)
    val passiveAutoIntervalSec = mutableStateOf(0)
    val passiveAutoSpeak = mutableStateOf(false)
    val lockscreenPopupEnabled = mutableStateOf(true)

    // Quizlet Flashcard Sorting Mode States
    val quizletSortingEnabled = mutableStateOf(false)
    val quizletTtsEnabled = mutableStateOf(true)
    val quizletFrontLang = mutableStateOf("zh") // "zh" (Foreign) or "vi" (Vietnamese)
    private val _memorizedIndices = MutableStateFlow<Set<Int>>(emptySet())
    val memorizedIndices: StateFlow<Set<Int>> = _memorizedIndices.asStateFlow()
    private val _stillLearningIndices = MutableStateFlow<Set<Int>>(emptySet())
    val stillLearningIndices: StateFlow<Set<Int>> = _stillLearningIndices.asStateFlow()
    private val _isQuizletCompleted = MutableStateFlow(false)
    val isQuizletCompleted: StateFlow<Boolean> = _isQuizletCompleted.asStateFlow()

    val isImportingLibrary = mutableStateOf(false)

    // Setup Form Settings
    val rawTextContent = mutableStateOf("")
    val targetLanguage = mutableStateOf("zh") // "en" or "zh"
    val practiceMode = mutableStateOf("foreign-vi") // "foreign-vi", "vi-foreign", "random"
    val isShuffle = mutableStateOf(true)
    val deckTitle = mutableStateOf("")

    // Current Practice State
    private val _practicePairs = MutableStateFlow<List<TranslationPair>>(emptyList())
    val practicePairs: StateFlow<List<TranslationPair>> = _practicePairs.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    // Current phrase direction: "en-vi" or "vi-en" (or "zh-vi" etc)
    private val _currentDirection = MutableStateFlow("en") // "vi" -> Vietnamese prompt, "en"/"zh" -> Foreign prompt
    val currentDirection: StateFlow<String> = _currentDirection.asStateFlow()

    val userAnswerInput = mutableStateOf("")
    val cardHeightState = mutableStateOf(240f)
    // Chiều cao tối thiểu (dp) của khung đáp án gợi ý có ảnh nền, chỉnh bằng thanh kéo
    val answerCardHeight = mutableStateOf(220f)

    private val _feedback = MutableStateFlow<FeedbackState>(FeedbackState.Idle)
    val feedback: StateFlow<FeedbackState> = _feedback.asStateFlow()

    // Audio loop control for memory retention
    private val _isAudioLooping = MutableStateFlow(false)
    val isAudioLooping: StateFlow<Boolean> = _isAudioLooping.asStateFlow()
    private var audioLoopJob: Job? = null

    // Hands-free Auto play loop (Feature 1)
    private val _isAutoPlaying = MutableStateFlow(false)
    val isAutoPlaying: StateFlow<Boolean> = _isAutoPlaying.asStateFlow()
    private var autoPlayJob: Job? = null

    // Prompt audio loop control for original text reading
    private val _isPromptAudioLooping = MutableStateFlow(false)
    val isPromptAudioLooping: StateFlow<Boolean> = _isPromptAudioLooping.asStateFlow()
    private var promptAudioLoopJob: Job? = null

    // Speech-to-Text active indicators
    val isSpeechRecognitionActive = mutableStateOf(false)
    val isTtsSpeaking = mutableStateOf(false)
    val currentSpeechTranscript = mutableStateOf("")
    val speechAccumulatedBuffer = mutableStateOf("")
    private var speechResetJob: Job? = null
    private var autoAdvanceJob: Job? = null

    init {
        // Prepopulate database with default items and builtin library on start
        viewModelScope.launch(Dispatchers.IO) {
            isImportingLibrary.value = true
            try {
                repository.prePopulatePresets(application)
            } finally {
                isImportingLibrary.value = false
            }
        }
        loadStudyState()
        
        // Safely initialize TTS engine to prevent crash on environments/devices where TTS is missing
        try {
            tts = TextToSpeech(application, this)
        } catch (e: Exception) {
            Log.e("TranslationViewModel", "Error initializing TextToSpeech engine", e)
        }
    }

    fun checkVoiceCommands(text: String): Boolean {
        val lower = text.lowercase().trim()
        
        // Command 1: "bỏ qua" (skip) or "跳过" (tiao guo) or "skip" / "next"
        if (lower.contains("bỏ qua") || lower.contains("bo qua") || 
            lower.contains("tiao guo") || lower.contains("跳过") || lower == "skip" || lower == "next") {
            nextItem()
            return true
        }
        
        // Command 2: "lặp lại" (repeat prompt / loop question) or "đọc lại" or "重复" (chong fu) or "repeat"
        if (lower.contains("lặp lại") || lower.contains("lap lai") || 
            lower.contains("đọc lại") || lower.contains("doc lai") ||
            lower.contains("chong fu") || lower.contains("重复") || lower == "repeat" || lower.contains("loop prompt")) {
            if (!_isPromptAudioLooping.value) {
                togglePromptAudioLoop()
            }
            return true
        }
        
        // Command 3: "đáp án" (answer / loop answer) or "答案" (da an) or "answer"
        if (lower.contains("đáp án") || lower.contains("dap an") || 
            lower.contains("da an") || lower.contains("答案") || lower == "answer" || lower.contains("loop answer")) {
            if (!_isAudioLooping.value) {
                toggleAudioLoop()
            }
            return true
        }
        
        return false
    }

    fun handleSpeechInput(text: String, isFinal: Boolean) {
        if (_feedback.value is FeedbackState.Correct) return

        if (checkVoiceCommands(text)) {
            return
        }

        val correct = getCorrectAnswer() ?: return
        val nCorrect = normalizeText(correct)
        if (nCorrect.isEmpty()) return

        val trimmedText = text.trim()
        val nNew = normalizeText(trimmedText)

        // 1. Check if the newly spoken text itself is immediately correct!
        if (nNew == nCorrect) {
            currentSpeechTranscript.value = trimmedText
            speechAccumulatedBuffer.value = ""
            _feedback.value = FeedbackState.Correct(0)
            stopAudioLoop()
            autoAdvanceJob?.cancel()
            autoAdvanceJob = viewModelScope.launch {
                delay(2000)
                nextItem()
            }
            return
        }

        // 2. Try combining with existing buffer
        val combined = if (speechAccumulatedBuffer.value.isEmpty()) {
            trimmedText
        } else {
            "${speechAccumulatedBuffer.value} $trimmedText"
        }
        val nCombined = normalizeText(combined)

        if (nCombined == nCorrect) {
            currentSpeechTranscript.value = combined
            speechAccumulatedBuffer.value = ""
            _feedback.value = FeedbackState.Correct(0)
            stopAudioLoop()
            autoAdvanceJob?.cancel()
            autoAdvanceJob = viewModelScope.launch {
                delay(2000)
                nextItem()
            }
            return
        }

        // 3. Prefix matching logic
        if (nCorrect.startsWith(nCombined)) {
            // User is on the right track!
            speechAccumulatedBuffer.value = combined
            currentSpeechTranscript.value = combined
            cancelSpeechResetTimer()
        } else if (nCorrect.startsWith(nNew)) {
            // Old buffer doesn't match prefix, but this new utterance correctly starts the answer!
            speechAccumulatedBuffer.value = trimmedText
            currentSpeechTranscript.value = trimmedText
            cancelSpeechResetTimer()
        } else {
            // Neither matches prefix. Keep it in transcript so they see it, but set or reset timer
            currentSpeechTranscript.value = combined
            startSpeechResetTimer()
        }
    }

    private fun startSpeechResetTimer() {
        speechResetJob?.cancel()
        speechResetJob = viewModelScope.launch {
            delay(3500) // 3.5s pause of completely mismatched speaking clears current transcript/buffer
            speechAccumulatedBuffer.value = ""
            currentSpeechTranscript.value = ""
        }
    }

    private fun cancelSpeechResetTimer() {
        speechResetJob?.cancel()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                engine.language = Locale.US
                engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { id ->
                            activeCallbacks.remove(id)?.invoke()
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let { id ->
                            activeCallbacks.remove(id)?.invoke()
                        }
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        utteranceId?.let { id ->
                            activeCallbacks.remove(id)?.invoke()
                        }
                    }
                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        utteranceId?.let { id ->
                            activeCallbacks.remove(id)?.invoke()
                        }
                    }
                })
                _isTtsReady.value = true
                Log.d("TranslationViewModel", "TTS is ready for use")
            }
        } else {
            Log.e("TranslationViewModel", "Failed to initialize TTS engine")
        }
    }

    fun startPractice(): Boolean {
        val rawInput = rawTextContent.value.trim()
        if (rawInput.isEmpty()) return false

        val pairs = parseRawContent(rawInput, targetLanguage.value)
        if (pairs.isEmpty()) return false

        var finalPairs = if (isShuffle.value) pairs.shuffled() else pairs
        if (practiceOnlyStarred.value) {
            val starredList = allStarredPairs.value
            finalPairs = finalPairs.filter { pair ->
                starredList.any { s -> 
                    s.vi.trim().lowercase() == pair.vi.trim().lowercase() && 
                    s.foreign.trim().lowercase() == pair.foreign.trim().lowercase() 
                }
            }
        }

        val limit = studyWordCountLimit.value
        if (limit > 0 && finalPairs.size > limit) {
            finalPairs = finalPairs.take(limit)
        }

        if (finalPairs.isEmpty()) {
            return false
        }

        _currentIndex.value = 0
        _practicePairs.value = finalPairs
        _currentScreen.value = AppScreen.Practice
        _feedback.value = FeedbackState.Idle
        userAnswerInput.value = ""
        stopAudioLoop()
        stopPromptAudioLoop()
        isSpeechRecognitionActive.value = false // Defensively turn off mic auto-activation on startup
        _practicedIndices.value = emptySet()
        _memorizedIndices.value = emptySet()
        _stillLearningIndices.value = emptySet()
        _isQuizletCompleted.value = false

        displayItem()
        return true
    }

    fun backToSetup() {
        _currentScreen.value = AppScreen.Setup
        _feedback.value = FeedbackState.Idle
        stopAudioLoop()
        stopPromptAudioLoop()
        stopAutoPlay()
        speechAccumulatedBuffer.value = ""
        currentSpeechTranscript.value = ""
        cancelSpeechResetTimer()
        autoAdvanceJob?.cancel()
        saveStudyState()
    }

    private fun displayItem() {
        val pairs = _practicePairs.value
        if (pairs.isEmpty()) return
        val safeIdx = _currentIndex.value.coerceIn(0, pairs.lastIndex)
        if (safeIdx != _currentIndex.value) {
            _currentIndex.value = safeIdx
        }
        val currentPair = pairs[safeIdx]

        // Decide actual direction based on settings
        val chosenDir = when (practiceMode.value) {
            "foreign-vi" -> "foreign-vi"
            "vi-foreign" -> "vi-foreign"
            else -> if (Math.random() < 0.5) "foreign-vi" else "vi-foreign"
        }

        if (chosenDir == "foreign-vi") {
            _currentDirection.value = targetLanguage.value // Prompt is in English/Chinese
        } else {
            _currentDirection.value = "vi" // Prompt is in Vietnamese
        }

        userAnswerInput.value = ""
        _feedback.value = FeedbackState.Idle
        stopAudioLoop()
        stopPromptAudioLoop()

        // Reset continuous speech recognition buffers and timers
        speechAccumulatedBuffer.value = ""
        currentSpeechTranscript.value = ""
        cancelSpeechResetTimer()
        autoAdvanceJob?.cancel()

        // Track seen question index
        _practicedIndices.value = _practicedIndices.value + _currentIndex.value

        saveStudyState()

        // Speak the prompt text after a short layout delay (ONLY if not auto-playing and TTS toggle is on!)
        if (!_isAutoPlaying.value && quizletTtsEnabled.value) {
            viewModelScope.launch {
                delay(150)
                speakPrompt()
            }
        }
    }

    fun speakPrompt() {
        val pairs = _practicePairs.value
        if (pairs.isEmpty()) return
        val item = pairs[_currentIndex.value.coerceIn(0, pairs.lastIndex)]
        val textToSpeak = if (_currentDirection.value == "vi") item.vi else item.foreign
        val lang = if (_currentDirection.value == "vi") "vi" else targetLanguage.value
        viewModelScope.launch {
            speakTextAndAwait(textToSpeak, lang, extraDelayAfterMs = 0L)
        }
    }

    fun speakAnswer() {
        val correctAnswer = getCorrectAnswer() ?: return
        val lang = if (_currentDirection.value == "vi") targetLanguage.value else "vi"
        viewModelScope.launch {
            speakTextAndAwait(correctAnswer, lang, extraDelayAfterMs = 0L)
        }
    }

    fun getCorrectAnswer(): String? {
        val pairs = _practicePairs.value
        if (pairs.isEmpty()) return null
        val item = pairs[_currentIndex.value.coerceIn(0, pairs.lastIndex)]
        return if (_currentDirection.value == "vi") item.foreign else item.vi
    }

    fun toggleAudioLoop() {
        if (_isAudioLooping.value) {
            stopAudioLoop()
        } else {
            stopPromptAudioLoop()
            startAudioLoop()
        }
    }

    private fun startAudioLoop() {
        val answer = getCorrectAnswer() ?: return
        val lang = if (_currentDirection.value == "vi") targetLanguage.value else "vi"
        _isAudioLooping.value = true
        audioLoopJob?.cancel()
        audioLoopJob = viewModelScope.launch {
            while (_isAudioLooping.value) {
                speakTextAndAwait(answer, lang, extraDelayAfterMs = 1500L)
            }
        }
    }

    private fun stopAudioLoop() {
        _isAudioLooping.value = false
        audioLoopJob?.cancel()
        audioLoopJob = null
        try {
            tts?.stop()
        } catch (e: Exception) {}
    }

    fun togglePromptAudioLoop() {
        if (_isPromptAudioLooping.value) {
            stopPromptAudioLoop()
        } else {
            stopAudioLoop()
            startPromptAudioLoop()
        }
    }

    private fun startPromptAudioLoop() {
        val pairs = _practicePairs.value
        if (pairs.isEmpty()) return
        val item = pairs[_currentIndex.value.coerceIn(0, pairs.lastIndex)]
        val textToSpeak = if (_currentDirection.value == "vi") item.vi else item.foreign
        val lang = if (_currentDirection.value == "vi") "vi" else targetLanguage.value
        _isPromptAudioLooping.value = true
        promptAudioLoopJob?.cancel()
        promptAudioLoopJob = viewModelScope.launch {
            while (_isPromptAudioLooping.value) {
                speakTextAndAwait(textToSpeak, lang, extraDelayAfterMs = 1500L)
            }
        }
    }

    fun stopPromptAudioLoop() {
        _isPromptAudioLooping.value = false
        promptAudioLoopJob?.cancel()
        promptAudioLoopJob = null
        try {
            tts?.stop()
        } catch (e: Exception) {}
    }

    private fun speakTextLocal(text: String, lang: String) {
        viewModelScope.launch {
            speakTextAndAwait(text, lang, extraDelayAfterMs = 0L)
        }
    }

    private suspend fun speakTextAndAwait(text: String, lang: String, extraDelayAfterMs: Long = 1000L) {
        if (tts == null || !_isTtsReady.value) {
            delay(extraDelayAfterMs)
            return
        }
        
        isTtsSpeaking.value = true
        try {
            // Strip pinyin annotations and parenthesis for pleasant audio output
            val cleanText = text
                .replace("\\(.*?\\)".toRegex(), "")
                .replace("\\uff08.*?\\uff09".toRegex(), "")
                .replace("\\[.*?\\]".toRegex(), "")
                .trim()

            if (cleanText.isEmpty()) {
                delay(extraDelayAfterMs)
                return
            }

            val utteranceId = "TranslationEngine_${System.currentTimeMillis()}"
            val deferred = CompletableDeferred<Unit>()
            
            activeCallbacks[utteranceId] = {
                if (deferred.isActive) {
                    deferred.complete(Unit)
                }
            }

            try {
                tts?.stop()
            } catch (e: Exception) {
                Log.e("TranslationViewModel", "Error stopping TTS", e)
            }
            // Give the TTS engine 300ms to cleanly reset its stream before speaking
            delay(300)

            tts?.apply {
                language = if (lang == "zh") {
                    Locale.CHINESE
                } else if (lang == "vi") {
                    val viLocale = Locale("vi", "VN")
                    val isSupported = isLanguageAvailable(viLocale)
                    if (isSupported >= TextToSpeech.LANG_AVAILABLE) viLocale else Locale.US
                } else {
                    Locale.US
                }
                setSpeechRate(writingSpeechRate ?: if (_isAutoPlaying.value) autoSpeed.value.coerceIn(0.6f, 2f) else 1f)
                speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            }

            // Safe fallback timeout based on length: e.g. 250ms per character + 4000ms buffer
            val fallbackDurationMs = (cleanText.length * 250L) + 4000L
            try {
                withTimeout(fallbackDurationMs) {
                    deferred.await()
                }
            } catch (e: TimeoutCancellationException) {
                // A missing engine callback must not stop the reading/writing sequence.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Timeout
            } finally {
                activeCallbacks.remove(utteranceId)
            }
            
            delay(extraDelayAfterMs)
        } finally {
            isTtsSpeaking.value = false
        }
    }

    fun checkAnswer() {
        val user = userAnswerInput.value.trim()
        val correct = getCorrectAnswer() ?: return

        if (normalizeText(user) == normalizeText(correct)) {
            _feedback.value = FeedbackState.Correct(0)
            stopAudioLoop()
        } else {
            _feedback.value = FeedbackState.Incorrect(user, correct)
        }
    }

    fun showAnswerToggle() {
        val correct = getCorrectAnswer() ?: return
        _feedback.value = FeedbackState.Revealed(correct)
        speakAnswer()
    }

    fun toggleAnswerRevealed() {
        val current = _feedback.value
        if (current is FeedbackState.Revealed) {
            _feedback.value = FeedbackState.Idle
        } else {
            val correct = getCorrectAnswer() ?: return
            _feedback.value = FeedbackState.Revealed(correct)
            speakAnswer()
        }
    }

    fun toggleAutoPlay() {
        if (_isAutoPlaying.value) {
            stopAutoPlay()
        } else {
            stopAudioLoop()
            stopPromptAudioLoop()
            _isAutoPlaying.value = true
            setAutoPlayServiceRunning(true)
            startAutoPlayLoop()
        }
    }

    fun stopAutoPlay() {
        _isAutoPlaying.value = false
        setAutoPlayServiceRunning(false)
        autoPlayJob?.cancel()
        autoPlayJob = null
        try {
            tts?.stop()
        } catch (e: Exception) {}
    }

    private fun startAutoPlayLoop() {
        autoPlayJob?.cancel()
        autoPlayJob = viewModelScope.launch {
            while (_isAutoPlaying.value) {
                val pairs = _practicePairs.value
                if (pairs.isEmpty()) {
                    delay(2000)
                    continue
                }
                val item = pairs[_currentIndex.value.coerceIn(0, pairs.lastIndex)]
                val promptText = if (_currentDirection.value == "vi") item.vi else item.foreign
                val promptLang = if (_currentDirection.value == "vi") "vi" else targetLanguage.value
                
                val answerText = if (_currentDirection.value == "vi") item.foreign else item.vi
                val answerLang = if (_currentDirection.value == "vi") targetLanguage.value else "vi"

                val repeatTimes = autoRepeatCount.value.coerceIn(MIN_AUTO_REPEAT, MAX_AUTO_REPEAT)

                // --- PROMPT (QUESTION) BLOCK: N times ---
                if (!speakRepeatedWhileAutoPlaying(promptText, promptLang, repeatTimes)) break

                // Wait after question is read before showing response
                delay(scaledPause(1000L))

                if (!_isAutoPlaying.value) break

                // Change feedback style to revealed so the student can verify the answer
                _feedback.value = FeedbackState.Revealed(answerText)

                // --- ANSWER BLOCK: N times ---
                if (!speakRepeatedWhileAutoPlaying(answerText, answerLang, repeatTimes)) break

                // Wait after answer speaking completes
                delay(scaledPause(1500L))

                if (!_isAutoPlaying.value) break

                // Slide upwards to next question automatically
                nextItem(byAutoPlay = true)
            }
        }
    }

    private suspend fun speakRepeatedWhileAutoPlaying(text: String, lang: String, times: Int): Boolean {
        repeat(times) {
            speakTextAndAwait(text, lang, extraDelayAfterMs = scaledPause(1200L))
            if (!_isAutoPlaying.value) return false
        }
        return true
    }

    private fun scaledPause(baseMs: Long): Long =
        (baseMs / autoSpeed.value.coerceIn(MIN_AUTO_SPEED, MAX_AUTO_SPEED)).toLong()

    fun updateAnswerCardHeight(value: Float) {
        answerCardHeight.value = value.coerceIn(150f, 620f)
    }

    fun saveAnswerCardHeight() {
        getApplication<Application>().getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
            .edit().putFloat("answer_card_height", answerCardHeight.value).apply()
    }

    private fun setAutoPlayServiceRunning(running: Boolean) {
        val app = getApplication<Application>()
        val intent = Intent(app, com.example.AutoPlayService::class.java)
        try {
            if (running) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(intent)
                else app.startService(intent)
            } else {
                app.stopService(intent)
            }
        } catch (e: Exception) {
            Log.e("TranslationViewModel", "AutoPlayService toggle failed", e)
        }
    }

    fun updateAutoSpeed(value: Float) {
        autoSpeed.value = (Math.round(value * 10f) / 10f).coerceIn(MIN_AUTO_SPEED, MAX_AUTO_SPEED)
        getApplication<Application>().getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
            .edit().putFloat("autoSpeed", autoSpeed.value).apply()
    }

    fun updateAutoRepeatCount(value: Int) {
        autoRepeatCount.value = value.coerceIn(MIN_AUTO_REPEAT, MAX_AUTO_REPEAT)
        getApplication<Application>().getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
            .edit().putInt("autoRepeatCount", autoRepeatCount.value).apply()
    }

    // --- Writing practice navigation ---
    fun syncWritingPosition(vi: String, foreign: String) {
        val index = _practicePairs.value.indexOfFirst { it.vi == vi && it.foreign == foreign }
        if (index >= 0) { _currentIndex.value = index; _feedback.value = FeedbackState.Idle; userAnswerInput.value = "" }
    }

    suspend fun speakWriting(text: String, lang: String, speed: Float) {
        writingSpeechRate = speed
        try { speakTextAndAwait(text, lang, 0L) } finally { writingSpeechRate = null; tts?.stop() }
    }
    private var writingSpeechRate: Float? = null

    fun openWriting(initialWord: String? = null) {
        stopAutoPlay()
        stopAudioLoop()
        stopPromptAudioLoop()
        isSpeechRecognitionActive.value = false
        if (_currentScreen.value == AppScreen.Setup) {
            val pairs = parseRawContent(rawTextContent.value, targetLanguage.value)
            _practicePairs.value = if (isShuffle.value) pairs.shuffled() else pairs
            _currentIndex.value = 0
        }
        writingWord.value = initialWord ?: _practicePairs.value.getOrNull(_currentIndex.value)?.let {
            parseStudyWords("${it.vi} = ${it.foreign}").firstOrNull()?.hanzi
        }.orEmpty()
        if (_currentScreen.value != AppScreen.Writing) {
            screenBeforeWriting = _currentScreen.value
        }
        _currentScreen.value = AppScreen.Writing
        saveWritingPrefs()
        saveStudyState()
    }

    fun closeWriting() {
        getApplication<Application>().getSharedPreferences("WritingStatePrefs", Context.MODE_PRIVATE)
            .edit().putBoolean("tip_dialog_open", false).apply()
        _currentScreen.value = if (screenBeforeWriting == AppScreen.Practice && _practicePairs.value.isNotEmpty()) {
            AppScreen.Practice
        } else {
            AppScreen.Setup
        }
        saveWritingPrefs()
        saveStudyState()
    }

    fun saveWritingPrefs() {
        writingBlurMode.value = writingBlurMode.value.coerceIn(0, 3)
        writingBlur.value = (writingBlurMode.value > 0)
        getApplication<Application>().getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE).edit()
            .putBoolean("writing_free", writingFreeMode.value)
            .putInt("writing_blur_mode", writingBlurMode.value)
            .putBoolean("writing_blur", writingBlur.value)
            .putBoolean("writing_autospeak", writingAutoSpeak.value)
            .putFloat("writing_speed", writingSpeed.value)
            .putString("writing_word", writingWord.value)
            .putString("screen_before_writing", screenBeforeWriting.name)
            .apply()
    }

    fun speakText(text: String, lang: String) {
        viewModelScope.launch {
            speakTextAndAwait(text, lang, extraDelayAfterMs = 0L)
        }
    }

    fun nextItem(byAutoPlay: Boolean = false) {
        if (!byAutoPlay) {
            stopAutoPlay()
        }
        lastQuizletSwipeDir.value = 0
        if (_practicePairs.value.isEmpty()) return
        _currentIndex.value = (_currentIndex.value + 1) % _practicePairs.value.size
        displayItem()
    }

    fun previousItem() {
        stopAutoPlay()
        lastQuizletSwipeDir.value = 0
        if (_practicePairs.value.isEmpty()) return
        _currentIndex.value = (_currentIndex.value - 1 + _practicePairs.value.size) % _practicePairs.value.size
        displayItem()
    }

    fun jumpToItem(index: Int) {
        stopAutoPlay()
        if (_practicePairs.value.isEmpty()) return
        if (index in 0 until _practicePairs.value.size) {
            _currentIndex.value = index
            displayItem()
        }
    }

    fun skipCurrentAndClear() {
        nextItem()
    }

    // Room CRUD options for user custom lists
    fun saveNewStudySetCompose(context: Context, folderId: Long? = null, onResult: (String) -> Unit) {
        val titleInput = deckTitle.value.trim()
        val textInput = rawTextContent.value.trim()
        if (titleInput.isEmpty()) {
            onResult("Vui lòng nhập tên Bộ Đề")
            return
        }
        if (textInput.isEmpty()) {
            onResult("Danh sách câu không được trống")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val count = parseRawContent(textInput, targetLanguage.value).size
            if (count == 0) {
                withContext(Dispatchers.Main) {
                    onResult("Không tìm thấy cặp câu hợp lệ. Vui lòng định dạng (A = B)")
                }
                return@launch
            }

            val set = StudySet(
                title = titleInput,
                language = targetLanguage.value,
                rawContent = textInput,
                isPreset = false,
                lastModified = System.currentTimeMillis(),
                folderId = folderId
            )
            repository.insert(set)
            withContext(Dispatchers.Main) {
                onResult("Đã lưu bộ đề: '$titleInput' ($count câu)!")
            }
        }
    }

    fun deleteStudySet(studySet: StudySet, onResult: ((Boolean, String) -> Unit)? = null) {
        val app = getApplication<Application>()
        if (BuiltinLibraryCatalog.isProtectedStudySet(studySet, allFolders.value)) {
            val msg = "Không thể xóa bài học gốc '${studySet.title}'. Bộ dữ liệu sách & từ vựng có sẵn được bảo vệ!"
            LockscreenService.sendSystemAlertNotification(app, "Bảo vệ bộ dữ liệu có sẵn", msg)
            onResult?.invoke(false, msg)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(studySet)
            val msg = "Đã xóa bài học '${studySet.title}' thành công."
            withContext(Dispatchers.Main) {
                LockscreenService.sendSystemAlertNotification(app, "Đã xóa bài học", msg)
                onResult?.invoke(true, msg)
            }
        }
    }

    fun saveWordToMultipleFoldersAndSets(
        vi: String,
        foreign: String,
        selectedFolderIds: Set<Long>,
        selectedSetIds: Set<Long>,
        onDone: (String) -> Unit
    ) {
        val cleanLine = "${vi.trim()} = ${foreign.trim()}"
        recordWordSavedTimestamp(foreign, vi, tag = "Đã lưu")
        viewModelScope.launch(Dispatchers.IO) {
            var savedCount = 0
            val currentSets = allStudySets.value
            val currentFolders = allFolders.value

            // 1. Save to explicitly selected StudySets
            for (setId in selectedSetIds) {
                val targetSet = currentSets.find { it.id == setId } ?: repository.getById(setId) ?: continue
                val existingLines = targetSet.rawContent.lines().map { it.trim() }
                if (!existingLines.contains(cleanLine)) {
                    val updatedRaw = if (targetSet.rawContent.isBlank()) cleanLine else "$cleanLine\n${targetSet.rawContent.trim()}"
                    repository.update(targetSet.copy(rawContent = updatedRaw, lastModified = System.currentTimeMillis()))
                }
                savedCount++
            }

            // 2. Save to selected Folders (into a dedicated 'Từ vựng đã lưu' set inside each folder if no set in that folder was already updated)
            for (folderId in selectedFolderIds) {
                val folder = currentFolders.find { it.id == folderId } ?: continue
                val existingSavedSet = currentSets.find {
                    it.folderId == folderId && (it.title.startsWith("📌") || it.title.startsWith("📝") || !BuiltinLibraryCatalog.isProtectedStudySet(it, currentFolders))
                }
                if (existingSavedSet != null && !selectedSetIds.contains(existingSavedSet.id)) {
                    val existingLines = existingSavedSet.rawContent.lines().map { it.trim() }
                    if (!existingLines.contains(cleanLine)) {
                        val updatedRaw = if (existingSavedSet.rawContent.isBlank()) cleanLine else "$cleanLine\n${existingSavedSet.rawContent.trim()}"
                        repository.update(existingSavedSet.copy(rawContent = updatedRaw, lastModified = System.currentTimeMillis()))
                    }
                    savedCount++
                } else if (existingSavedSet == null) {
                    val newSet = StudySet(
                        title = "📌 Từ đã lưu - ${folder.name.take(22)}",
                        language = targetLanguage.value,
                        rawContent = cleanLine,
                        isPreset = false,
                        lastModified = System.currentTimeMillis(),
                        folderId = folderId
                    )
                    repository.insert(newSet)
                    savedCount++
                }
            }

            val dateStr = getWordSavedDateFormatted(foreign)
            withContext(Dispatchers.Main) {
                onDone("Đã lưu vào $savedCount thư mục / bài học (Ngày lưu: $dateStr)!")
            }
        }
    }

    fun saveQuickVocabOrNote(
        isNoteType: Boolean,
        viOrNote: String,
        foreignOrHanzi: String,
        pinyinOrTag: String = "",
        customSubSetTitle: String = "",
        onDone: (String) -> Unit
    ) {
        val tagToSave = if (isNoteType && pinyinOrTag in listOf("Chưa thuộc", "Cần ôn tập", "Hay nhầm", "Đã thuộc")) pinyinOrTag else "Đã lưu"
        recordWordSavedTimestamp(foreignOrHanzi, viOrNote, tag = tagToSave)
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureQuickSaveFoldersExist()
            val folders = database.studySetDao().getFoldersSync()
            val sets = database.studySetDao().getStudySetsSync()
            val targetFolderName = if (isNoteType) {
                BuiltinLibraryCatalog.QUICK_NOTE_FOLDER_NAME
            } else {
                BuiltinLibraryCatalog.QUICK_VOCAB_FOLDER_NAME
            }
            val defaultSetTitle = if (isNoteType) {
                BuiltinLibraryCatalog.QUICK_NOTE_SET_TITLE
            } else {
                BuiltinLibraryCatalog.QUICK_VOCAB_SET_TITLE
            }

            val folder = folders.find { it.name == targetFolderName }
            val folderId = folder?.id ?: database.studySetDao().insertFolder(Folder(name = targetFolderName))
            val desiredTitle = customSubSetTitle.trim().ifEmpty { defaultSetTitle }

            val formattedLine = if (isNoteType) {
                val tagPrefix = if (pinyinOrTag.isNotBlank()) "[${pinyinOrTag.trim()}] " else "[Ghi chú] "
                val rhs = foreignOrHanzi.trim().ifEmpty { "笔记 (Bǐjì)" }
                "$tagPrefix${viOrNote.trim()} = $rhs"
            } else {
                val p = pinyinOrTag.trim().trim('(', ')', '[', ']')
                val rhs = if (p.isNotEmpty() && !foreignOrHanzi.contains("(")) {
                    "${foreignOrHanzi.trim()} ($p)"
                } else {
                    foreignOrHanzi.trim()
                }
                "${viOrNote.trim()} = $rhs"
            }

            val targetSet = sets.find { it.folderId == folderId && it.title.equals(desiredTitle, ignoreCase = true) }
            if (targetSet != null) {
                val newRaw = if (targetSet.rawContent.isBlank()) formattedLine else "$formattedLine\n${targetSet.rawContent.trim()}"
                repository.update(targetSet.copy(rawContent = newRaw, lastModified = System.currentTimeMillis()))
            } else {
                repository.insert(
                    StudySet(
                        title = desiredTitle,
                        language = "zh",
                        rawContent = formattedLine,
                        isPreset = false,
                        lastModified = System.currentTimeMillis(),
                        folderId = folderId
                    )
                )
            }

            val dateStr = getWordSavedDateFormatted(foreignOrHanzi)
            withContext(Dispatchers.Main) {
                onDone(
                    if (isNoteType) "Đã lưu vào '$desiredTitle' (Ngày: $dateStr)!"
                    else "Đã lưu từ vựng vào '$desiredTitle' (Ngày: $dateStr)!"
                )
            }
        }
    }

    private fun extractCleanHanziKey(raw: String): String {
        val trimmed = raw.trim()
        val parenIdx = trimmed.indexOfAny(charArrayOf('(', '（', '[', '【'))
        val base = if (parenIdx != -1) trimmed.substring(0, parenIdx).trim() else trimmed
        return base.ifEmpty { trimmed }
    }

    fun formatSavedTimestamp(ms: Long): String {
        if (ms <= 0L) return ""
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(java.util.Date(ms))
    }

    fun recordWordSavedTimestamp(hanziOrForeign: String, vi: String = "", tag: String? = null) {
        val key = extractCleanHanziKey(hanziOrForeign)
        if (key.isEmpty()) return
        val now = System.currentTimeMillis()
        val prefs = getApplication<Application>().getSharedPreferences("VocabNotesPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putLong("time_$key", now)
        if (vi.isNotBlank()) editor.putString("vi_$key", vi.trim())
        if (hanziOrForeign.isNotBlank()) editor.putString("foreign_$key", hanziOrForeign.trim())
        if (!tag.isNullOrBlank()) {
            editor.putString("tag_$key", tag)
        } else if (!prefs.contains("tag_$key")) {
            editor.putString("tag_$key", "Đã lưu")
        }
        val keysSet = (prefs.getStringSet("tracked_keys", emptySet()) ?: emptySet()).toMutableSet()
        keysSet.add(key)
        editor.putStringSet("tracked_keys", keysSet)
        editor.apply()
        vocabStatusVersion.value++
    }

    fun getWordSavedTimestamp(hanziKey: String): Long {
        val key = extractCleanHanziKey(hanziKey)
        val prefs = getApplication<Application>().getSharedPreferences("VocabNotesPrefs", Context.MODE_PRIVATE)
        return prefs.getLong("time_$key", 0L)
    }

    fun getWordSavedDateFormatted(hanziKey: String, fallbackMillis: Long = 0L): String {
        val ts = getWordSavedTimestamp(hanziKey).let { if (it > 0L) it else fallbackMillis }
        return if (ts > 0L) formatSavedTimestamp(ts) else ""
    }

    fun getWordPersonalTag(hanziKey: String): String {
        val key = extractCleanHanziKey(hanziKey)
        val prefs = getApplication<Application>().getSharedPreferences("VocabNotesPrefs", Context.MODE_PRIVATE)
        return prefs.getString("tag_$key", "Đã lưu") ?: "Đã lưu"
    }

    fun getWordPersonalNote(hanziKey: String): String {
        val key = extractCleanHanziKey(hanziKey)
        val prefs = getApplication<Application>().getSharedPreferences("VocabNotesPrefs", Context.MODE_PRIVATE)
        return prefs.getString("note_$key", "") ?: ""
    }

    fun saveWordPersonalNoteAndTag(hanziKey: String, tag: String, note: String, vi: String = "", foreign: String = "") {
        val key = extractCleanHanziKey(hanziKey)
        if (key.isEmpty()) return
        val now = System.currentTimeMillis()
        val prefs = getApplication<Application>().getSharedPreferences("VocabNotesPrefs", Context.MODE_PRIVATE)
        val keysSet = (prefs.getStringSet("tracked_keys", emptySet()) ?: emptySet()).toMutableSet()
        keysSet.add(key)
        val editor = prefs.edit()
            .putString("tag_$key", tag)
            .putString("note_$key", note)
            .putLong("time_$key", now)
            .putStringSet("tracked_keys", keysSet)
        if (vi.isNotBlank()) editor.putString("vi_$key", vi.trim())
        if (foreign.isNotBlank()) editor.putString("foreign_$key", foreign.trim())
        editor.apply()
        vocabStatusVersion.value++
    }

    fun removeWordStatusRecord(hanziKey: String) {
        val key = extractCleanHanziKey(hanziKey)
        val prefs = getApplication<Application>().getSharedPreferences("VocabNotesPrefs", Context.MODE_PRIVATE)
        val keysSet = (prefs.getStringSet("tracked_keys", emptySet()) ?: emptySet()).toMutableSet()
        keysSet.remove(key)
        prefs.edit()
            .remove("tag_$key")
            .remove("note_$key")
            .remove("time_$key")
            .remove("vi_$key")
            .remove("foreign_$key")
            .putStringSet("tracked_keys", keysSet)
            .apply()
        vocabStatusVersion.value++
    }

    data class TaggedVocabRecord(
        val key: String,
        val vi: String,
        val foreign: String,
        val tag: String,
        val note: String,
        val savedTimestamp: Long,
        val formattedDate: String
    )

    fun getAllTaggedVocabRecords(): List<TaggedVocabRecord> {
        val prefs = getApplication<Application>().getSharedPreferences("VocabNotesPrefs", Context.MODE_PRIVATE)
        val tracked = (prefs.getStringSet("tracked_keys", emptySet()) ?: emptySet()).toMutableSet()
        // Also discover any tag_ keys already saved in prefs
        prefs.all.keys.forEach { k ->
            if (k.startsWith("tag_")) {
                tracked.add(k.removePrefix("tag_"))
            }
        }
        val sets = allStudySets.value
        val results = ArrayList<TaggedVocabRecord>(tracked.size)
        for (key in tracked) {
            if (key.isBlank()) continue
            val tag = prefs.getString("tag_$key", "Đã lưu") ?: "Đã lưu"
            val note = prefs.getString("note_$key", "") ?: ""
            var vi = prefs.getString("vi_$key", "") ?: ""
            var foreign = prefs.getString("foreign_$key", "") ?: key
            val ts = prefs.getLong("time_$key", 0L)

            if (vi.isBlank()) {
                // Lookup in studySets or starredPairs
                val starredMatch = allStarredPairs.value.find { it.foreign.contains(key) }
                if (starredMatch != null) {
                    vi = starredMatch.vi
                    foreign = starredMatch.foreign
                } else {
                    for (set in sets) {
                        if (!set.rawContent.contains(key)) continue
                        val line = set.rawContent.lineSequence().firstOrNull { it.contains(key) && (it.contains("=") || it.contains("#")) }
                        if (line != null) {
                            val parts = line.split("=", "#").map { it.trim() }
                            if (parts.size >= 2) {
                                val hasZh0 = parts[0].any { isCjk(it) }
                                vi = if (hasZh0) parts[1] else parts[0]
                                foreign = if (hasZh0) parts[0] else parts[1]
                                break
                            }
                        }
                    }
                }
            }
            results.add(
                TaggedVocabRecord(
                    key = key,
                    vi = vi.ifBlank { key },
                    foreign = foreign.ifBlank { key },
                    tag = tag,
                    note = note,
                    savedTimestamp = ts,
                    formattedDate = if (ts > 0L) formatSavedTimestamp(ts) else "Đã lưu"
                )
            )
        }
        return results.sortedByDescending { it.savedTimestamp }
    }

    fun startPracticeWithCustomList(pairs: List<TranslationPair>, customTitle: String): Boolean {
        if (pairs.isEmpty()) return false
        rawTextContent.value = pairs.joinToString("\n") { "${it.vi} = ${it.foreign}" }
        deckTitle.value = customTitle
        return startPractice()
    }

    fun updateStudySetContent(studySet: StudySet, newTitle: String, newRawContent: String, onDone: ((String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = studySet.copy(
                title = newTitle.trim().ifEmpty { studySet.title },
                rawContent = newRawContent.trim(),
                lastModified = System.currentTimeMillis()
            )
            repository.update(updated)
            if (deckTitle.value == studySet.title || rawTextContent.value == studySet.rawContent) {
                withContext(Dispatchers.Main) {
                    rawTextContent.value = updated.rawContent
                    deckTitle.value = updated.title
                    saveStudyState()
                }
            }
            withContext(Dispatchers.Main) {
                onDone?.invoke("Đã cập nhật bộ đề '${updated.title}'!")
            }
        }
    }

    fun reloadBuiltinLibrary(context: Context, onDone: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            isImportingLibrary.value = true
            try {
                val added = repository.ensureBuiltinLibraryLoaded(context, forceReload = true)
                withContext(Dispatchers.Main) {
                    onDone(added)
                }
            } finally {
                isImportingLibrary.value = false
            }
        }
    }

    // Quizlet Flashcard Sorting Actions
    fun setQuizletSortingEnabled(enabled: Boolean) {
        quizletSortingEnabled.value = enabled
        getApplication<Application>().getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
            .edit().putBoolean("quizlet_sorting_enabled", enabled).apply()
    }

    fun setQuizletTtsEnabled(enabled: Boolean) {
        quizletTtsEnabled.value = enabled
        getApplication<Application>().getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
            .edit().putBoolean("quizlet_tts_enabled", enabled).apply()
    }

    fun setQuizletFrontLang(lang: String) {
        quizletFrontLang.value = lang
        practiceMode.value = if (lang == "vi") "vi-foreign" else "foreign-vi"
        _currentDirection.value = if (lang == "vi") "vi" else targetLanguage.value
        getApplication<Application>().getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
            .edit()
            .putString("quizlet_front_lang", lang)
            .putString("practiceMode", practiceMode.value)
            .apply()
    }

    fun markQuizletCard(memorized: Boolean) {
        val total = _practicePairs.value.size
        if (total == 0) return
        val idx = _currentIndex.value
        lastQuizletSwipeDir.value = if (memorized) 1 else -1

        // Also sync this card to the Saved & Learning Status Hub ("Đã thuộc" / "Chưa thuộc") with timestamp
        _practicePairs.value.getOrNull(idx)?.let { currentPair ->
            val currentNote = getWordPersonalNote(currentPair.foreign)
            saveWordPersonalNoteAndTag(
                hanziKey = currentPair.foreign,
                tag = if (memorized) "Đã thuộc" else "Chưa thuộc",
                note = currentNote,
                vi = currentPair.vi,
                foreign = currentPair.foreign
            )
        }

        if (memorized) {
            _memorizedIndices.value = _memorizedIndices.value + idx
            _stillLearningIndices.value = _stillLearningIndices.value - idx
        } else {
            _stillLearningIndices.value = _stillLearningIndices.value + idx
            _memorizedIndices.value = _memorizedIndices.value - idx
        }

        val classifiedCount = (_memorizedIndices.value + _stillLearningIndices.value).size
        if (idx + 1 >= total || classifiedCount >= total) {
            _isQuizletCompleted.value = true
            stopAutoPlay()
        } else {
            _currentIndex.value = idx + 1
            displayItem()
        }
    }

    fun continueQuizletStillLearning() {
        val unmemorized = _stillLearningIndices.value.sorted().mapNotNull { _practicePairs.value.getOrNull(it) }
        if (unmemorized.isEmpty()) {
            resetQuizletProgress()
            return
        }
        _currentIndex.value = 0
        _practicePairs.value = if (isShuffle.value) unmemorized.shuffled() else unmemorized
        _memorizedIndices.value = emptySet()
        _stillLearningIndices.value = emptySet()
        _isQuizletCompleted.value = false
        displayItem()
    }

    fun resetQuizletProgress() {
        val rawInput = rawTextContent.value.trim()
        val fullPairs = parseRawContent(rawInput, targetLanguage.value)
        _currentIndex.value = 0
        if (fullPairs.isNotEmpty()) {
            var finalPairs = if (isShuffle.value) fullPairs.shuffled() else fullPairs
            if (practiceOnlyStarred.value) {
                val starredList = allStarredPairs.value
                finalPairs = finalPairs.filter { pair ->
                    starredList.any { s ->
                        s.vi.trim().lowercase() == pair.vi.trim().lowercase() &&
                            s.foreign.trim().lowercase() == pair.foreign.trim().lowercase()
                    }
                }
            }
            val limit = studyWordCountLimit.value
            if (limit > 0 && finalPairs.size > limit) {
                finalPairs = finalPairs.take(limit)
            }
            if (finalPairs.isNotEmpty()) {
                _practicePairs.value = finalPairs
            }
        }
        _memorizedIndices.value = emptySet()
        _stillLearningIndices.value = emptySet()
        _isQuizletCompleted.value = false
        displayItem()
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    // Heuristics for parsing
    private fun parseRawContent(rawContent: String, language: String): List<TranslationPair> {
        val lines = rawContent.split("\n").filter { it.contains("=") || it.contains("#") }
        return lines.mapNotNull { line ->
            val parts = line.split("=", "#").map { it.trim() }
            if (parts.size < 2) return@mapNotNull null
            
            val p1 = parts[0]
            val p2 = parts[1]
            
            if (language == "zh") {
                val hasChinese1 = p1.any { it.code in 0x4E00..0x9FFF }
                val hasChinese2 = p2.any { it.code in 0x4E00..0x9FFF }
                if (hasChinese1) {
                    TranslationPair(vi = p2, foreign = p1)
                } else if (hasChinese2) {
                    TranslationPair(vi = p1, foreign = p2)
                } else {
                    TranslationPair(vi = p1, foreign = p2)
                }
            } else {
                val viVowels = "àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđÀÁẠẢÃÂẦẤẬẨẪĂẰẮẶẲẴÈÉẸẺẼÊỀẾỆỂỄÌÍỊỈĨÒÓỌỔỖƠỜỚỢỞỠÙÚỤỦŨƯỪỨỰỬỮỲÝỴỶỸĐ"
                val hasVi1 = p1.any { viVowels.contains(it) }
                val hasVi2 = p2.any { viVowels.contains(it) }
                if (hasVi1 && !hasVi2) {
                    TranslationPair(vi = p1, foreign = p2)
                } else if (hasVi2 && !hasVi1) {
                    TranslationPair(vi = p2, foreign = p1)
                } else {
                    TranslationPair(vi = p1, foreign = p2)
                }
            }
        }.filter { it.vi.isNotEmpty() && it.foreign.isNotEmpty() }
    }

    fun normalizeText(text: String): String {
        var normalized = text.lowercase()
        
        normalized = normalized
            .replace("\\bi'm\\b".toRegex(), "i am")
            .replace("\\b(you|we|they)'re\\b".toRegex(), "$1 are")
            .replace("\\b(he|she|it)'s\\b".toRegex(), "$1 is")
            .replace("\\b(can)'t\\b".toRegex(), "$1 not")
            .replace("\\b(won)'t\\b".toRegex(), "will not")
            .replace("\\b(don|doesn|didn|haven|hasn|hadn|isn|aren|wasn|weren|shouldn|wouldn|couldn)'t\\b".toRegex(), "$1 not")
            .replace("\\b(i|you|he|she|it|we|they)'ll\\b".toRegex(), "$1 will")
            .replace("\\b(i|you|he|she|it|we|they)'d\\b".toRegex(), "$1 would")
            .replace("\\b(i|you|he|she|it|we|they)'ve\\b".toRegex(), "$1 have")

        // Remove parenthesis chunks
        normalized = normalized.replace("\\(.*?\\)".toRegex(), "")
        normalized = normalized.replace("\\uff08.*?\\uff09".toRegex(), "")
        normalized = normalized.replace("\\[.*?\\]".toRegex(), "")
        
        // Strip punctuations and spacing
        val punctRegex = "[.,?!;:'\"“”…‘’。，、？！；：\\s]".toRegex()
        normalized = normalized.replace(punctRegex, "")
        
        return normalized
    }

    fun saveStudyState() {
        Log.d("StudyState", "Saving state: screen=${_currentScreen.value}, index=${_currentIndex.value}")
        val sharedPrefs = getApplication<Application>().getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putString("rawTextContent", rawTextContent.value)
            putString("targetLanguage", targetLanguage.value)
            putString("practiceMode", practiceMode.value)
            putBoolean("isShuffle", isShuffle.value)
            putInt("currentIndex", _currentIndex.value)
            putString("currentScreen", _currentScreen.value.name)
            putString("deckTitle", deckTitle.value)
            
            val serializedPairs = _practicePairs.value.joinToString("\n") { "${it.vi}||${it.foreign}" }
            putString("serializedPairs", serializedPairs)
            putString("currentDirection", _currentDirection.value)
            apply()
        }
        LockscreenService.notifyWordChanged(getApplication())
    }

    fun loadStudyState() {
        Log.d("StudyState", "Loading saved state...")
        val sharedPrefs = getApplication<Application>().getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
        
        // Restore lockscreen & passive notification settings
        isLockscreenEnabled.value = sharedPrefs.getBoolean("lockscreen_enabled", false)
        isPassiveNotifEnabled.value = sharedPrefs.getBoolean("passive_notif_enabled", false)
        passiveAutoIntervalSec.value = sharedPrefs.getInt("passive_auto_interval_sec", 0)
        passiveAutoSpeak.value = sharedPrefs.getBoolean("passive_auto_speak", false)
        lockscreenPopupEnabled.value = sharedPrefs.getBoolean("lockscreen_popup_enabled", true)

        // Restore Quizlet flashcard settings & word count limit
        quizletSortingEnabled.value = sharedPrefs.getBoolean("quizlet_sorting_enabled", false)
        quizletTtsEnabled.value = sharedPrefs.getBoolean("quizlet_tts_enabled", true)
        quizletFrontLang.value = sharedPrefs.getString("quizlet_front_lang", "zh") ?: "zh"
        studyWordCountLimit.value = sharedPrefs.getInt("study_word_count_limit", 0).coerceAtLeast(0)

        autoRepeatCount.value = sharedPrefs.getInt("autoRepeatCount", 2).coerceIn(MIN_AUTO_REPEAT, MAX_AUTO_REPEAT)
        autoSpeed.value = sharedPrefs.getFloat("autoSpeed", 1f).coerceIn(MIN_AUTO_SPEED, MAX_AUTO_SPEED)
        writingFreeMode.value = sharedPrefs.getBoolean("writing_free", false)
        writingBlurMode.value = sharedPrefs.getInt("writing_blur_mode", if (sharedPrefs.getBoolean("writing_blur", true)) 1 else 0)
        writingBlurMode.value = writingBlurMode.value.coerceIn(0, 3)
        writingBlur.value = (writingBlurMode.value > 0)
        writingAutoSpeak.value = sharedPrefs.getBoolean("writing_autospeak", true)
        answerCardHeight.value = sharedPrefs.getFloat("answer_card_height", 220f).coerceIn(150f, 620f)
        writingSpeed.value = sharedPrefs.getFloat("writing_speed", 1.5f).coerceIn(0.5f, 3f)
        writingWord.value = sharedPrefs.getString("writing_word", "") ?: ""
        val beforeStr = sharedPrefs.getString("screen_before_writing", AppScreen.Setup.name) ?: AppScreen.Setup.name
        screenBeforeWriting = if (beforeStr == AppScreen.Practice.name) AppScreen.Practice else AppScreen.Setup

        val screenStr = sharedPrefs.getString("currentScreen", AppScreen.Setup.name) ?: AppScreen.Setup.name
        val raw = sharedPrefs.getString("rawTextContent", "") ?: ""
        if (raw.isNotEmpty()) {
            rawTextContent.value = raw
            targetLanguage.value = sharedPrefs.getString("targetLanguage", "zh") ?: "zh"
            practiceMode.value = sharedPrefs.getString("practiceMode", "foreign-vi") ?: "foreign-vi"
            isShuffle.value = sharedPrefs.getBoolean("isShuffle", true)
            deckTitle.value = sharedPrefs.getString("deckTitle", "") ?: ""
            
            _currentScreen.value = when (screenStr) {
                AppScreen.Practice.name -> AppScreen.Practice
                AppScreen.Writing.name -> AppScreen.Writing
                else -> AppScreen.Setup
            }
            
            val savedPairsStr = sharedPrefs.getString("serializedPairs", "") ?: ""
            if (savedPairsStr.isNotEmpty() && (_currentScreen.value == AppScreen.Practice || _currentScreen.value == AppScreen.Writing)) {
                val pairs = savedPairsStr.split("\n").mapNotNull { line ->
                    val parts = line.split("||")
                    if (parts.size == 2) TranslationPair(parts[0], parts[1]) else null
                }
                if (pairs.isNotEmpty()) {
                    _practicePairs.value = pairs
                    val savedIndex = sharedPrefs.getInt("currentIndex", 0)
                    _currentIndex.value = if (savedIndex in pairs.indices) savedIndex else 0
                    _currentDirection.value = sharedPrefs.getString("currentDirection", targetLanguage.value) ?: targetLanguage.value
                    
                    // Delay speak slightly on restore if in Practice screen
                    if (_currentScreen.value == AppScreen.Practice && quizletTtsEnabled.value) {
                        viewModelScope.launch {
                            delay(500)
                            speakPrompt()
                        }
                    }
                } else if (_currentScreen.value == AppScreen.Practice) {
                    _currentScreen.value = AppScreen.Setup
                }
            }
        } else if (screenStr == AppScreen.Writing.name) {
            _currentScreen.value = AppScreen.Writing
        }
    }

    fun togglePassiveNotification(context: Context, enabled: Boolean) {
        isPassiveNotifEnabled.value = enabled
        val prefs = context.getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("passive_notif_enabled", enabled).apply()
        syncPassiveServiceState(context)
    }

    fun updatePassiveSettings(
        context: Context,
        intervalSec: Int = passiveAutoIntervalSec.value,
        autoSpeak: Boolean = passiveAutoSpeak.value,
        popupOnLock: Boolean = lockscreenPopupEnabled.value
    ) {
        passiveAutoIntervalSec.value = intervalSec
        passiveAutoSpeak.value = autoSpeak
        lockscreenPopupEnabled.value = popupOnLock
        context.getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE).edit()
            .putInt("passive_auto_interval_sec", intervalSec)
            .putBoolean("passive_auto_speak", autoSpeak)
            .putBoolean("lockscreen_popup_enabled", popupOnLock)
            .apply()
        LockscreenService.notifyWordChanged(context)
    }

    private fun syncPassiveServiceState(context: Context) {
        val shouldRun = isLockscreenEnabled.value || isPassiveNotifEnabled.value
        val serviceIntent = Intent(context, LockscreenService::class.java).apply {
            action = LockscreenService.ACTION_REFRESH
        }
        if (shouldRun) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            context.stopService(Intent(context, LockscreenService::class.java))
        }
        com.example.VocabAppWidgetProvider.updateAllWidgets(context)
    }

    fun toggleLockscreen(context: Context) {
        val newVal = !isLockscreenEnabled.value
        if (newVal) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                android.widget.Toast.makeText(context, "Vui lòng cấp quyền 'Hiển thị trên các ứng dụng khác' để dùng màn hình khóa!", android.widget.Toast.LENGTH_LONG).show()
                return
            }
        }

        isLockscreenEnabled.value = newVal
        val prefs = context.getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("lockscreen_enabled", newVal).apply()
        syncPassiveServiceState(context)
    }
}
