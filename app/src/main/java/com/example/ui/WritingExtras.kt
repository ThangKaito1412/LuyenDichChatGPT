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
    var note by remember(word) { mutableStateOf(prefs.getString("$word.text", "").orEmpty()) }
    var picture by remember(word) { mutableStateOf(prefs.getString("$word.image", "").orEmpty()) }
    val lines = remember(word) { mutableStateListOf<List<Offset>>().apply {
        runCatching { val a = JSONArray(prefs.getString("$word.ink", "[]")); for (i in 0 until a.length()) {
            val stroke = a.getJSONArray(i); add((0 until stroke.length()).map { j -> val p = stroke.getJSONArray(j); Offset(p.getDouble(0).toFloat(), p.getDouble(1).toFloat()) })
        } }
    } }
    val live = remember { mutableStateListOf<Offset>() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }; picture = uri.toString() }
    }
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(picture) {
        bitmap = withContext(Dispatchers.IO) { runCatching {
            if (picture.startsWith("content:")) context.contentResolver.openInputStream(android.net.Uri.parse(picture))?.use { BitmapFactory.decodeStream(it) }
            else if (picture.startsWith("https://")) {
                val connection = URL(picture).openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }
                connection.getInputStream().use { BitmapFactory.decodeStream(it) }
            } else null
        }.getOrNull() }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Mẹo nhớ cá nhân") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(note, { note = it }, label = { Text("Nội dung / câu chuyện ghi nhớ") })
            OutlinedTextField(picture, { picture = it }, label = { Text("Link ảnh HTTPS") })
            TextButton(onClick = { picker.launch(arrayOf("image/*")) }) { Text("Chèn hình ảnh từ máy") }
            bitmap?.let { Image(it.asImageBitmap(), "Ảnh mẹo nhớ", Modifier.fillMaxWidth().heightIn(max = 180.dp)) }
            if (picture.isNotBlank() && bitmap == null) Text("Chưa tải được ảnh. Kiểm tra link hoặc chọn ảnh từ máy.")
            Text("Viết tay để ghi nhớ")
            Canvas(Modifier.fillMaxWidth().height(170.dp).background(Color(0xFFF0F0F0)).pointerInput(Unit) {
                detectDragGestures(onDragStart = { live.clear(); live.add(Offset(it.x / size.width, it.y / size.height)) },
                    onDragEnd = { lines.add(live.toList()); live.clear() }, onDragCancel = { live.clear() }) { change, _ ->
                    change.consume(); live.add(Offset(change.position.x / size.width, change.position.y / size.height))
                }
            }) {
                (lines.toList() + listOf(live.toList())).forEach { stroke -> stroke.zipWithNext().forEach { (a,b) ->
                    drawLine(Color.Black, Offset(a.x * size.width, a.y * size.height), Offset(b.x * size.width, b.y * size.height), 4f)
                } }
            }
            TextButton(onClick = { lines.clear(); live.clear() }) { Text("Xóa nét viết tay") }
        }
    }, confirmButton = { Button(onClick = {
        val ink = JSONArray(); lines.forEach { line -> val stroke = JSONArray(); line.forEach { stroke.put(JSONArray().put(it.x.toDouble()).put(it.y.toDouble())) }; ink.put(stroke) }
        prefs.edit().putString("$word.text", note).putString("$word.image", picture).putString("$word.ink", ink.toString()).apply(); onDismiss()
    }) { Text("Lưu mẹo") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Đóng") } })
}
