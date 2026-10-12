package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import androidx.core.content.ContextCompat
import com.example.data.StudySet
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.FeedbackState
import com.example.viewmodel.TranslationViewModel
import com.example.data.isCjk
import com.example.ui.AutoRepeatControl
import com.example.ui.WritingScreen
import com.example.ui.bounceClick
import com.example.ui.theme.feedback
import androidx.activity.compose.BackHandler

class MainActivity : ComponentActivity() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var activeViewModel: TranslationViewModel? = null
    private var consecutiveErrors = 0
    private var isRestarting = false
    private var isSpeechRecognizerCurrentlyRunning = false

    private fun cancelSpeechRecognizer() {
        isSpeechRecognizerCurrentlyRunning = false
        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateLockscreenConfiguration() {
        try {
            val prefs = getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
            val lockEnabled = prefs.getBoolean("lockscreen_enabled", false)
            if (lockEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    setShowWhenLocked(true)
                    setTurnScreenOn(true)
                } else {
                    @Suppress("DEPRECATION")
                    window.addFlags(
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    )
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    setShowWhenLocked(false)
                    setTurnScreenOn(false)
                } else {
                    @Suppress("DEPRECATION")
                    window.clearFlags(
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateLockscreenConfiguration()
    }

    override fun onResume() {
        super.onResume()
        updateLockscreenConfiguration()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateLockscreenConfiguration()
        enableEdgeToEdge()
        initSpeechRecognizer()

        setContent {
            var darkThemeManual by remember { mutableStateOf<Boolean?>(null) }
            val systemTheme = androidx.compose.foundation.isSystemInDarkTheme()
            val useDarkTheme = darkThemeManual ?: systemTheme

            MyApplicationTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: TranslationViewModel = viewModel()
                    activeViewModel = viewModel

                    // Reactive coordination to cleanly separate TTS and Speech Recognition
                    LaunchedEffect(
                        viewModel.isTtsSpeaking.value,
                        viewModel.isSpeechRecognitionActive.value,
                        viewModel.feedback.value
                    ) {
                        if (viewModel.isTtsSpeaking.value) {
                            cancelSpeechRecognizer()
                        } else {
                            if (viewModel.isSpeechRecognitionActive.value && viewModel.feedback.value !is FeedbackState.Correct) {
                                delay(600) // Calm delay before starting mic after TTS ends or when state becomes active
                                startSpeechRecognizerListening()
                            } else {
                                cancelSpeechRecognizer()
                            }
                        }
                    }

                    TranslationAppScreen(
                        viewModel = viewModel,
                        isDark = useDarkTheme,
                        onToggleTheme = {
                            darkThemeManual = !useDarkTheme
                        },
                        onStartDictationClick = {
                            startSpeechRecognizerListening()
                        },
                        onStopDictationClick = {
                            viewModel.isSpeechRecognitionActive.value = false
                            cancelSpeechRecognizer()
                        }
                    )
                }
            }
        }
    }

    private fun initSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            isSpeechRecognizerCurrentlyRunning = true
                        }
                        override fun onBeginningOfSpeech() {
                            isSpeechRecognizerCurrentlyRunning = true
                        }
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        
                        override fun onError(error: Int) {
                            isSpeechRecognizerCurrentlyRunning = false
                            val message = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Lỗi âm thanh"
                                SpeechRecognizer.ERROR_CLIENT -> "Lỗi kết nối client"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Thiếu quyền ghi âm"
                                SpeechRecognizer.ERROR_NETWORK -> "Lỗi kết nối mạng"
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Hết thời gian kết nối"
                                SpeechRecognizer.ERROR_NO_MATCH -> "Vui lòng nói rõ ràng hơn"
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Đang thiết lập lại micro..."
                                SpeechRecognizer.ERROR_SERVER -> "Lỗi máy chủ giọng nói"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Đang chờ giọng nói..."
                                else -> "Hãy thử nói lại"
                            }
                            
                            activeViewModel?.apply {
                                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                                    isSpeechRecognitionActive.value = false
                                    currentSpeechTranscript.value = ""
                                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                                } else if (!isSpeechRecognitionActive.value) {
                                    currentSpeechTranscript.value = ""
                                }
                            }
                            
                            if (error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                                restartListeningIfNeeded(isFromOkResult = false, errorCode = error)
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            isSpeechRecognizerCurrentlyRunning = false
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val recognizedText = matches[0]
                                activeViewModel?.handleSpeechInput(recognizedText, isFinal = true)
                            }
                            restartListeningIfNeeded(isFromOkResult = true)
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val recognizedText = matches[0]
                                activeViewModel?.handleSpeechInput(recognizedText, isFinal = false)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun recreateSpeechRecognizer() {
        runOnUiThread {
            isSpeechRecognizerCurrentlyRunning = false
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
            initSpeechRecognizer()
        }
    }

    private fun restartListeningIfNeeded(isFromOkResult: Boolean = false, errorCode: Int? = null) {
        val vm = activeViewModel ?: return
        if (!vm.isSpeechRecognitionActive.value) return
        if (vm.isTtsSpeaking.value) return
        
        // Block restart if matched correct answer to prevent audio-loop/re-triggering on transition
        if (vm.feedback.value is FeedbackState.Correct) {
            return
        }

        if (isRestarting) return
        isRestarting = true

        lifecycleScope.launch {
            try {
                if (isFromOkResult) {
                    consecutiveErrors = 0
                    delay(300)
                } else if (errorCode != null) {
                    consecutiveErrors++
                    when (errorCode) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            // Do not destroy/recreate recognizer for simple timeout or no-match.
                            // Simply hold a peaceful delay before checking if we should listen again.
                            delay(2500)
                        }
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            recreateSpeechRecognizer()
                            delay(2000)
                        }
                        else -> {
                            // Only recreate on consecutive other system mistakes
                            if (consecutiveErrors >= 3) {
                                recreateSpeechRecognizer()
                                delay(3000)
                            } else {
                                delay(1500)
                            }
                        }
                    }
                } else {
                    delay(300)
                }

                if (vm.isSpeechRecognitionActive.value && !vm.isTtsSpeaking.value && vm.feedback.value !is FeedbackState.Correct) {
                    startSpeechRecognizerListening()
                }
            } finally {
                isRestarting = false
            }
        }
    }

    private fun startSpeechRecognizerListening() {
        val vm = activeViewModel ?: return
        if (vm.isTtsSpeaking.value) {
            // Do not open mic while TTS is speaking
            return
        }
        if (isSpeechRecognizerCurrentlyRunning) {
            // Already listening, do not duplicate start
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val langCode = if (vm.currentDirection.value == "vi") {
                if (vm.targetLanguage.value == "zh") "zh-CN" else "en-US"
            } else {
                "vi-VN"
            }
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langCode)
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(langCode))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Configure custom timeout extras to tell the engine to keep listening much longer (mutes rapid automatic restarts)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try {
                if (speechRecognizer == null) {
                    initSpeechRecognizer()
                }
                if (speechRecognizer == null) {
                    vm.isSpeechRecognitionActive.value = false
                    Toast.makeText(this, "Không thể khởi chạy nhận diện giọng nói trên thiết bị này.", Toast.LENGTH_SHORT).show()
                    return
                }
                vm.isSpeechRecognitionActive.value = true
                cancelSpeechRecognizer()
                isSpeechRecognizerCurrentlyRunning = true
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                isSpeechRecognizerCurrentlyRunning = false
                // If starting fails due to engine state corruption, recreate instead of giving up
                recreateSpeechRecognizer()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }
}

