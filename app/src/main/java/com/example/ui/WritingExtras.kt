package com.example.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.composed
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL

fun Modifier.tripleTap(onTriple: () -> Unit): Modifier = composed {
    val currentOnTriple by rememberUpdatedState(onTriple)
    pointerInput(Unit) {
        var taps = 0
        var last = 0L
        detectTapGestures(onTap = {
            val now = android.os.SystemClock.uptimeMillis()
            taps = if (now - last < 600) taps + 1 else 1
            last = now
            if (taps == 3) {
                taps = 0
                currentOnTriple()
            }
        })
    }
}

// Delay single/double actions until the triple-tap window ends, so selecting a row
// cannot close the picker before a fast star gesture has completed.
fun Modifier.wordTaps(onSingle: () -> Unit, onDouble: () -> Unit, onTriple: () -> Unit): Modifier = composed {
    val single by rememberUpdatedState(onSingle)
    val double by rememberUpdatedState(onDouble)
    val triple by rememberUpdatedState(onTriple)
    val scope = rememberCoroutineScope()
    pointerInput(Unit) {
        var count = 0
        var pending: Job? = null
        detectTapGestures(onTap = {
            pending?.cancel(); count++
            if (count == 3) { count = 0; triple() }
            else pending = scope.launch {
                delay(400)
                val taps = count; count = 0
                if (taps == 2) double() else single()
            }
        })
    }
}

@Stable
class WritingOptions(context: Context) {
    private val prefs = context.getSharedPreferences("WritingOptions", Context.MODE_PRIVATE)
    var output by mutableIntStateOf(prefs.getInt("output", 2))
    var readCount by mutableIntStateOf(prefs.getInt("readCount", 2))
    var writeCount by mutableIntStateOf(prefs.getInt("writeCount", 2))
    var readSpeed by mutableFloatStateOf(prefs.getFloat("readSpeed", 1f))
    var writeSpeed by mutableFloatStateOf(prefs.getFloat("writeSpeed", 1f))
    var autoHide by mutableStateOf(prefs.getBoolean("autoHide", true))
    fun save() { prefs.edit().putInt("output", output).putInt("readCount", readCount)
        .putInt("writeCount", writeCount).putFloat("readSpeed", readSpeed)
        .putFloat("writeSpeed", writeSpeed).putBoolean("autoHide", autoHide).apply() }
}

