package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.BuiltinLibraryCatalog
import com.example.data.Folder
import com.example.data.StudySet
import com.example.data.isCjk
import com.example.viewmodel.TranslationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.TimeUnit

data class TwoWayTranslationResult(
    val sourceText: String,
    val sourceLang: String, // "vi" or "zh"
    val translatedText: String,
    val targetLang: String, // "zh" or "vi"
    val pinyin: String = "",
    val reverseTranslation: String = ""
)

object TwoWayTranslatorEngine {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun stripAccents(input: String): String {
        val normalized = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
        return normalized
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace('đ', 'd')
            .replace("ü", "v")
    }

    fun matchesQueryWithPinyin(lineOrText: String, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        if (lineOrText.contains(q, ignoreCase = true)) return true
        val strippedQuery = stripAccents(q)
        if (strippedQuery.length < 2) return false
        val strippedLine = stripAccents(lineOrText)
        if (strippedLine.contains(strippedQuery)) return true
        // Also support compact pinyin without spaces (e.g. "baoxian" matching "bao xian")
        val compactQuery = strippedQuery.replace(" ", "")
        if (compactQuery.length >= 3 && strippedLine.replace(" ", "").contains(compactQuery)) {
            return true
        }
        return false
    }

    suspend fun translateTwoWay(text: String, studySets: List<StudySet>): TwoWayTranslationResult = withContext(Dispatchers.IO) {
        val clean = text.trim()
        if (clean.isEmpty()) {
            return@withContext TwoWayTranslationResult("", "vi", "", "zh")
        }
        val hasChinese = clean.any { isCjk(it) }
        val sl = if (hasChinese) "zh-CN" else "vi"
        val tl = if (hasChinese) "vi" else "zh-CN"

        try {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&dt=rm&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    if (body.isNotEmpty()) {
                        val root = JSONArray(body)
                        val sentences = root.optJSONArray(0)
                        val sbTranslated = StringBuilder()
                        var extractedPinyin = ""
                        if (sentences != null) {
                            for (i in 0 until sentences.length()) {
                                val seg = sentences.optJSONArray(i) ?: continue
                                val transPart = seg.optString(0, "")
                                if (transPart.isNotEmpty() && transPart != "null") {
                                    sbTranslated.append(transPart)
                                }
                                // Romanization / Pinyin is typically at index 2 or 3 of the last segment
                                val rom2 = seg.optString(2, "")
                                val rom3 = seg.optString(3, "")
                                if (rom2.isNotEmpty() && rom2 != "null") extractedPinyin = rom2
                                else if (rom3.isNotEmpty() && rom3 != "null" && extractedPinyin.isEmpty()) extractedPinyin = rom3
                            }
                        }
                        val finalTranslated = sbTranslated.toString().trim()
                        if (finalTranslated.isNotEmpty()) {
                            return@withContext TwoWayTranslationResult(
                                sourceText = clean,
                                sourceLang = if (hasChinese) "zh" else "vi",
                                translatedText = finalTranslated,
                                targetLang = if (hasChinese) "vi" else "zh",
                                pinyin = extractedPinyin
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fall through to offline synthesis
        }

        // Offline fallback using the 18,000+ built-in studySet dictionary
        var bestVi = ""
        var bestZh = ""
        var bestPinyin = ""
        for (set in studySets) {
            for (line in set.rawContent.lineSequence()) {
                if (!line.contains("=") && !line.contains("#")) continue
                if (matchesQueryWithPinyin(line, clean)) {
                    val item = parseEditableVocabItems(line).firstOrNull() ?: continue
                    bestVi = item.vi
                    bestZh = item.hanzi
                    bestPinyin = item.pinyin
                    break
                }
            }
            if (bestZh.isNotEmpty()) break
        }

        if (bestZh.isNotEmpty()) {
            return@withContext if (hasChinese) {
                TwoWayTranslationResult(
                    sourceText = clean,
                    sourceLang = "zh",
                    translatedText = bestVi,
                    targetLang = "vi",
                    pinyin = bestPinyin
                )
            } else {
                TwoWayTranslationResult(
                    sourceText = clean,
                    sourceLang = "vi",
                    translatedText = bestZh,
                    targetLang = "zh",
                    pinyin = bestPinyin
                )
            }
        }

        TwoWayTranslationResult(
            sourceText = clean,
            sourceLang = if (hasChinese) "zh" else "vi",
            translatedText = if (hasChinese) "(Chưa tìm thấy bản dịch offline cho câu này)" else "(Chưa tìm thấy bản dịch offline cho câu này)",
            targetLang = if (hasChinese) "vi" else "zh"
        )
    }

    suspend fun performFreeOcrOnBitmap(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            val scaled = if (bitmap.width > 1024 || bitmap.height > 1024) {
                val ratio = minOf(1024f / bitmap.width, 1024f / bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt().coerceAtLeast(1),
                    (bitmap.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else bitmap

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, baos)
            val base64Image = "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

            // Try Chinese + Vietnamese OCR via OCR.space public demo key
            val formBody = FormBody.Builder()
                .add("apikey", "helloworld")
                .add("base64Image", base64Image)
                .add("language", "chs")
                .add("isOverlayRequired", "false")
                .build()

            val request = Request.Builder()
                .url("https://api.ocr.space/parse/image")
                .post(formBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string().orEmpty()
                    val json = JSONObject(bodyStr)
                    val parsedResults = json.optJSONArray("ParsedResults")
                    if (parsedResults != null && parsedResults.length() > 0) {
                        val parsedText = parsedResults.getJSONObject(0).optString("ParsedText", "").trim()
                        if (parsedText.isNotEmpty()) {
                            return@withContext parsedText.replace("\r\n", " ").replace("\n", " ").trim()
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        ""
    }
}

@Composable
fun LiveTwoWayVoiceTranslatorDialog(
    viewModel: TranslationViewModel,
    studySets: List<StudySet>,
    onDismiss: () -> Unit,
    onRequestMultiFolderSave: (vi: String, foreign: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeListeningLang by remember { mutableStateOf<String?>(null) } // "vi-VN" or "zh-CN"
    var livePartialText by remember { mutableStateOf("") }
    var manualInputText by remember { mutableStateOf("") }
    var isTranslating by remember { mutableStateOf(false) }
    var autoSpeakTranslation by remember { mutableStateOf(true) }
    var historyList by remember { mutableStateOf(listOf<TwoWayTranslationResult>()) }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    fun translateAndAppend(inputText: String) {
        val trimmed = inputText.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            isTranslating = true
            val res = TwoWayTranslatorEngine.translateTwoWay(trimmed, studySets)
            isTranslating = false
            historyList = listOf(res) + historyList
            if (autoSpeakTranslation && res.translatedText.isNotEmpty() && !res.translatedText.startsWith("(")) {
                viewModel.speakText(res.translatedText, res.targetLang)
            }
        }
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                activeListeningLang = null
            }
            override fun onError(error: Int) {
                activeListeningLang = null
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val finalSpoken = matches?.firstOrNull().orEmpty().trim()
                activeListeningLang = null
                if (finalSpoken.isNotEmpty()) {
                    livePartialText = finalSpoken
                    manualInputText = finalSpoken
                    translateAndAppend(finalSpoken)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val part = matches?.firstOrNull().orEmpty().trim()
                if (part.isNotEmpty()) {
                    livePartialText = part
                    manualInputText = part
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer?.setRecognitionListener(listener)
        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
        }
    }

    fun startListening(localeTag: String) {
        if (speechRecognizer == null) {
            Toast.makeText(context, "Thiết bị không hỗ trợ nhận diện giọng nói trực tiếp", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            speechRecognizer.cancel()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, localeTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            livePartialText = ""
            activeListeningLang = localeTag
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            activeListeningLang = null
            Toast.makeText(context, "Không thể bật micro: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    var pendingLocaleAfterPermission by remember { mutableStateOf<String?>(null) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingLocaleAfterPermission?.let { startListening(it) }
        } else {
            Toast.makeText(context, "Vui lòng cấp quyền Micro để dùng Dịch Nói 2 Chiều!", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestListen(localeTag: String) {
        if (activeListeningLang == localeTag) {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {}
            activeListeningLang = null
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startListening(localeTag)
        } else {
            pendingLocaleAfterPermission = localeTag
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🎙️ Dịch Nói Nhanh 2 Chiều (Việt ↔ Trung)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Hiển thị văn bản trực tiếp khi đang nói • Tự động phát âm 2 chiều",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live Streaming Speech Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (activeListeningLang != null)
                            Color(0xFFEF4444).copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(
                        1.5.dp,
                        if (activeListeningLang != null) Color(0xFFEF4444)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (activeListeningLang) {
                                    "vi-VN" -> "🔴 ĐANG NGHE TIẾNG VIỆT (Nói trực tiếp...)"
                                    "zh-CN" -> "🔴 ĐANG NGHE TIẾNG TRUNG (请说话...)"
                                    else -> "VĂN BẢN TRỰC TIẾP / NHẬP NHANH"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (activeListeningLang != null) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Tự đọc", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = autoSpeakTranslation,
                                    onCheckedChange = { autoSpeakTranslation = it },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = manualInputText,
                                onValueChange = { manualInputText = it },
                                placeholder = {
                                    Text(
                                        if (activeListeningLang != null) "Đang nhận diện giọng nói trực tiếp..."
                                        else "Chạm nút Micro bên dưới để nói hoặc nhập câu cần dịch...",
                                        fontSize = 13.sp
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                maxLines = 3
                            )
                            Button(
                                onClick = { translateAndAppend(manualInputText) },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Text("Dịch", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isTranslating) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(CircleShape))
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Conversation / Translation Results Stream
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (historyList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.RecordVoiceOver,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Nhấn 'Nói Tiếng Việt' hoặc 'Nói Tiếng Trung' bên dưới.\nHệ thống sẽ hiển thị văn bản trực tiếp và dịch 2 chiều tức thì!",
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(historyList) { item ->
                            val viPart = if (item.sourceLang == "vi") item.sourceText else item.translatedText
                            val zhPart = if (item.sourceLang == "zh") item.sourceText else item.translatedText
                            val zhWithPinyin = if (item.pinyin.isNotBlank() && !zhPart.contains("(")) {
                                "$zhPart (${item.pinyin})"
                            } else zhPart

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (item.sourceLang == "vi") "🇻🇳 Việt ➔ 🇨🇳 Trung" else "🇨🇳 Trung ➔ 🇻🇳 Việt",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = { viewModel.speakText(zhPart, "zh") },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Default.VolumeUp, contentDescription = "Đọc tiếng Trung", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(
                                                onClick = { viewModel.speakText(viPart, "vi") },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Default.RecordVoiceOver, contentDescription = "Đọc tiếng Việt", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.secondary)
                                            }
                                            IconButton(
                                                onClick = { onRequestMultiFolderSave(viPart, zhWithPinyin) },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Default.BookmarkAdd, contentDescription = "Lưu vào thư mục", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = zhPart,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (item.pinyin.isNotBlank()) {
                                        Text(
                                            text = "[${item.pinyin}]",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = viPart,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom 2-Way Voice Control Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isViListening = activeListeningLang == "vi-VN"
                    val isZhListening = activeListeningLang == "zh-CN"

                    Button(
                        onClick = { requestListen("vi-VN") },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isViListening) Color(0xFFEF4444) else Color(0xFF0284C7)
                        )
                    ) {
                        Icon(
                            if (isViListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Nói Tiếng Việt",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isViListening) "Dừng nghe..." else "🇻🇳 Nói Tiếng Việt",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = { requestListen("zh-CN") },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isZhListening) Color(0xFFEF4444) else Color(0xFFD97706)
                        )
                    ) {
                        Icon(
                            if (isZhListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Nói Tiếng Trung",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isZhListening) "停止..." else "🇨🇳 Nói Tiếng Trung",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ImageOcrTranslateDialog(
    initialBitmap: Bitmap?,
    studySets: List<StudySet>,
    onDismiss: () -> Unit,
    onApplyQueryToSearch: (String) -> Unit,
    onSpeak: (String, String) -> Unit,
    onSaveWordToFolders: (vi: String, foreign: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentBitmap by remember { mutableStateOf(initialBitmap) }
    var extractedText by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var translationResult by remember { mutableStateOf<TwoWayTranslationResult?>(null) }

    fun processBitmap(bmp: Bitmap) {
        currentBitmap = bmp
        scope.launch {
            isScanning = true
            val text = TwoWayTranslatorEngine.performFreeOcrOnBitmap(bmp)
            extractedText = text
            if (text.isNotBlank()) {
                translationResult = TwoWayTranslatorEngine.translateTwoWay(text, studySets)
            }
            isScanning = false
        }
    }

    LaunchedEffect(initialBitmap) {
        if (initialBitmap != null) {
            processBitmap(initialBitmap)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            processBitmap(bmp)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            takePictureLauncher.launch(null)
        } else {
            Toast.makeText(context, "Cần cấp quyền Camera để chụp ảnh dịch trực tiếp", Toast.LENGTH_SHORT).show()
        }
    }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                if (bmp != null) {
                    processBitmap(bmp)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Không thể đọc ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📷 Tra cứu & Dịch qua Hình ảnh", fontWeight = FontWeight.ExtraBold, fontSize = 16.5.sp)
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (hasCam) takePictureLauncher.launch(null)
                            else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chụp trực tiếp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Thư viện ảnh", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                currentBitmap?.let { bmp ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Ảnh đã chọn",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                if (isScanning) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Đang nhận diện chữ Hán / Tiếng Việt từ ảnh và dịch 2 chiều...", fontSize = 12.sp)
                    }
                }

                OutlinedTextField(
                    value = extractedText,
                    onValueChange = { extractedText = it },
                    label = { Text("Văn bản nhận diện từ ảnh (có thể chỉnh sửa)", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 4,
                    trailingIcon = {
                        IconButton(onClick = {
                            if (extractedText.isNotBlank()) {
                                scope.launch {
                                    isScanning = true
                                    translationResult = TwoWayTranslatorEngine.translateTwoWay(extractedText, studySets)
                                    isScanning = false
                                }
                            }
                        }) {
                            Icon(Icons.Default.Translate, contentDescription = "Dịch ngay")
                        }
                    }
                )

                translationResult?.let { res ->
                    if (res.translatedText.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Bản dịch 2 chiều:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = res.translatedText,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (res.pinyin.isNotBlank()) {
                                    Text(
                                        text = "[${res.pinyin}]",
                                        fontSize = 12.5.sp,
                                        color = Color(0xFFB45309),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { onSpeak(res.translatedText, res.targetLang) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("🔊 Nghe", fontSize = 11.5.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val vi = if (res.sourceLang == "vi") res.sourceText else res.translatedText
                                            val zh = if (res.sourceLang == "zh") res.sourceText else res.translatedText
                                            onSaveWordToFolders(vi, zh)
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("🔖 Lưu thư mục", fontSize = 11.5.sp)
                                    }
                                    Button(
                                        onClick = {
                                            onApplyQueryToSearch(extractedText.trim())
                                            onDismiss()
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("🔍 Tra trong kho", fontSize = 11.5.sp)
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

@Composable
fun MultiFolderSaveDialog(
    vi: String,
    foreign: String,
    folders: List<Folder>,
    studySets: List<StudySet>,
    viewModel: TranslationViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var editedVi by remember { mutableStateOf(vi) }
    var editedForeign by remember { mutableStateOf(foreign) }
    var selectedFolderIds by remember { mutableStateOf(setOf<Long>()) }
    var selectedSetIds by remember { mutableStateOf(setOf<Long>()) }
    var newFolderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column {
                Text(
                    text = "🔖 Lưu vào Nhiều Thư Mục & Bài Học",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "1 từ vựng hoặc câu có thể chọn lưu vào nhiều thư mục cùng lúc",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = editedForeign,
                    onValueChange = { editedForeign = it },
                    label = { Text("Chữ Hán / Câu tiếng Trung", fontSize = 11.5.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editedVi,
                    onValueChange = { editedVi = it },
                    label = { Text("Nghĩa Tiếng Việt / Ghi chú", fontSize = 11.5.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick create new folder row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        placeholder = { Text("Tạo thêm thư mục mới...", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val name = newFolderName.trim()
                            if (name.isNotEmpty()) {
                                viewModel.createFolder(name) { newId ->
                                    selectedFolderIds = selectedFolderIds + newId
                                }
                                newFolderName = ""
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("+ Tạo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = "Chọn các thư mục muốn lưu (${selectedFolderIds.size + selectedSetIds.size} đã chọn):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    folders.forEach { folder ->
                        val isChecked = selectedFolderIds.contains(folder.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                                .clickable {
                                    selectedFolderIds = if (isChecked) selectedFolderIds - folder.id else selectedFolderIds + folder.id
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedFolderIds = if (checked) selectedFolderIds + folder.id else selectedFolderIds - folder.id
                                }
                            )
                            Text(
                                text = folder.name,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedFolderIds.isEmpty() && selectedSetIds.isEmpty()) {
                        // Default to Quick Vocab folder if none checked
                        viewModel.saveQuickVocabOrNote(
                            isNoteType = false,
                            viOrNote = editedVi,
                            foreignOrHanzi = editedForeign
                        ) { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    } else {
                        viewModel.saveWordToMultipleFoldersAndSets(
                            vi = editedVi,
                            foreign = editedForeign,
                            selectedFolderIds = selectedFolderIds,
                            selectedSetIds = selectedSetIds
                        ) { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Lưu ngay", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

@Composable
fun QuickDualSaveNoteVocabDialog(
    viewModel: TranslationViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0 = Từ vựng lưu nhanh, 1 = Ghi chú cá nhân
    var hanziInput by remember { mutableStateOf("") }
    var pinyinInput by remember { mutableStateOf("") }
    var viOrNoteInput by remember { mutableStateOf("") }
    var selectedNoteTag by remember { mutableStateOf("Cần ôn tập") }
    var subTopicTitle by remember { mutableStateOf("") }

    val noteTags = listOf("Cần ôn tập", "Ngữ pháp", "Hay nhầm", "Mẹo nhớ", "Mẫu câu")

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column {
                Text(
                    text = "📌 Nhập Nhanh Từ Vựng & Ghi Chú (2 Thư Mục Riêng)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Phân loại tự động vào 2 thư mục riêng biệt để ôn tập nhanh",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 2 Type Selector Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selectedTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📌 Lưu Từ Vựng",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selectedTab == 1) Color(0xFFD97706) else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📝 Lưu Ghi Chú",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (selectedTab == 0) {
                    Text(
                        text = "Lưu vào: ${BuiltinLibraryCatalog.QUICK_VOCAB_FOLDER_NAME}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = hanziInput,
                        onValueChange = { hanziInput = it },
                        label = { Text("Từ vựng chữ Hán (VD: 保险)", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pinyinInput,
                        onValueChange = { pinyinInput = it },
                        label = { Text("Phiên âm Pinyin (VD: Bǎo xiǎn)", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = viOrNoteInput,
                        onValueChange = { viOrNoteInput = it },
                        label = { Text("Nghĩa Tiếng Việt (VD: Bảo hiểm)", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subTopicTitle,
                        onValueChange = { subTopicTitle = it },
                        label = { Text("Tên bài con (Tùy chọn, để trống = Tổng hợp)", fontSize = 11.5.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "Lưu vào: ${BuiltinLibraryCatalog.QUICK_NOTE_FOLDER_NAME}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97706)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        noteTags.forEach { tag ->
                            val isSelected = selectedNoteTag == tag
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedNoteTag = tag },
                                label = { Text(tag, fontSize = 11.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = viOrNoteInput,
                        onValueChange = { viOrNoteInput = it },
                        label = { Text("Nội dung Ghi chú / Mẹo nhớ / Ngữ pháp...", fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                    OutlinedTextField(
                        value = hanziInput,
                        onValueChange = { hanziInput = it },
                        label = { Text("Ví dụ chữ Hán / Cấu trúc đi kèm (nếu có)", fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                    OutlinedTextField(
                        value = subTopicTitle,
                        onValueChange = { subTopicTitle = it },
                        label = { Text("Tên sổ tay con (Tùy chọn, để trống = Mặc định)", fontSize = 11.5.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (viOrNoteInput.isBlank() && hanziInput.isBlank()) {
                        Toast.makeText(context, "Vui lòng nhập nội dung cần lưu!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (selectedTab == 0) {
                        viewModel.saveQuickVocabOrNote(
                            isNoteType = false,
                            viOrNote = viOrNoteInput.ifBlank { hanziInput },
                            foreignOrHanzi = hanziInput.ifBlank { viOrNoteInput },
                            pinyinOrTag = pinyinInput,
                            customSubSetTitle = subTopicTitle
                        ) { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        viewModel.saveQuickVocabOrNote(
                            isNoteType = true,
                            viOrNote = viOrNoteInput.ifBlank { hanziInput },
                            foreignOrHanzi = hanziInput.ifBlank { "笔记 (Ghi chú)" },
                            pinyinOrTag = selectedNoteTag,
                            customSubSetTitle = subTopicTitle
                        ) { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                    onDismiss()
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (selectedTab == 0) "Lưu vào Thư mục Từ vựng" else "Lưu vào Thư mục Ghi chú", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}