@SuppressLint("ContextCast")
@Composable
fun TranslationAppScreen(
    viewModel: TranslationViewModel,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onStartDictationClick: () -> Unit,
    onStopDictationClick: () -> Unit
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val context = LocalContext.current

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { it / 7 * dir } +
                fadeIn(tween(300))).togetherWith(
                slideOutHorizontally(tween(340, easing = FastOutSlowInEasing)) { -it / 7 * dir } +
                    fadeOut(tween(200))
            )
        },
        label = "screenTransition"
    ) { screen ->
        when (screen) {
            AppScreen.Setup -> {
                SetupScreenView(
                    viewModel = viewModel,
                    isDark = isDark,
                    onToggleTheme = onToggleTheme
                )
            }
            AppScreen.Practice -> {
                PracticeScreenView(
                    viewModel = viewModel,
                    isDark = isDark,
                    onStartSpeech = {
                        onStartDictationClick()
                    },
                    onStopSpeech = {
                        onStopDictationClick()
                    }
                )
            }
            AppScreen.Writing -> {
                WritingScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun SetupScreenView(
    viewModel: TranslationViewModel,
    isDark: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val studySets by viewModel.allStudySets.collectAsState()
    val folders by viewModel.allFolders.collectAsState()
    val targetLangState = viewModel.targetLanguage.value

    var selectedFolderFilterId by remember { mutableStateOf<Long?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }
    var folderForNewDeckId by remember { mutableStateOf<Long?>(null) }
    var showFolderAssignDialogForDeck by remember { mutableStateOf<StudySet?>(null) }
    var showFolderManageDialog by remember { mutableStateOf(false) }
    var folderToRenameId by remember { mutableStateOf<Long?>(null) }
    var renameFolderInput by remember { mutableStateOf("") }
    var showConfigDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // App Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = "Luyện Dịch",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Tự học dịch song ngữ, tăng phản xạ nói",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                // Lockscreen, Settings & Theme Toggles
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isLockEnabled = viewModel.isLockscreenEnabled.value
                    IconButton(
                        onClick = { viewModel.toggleLockscreen(context) },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (isLockEnabled) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            imageVector = if (isLockEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Màn hình khóa",
                            tint = if (isLockEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { showConfigDialog = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Cấu hình luyện tập",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Đổi giao diện sáng/tối",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Writing practice entry card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick { viewModel.openWriting() }
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tập viết chữ Hán",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Xem nét mẫu • Viết theo nét • Viết tự do rồi chấm điểm",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Tập viết",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Custom list item lists (moved to top for convenience)
        val userDecks = studySets.filter { !it.isPreset }
        if (userDecks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BỘ ĐỀ CỦA BẠN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { showCreateFolderDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "Thêm thư mục", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tạo Thư mục", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }

                        if (folders.isNotEmpty()) {
                            TextButton(
                                onClick = { showFolderManageDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = "Quản lý", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Quản lý", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // Horizontal row of Folder Chips for filtering
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "All" chip
                    FilterChip(
                        selected = selectedFolderFilterId == null,
                        onClick = { selectedFolderFilterId = null },
                        label = { Text("📁 Tất cả (${userDecks.size})", fontSize = 11.sp, fontWeight = FontWeight.Medium) }
                    )

                    // "Ungrouped" chip
                    val ungroupedCount = userDecks.count { it.folderId == null }
                    FilterChip(
                        selected = selectedFolderFilterId == -1L,
                        onClick = { selectedFolderFilterId = -1L },
                        label = { Text("📦 Chưa phân loại ($ungroupedCount)", fontSize = 11.sp, fontWeight = FontWeight.Medium) }
                    )

                    // Individual Custom Folder chips
                    folders.forEach { folder ->
                        val countInFolder = userDecks.count { it.folderId == folder.id }
                        FilterChip(
                            selected = selectedFolderFilterId == folder.id,
                            onClick = { selectedFolderFilterId = folder.id },
                            label = { Text("📂 ${folder.name} ($countInFolder)", fontSize = 11.sp, fontWeight = FontWeight.Medium) }
                        )
                    }
                }
            }

            val filteredDecks = when (selectedFolderFilterId) {
                null -> userDecks
                -1L -> userDecks.filter { it.folderId == null }
                else -> userDecks.filter { it.folderId == selectedFolderFilterId }
            }

            if (filteredDecks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Trống. Không có bộ đề nào trong thư mục này.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            } else {
                items(filteredDecks) { deck ->
                    val folderOfDeck = folders.find { it.id == deck.folderId }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.rawTextContent.value = deck.rawContent
                                viewModel.targetLanguage.value = deck.language
                                Toast.makeText(context, "Đã tải: ${deck.title}!", Toast.LENGTH_SHORT).show()
                            },
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(deck.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Ngôn ngữ: ${if (deck.language == "zh") "Tiếng Trung" else "Tiếng Anh"}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .clickable { showFolderAssignDialogForDeck = deck }
                                    ) {
                                        Text(
                                            text = if (folderOfDeck != null) "📂 ${folderOfDeck.name}" else "📁 Chọn thư mục...",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Reassign folder button
                                IconButton(onClick = { showFolderAssignDialogForDeck = deck }) {
                                    Icon(
                                        Icons.Default.DriveFileMove,
                                        contentDescription = "Di chuyển thư mục",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(onClick = { viewModel.deleteStudySet(deck) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Xóa",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Input Text Block
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DANH SÁCH CÂU ĐỂ LUYỆN TẬP",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Định dạng: Câu Việt = Câu nước ngoài (ngăn cách bằng dấu = hoặc #). Mỗi cặp câu ghi trên một dòng mới.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = viewModel.rawTextContent.value,
                        onValueChange = { viewModel.rawTextContent.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        placeholder = {
                            Text(
                                text = "Ví dụ:\nchào buổi sáng = good morning\ntôi khỏe = i am fine\nThành phố này rất đẹp = This city is beautiful",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                        shape = RoundedCornerShape(16.dp),
                        maxLines = 100
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Paste & Clear buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clipData = clipboardManager.primaryClip
                                if (clipData != null && clipData.itemCount > 0) {
                                    val text = clipData.getItemAt(0).text.toString()
                                    viewModel.rawTextContent.value = text
                                    Toast.makeText(context, "Đã dán từ clipboard!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Bộ nhớ tạm trống", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Dán", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dán văn bản", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { viewModel.rawTextContent.value = "" },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f), contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Xóa", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Xóa tất cả", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Preset Quick Loads
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "TẢI NHANH CÂU MẪU ĐỀ NGHỊ",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        val matchingSets = studySets.filter { 
                            it.isPreset && it.language == targetLangState
                        }

                        if (matchingSets.isEmpty()) {
                            Text("Không có preset. Hãy kết nối cơ sở dữ liệu.", fontSize = 12.sp)
                        }

                        matchingSets.forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                    .clickable {
                                        viewModel.rawTextContent.value = preset.rawContent
                                        Toast.makeText(context, "Đã tải: ${preset.title}", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = preset.title.substringBefore(" ("),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }



        // Action Deck Persistence and Start
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "LƯU ĐỀ ĐÃ NHẬP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = viewModel.deckTitle.value,
                            onValueChange = { viewModel.deckTitle.value = it },
                            placeholder = { Text("Tên bộ câu hỏi mới...", fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                        )

                        Button(
                            onClick = {
                                viewModel.saveNewStudySetCompose(context, folderForNewDeckId) { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    viewModel.deckTitle.value = ""
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Lưu", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lưu", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Folder selector dropdown when saving
                    var folderDropdownExpanded by remember { mutableStateOf(false) }
                    val currentSelectedFolderLabel = if (folderForNewDeckId == null) {
                        "📁 Chưa phân loại / Không chọn thư mục"
                    } else {
                        "📂 " + (folders.find { it.id == folderForNewDeckId }?.name ?: "Nhấp để chọn")
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .clickable { folderDropdownExpanded = true }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Lưu vào thư mục: $currentSelectedFolderLabel",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Chọn thư mục",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = folderDropdownExpanded,
                            onDismissRequest = { folderDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("📁 Chưa phân loại", fontSize = 13.sp) },
                                onClick = {
                                    folderForNewDeckId = null
                                    folderDropdownExpanded = false
                                }
                            )
                            folders.forEach { folder ->
                                DropdownMenuItem(
                                    text = { Text("📂 ${folder.name}", fontSize = 13.sp) },
                                    onClick = {
                                        folderForNewDeckId = folder.id
                                        folderDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                }
            }
        }


    }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp, top = 24.dp)
        ) {
            Button(
                onClick = {
                    if (viewModel.rawTextContent.value.isEmpty()) {
                        Toast.makeText(context, "Mời nhập danh sách hoặc chọn câu mẫu!", Toast.LENGTH_SHORT).show()
                    } else {
                        val ok = viewModel.startPractice()
                        if (!ok) {
                            Toast.makeText(context, "Không có câu được gắn sao nào trong bộ đề này!", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Bắt đầu")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Bắt đầu Luyện tập", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // --- DIALOG POPUPS ---

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Tạo Thư mục mới", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = newFolderNameInput,
                    onValueChange = { newFolderNameInput = it },
                    placeholder = { Text("Tên thư mục...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFolderNameInput.trim()
                        if (name.isNotEmpty()) {
                            viewModel.createFolder(name)
                            newFolderNameInput = ""
                            showCreateFolderDialog = false
                            Toast.makeText(context, "Đã tạo thư mục '$name'", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Vui lòng nhập tên", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Tạo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    showFolderAssignDialogForDeck?.let { deck ->
        AlertDialog(
            onDismissRequest = { showFolderAssignDialogForDeck = null },
            title = { Text("Di chuyển vào Thư mục", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.assignSetToFolder(deck.id, null)
                                    showFolderAssignDialogForDeck = null
                                    Toast.makeText(context, "Đã bỏ phân loại", Toast.LENGTH_SHORT).show()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (deck.folderId == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Text("📁 Chưa phân loại", modifier = Modifier.padding(14.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    items(folders) { folder ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.assignSetToFolder(deck.id, folder.id)
                                    showFolderAssignDialogForDeck = null
                                    Toast.makeText(context, "Đã di chuyển vào thư mục '${folder.name}'", Toast.LENGTH_SHORT).show()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (deck.folderId == folder.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Text("📂 ${folder.name}", modifier = Modifier.padding(14.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFolderAssignDialogForDeck = null }) {
                    Text("Đóng")
                }
            }
        )
    }

    if (showFolderManageDialog) {
        AlertDialog(
            onDismissRequest = { showFolderManageDialog = false },
            title = { Text("Quản lý Thư mục", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                if (folders.isEmpty()) {
                    Text("Chưa có thư mục nào để quản lý.", fontSize = 13.sp, color = Color.Gray)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(folders) { folder ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (folderToRenameId == folder.id) {
                                        OutlinedTextField(
                                            value = renameFolderInput,
                                            onValueChange = { renameFolderInput = it },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                                        )
                                        IconButton(
                                            onClick = {
                                                val newName = renameFolderInput.trim()
                                                if (newName.isNotEmpty()) {
                                                    viewModel.renameFolder(folder, newName)
                                                    folderToRenameId = null
                                                    Toast.makeText(context, "Đã đổi tên thư mục", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = "Đồng ý", tint = MaterialTheme.feedback.success, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = { folderToRenameId = null }) {
                                            Icon(Icons.Default.Close, contentDescription = "Hủy", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    } else {
                                        Text(
                                            text = folder.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                folderToRenameId = folder.id
                                                renameFolderInput = folder.name
                                            }
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Sửa", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                viewModel.deleteFolder(folder)
                                                if (selectedFolderFilterId == folder.id) {
                                                    selectedFolderFilterId = null
                                                 }
                                                Toast.makeText(context, "Đã xóa thư mục '${folder.name}'", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {

                                             Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                         }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFolderManageDialog = false }) {
                    Text("Xong")
                }
            }
        )
    }

    if (showConfigDialog) {
        val targetLangState = viewModel.targetLanguage.value
        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cấu hình Luyện tập",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { showConfigDialog = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Language Options
                    Text("1. Ngôn ngữ mục tiêu:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { 
                                viewModel.targetLanguage.value = "en"
                            }
                        ) {
                            RadioButton(
                                selected = targetLangState == "en",
                                onClick = { viewModel.targetLanguage.value = "en" }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tiếng Anh", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { 
                                viewModel.targetLanguage.value = "zh"
                            }
                        ) {
                            RadioButton(
                                selected = targetLangState == "zh",
                                onClick = { viewModel.targetLanguage.value = "zh" }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tiếng Trung", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // 2. Mode options
                    Text("2. Chế độ luyện tập:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val activeMode = viewModel.practiceMode.value
                        val foreignLabel = if (targetLangState == "zh") "Tiếng Trung" else "Tiếng Anh"

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.practiceMode.value = "foreign-vi" }
                        ) {
                            RadioButton(
                                selected = activeMode == "foreign-vi",
                                onClick = { viewModel.practiceMode.value = "foreign-vi" }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dịch từ $foreignLabel sang Việt", fontSize = 13.sp)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.practiceMode.value = "vi-foreign" }
                        ) {
                            RadioButton(
                                selected = activeMode == "vi-foreign",
                                onClick = { viewModel.practiceMode.value = "vi-foreign" }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dịch từ Tiếng Việt sang $foreignLabel", fontSize = 13.sp)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.practiceMode.value = "random" }
                        ) {
                            RadioButton(
                                selected = activeMode == "random",
                                onClick = { viewModel.practiceMode.value = "random" }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ngẫu nhiên hai chiều", fontSize = 13.sp)
                        }
                    }

                    // 3. Shuffle checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.isShuffle.value = !viewModel.isShuffle.value }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = viewModel.isShuffle.value,
                            onCheckedChange = { viewModel.isShuffle.value = it }
                        )
                        Text("Xáo trộn thứ tự các câu", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    // 4. Starred only checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.practiceOnlyStarred.value = !viewModel.practiceOnlyStarred.value }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = viewModel.practiceOnlyStarred.value,
                            onCheckedChange = { viewModel.practiceOnlyStarred.value = it }
                        )
                        Text("⭐ Chỉ luyện tập câu gắn sao trước", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    // 5. Auto-scroll repeat count
                    Text("5. Tự cuộn - số lần lặp mỗi câu:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    AutoRepeatControl(
                        value = viewModel.autoRepeatCount.value,
                        onChange = { viewModel.updateAutoRepeatCount(it) },
                        speed = viewModel.autoSpeed.value,
                        onSpeedChange = { viewModel.updateAutoSpeed(it) }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showConfigDialog = false }) {
                    Text("Xong")
                }
            }
        )
    }
}

@Composable
fun PracticeScreenView(
    viewModel: TranslationViewModel,
    isDark: Boolean,
    onStartSpeech: () -> Unit,
    onStopSpeech: () -> Unit
) {
    val context = LocalContext.current
    val practicePairs by viewModel.practicePairs.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val currentDirection by viewModel.currentDirection.collectAsState()
    val feedback by viewModel.feedback.collectAsState()
    val isLooping by viewModel.isAudioLooping.collectAsState()
    val isPromptLooping by viewModel.isPromptAudioLooping.collectAsState()
    val isAutoPlaying by viewModel.isAutoPlaying.collectAsState()
    val isDictating = viewModel.isSpeechRecognitionActive.value
    val dictatingText = viewModel.currentSpeechTranscript.value
    val allStarredPairs by viewModel.allStarredPairs.collectAsState()
    val practicedIndices by viewModel.practicedIndices.collectAsState()

    val total = practicePairs.size
    if (total == 0) return

    var showQuestionSelector by remember { mutableStateOf(false) }
    var isSearchMenuExpanded by remember { mutableStateOf(false) }
    var showWebViewUrl by remember { mutableStateOf<String?>(null) }
    var showAutoRepeatDialog by remember { mutableStateOf(false) }
    var hideInput by remember { mutableStateOf(false) }
    var showParagraphDialog by remember { mutableStateOf(false) }
    var paragraphKeywords by remember { mutableStateOf("") }
    BackHandler { viewModel.backToSetup() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // Set permission request
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onStartSpeech()
        } else {
            viewModel.isSpeechRecognitionActive.value = false
            Toast.makeText(context, "Cần cấp quyền Microphone để trả lời bằng giọng nói", Toast.LENGTH_LONG).show()
        }
    }

    // Android 13+: cần quyền thông báo để hiện thông báo "đang tự cuộn" (dịch vụ chạy nền vẫn hoạt động nếu từ chối)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> viewModel.toggleAutoPlay() }

    val isSpeechRecognitionActiveState = viewModel.isSpeechRecognitionActive.value
    LaunchedEffect(isSpeechRecognitionActiveState, currentIndex, currentDirection) {
        if (isSpeechRecognitionActiveState) {
            val hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (hasMicPermission) {
                onStartSpeech()
            } else {
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LaunchedEffect(feedback) {
        if (feedback !is com.example.viewmodel.FeedbackState.Idle) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    val currentPair = practicePairs[currentIndex]
    val promptText = if (currentDirection == "vi") currentPair.vi else currentPair.foreign
    val directionBadgeText = if (currentDirection == "vi") "VI ➔ ${viewModel.targetLanguage.value.uppercase()}" else "${viewModel.targetLanguage.value.uppercase()} ➔ VI"

    val dragThreshold = 120f
    var dragAmountY by remember { mutableStateOf(0f) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var tapCount by remember { mutableStateOf(0) }
    var showTikTokStarPop by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val isStarred = allStarredPairs.any { s ->
        s.vi.trim().lowercase() == currentPair.vi.trim().lowercase() &&
        s.foreign.trim().lowercase() == currentPair.foreign.trim().lowercase()
    }

    // Dialog displaying list of upcoming and past questions for selective learning
    if (showQuestionSelector) {
        AlertDialog(
            onDismissRequest = { showQuestionSelector = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Danh sách câu luyện tập",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = { showQuestionSelector = false }) {
                        Text("Đóng", fontSize = 13.sp)
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chọn câu luyện tập hoặc xem lại tiến độ:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        val areAllStarred = practicePairs.isNotEmpty() && practicePairs.all { pair ->
                            allStarredPairs.any { s ->
                                s.vi.trim().lowercase() == pair.vi.trim().lowercase() &&
                                s.foreign.trim().lowercase() == pair.foreign.trim().lowercase()
                            }
                        }
                        TextButton(
                            onClick = {
                                if (areAllStarred) {
                                    viewModel.unstarAllPracticePairs()
                                } else {
                                    viewModel.starAllPracticePairs()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (areAllStarred) Icons.Default.StarBorder else Icons.Default.Star,
                                contentDescription = if (areAllStarred) "Bỏ sao tất cả" else "Sao tất cả",
                                modifier = Modifier.size(14.dp),
                                tint = if (areAllStarred) Color.Gray else Color(0xFFFFD700)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (areAllStarred) "Hủy chọn" else "Chọn tất cả ⭐",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Box(modifier = Modifier.height(300.dp)) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(practicePairs) { idx, pair ->
                                val isCurrent = idx == currentIndex
                                val isPracticed = practicedIndices.contains(idx)
                                val hasStar = allStarredPairs.any { s ->
                                    s.vi.trim().lowercase() == pair.vi.trim().lowercase() &&
                                    s.foreign.trim().lowercase() == pair.foreign.trim().lowercase()
                                }
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isCurrent) 
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else if (isPracticed)
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                            else
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
                                        )
                                        .clickable {
                                            viewModel.jumpToItem(idx)
                                            showQuestionSelector = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Câu ${idx + 1}" + if (isCurrent) " (Đang rèn luyện)" else "",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Gray
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (currentDirection == "vi") pair.vi else pair.foreign,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (hasStar) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "Starred status",
                                            tint = if (hasStar) Color(0xFFFFD700) else Color.Gray,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clickable {
                                                    viewModel.toggleStar(pair.vi, pair.foreign, viewModel.targetLanguage.value)
                                                }
                                        )
                                        
                                        if (isPracticed) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Đã làm",
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(currentIndex) {
                detectTapGestures { offset ->
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < 450) {
                        tapCount++
                    } else {
                        tapCount = 1
                    }
                    lastTapTime = now
                    if (tapCount >= 3) {
                        viewModel.starPairOnly(currentPair.vi, currentPair.foreign, viewModel.targetLanguage.value)
                        showTikTokStarPop = true
                        coroutineScope.launch {
                            delay(800)
                            showTikTokStarPop = false
                        }
                        tapCount = 0
                    }
                }
            }
            .pointerInput(currentIndex) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragAmountY += dragAmount
                    },
                    onDragEnd = {
                        if (dragAmountY < -dragThreshold) {
                            viewModel.nextItem()
                        } else if (dragAmountY > dragThreshold) {
                            viewModel.previousItem()
                        }
                        dragAmountY = 0f
                    },
                    onDragCancel = {
                        dragAmountY = 0f
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            // Upper progress indicators (Clickable to display selector)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { showQuestionSelector = true }
                .padding(vertical = 4.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "Quản lý câu hỏi",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "CÂU ${currentIndex + 1} / $total (Danh sách ▾)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Auto-scroll repeat setting pill
                Row(
                    modifier = Modifier
                        .bounceClick { showAutoRepeatDialog = true }
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isAutoPlaying) MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Số lần lặp khi Tự cuộn",
                        tint = if (isAutoPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "×${viewModel.autoRepeatCount.value}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAutoPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (currentDirection == "vi")
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else
                            Color(0xFF10B981).copy(alpha = 0.15f)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = directionBadgeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (currentDirection == "vi") MaterialTheme.colorScheme.primary else Color(0xFF10B981)
                )
            }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Progress line
        val progress = (currentIndex + 1).toFloat() / total.toFloat()
        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = tween(450, easing = FastOutSlowInEasing),
            label = "practiceProgress"
        )
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Large Flashcard sliding, Swiping and Double-Tap area (Dynamic size to prevent card squeezing)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            AnimatedContent(
                targetState = currentIndex,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInVertically { height -> height } + fadeIn()).togetherWith(
                            slideOutVertically { height -> -height } + fadeOut()
                        )
                    } else {
                        (slideInVertically { height -> -height } + fadeIn()).togetherWith(
                            slideOutVertically { height -> height } + fadeOut()
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().wrapContentHeight()
            ) { targetIndex ->
                val activePair = practicePairs[targetIndex]
                val activePromptText = if (currentDirection == "vi") activePair.vi else activePair.foreign
                                var searchSourceOriginal by remember { mutableStateOf(true) }
                val activeAnswerText = if (currentDirection == "vi") activePair.foreign else activePair.vi
                val rawSearchTerm = if (searchSourceOriginal) activePromptText else activeAnswerText
                val cleanedSearchTerm = remember(rawSearchTerm) {
                    val text = rawSearchTerm
                    val parenIndex = text.indexOfAny(charArrayOf('(', '（', '[', '【'))
                    if (parenIndex != -1) text.substring(0, parenIndex).trim() else text.trim()
                }

                Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                    // Prompt Card (Original Text block)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "ORIGINAL TEXT",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = activePromptText,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = 24.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                                    )
                                }

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // "nút gắn sao có sẵn" - explicit star button for toggling star state (Box used to prevent default M3 IconButton overlapping)
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                                .clickable { 
                                                    viewModel.toggleStar(activePair.vi, activePair.foreign, viewModel.targetLanguage.value) 
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = "Gắn sao",
                                                tint = if (isStarred) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // NEW: Tra cứu (Dictionary/Search lookup button)
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(if (isSearchMenuExpanded) MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                                .clickable { isSearchMenuExpanded = !isSearchMenuExpanded },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = "Tra cứu từ vựng",
                                                tint = if (isSearchMenuExpanded) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Pronunciation Speaker button
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                                .clickable { viewModel.speakPrompt() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.VolumeUp,
                                                contentDescription = "Nghe",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // Question continuous replay loop button
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(if (isPromptLooping) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                                .clickable { viewModel.togglePromptAudioLoop() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Lặp câu hỏi",
                                                tint = if (isPromptLooping) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Beautiful Sub-Menu for Dictionary/Lookup Links (Feature 2)
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isSearchMenuExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                        .padding(12.dp)
                                 ) {
                                     Row(
                                         verticalAlignment = Alignment.CenterVertically,
                                         horizontalArrangement = Arrangement.spacedBy(8.dp),
                                         modifier = Modifier.padding(bottom = 10.dp)
                                     ) {
                                         Text("Tra cứu từ: ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                         
                                         Box(
                                             modifier = Modifier
                                                 .clip(RoundedCornerShape(8.dp))
                                                 .background(if (searchSourceOriginal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                 .clickable { searchSourceOriginal = true }
                                                 .padding(horizontal = 10.dp, vertical = 4.dp)
                                         ) {
                                             Text("Câu Hỏi (Prompt)", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = if (searchSourceOriginal) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                         }

                                         Box(
                                             modifier = Modifier
                                                 .clip(RoundedCornerShape(8.dp))
                                                 .background(if (!searchSourceOriginal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                 .clickable { searchSourceOriginal = false }
                                                 .padding(horizontal = 10.dp, vertical = 4.dp)
                                         ) {
                                             Text("Đáp Án (Answer)", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = if (!searchSourceOriginal) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                         }
                                     }

                                     Text(
                                         text = "Tra Cứu Từ Vựng: '$cleanedSearchTerm'",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val encodedTerm = remember(cleanedSearchTerm) {
                                            java.net.URLEncoder.encode(cleanedSearchTerm, "UTF-8")
                                        }
                                        
                                        LookupChip(
                                            label = "✍️ Tập viết",
                                            onClick = {
                                                val han = cleanKeyword(activePair.foreign).filter { isCjk(it) }
                                                if (han.isNotEmpty()) {
                                                    viewModel.openWriting(han)
                                                } else {
                                                    Toast.makeText(context, "Câu này không có chữ Hán để luyện viết", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )

                                        LookupChip(
                                            label = "🔍 Chi tiết",
                                            onClick = {
                                                val rawPrompt = "Phân tích từ \"$cleanedSearchTerm\". 📚 Giải thích Từ Vựng: 📖 Giải thích chung [1 Dòng Nghĩa chính cùng icon Và Viết 3-5 câu chi tiết về nghĩa chính xác, thường dùng chỉ điều gì/ai, sắc thái cảm xúc, mức độ trang trọng] 🔍 Phân tích từng thành phần [Kẻ bảng: Pinyin, Chữ Hán, Nghĩa gốc, Vai trò trong từ] 📖 Ví dụ mở rộng theo từng chữ Hán [Với MỖI chữ Hán đưa ra ĐÚNG 5 ví dụ ưu tiên HSK 1-4. Kẻ bảng: Ví dụ, Pinyin đầy đủ, Nghĩa tiếng Việt] 💡 Mẹo để dễ nhớ [CỤ THỂ, THỰC TẾ, BỐ TRÍ TRONG 1 BẢNG: Phân tích cấu tạo từ, Liên kết với từ tiếng Việt, Câu ví dụ thực tế, Phân biệt với từ dễ nhầm, Mẹo phát âm] 👍 Từ đồng nghĩa [Tối thiểu 3 từ, kẻ bảng kèm sự khác biệt và độ phổ biến] 👎 Từ trái nghĩa [Tối thiểu 2 từ, kẻ bảng kèm giải thích] 🤝  Tạo Bảng Kiểm tra mức độ phổ biến (Cột: Từ vựng Chữ Hán, Pinyin có dấu, Nghĩa tiếng Việt, Độ phổ biến ⭐, Ngữ cảnh và Khả năng thay thế). Sắp xếp theo độ phổ biến giảm dần. Tạo Bảng 2 Tần suất xuất hiện trong câu mẫu (Ít nhất 5 câu đời sống/phim/HSK. Cột: Câu ví dụ Chữ Hán, Pinyin có dấu, Dịch nghĩa, Ghi chú ngữ pháp/Sắc thái). YÊU CẦU BẮT BUỘC: 100% Pinyin phải có dấu thanh đầy đủ, Sử dụng emoji nổi bật, Nếu nhiều nghĩa hãy chia nhóm rõ ràng. Cách dùng phổ biến [Tối thiểu 5 collocations, kẻ bảng kèm ví dụ và pinyin] ⚠️ Lưu ý cách dùng [Viết 4-6 điểm cụ thể về ngữ cảnh, lỗi thường gặp, cấu trúc] 📝 3 câu hỏi kiểm tra [1 câu trắc nghiệm ABCD điền lỗ chỉ dùng pinyin, 1 câu dịch Việt-Trung, 1 câu dịch Trung-Việt có pinyin] 🎉 Đáp án chỉ in ở cuối. YÊU CẦU BẮT BUỘC: Không bỏ sót phần nào, 100% pinyin phải có dấu thanh đầy đủ, sử dụng emoji sinh động."
                                                val url = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(rawPrompt, "UTF-8")
                                                showWebViewUrl = url
                                            }
                                        )
                                        
                                        LookupChip(
                                            label = "📈 Phổ biến",
                                            onClick = {
                                                val rawPrompt = "Đóng vai chuyên gia ngôn ngữ học Trung-Việt phân tích từ \"$cleanedSearchTerm\". Bước 1: Xác định từ tương ứng (tìm từ tiếng Trung chính xác, đồng nghĩa hoặc thay thế phổ biến). Bước 2: Tạo Bảng 1 Kiểm tra mức độ phổ biến (Cột: Từ vựng Chữ Hán, Pinyin có dấu, Nghĩa tiếng Việt, Độ phổ biến ⭐, Ngữ cảnh & Khả năng thay thế). Sắp xếp theo độ phổ biến giảm dần. Bước 3: Tạo Bảng 2 Tần suất xuất hiện trong câu mẫu (Ít nhất 5 câu đời sống/phim/HSK. Cột: Câu ví dụ Chữ Hán, Pinyin có dấu, Dịch nghĩa, Ghi chú ngữ pháp/Sắc thái). YÊU CẦU BẮT BUỘC: 100% Pinyin phải có dấu thanh đầy đủ, Sử dụng emoji nổi bật, Nếu nhiều nghĩa hãy chia nhóm rõ ràng."
                                                val url = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(rawPrompt, "UTF-8")
                                                showWebViewUrl = url
                                            }
                                        )
                                        
                                        LookupChip(
                                            label = "📝 Viết đoạn văn",
                                            onClick = {
                                                if (paragraphKeywords.isBlank()) {
                                                    paragraphKeywords = cleanKeyword(activePair.foreign)
                                                }
                                                showParagraphDialog = true
                                            }
                                        )

                                        LookupChip(
                                            label = "⛩️ Tra Hanzii",
                                            onClick = {
                                                showWebViewUrl = "https://hanzii.net/search/word/${encodedTerm}?hl=vi"
                                            }
                                        )
                                        
                                        LookupChip(
                                            label = "🖼️ Baidu",
                                            onClick = {
                                                showWebViewUrl = "https://image.baidu.com/search/index?tn=baiduimage&word=${encodedTerm}"
                                             }
                                         )
                                         
                                         LookupChip(
                                             label = "🖼️ GG Ảnh",
                                             onClick = {
                                                 showWebViewUrl = "https://www.google.com/search?tbm=isch&q=${encodedTerm}"
                                            }
                                        )
                                        
                                        LookupChip(
                                            label = "🗣️ Youglish",
                                            onClick = {
                                                showWebViewUrl = "https://youglish.com/pronounce/${encodedTerm}/chinese"
                                            }
                                        )

                                        LookupChip(
                                            label = "🎨 Tạo ảnh",
                                            onClick = {
                                                val rawPrompt = "Tạo một bức ảnh minh họa với ai nanobana2 là ý nghĩa của từ/cụm từ \"$cleanedSearchTerm\" theo cách dễ ghi nhớ nhất. Bối cảnh bức ảnh phải kể một câu chuyện thực tế hoặc thể hiện một tình huống giao tiếp. Hãy tạo neo liên tưởng đầy đủ cácMùi hương; Cảm xúc mạnh mẽ; Âm thanh đang diễn ra; và Tình huống đặc biệt để người dùng dễ hình dung, dễ nhớ nhất.. có tiếng trung pinyin đồng thời bản dịch phải là tiếng Việt. hình ảnh theo style chân thật đời sống khi vào phải tạo thẳng ảnh không chat gì thêm."
                                                val url = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(rawPrompt, "UTF-8")
                                                showWebViewUrl = url
                                            }
                                        )
                                        
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (!hideInput) {
                    val cardHeightDp = viewModel.cardHeightState.value.dp

                    // Text answer typing Area (Your Translation block - spacious, draggable and non-overlapping!)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cardHeightDp),
                        shape = RoundedCornerShape(24.dp),
                        border = androidx.compose.foundation.BorderStroke(2.2.dp, MaterialTheme.colorScheme.primary),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "BẢN DỊCH CỦA BẠN",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                        .clickable {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            hideInput = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Ẩn khung bản dịch",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))

                            var enterCountForDblClick by remember { mutableStateOf(0) }
                            LaunchedEffect(currentIndex) {
                                enterCountForDblClick = 0
                            }

                            TextField(
                                value = viewModel.userAnswerInput.value,
                                onValueChange = { inputStr ->
                                    // "không cho xuống dòng" -> filter out newlines immediately!
                                    viewModel.userAnswerInput.value = inputStr.replace("\n", "").replace("\r", "")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                                            enterCountForDblClick++
                                            if (enterCountForDblClick >= 2) {
                                                viewModel.nextItem()
                                                enterCountForDblClick = 0
                                            } else {
                                                keyboardController?.hide()
                                                focusManager.clearFocus()
                                                viewModel.checkAnswer() // enterKeyCheck
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                placeholder = { 
                                    Text(
                                        "Nhập dịch nghĩa hoặc nhấn nút Micro trợ lý để nói...", 
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                    ) 
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = true, // "không cho xuống dòng"
                                maxLines = 1,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onDone = {
                                        enterCountForDblClick++
                                        if (enterCountForDblClick >= 2) {
                                            viewModel.nextItem()
                                            enterCountForDblClick = 0
                                        } else {
                                            viewModel.checkAnswer()
                                        }
                                    }
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Bottom bar inside the translation card - separated from input above, impossible to overlap!
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Microphone active state descriptor or tips
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isDictating) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color.Red)
                                        )
                                        Text(
                                            text = "Đang thu âm...",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else {
                                        Text(
                                            text = "Dịch bằng giọng nói: Chạm Mic",
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    }
                                }

                                // 3 precise controls
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // 1. Check Answer Button (Box for safe spacing)
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .clickable {
                                                keyboardController?.hide()
                                                focusManager.clearFocus()
                                                viewModel.checkAnswer()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Kiểm tra đáp án",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // 2. Next Button (Box for safe spacing)
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { viewModel.nextItem() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Câu tiếp theo",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // 3. Mic recorder speech dictation button
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isDictating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            )
                                            .clickable {
                                                if (isDictating) {
                                                    onStopSpeech()
                                                } else {
                                                    val hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                                    if (hasMicPermission) {
                                                        onStartSpeech()
                                                    } else {
                                                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isDictating) Icons.Default.MicOff else Icons.Default.Mic,
                                            contentDescription = "Ghi âm",
                                            tint = if (isDictating) Color.White else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val density = LocalDensity.current
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures(
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                val deltaDp = with(density) { dragAmount.toDp() }
                                                val newHeight = (viewModel.cardHeightState.value + deltaDp.value).coerceIn(130f, 350f)
                                                viewModel.cardHeightState.value = newHeight
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(44.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                                )
                            }
                        }
                    }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                .clickable { hideInput = false }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "KHUNG BẢN DỊCH ĐANG ẨN",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Hiện", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Hiện khung bản dịch",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live mic Transcription indicators
        AnimatedVisibility(
            visible = isDictating,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    )
                    Text(
                        text = if (dictatingText.isEmpty() || dictatingText == "Hãy nói câu của bạn...")
                            "Đang lắng nghe... Hãy nói câu dịch của bạn"
                        else
                            "Đang nghe: '$dictatingText'",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Large Feedback Results Pane & Toggle tap area (Requirement 4: fill height & click to toggle)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Transparent)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    viewModel.toggleAnswerRevealed()
                }
                .padding(vertical = 4.dp)
        ) {
            when (val state = feedback) {
                is FeedbackState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Chạm vào vùng trống này để hiện/ẩn đáp án gợi ý",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                        )
                    }
                }
                is FeedbackState.Correct -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.feedback.successContainer),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.feedback.success.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎉  Chính xác! ",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.feedback.success,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                is FeedbackState.Incorrect -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "⚠️  Chưa đúng!",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Đáp án của bạn: '${state.userAnswer}'",
                                fontSize = 12.sp,
                                color = Color.Gray.copy(alpha = 0.8f),
                                textDecoration = TextDecoration.LineThrough
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Đáp án gợi ý:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = buildHighlightedAnswer(state.userAnswer, state.correctAnswer),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Speak Answer item (Upgraded to 48.dp perfect touch target size compliant and non-squeezable)
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
                                        .clickable { viewModel.speakAnswer() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp, 
                                        contentDescription = "Nghe", 
                                        modifier = Modifier.size(24.dp), 
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Loop Audio Speak answer (Upgraded to 48.dp perfect touch target size compliant and non-squeezable)
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(if (isLooping) MaterialTheme.colorScheme.error.copy(alpha = 0.16f) else MaterialTheme.colorScheme.error.copy(alpha = 0.04f))
                                        .clickable { viewModel.toggleAudioLoop() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Lặp",
                                        modifier = Modifier.size(24.dp),
                                        tint = if (isLooping) Color.Red else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
                is FeedbackState.Revealed -> {
                    val imageTerm = cleanKeyword(currentPair.foreign).ifBlank { cleanKeyword(currentPair.vi) }
                    val viTerm = cleanKeyword(currentPair.vi).ifBlank { imageTerm }
                    val image by com.example.ui.rememberRemoteImage(
                        key = imageTerm + "|" + viTerm,
                        urls = listOf(
                            "https://tse1.mm.bing.net/th?q=" + java.net.URLEncoder.encode(imageTerm, "UTF-8") + "&w=900&h=520&c=7&rs=1&p=0",
                            "https://tse2.mm.bing.net/th?q=" + java.net.URLEncoder.encode(viTerm, "UTF-8") + "&w=900&h=520&c=7&rs=1&p=0"
                        )
                    )
                    val hasImage = image != null
                    // Khi có ảnh nền: luôn phủ lớp tối + chữ trắng để chữ không bị chìm theo màu ảnh.
                    val fg = if (hasImage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    val chipBg = if (hasImage) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 170.dp)) {
                            image?.let { bmp ->
                                androidx.compose.foundation.Image(
                                    bitmap = bmp,
                                    contentDescription = "Hình minh họa: $imageTerm",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.matchParentSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.80f))
                                            )
                                        )
                                )
                            }
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "💡  Đáp án gợi ý:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = fg.copy(alpha = if (hasImage) 0.85f else 1f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = state.correctAnswer,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = fg,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(chipBg)
                                            .clickable { viewModel.speakAnswer() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "Nghe",
                                            modifier = Modifier.size(24.dp),
                                            tint = fg
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(if (isLooping) chipBg.copy(alpha = 0.35f) else chipBg)
                                            .clickable { viewModel.toggleAudioLoop() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Lặp",
                                            modifier = Modifier.size(24.dp),
                                            tint = if (isLooping) Color(0xFFFF6B6B) else fg
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom horizontal navigation option bars: "Quay lại, Kiểm tra, Xem đáp án, Bỏ qua"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.backToSetup() },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp, horizontal = 2.dp),
                modifier = Modifier.weight(1.0f).height(42.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("Quay lại", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    viewModel.checkAnswer()
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp, horizontal = 2.dp),
                modifier = Modifier.weight(1.6f).height(42.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = "Kiểm tra", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("Kiểm tra", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            // NEW: "Tự cuộn" (Auto-play control button) - Located in between "Xem đáp án" and "Bỏ qua"
            Button(
                onClick = {
                    val needsNotifPermission = !isAutoPlaying &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    if (needsNotifPermission) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.toggleAutoPlay()
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAutoPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    contentColor = if (isAutoPlaying) Color.White else MaterialTheme.colorScheme.primary
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp, horizontal = 2.dp),
                modifier = Modifier.weight(1.1f).height(42.dp)
            ) {
                Icon(
                    imageVector = if (isAutoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Tự động cuộn",
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(if (isAutoPlaying) "Dừng Auto" else "Tự cuộn", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            // Nút "Bỏ qua" kế nút tự cuộn để bỏ qua câu hiện tại và lướt lên
            Button(
                onClick = { viewModel.nextItem() },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp, horizontal = 2.dp),
                modifier = Modifier.weight(1.0f).height(42.dp)
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Bỏ qua", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("Bỏ qua", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showAutoRepeatDialog) {
        AlertDialog(
            onDismissRequest = { showAutoRepeatDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Cài đặt Tự cuộn", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                AutoRepeatControl(
                    value = viewModel.autoRepeatCount.value,
                    onChange = { viewModel.updateAutoRepeatCount(it) },
                    speed = viewModel.autoSpeed.value,
                    onSpeedChange = { viewModel.updateAutoSpeed(it) }
                )
            },
            confirmButton = {
                Button(onClick = { showAutoRepeatDialog = false }, shape = RoundedCornerShape(16.dp)) {
                    Text("Xong")
                }
            }
        )
    }

    if (showParagraphDialog) {
        val keywords = remember(paragraphKeywords) { parseKeywords(paragraphKeywords) }
        val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
        fun addKeywords(extra: List<String>) {
            paragraphKeywords = mergeKeywords(paragraphKeywords, extra)
        }
        AlertDialog(
            onDismissRequest = { showParagraphDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = {
                Column {
                    Text("Viết đoạn văn từ khóa", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Dịch sang tiếng Trung và viết đoạn văn ≥ 300 từ dùng đủ các từ khóa",
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = paragraphKeywords,
                        onValueChange = { paragraphKeywords = it },
                        label = { Text("Từ khóa (tiếng Việt hoặc chữ Hán, cách nhau bằng dấu phẩy)", fontSize = 12.sp) },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KeywordChip("＋ Câu hiện tại") { addKeywords(listOf(currentPair.foreign)) }
                        KeywordChip("＋ 5 câu tiếp") {
                            addKeywords((0 until minOf(5, total)).map { practicePairs[(currentIndex + it) % total].foreign })
                        }
                        KeywordChip("＋ 10 câu tiếp") {
                            addKeywords((0 until minOf(10, total)).map { practicePairs[(currentIndex + it) % total].foreign })
                        }
                        KeywordChip("⭐ Câu gắn sao") {
                            val starred = practicePairs.filter { pair ->
                                allStarredPairs.any { s ->
                                    s.vi.trim().lowercase() == pair.vi.trim().lowercase() &&
                                        s.foreign.trim().lowercase() == pair.foreign.trim().lowercase()
                                }
                            }
                            if (starred.isEmpty()) {
                                Toast.makeText(context, "Chưa có câu nào được gắn sao", Toast.LENGTH_SHORT).show()
                            } else {
                                addKeywords(starred.map { it.foreign })
                            }
                        }
                        KeywordChip("Cả bộ đề") { addKeywords(practicePairs.map { it.foreign }) }
                        KeywordChip("✕ Xóa hết") { paragraphKeywords = "" }
                    }

                    Text(
                        text = if (keywords.size >= MAX_PARAGRAPH_KEYWORDS)
                            "${keywords.size} từ khóa (đã đạt tối đa $MAX_PARAGRAPH_KEYWORDS)"
                        else "${keywords.size} từ khóa",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = keywords.isNotEmpty(),
                    shape = RoundedCornerShape(16.dp),
                    onClick = {
                        val prompt = buildParagraphPrompt(keywords.joinToString(", "))
                        showWebViewUrl = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(prompt, "UTF-8")
                        showParagraphDialog = false
                    }
                ) {
                    Text("Tạo đoạn văn")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        enabled = keywords.isNotEmpty(),
                        onClick = {
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("prompt", buildParagraphPrompt(keywords.joinToString(", ")))
                            )
                            Toast.makeText(context, "Đã sao chép prompt", Toast.LENGTH_SHORT).show()
                        }
                    ) { Text("Sao chép") }
                    TextButton(onClick = { showParagraphDialog = false }) { Text("Đóng") }
                }
            }
        )
    }

    // Elegant Floating Overlay custom WebView Dialog (Feature 2)
    if (showWebViewUrl != null) {
        var webViewRef by remember { mutableStateOf<WebView?>(null) }
        var loadedUrl by remember { mutableStateOf<String?>(showWebViewUrl) }
        
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showWebViewUrl = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Tra cứu",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cửa sổ tra cứu",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Toolbar Control buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val context = LocalContext.current
                            
                            IconButton(
                                onClick = { 
                                    if (webViewRef?.canGoBack() == true) {
                                        webViewRef?.goBack()
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Trở về trang trước", modifier = Modifier.size(18.dp))
                            }
                            
                            IconButton(
                                onClick = { webViewRef?.reload() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Tải lại", modifier = Modifier.size(18.dp))
                            }
                            
                            IconButton(
                                onClick = { 
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(showWebViewUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Không mở được trình duyệt ngoài", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Mở bằng trình duyệt ngoài", modifier = Modifier.size(18.dp))
                            }
                            
                            IconButton(
                                onClick = { showWebViewUrl = null },
                                modifier = Modifier.size(36.dp),
                                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Đóng", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    
                    // Native Interactive Android WebView container
                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    val webViewInstance = this
                                    webViewClient = WebViewClient()
                                    webChromeClient = WebChromeClient()
                                    
                                    // Enable cookies & third-party cookies for seamless login and user preferences (e.g., Google Search Labs / AI Mode)
                                    android.webkit.CookieManager.getInstance().apply {
                                        setAcceptCookie(true)
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                            setAcceptThirdPartyCookies(webViewInstance, true)
                                        }
                                    }
                                    
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        javaScriptCanOpenWindowsAutomatically = true
                                        useWideViewPort = true
                                        loadWithOverviewMode = true
                                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                        
                                        // Standardize User-Agent to match standard Google Chrome to enable Google Search AI Mode (Chế độ AI)
                                        val originalUA = userAgentString
                                        val cleanUA = originalUA
                                            .replace("; wv", "")
                                            .replace("Version/4.0 ", "")
                                        userAgentString = if (cleanUA.contains("Safari/537.36")) {
                                            cleanUA.substringBefore("Safari/537.36") + "Safari/537.36"
                                        } else {
                                            cleanUA
                                        }
                                    }
                                    showWebViewUrl?.let { loadUrl(it) }
                                }
                            },
                            update = { webView ->
                                webViewRef = webView
                                val targetUrl = showWebViewUrl
                                if (targetUrl != null && targetUrl != loadedUrl) {
                                    loadedUrl = targetUrl
                                    webView.loadUrl(targetUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // TikTok Heart/Star pop animation splash overlay
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = showTikTokStarPop,
            enter = scaleIn(initialScale = 0.2f, animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(200)),
            exit = scaleOut(targetScale = 1.6f, animationSpec = tween(400, easing = LinearOutSlowInEasing)) + fadeOut(animationSpec = tween(350))
        ) {
            Card(
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.82f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.size(130.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Double click starred",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(80.dp)
                    )
                }
            }
        }
    }
}
}

const val MAX_PARAGRAPH_KEYWORDS = 30

/** Bỏ phần chú thích trong ngoặc (pinyin, ghi chú) để lấy từ khóa gọn. */
fun cleanKeyword(text: String): String {
    val idx = text.indexOfAny(charArrayOf('(', '（', '[', '【'))
    return (if (idx != -1) text.substring(0, idx) else text).trim()
}

fun parseKeywords(text: String): List<String> =
    text.split(',', '\n', ';', '，', '、')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .take(MAX_PARAGRAPH_KEYWORDS)

fun mergeKeywords(existing: String, extra: List<String>): String =
    (parseKeywords(existing) + extra.map { cleanKeyword(it) }.filter { it.isNotEmpty() })
        .distinctBy { it.lowercase() }
        .take(MAX_PARAGRAPH_KEYWORDS)
        .joinToString(", ")

fun buildParagraphPrompt(input: String): String =
    "Sử dụng các từ vựng chữ Hán sau: \"$input\", hãy: " +
        "1. Cung cấp Pinyin và dịch nghĩa tiếng Việt cho các từ vựng này. " +
        "2. Viết một đoạn văn hoàn chỉnh (ít nhất 300 từ trở lên) bằng chữ Hán sử dụng tất cả các từ vựng trên. " +
        "3. Cung cấp Pinyin cho toàn bộ đoạn văn, trong đó bôi đậm (dùng thẻ <b> hoặc **) các từ khóa đã cho. " +
        "4. Dịch toàn bộ đoạn văn sang tiếng Việt chuẩn, tự nhiên. " +
        "5. Tạo một phiên bản dịch \"hỗn hợp\": lấy bản dịch tiếng Việt ở bước 4, nhưng thay thế các từ đã dịch bằng TỪ VỰNG GỐC (Pinyin) được tô đậm. " +
        "6. Liệt kê lại bảng từ vựng đã dùng (Chữ Hán | Pinyin | Nghĩa tiếng Việt). " +
        "ĐỊNH DẠNG ĐẦU RA BẮT BUỘC: KHÔNG trả về JSON, KHÔNG dùng code block; trình bày trực tiếp, dễ đọc, theo đúng thứ tự các mục sau, mỗi mục có tiêu đề riêng: " +
        "\"Viết Đoạn Văn Từ Khóa\" (tiêu đề chung, liệt kê danh sách từ khóa kèm pinyin và nghĩa); " +
        "\"🈶 Đoạn văn Hán tự:\" (một đoạn văn liền mạch bằng chữ Hán); " +
        "\"📝 Đoạn văn Pinyin:\" (một đoạn văn liền mạch, từ khóa in đậm); " +
        "\"📖 Bản dịch tiếng Việt:\" (một đoạn văn liền mạch); " +
        "\"📖 Bản dịch hỗn hợp (Việt - Pinyin):\" (một đoạn văn liền mạch, từ khóa thay bằng pinyin in đậm); " +
        "\"🗣️ Từ vựng đã sử dụng:\" (bảng: Chữ Hán | Pinyin | Nghĩa). " +
        "Pinyin phải có dấu thanh đầy đủ."

@Composable
fun KeywordChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun LookupChip(
    label: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.bounceClick { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

// DP-based LCS highlighter for Vietnamese / foreign letters correctly typed
fun buildHighlightedAnswer(user: String, correct: String): AnnotatedString {
    // Separate PinYin or notes if Chinese
    val parenIndex = correct.indexOfAny(charArrayOf('(', '（'))
    val mainPart = if (parenIndex != -1) correct.substring(0, parenIndex).trim() else correct
    val parenPart = if (parenIndex != -1) correct.substring(parenIndex) else ""

    val isPunct = { c: Char -> " \t\n\r\u3002\uff0c\u3001\uff1f\uff01\uff1b\uff1a,?.!;:'\"“”…‘’".contains(c) }

    val userChars = user.filter { !isPunct(it) }.map { it.lowercaseChar() }
    val correctChars = mainPart.toList()
    val correctNonPunct = correctChars.filter { !isPunct(it) }.map { it.lowercaseChar() }

    val m = userChars.size
    val n = correctNonPunct.size
    val dp = Array(m + 1) { IntArray(n + 1) { 0 } }
    for (i in 1..m) {
        for (j in 1..n) {
            if (userChars[i - 1] == correctNonPunct[j - 1]) {
                dp[i][j] = dp[i - 1][j - 1] + 1
            } else {
                dp[i][j] = maxOf(dp[i - 1][j], dp[i][j - 1])
            }
        }
    }

    val matchedCorrectIndices = mutableSetOf<Int>()
    var i = m
    var j = n
    while (i > 0 && j > 0) {
        if (userChars[i - 1] == correctNonPunct[j - 1]) {
            matchedCorrectIndices.add(j - 1)
            i--
            j--
        } else if (dp[i - 1][j] >= dp[i][j - 1]) {
            i--
        } else {
            j--
        }
    }

    return buildAnnotatedString {
        var nonPunctIdx = 0
        for (char in correctChars) {
            if (isPunct(char)) {
                append(char)
            } else {
                if (matchedCorrectIndices.contains(nonPunctIdx)) {
                    withStyle(style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                        color = Color(0xFF10B981) // Matching emerald color for corrected letters
                    )) {
                        append(char)
                    }
                } else {
                    withStyle(style = SpanStyle(
                        color = Color.Gray.copy(alpha = 0.45f)
                    )) {
                        append(char)
                    }
                }
                nonPunctIdx++
            }
        }
        if (parenPart.isNotEmpty()) {
            withStyle(style = SpanStyle(
                color = Color.Gray,
                fontStyle = FontStyle.Italic
            )) {
                append(" $parenPart")
            }
        }
    }
}