@Composable
fun WritingSettings(
    o: WritingOptions,
    autoSpeak: Boolean,
    onSpeak: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { o.save(); onDismiss() },
        title = { Text("Cài đặt luyện viết", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text("Tự đọc khi đổi chữ", Modifier.weight(1f), fontSize = 14.sp)
                    Switch(checked = autoSpeak, onCheckedChange = onSpeak)
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Đọc trước khi viết:",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(4.dp))
                listOf("Tiếng Trung", "Tiếng Việt", "Việt rồi Trung").forEachIndexed { i, label ->
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .clickable { o.output = i }
                            .padding(horizontal = 4.dp)
                    ) {
                        RadioButton(
                            selected = o.output == i,
                            onClick = { o.output = i },
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(label, fontSize = 14.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text("Làm mờ theo ngôn ngữ đang đọc", Modifier.weight(1f), fontSize = 14.sp)
                    Switch(checked = o.autoHide, onCheckedChange = { o.autoHide = it })
                }

                Spacer(Modifier.height(6.dp))
                Text("Số lần đọc mỗi ngôn ngữ: ${o.readCount}", fontSize = 13.sp)
                Slider(o.readCount.toFloat(), { o.readCount = it.toInt() }, valueRange = 1f..10f, steps = 8)

                Text("Tốc độ đọc: %.1fx".format(o.readSpeed), fontSize = 13.sp)
                Slider(o.readSpeed, { o.readSpeed = it }, valueRange = 0.5f..2f)

                Text("Số lần viết mỗi chữ: ${o.writeCount}", fontSize = 13.sp)
                Slider(o.writeCount.toFloat(), { o.writeCount = it.toInt() }, valueRange = 1f..10f, steps = 8)

                Text("Tốc độ viết: %.1fx".format(o.writeSpeed), fontSize = 13.sp)
                Slider(o.writeSpeed, { o.writeSpeed = it }, valueRange = 0.5f..3f)
            }
        },
        confirmButton = {
            Button(onClick = { o.save(); onDismiss() }) {
                Text("Xong")
            }
        }
    )
}

@Composable
fun MemoryTipDialog(word: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("MemoryTips", Context.MODE_PRIVATE) }
    val writingStatePrefs = remember { context.getSharedPreferences("WritingStatePrefs", Context.MODE_PRIVATE) }

    // Khóa cứng từ vựng đang mở mẹo nhớ để khi mở trình chọn tệp (nhiều tab làm hệ thống khởi tạo lại)
    // đường dẫn ảnh luôn lưu đúng vào từ này, tuyệt đối không bị nhảy sang từ khác.
    val lockedWord = remember {
        val initial = word.trim().ifBlank {
            writingStatePrefs.getString("locked_tip_word", "")?.trim().orEmpty()
        }
        if (initial.isNotBlank()) {
            writingStatePrefs.edit()
                .putString("locked_tip_word", initial)
                .putBoolean("tip_dialog_open", true)
                .apply()
        }
        initial
    }

    var note by remember(lockedWord) { mutableStateOf(prefs.getString("$lockedWord.text", "").orEmpty()) }
    var picture by remember(lockedWord) { mutableStateOf(prefs.getString("$lockedWord.image", "").orEmpty()) }
    val lines = remember(lockedWord) {
        mutableStateListOf<List<Offset>>().apply {
            runCatching {
                val a = JSONArray(prefs.getString("$lockedWord.ink", "[]"))
                for (i in 0 until a.length()) {
                    val stroke = a.getJSONArray(i)
                    add((0 until stroke.length()).map { j ->
                        val p = stroke.getJSONArray(j)
                        Offset(p.getDouble(0).toFloat(), p.getDouble(1).toFloat())
                    })
                }
            }
        }
    }
    val live = remember { mutableStateListOf<Offset>() }

    fun persistCurrentTipState(updatedPicture: String = picture, updatedNote: String = note) {
        if (lockedWord.isBlank()) return
        val ink = JSONArray()
        lines.forEach { line ->
            val stroke = JSONArray()
            line.forEach { stroke.put(JSONArray().put(it.x.toDouble()).put(it.y.toDouble())) }
            ink.put(stroke)
        }
        prefs.edit()
            .putString("$lockedWord.text", updatedNote)
            .putString("$lockedWord.image", updatedPicture)
            .putString("$lockedWord.ink", ink.toString())
            .apply()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && lockedWord.isNotBlank()) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            // Sao chép ngay ảnh đã chọn vào bộ nhớ cục bộ của ứng dụng để lấy đường dẫn máy cố định
            // và lưu tức thì vào đúng từ lockedWord (tránh mất ảnh hoặc lưu nhầm từ khi máy chạy nhiều tab)
            val copiedLocalPath = runCatching {
                val dir = java.io.File(context.filesDir, "memory_tip_images").apply { mkdirs() }
                val safeKey = lockedWord.hashCode().toUInt().toString(16)
                val destFile = java.io.File(dir, "tip_${safeKey}_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && destFile.length() > 0L) destFile.absolutePath else null
            }.getOrNull()

            val finalPath = copiedLocalPath ?: uri.toString()
            picture = finalPath
            persistCurrentTipState(updatedPicture = finalPath, updatedNote = note)
        }
    }

    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(picture, lockedWord) {
        val currentPic = picture.trim()
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    currentPic.isEmpty() -> null
                    currentPic.startsWith("/") -> {
                        val f = java.io.File(currentPic)
                        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                    }
                    currentPic.startsWith("file://") -> {
                        val path = android.net.Uri.parse(currentPic).path.orEmpty()
                        val f = java.io.File(path)
                        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                    }
                    currentPic.startsWith("content:") -> {
                        context.contentResolver.openInputStream(android.net.Uri.parse(currentPic))
                            ?.use { BitmapFactory.decodeStream(it) }
                    }
                    currentPic.startsWith("http://") || currentPic.startsWith("https://") -> {
                        val connection = URL(currentPic).openConnection().apply {
                            connectTimeout = 8000
                            readTimeout = 8000
                        }
                        connection.getInputStream().use { BitmapFactory.decodeStream(it) }
                    }
                    else -> {
                        val f = java.io.File(currentPic)
                        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                    }
                }
            }.getOrNull()
        }
    }

    AlertDialog(
        onDismissRequest = {
            persistCurrentTipState()
            writingStatePrefs.edit().putBoolean("tip_dialog_open", false).apply()
            onDismiss()
        },
        title = {
            Column {
                Text("Mẹo nhớ cá nhân: $lockedWord", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(
                    "Đường dẫn ảnh cục bộ được khóa cố định cho từ \"$lockedWord\"",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = note,
                    onValueChange = {
                        note = it
                        prefs.edit().putString("$lockedWord.text", it).apply()
                    },
                    label = { Text("Nội dung / câu chuyện ghi nhớ") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = picture,
                    onValueChange = {
                        picture = it
                        prefs.edit().putString("$lockedWord.image", it).apply()
                    },
                    label = { Text("Đường dẫn ảnh cục bộ tại máy hoặc Link ảnh HTTPS") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            writingStatePrefs.edit()
                                .putString("locked_tip_word", lockedWord)
                                .putString("current_word", lockedWord)
                                .putBoolean("tip_dialog_open", true)
                                .apply()
                            persistCurrentTipState()
                            picker.launch(arrayOf("image/*"))
                        }
                    ) {
                        Text("📂 Chọn hình ảnh từ máy")
                    }
                    if (picture.isNotBlank()) {
                        TextButton(
                            onClick = {
                                picture = ""
                                bitmap = null
                                prefs.edit().remove("$lockedWord.image").apply()
                            }
                        ) {
                            Text("Xóa ảnh")
                        }
                    }
                }
                bitmap?.let {
                    Image(
                        it.asImageBitmap(),
                        "Ảnh mẹo nhớ",
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 190.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    )
                }
                if (picture.isNotBlank() && bitmap == null) {
                    Text(
                        "Chưa tải được ảnh. Kiểm tra đường dẫn máy hoặc chọn lại ảnh từ thư mục máy.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text("Viết tay để ghi nhớ")
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .background(Color(0xFFF0F0F0))
                        .pointerInput(lockedWord) {
                            detectDragGestures(
                                onDragStart = {
                                    live.clear()
                                    live.add(Offset(it.x / size.width, it.y / size.height))
                                },
                                onDragEnd = {
                                    lines.add(live.toList())
                                    live.clear()
                                    persistCurrentTipState()
                                },
                                onDragCancel = { live.clear() }
                            ) { change, _ ->
                                change.consume()
                                live.add(Offset(change.position.x / size.width, change.position.y / size.height))
                            }
                        }
                ) {
                    (lines.toList() + listOf(live.toList())).forEach { stroke ->
                        stroke.zipWithNext().forEach { (a, b) ->
                            drawLine(
                                Color.Black,
                                Offset(a.x * size.width, a.y * size.height),
                                Offset(b.x * size.width, b.y * size.height),
                                4f
                            )
                        }
                    }
                }
                TextButton(onClick = {
                    lines.clear()
                    live.clear()
                    persistCurrentTipState()
                }) {
                    Text("Xóa nét viết tay")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                persistCurrentTipState()
                writingStatePrefs.edit().putBoolean("tip_dialog_open", false).apply()
                onDismiss()
            }) {
                Text("Lưu mẹo")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                writingStatePrefs.edit().putBoolean("tip_dialog_open", false).apply()
                onDismiss()
            }) {
                Text("Đóng")
            }
        }
    )
}
