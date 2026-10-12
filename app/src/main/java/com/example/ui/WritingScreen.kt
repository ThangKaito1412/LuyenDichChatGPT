package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FreeResult
import com.example.data.HanziChar
import com.example.data.HanziRepository
import com.example.data.StarredPair
import com.example.data.StudyWord
import com.example.data.containsCjk
import com.example.data.isCjk
import com.example.data.parseStudyWords
import com.example.data.polylineLength
import com.example.data.scoreFreeWriting
import com.example.data.strokeMatches
import com.example.viewmodel.TranslationViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

// ---------------------------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------------------------

class UserStroke(val points: List<Offset>, val path: Path, val score: Float = -1f)

@Stable
class WritingBoardState {
    val userStrokes = mutableStateListOf<UserStroke>()
    val live = mutableStateListOf<Offset>()
    var doneCount by mutableIntStateOf(0)
    var misses by mutableIntStateOf(0)
    var totalMisses by mutableIntStateOf(0)
    var flash by mutableStateOf<Path?>(null)
    var message by mutableStateOf<String?>(null)
    var result by mutableStateOf<FreeResult?>(null)
    var showAnswer by mutableStateOf(false)
    var autoProgress by mutableFloatStateOf(-1f)
}

private sealed interface HanziLoad {
    data object Loading : HanziLoad
    data class Ready(val data: HanziChar) : HanziLoad
    data class Failed(val message: String) : HanziLoad
}

private const val BRUSH_WIDTH = 36f

/** Màu luân phiên cho từng nét: san hô / xanh nhạt. */
private val StrokePalette = listOf(Color(0xFFF08080), Color(0xFFA9CBEA))

private fun strokeColor(index: Int): Color = StrokePalette[index % StrokePalette.size]

/** Làm mượt đường vẽ bằng các đoạn bậc hai đi qua trung điểm. */
private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) {
        path.lineTo(points[0].x + 0.1f, points[0].y + 0.1f)
        return path
    }
    for (i in 1 until points.size - 1) {
        val mid = Offset((points[i].x + points[i + 1].x) / 2f, (points[i].y + points[i + 1].y) / 2f)
        path.quadraticTo(points[i].x, points[i].y, mid.x, mid.y)
    }
    path.lineTo(points.last().x, points.last().y)
    return path
}

private fun partialPolyline(points: List<Offset>, fraction: Float): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) {
        path.lineTo(points[0].x + 0.1f, points[0].y + 0.1f)
        return path
    }
    var remain = polylineLength(points) * fraction
    for (i in 1 until points.size) {
        val seg = (points[i] - points[i - 1]).getDistance()
        if (seg <= remain) {
            path.lineTo(points[i].x, points[i].y)
            remain -= seg
        } else {
            val t = if (seg > 0f) remain / seg else 0f
            path.lineTo(
                points[i - 1].x + (points[i].x - points[i - 1].x) * t,
                points[i - 1].y + (points[i].y - points[i - 1].y) * t
            )
            break
        }
    }
    return path
}

private fun DrawScope.drawRevealedStroke(path: Path, median: List<Offset>, fraction: Float, color: Color) {
    if (fraction <= 0f) return
    if (fraction >= 1f) {
        drawPath(path, color)
        return
    }
    val poly = partialPolyline(median, fraction)
    clipPath(path) {
        drawPath(
            poly, color,
            style = Stroke(width = 170f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/** Ô ly kiểu "米字格": chữ thập nét liền + hai đường chéo nét đứt. */
private fun DrawScope.drawGrid(color: Color) {
    val w = size.width
    val h = size.height
    val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))
    drawLine(color, Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = 2f)
    drawLine(color, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 2f)
    drawLine(color.copy(alpha = color.alpha * 0.8f), Offset(0f, 0f), Offset(w, h), strokeWidth = 2f, pathEffect = dash)
    drawLine(color.copy(alpha = color.alpha * 0.8f), Offset(w, 0f), Offset(0f, h), strokeWidth = 2f, pathEffect = dash)
}

private fun scoreColor(score: Float): Color = when {
    score >= 0.72f -> Color(0xFF22C55E)
    score >= 0.5f -> Color(0xFFF59E0B)
    else -> Color(0xFFEF4444)
}

// ---------------------------------------------------------------------------------------------
// Board (canvas)
// ---------------------------------------------------------------------------------------------

@Composable
private fun HanziBoard(
    data: HanziChar?,
    board: WritingBoardState,
    freeMode: Boolean,
    showGhost: Boolean,
    enabled: Boolean,
    onStrokeFinished: (List<Offset>) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val gridColor = colors.onSurface.copy(alpha = 0.13f)
    val ghostColor = colors.onSurface.copy(alpha = 0.16f)
    val inkColor = colors.onSurface
    val doneColor = colors.primary
    val answerColor = colors.secondary.copy(alpha = 0.45f)
    val wrongColor = colors.error

    val currentOnFinished by rememberUpdatedState(onStrokeFinished)
    // Chỉ chạy hiệu ứng nhấp nháy khi thật sự cần gợi ý nét kế tiếp (tránh vẽ lại liên tục).
    val needHint = !freeMode && board.misses >= 2
    val pulseAlpha: State<Float>? = if (needHint) {
        rememberInfiniteTransition(label = "hint").animateFloat(
            initialValue = 0.15f, targetValue = 0.75f,
            animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "hintAlpha"
        )
    } else null

    Canvas(
        modifier = modifier
            .testTag("hanziBoard")
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surface)
            .pointerInput(data, board, freeMode, enabled) {
                if (!enabled || data == null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val factor = 1024f / size.width.toFloat()
                    board.live.clear()
                    board.live.add(down.position * factor)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        if (change.position != change.previousPosition) {
                            board.live.add(change.position * factor)
                            change.consume()
                        }
                    }
                    val points = board.live.toList()
                    board.live.clear()
                    if (points.isNotEmpty()) currentOnFinished(points)
                }
            }
    ) {
        drawGrid(gridColor)
        val k = size.width / 1024f
        withTransform({ scale(k, k, pivot = Offset.Zero) }) {
            if (data != null) {
                if (showGhost) data.strokes.forEach { drawPath(it, ghostColor) }
                if (board.showAnswer) data.strokes.forEach { drawPath(it, answerColor) }

                if (board.autoProgress >= 0f) {
                    data.strokes.forEachIndexed { i, path ->
                        val fraction = (board.autoProgress - i).coerceIn(0f, 1f)
                        if (fraction > 0f) drawRevealedStroke(path, data.medians[i], fraction, strokeColor(i))
                    }
                }
                if (!freeMode && board.autoProgress < 0f) {
                    for (i in 0 until min(board.doneCount, data.strokeCount)) {
                        drawPath(data.strokes[i], strokeColor(i))
                    }
                    // Gợi ý nét kế tiếp sau 2 lần viết sai
                    if (board.misses >= 2 && board.doneCount < data.strokeCount) {
                        drawPath(data.strokes[board.doneCount], strokeColor(board.doneCount).copy(alpha = pulseAlpha?.value ?: 0.4f))
                    }
                }
            }

            board.userStrokes.forEachIndexed { index, stroke ->
                val c = if (stroke.score >= 0f) scoreColor(stroke.score) else strokeColor(index)
                drawPath(stroke.path, c, style = Stroke(width = BRUSH_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            board.flash?.let {
                drawPath(it, wrongColor.copy(alpha = 0.8f), style = Stroke(width = BRUSH_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (board.live.isNotEmpty()) {
                drawPath(
                    smoothPath(board.live.toList()),
                    strokeColor(if (freeMode) board.userStrokes.size else board.doneCount),
                    style = Stroke(width = BRUSH_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Demo sheet
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoSheet(
    data: HanziChar,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var replayKey by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    val currentSpeed by rememberUpdatedState(speed)
    val colors = MaterialTheme.colorScheme
    val gridColor = colors.onSurface.copy(alpha = 0.13f)
    val doneColor = colors.error.copy(alpha = 0.85f)
    val activeColor = colors.primary

    LaunchedEffect(data, replayKey) {
        val n = data.strokeCount.toFloat()
        while (isActive) {
            progress = 0f
            delay(250)
            var last = androidx.compose.runtime.withFrameNanos { it }
            while (progress < n) {
                val now = androidx.compose.runtime.withFrameNanos { it }
                val dt = (now - last) / 1_000_000_000f
                last = now
                progress = min(n, progress + dt * currentSpeed * 1.1f)
            }
            delay(1400)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Thứ tự nét: ${data.char}  (${data.strokeCount} nét)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(12.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.4f))
            ) {
                drawGrid(gridColor)
                val k = size.width / 1024f
                withTransform({ scale(k, k, pivot = Offset.Zero) }) {
                    for (i in 0 until data.strokeCount) {
                        val f = (progress - i).coerceIn(0f, 1f)
                        if (f <= 0f) continue
                        val drawing = f < 1f
                        drawRevealedStroke(
                            data.strokes[i], data.medians[i], f,
                            strokeColor(i)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tốc độ ${"%.1f".format(speed)}x",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(96.dp)
                )
                Slider(
                    value = speed,
                    onValueChange = { onSpeedChange((Math.round(it * 10f) / 10f)) },
                    valueRange = 0.5f..3f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = 0.12f))
                        .clickable { replayKey++ },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Replay, contentDescription = "Phát lại", tint = colors.primary)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Word picker sheet
// ---------------------------------------------------------------------------------------------

data class WritingDeck(
    val id: Long,
    val title: String,
    val rawContent: String,
    val folderId: Long?
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun WordPickerSheet(
    folders: List<com.example.data.Folder>,
    decks: List<WritingDeck>,
    blurMode: Int,
    onlyStarred: Boolean,
    onOnlyStarred: (Boolean) -> Unit,
    selectedDeckId: Long,
    onDeckSelected: (Long) -> Unit,
    words: List<StudyWord>,
    currentWord: String,
    starred: List<StarredPair>,
    onToggleStar: (StudyWord) -> Unit,
    onSetAllStars: (Boolean) -> Unit,
    onCopy: (StudyWord) -> Unit,
    onPick: (StudyWord) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filter by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val gold = Color(0xFFFFD700)

    var selectedFolderId by remember {
        mutableStateOf<Long?>(decks.find { it.id == selectedDeckId }?.folderId)
    }

    val folderDecks = remember(selectedFolderId, decks) {
        if (selectedFolderId == null) {
            decks.filter { it.folderId == null }
        } else {
            decks.filter { it.folderId == selectedFolderId }
        }
    }

    fun key(vi: String, foreign: String) = vi.trim().lowercase() + "\u0000" + foreign.trim().lowercase()
    val starKeys = remember(starred) { starred.map { key(it.vi, it.foreign) }.toSet() }
    fun isStarred(w: StudyWord) = key(w.rawVi, w.rawForeign) in starKeys
    val allStarred = words.isNotEmpty() && words.all { isStarred(it) }

    val shown = remember(words, filter) {
        val f = filter.trim().lowercase()
        if (f.isEmpty()) words else words.filter {
            it.hanzi.contains(f) || it.meaning.lowercase().contains(f) || it.pinyin.lowercase().contains(f)
        }
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = words.indexOfFirst { it.hanzi == currentWord }.coerceAtLeast(0)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Text("Danh sách luyện viết", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))

            // 1. CHỌN THƯ MỤC
            Text("1. Chọn thư mục:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val ungroupedCount = decks.count { it.folderId == null }
                FilterChip(
                    selected = selectedFolderId == null,
                    onClick = {
                        selectedFolderId = null
                        val first = decks.firstOrNull { it.folderId == null }
                        if (first != null) onDeckSelected(first.id)
                    },
                    label = { Text("📦 Chưa phân loại ($ungroupedCount)", fontSize = 11.5.sp) }
                )

                folders.forEach { folder ->
                    val count = decks.count { it.folderId == folder.id }
                    FilterChip(
                        selected = selectedFolderId == folder.id,
                        onClick = {
                            selectedFolderId = folder.id
                            val first = decks.firstOrNull { it.folderId == folder.id }
                            if (first != null) onDeckSelected(first.id)
                        },
                        label = { Text("📂 ${folder.name} ($count)", fontSize = 11.5.sp) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // 2. CHỌN BÀI HỌC TRONG THƯ MỤC
            Text("2. Chọn bài học:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            Spacer(Modifier.height(4.dp))
            if (folderDecks.isEmpty()) {
                Text(
                    "Thư mục này chưa có bài học nào chứa chữ Hán.",
                    fontSize = 12.sp,
                    color = colors.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(folderDecks) { deck ->
                        FilterChip(
                            selected = deck.id == selectedDeckId,
                            onClick = { onDeckSelected(deck.id) },
                            label = { Text(deck.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
            FilterChip(selected = onlyStarred, onClick = { onOnlyStarred(!onlyStarred) },
                label = { Text("Chỉ từ đánh sao ⭐") })
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("Lọc theo chữ Hán, pinyin hoặc nghĩa...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${shown.size} từ  •  2 chạm: sao chép • 3 chạm: đánh sao",
                    fontSize = 11.sp,
                    color = colors.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { onSetAllStars(!allStarred) },
                    enabled = words.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (allStarred) Icons.Default.StarBorder else Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (allStarred) Color.Gray else gold
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (allStarred) "Hủy chọn tất cả" else "Chọn tất cả ⭐",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Không có từ nào chứa chữ Hán trong bài học này.",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = colors.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    itemsIndexed(shown) { idx, w ->
                        val isCurrent = w.hanzi == currentWord
                        val hasStar = isStarred(w)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isCurrent) colors.primary.copy(alpha = 0.14f)
                                    else colors.surfaceVariant.copy(alpha = 0.45f)
                                )
                                .wordTaps(
                                    onSingle = { onPick(w) },
                                    onDouble = { onCopy(w) },
                                    onTriple = { onToggleStar(w) }
                                )
                                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Từ ${idx + 1}" + if (isCurrent) " (Đang luyện)" else "",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) colors.primary else Color.Gray
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    HanziText(
                                        blurred = blurMode == 1 || blurMode == 3,
                                        text = w.hanzi,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primary,
                                        modifier = Modifier.widthIn(max = 150.dp),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        if (w.pinyin.isNotEmpty()) {
                                            Text(if (blurMode >= 2) "●●●●" else w.pinyin, fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Text(if (blurMode >= 2) "●●●●" else w.meaning, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable { onToggleStar(w) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (hasStar) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Gắn sao",
                                    tint = if (hasStar) gold else Color.Gray,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritingScreen(viewModel: TranslationViewModel) {
    val context = LocalContext.current
    val starredPairs by viewModel.allStarredPairs.collectAsState()
    val folders by viewModel.allFolders.collectAsState()
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val studySets by viewModel.allStudySets.collectAsState()

    BackHandler { viewModel.closeWriting() }

    // --- Nguồn từ vựng: các bộ đề do người dùng tự tạo có chữ Hán (loại trừ các bộ đề mẫu preset) + danh sách đang nhập ---
    val rawInput = viewModel.rawTextContent.value
    // Danh sách đang luyện tập (đã xáo trộn giống hệt chế độ luyện học) đứng đầu để hai chế độ đồng bộ thứ tự.
    val practicePairs by viewModel.practicePairs.collectAsState()
    val practiceRaw = remember(practicePairs) {
        practicePairs.joinToString("\n") { "${it.vi} = ${it.foreign}" }
    }
    val decks = remember(studySets, rawInput, practiceRaw) {
        buildList {
            if (containsCjk(practiceRaw)) add(WritingDeck(-3L, "Đang luyện tập", practiceRaw, null))
            if (containsCjk(rawInput)) add(WritingDeck(-1L, "Đang nhập", rawInput, null))
            studySets.filter { !it.isPreset && containsCjk(it.rawContent) }.forEach {
                add(WritingDeck(it.id, it.title.substringBefore(" ("), it.rawContent, it.folderId))
            }
        }
    }
    var selectedDeckId by remember(decks) { mutableStateOf(decks.firstOrNull()?.id ?: -2L) }
    var onlyStarred by remember { mutableStateOf(false) }
    val allDeckWords = remember(selectedDeckId, decks, viewModel.isShuffle.value) {
        decks.firstOrNull { it.id == selectedDeckId }?.let { val words = parseStudyWords(it.rawContent); if (it.id != -3L && viewModel.isShuffle.value) words.shuffled() else words } ?: emptyList()
    }

    val deckWords = remember(allDeckWords, onlyStarred, starredPairs) {
        if (!onlyStarred) allDeckWords else allDeckWords.filter { w -> starredPairs.any { it.vi == w.rawVi && it.foreign == w.rawForeign } }
    }
    var word by remember {
        mutableStateOf(viewModel.writingWord.value.ifBlank { "" })
    }
    LaunchedEffect(deckWords) {
        if (word.isBlank() || deckWords.none { it.hanzi == word }) {
            word = deckWords.firstOrNull()?.hanzi.orEmpty()
        }
    }
    val chars = remember(word) { word.filter { isCjk(it) }.map { it.toString() } }
    var startAtLast by remember { mutableStateOf(false) }
    var charIndex by remember(word) {
        mutableIntStateOf(if (startAtLast) (chars.size - 1).coerceAtLeast(0) else 0)
    }
    LaunchedEffect(word, selectedDeckId) {
        startAtLast = false
        if (selectedDeckId == -3L) allDeckWords.firstOrNull { it.hanzi == word }?.let {
            viewModel.syncWritingPosition(it.rawVi, it.rawForeign)
        }
    }
    val options = remember { WritingOptions(context) }
    var autoWriting by remember { mutableStateOf(false) }
    var autoCycle by remember { mutableIntStateOf(0) }
    var autoBlur by remember { mutableStateOf<Int?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showTip by remember { mutableStateOf(false) }
    val blurMode = autoBlur ?: if (autoWriting && options.autoHide && options.output == 2) 2 else viewModel.writingBlurMode.value
    val blurChars = (blurMode == 1 || blurMode == 3)
    val hideSub = (blurMode == 2 || blurMode == 3)
    val autoSpeak = viewModel.writingAutoSpeak.value
    // Tự đọc: đổi từ -> đọc cả từ; đổi sang chữ khác trong từ -> đọc riêng chữ đó.
    LaunchedEffect(word, charIndex, autoSpeak, autoWriting) {
        if (autoSpeak && !autoWriting && word.isNotBlank()) {
            delay(350)
            val text = if (charIndex == 0) word.filter { isCjk(it) } else chars.getOrNull(charIndex) ?: word
            if (text.isNotBlank()) viewModel.speakText(text, "zh")
        }
    }
    val currentChar = chars.getOrNull(charIndex)
    val mainScroll = rememberScrollState()
    val wordInfo = remember(word, decks) {
        decks.firstNotNullOfOrNull { d -> parseStudyWords(d.rawContent).firstOrNull { it.hanzi == word } }
    }
    val charResults = remember(word) { mutableStateMapOf<Int, Int>() }
    var showSearchOptions by remember { mutableStateOf(false) }
    var showWebViewUrl by remember { mutableStateOf<String?>(null) }

    // --- Tải dữ liệu nét chữ ---
    var reloadKey by remember { mutableIntStateOf(0) }
    var load by remember { mutableStateOf<HanziLoad>(HanziLoad.Loading) }
    LaunchedEffect(currentChar, reloadKey) {
        val ch = currentChar
        if (ch == null) {
            load = HanziLoad.Failed("Hãy nhập hoặc chọn một chữ Hán để bắt đầu.")
            return@LaunchedEffect
        }
        load = HanziLoad.Loading
        load = HanziRepository.load(context, ch).fold(
            onSuccess = { HanziLoad.Ready(it) },
            onFailure = { HanziLoad.Failed(it.message ?: "Không tải được dữ liệu chữ.") }
        )
    }
    LaunchedEffect(word) {
        chars.forEach { HanziRepository.load(context, it) }
    }

    val data = (load as? HanziLoad.Ready)?.data
    val freeMode = viewModel.writingFreeMode.value
    var showGhost by remember { mutableStateOf(!freeMode) }
    var boardKey by remember { mutableIntStateOf(0) }
    val board = remember(currentChar, charIndex, freeMode, boardKey) { WritingBoardState() }

    var showDemo by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    fun setWord(newWord: String) {
        word = newWord
        viewModel.writingWord.value = newWord
        if (selectedDeckId == -3L) allDeckWords.firstOrNull { it.hanzi == newWord }?.let {
            viewModel.syncWritingPosition(it.rawVi, it.rawForeign)
        }
        viewModel.saveWritingPrefs()
    }

    val guidedComplete = !autoWriting && data != null && !freeMode && board.doneCount >= data.strokeCount
    LaunchedEffect(guidedComplete) {
        if (guidedComplete) {
            charResults[charIndex] = (100 - board.totalMisses * 7).coerceIn(40, 100)
            delay(250)
            mainScroll.animateScrollTo(mainScroll.maxValue, tween(550, easing = FastOutSlowInEasing))
        }
    }
    // Chấm điểm xong: tự cuộn mượt xuống khung kết quả; viết lại/đổi chữ thì cuộn về đầu.
    LaunchedEffect(board.result) {
        if (board.result != null) {
            delay(250)
            mainScroll.animateScrollTo(mainScroll.maxValue, tween(550, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(board) {
        if (mainScroll.value > 0) mainScroll.animateScrollTo(0, tween(350, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(board.flash) {
        if (board.flash != null) {
            delay(420)
            board.flash = null
        }
    }
    LaunchedEffect(board.message) {
        if (board.message != null) {
            delay(2200)
            board.message = null
        }
    }

    fun handleStroke(points: List<Offset>) {
        val d = data ?: return
        if (freeMode) {
            if (board.result != null) return
            board.userStrokes.add(UserStroke(points, smoothPath(points)))
        } else {
            if (board.doneCount >= d.strokeCount) return
            val expected = d.medians[board.doneCount]
            if (strokeMatches(points, expected)) {
                board.doneCount++
                board.misses = 0
                board.message = null
            } else {
                board.misses++
                board.totalMisses++
                board.flash = smoothPath(points)
                val other = (board.doneCount + 1 until d.strokeCount).firstOrNull { strokeMatches(points, d.medians[it]) }
                board.message = if (other != null) {
                    "Sai thứ tự nét: hãy viết nét ${board.doneCount + 1} trước"
                } else {
                    "Chưa đúng, hãy thử lại nét ${board.doneCount + 1}"
                }
            }
        }
    }

    fun gradeFree() {
        val d = data ?: return
        if (board.userStrokes.isEmpty()) {
            board.message = "Hãy viết chữ trước khi chấm điểm"
            return
        }
        val result = scoreFreeWriting(board.userStrokes.map { it.points }, d)
        board.result = result
        board.showAnswer = true
        val scored = board.userStrokes.mapIndexed { i, s ->
            UserStroke(s.points, s.path, result.strokeScores.getOrElse(i) { 0f })
        }
        board.userStrokes.clear()
        board.userStrokes.addAll(scored)
        charResults[charIndex] = result.score
    }

    // Sau: ưu tiên chữ kế tiếp trong cùng từ; hết chữ cuối mới sang từ kế tiếp trong danh sách.
    fun nextChar() {
        if (charIndex < chars.size - 1) {
            charIndex++
            return
        }
        if (deckWords.isEmpty()) {
            boardKey++
            return
        }
        val idx = deckWords.indexOfFirst { it.hanzi == word }
        charIndex = 0
        setWord(deckWords[if (idx < 0) 0 else (idx + 1) % deckWords.size].hanzi)
    }

    // Trước: lùi chữ trong cùng từ; ở chữ đầu thì về chữ cuối của từ trước đó.
    fun prevChar() {
        if (charIndex > 0) {
            charIndex--
            return
        }
        if (deckWords.isEmpty()) return
        val idx = deckWords.indexOfFirst { it.hanzi == word }
        startAtLast = true
        setWord(deckWords[if (idx < 0) deckWords.lastIndex else (idx - 1 + deckWords.size) % deckWords.size].hanzi)
    }

    LaunchedEffect(autoWriting, autoCycle, word, charIndex, data) {
        if (!autoWriting || data == null || data.char != currentChar) return@LaunchedEffect
        try {
            if (charIndex == 0) {
                suspend fun read(lang: String, text: String) {
                    autoBlur = if (options.autoHide && options.output == 2) { if (lang == "vi") 1 else 2 } else null
                    repeat(options.readCount) { viewModel.speakWriting(text, lang, options.readSpeed); delay(350) }
                }
                if (options.output != 0) read("vi", wordInfo?.meaning.orEmpty())
                if (options.output != 1) read("zh", word)
            }
            repeat(options.writeCount) {
                board.userStrokes.clear(); board.doneCount = 0
                for (stroke in 0 until data.strokeCount) {
                    val frames = (35 / options.writeSpeed).toInt().coerceAtLeast(5)
                    for (frame in 0..frames) { board.autoProgress = stroke + frame.toFloat() / frames; delay(20) }
                    delay(150)
                }
                delay(650)
            }
            nextChar(); autoCycle++
        } finally { board.autoProgress = -1f; autoBlur = null }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(Icons.Default.ArrowBack, "Quay lại") { viewModel.closeWriting() }
            Text(
                text = "Tập viết chữ Hán",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            RoundIconButton(Icons.Default.Settings, "Cài đặt luyện viết") { showSettings = true }
            RoundIconButton(Icons.Default.Search, "Tra cứu từ vựng") {
                showSearchOptions = !showSearchOptions
            }
        }

        // Bảng tra cứu nổi bật: HeyChinese & Lục Thư
        AnimatedVisibility(
            visible = showSearchOptions,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.secondaryContainer.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Tra cứu chữ Hán: \"$word\"",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val clean = word.filter { isCjk(it) }.ifEmpty { word }
                                val encW = java.net.URLEncoder.encode(clean, "UTF-8")
                                val lastC = clean.lastOrNull()?.toString() ?: clean
                                val encC = java.net.URLEncoder.encode(lastC, "UTF-8")
                                showWebViewUrl = "https://heychinese.net/search/hanzi/$encW?hl=vi&c=$encC"
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                        ) {
                            Text("🏮 HeyChinese", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val clean = word.filter { isCjk(it) }.ifEmpty { word }
                                val prompt = """[Bảng phân tích chi tiết chữ Hán] Bạn là một chuyên gia ngôn ngữ học và Hán tự học. Hãy phân tích từ vựng chữ Hán: "$clean" theo các yêu cầu sau:
1. Nhận diện toàn bộ các chữ Hán trong từ "$clean".
2. Với TỪNG chữ Hán nhận diện được, hãy lập một BẢNG MARKDOWN phân tích theo 6 hàng tương ứng với 6 phương pháp tạo chữ ("Lục thư") (không cần cột STT để tối ưu không gian hiển thị). Cấu trúc trình bày cho mỗi chữ Hán như sau:
### [Chữ Hán] — Phiên âm: [Pinyin & Hán Việt] — Ý nghĩa: [Nghĩa của từ]
| Phương pháp Lục thư | Áp dụng | Phân tích chi tiết (Bộ thủ, Ký hiệu, Biểu ý / Biểu thanh) | Mẹo ghi nhớ / Câu chuyện chiết tự |
|---|:---:|---|---|
| Tượng hình (象形) | [Có / X] | ... | ... |
| Chỉ sự (指事) | [Có / X] | ... | ... |
| Hội ý (会意) | [Có / X] | ... | ... |
| Hình thanh (形声) | [Có / X] | ... | ... |
| Chuyển chú (转注) | [Có / X] | ... | ... |
| Giả tá (假借) | [Có / X] | ... | ... |
--- QUY TẮC ĐIỀN BẢNG ---
- Ở cột "Áp dụng":
+ Nếu chữ CÓ sử dụng phương pháp đó: Ghi "Có".
+ Nếu chữ KHÔNG sử dụng phương pháp đó: Bắt buộc điền "X", hai cột phân tích phía sau của hàng đó cũng ghi "X".
- Cột "Phân tích chi tiết": Nêu rõ thành phần cấu tạo (nét vẽ mô phỏng, ký hiệu chỉ điểm, bộ thủ chỉ nghĩa, thành phần chỉ âm đọc, hoặc sự chuyển nghĩa/mượn âm).
- Cột "Mẹo ghi nhớ / Câu chuyện chiết tự": Nêu câu chuyện ngắn gọn, hình ảnh liên tưởng dễ nhớ giúp người học thuộc mặt chữ ngay lập tức.
--- TIÊU CHÍ ĐỐI CHIẾU 6 PHƯƠNG PHÁP ---
1. Tượng hình (象形): Vẽ mô phỏng lại hình ảnh thực tế của sự vật ngoài đời thực (nhìn thấy gì vẽ nấy như trăng, núi, cây).
2. Chỉ sự (指事): Lấy chữ tượng hình sẵn có rồi thêm nét/ký hiệu chỉ điểm để chỉ vào vị trí cụ thể (như gốc cây, ngọn cây, trên, dưới).
3. Hội ý (会意): Ghép nghĩa của hai hay nhiều thành phần lại để tạo thành một ý nghĩa mới hoàn toàn (như người tựa gốc cây thành chữ nghỉ ngơi).
4. Hình thanh (形声): Gồm một phần biểu ý (chỉ ý nghĩa/bộ thủ) kết hợp với một phần biểu thanh (quyết định hoặc gợi ý âm đọc).
5. Chuyển chú (转注): Mở rộng từ nghĩa gốc ban đầu, hoặc biến đổi/thêm nét từ chữ gốc để tạo thành nghĩa mới có quan hệ mật thiết với gốc.
6. Giả tá (假借): Mượn hình chữ và âm đọc có sẵn để biểu thị một khái niệm mới hoàn toàn (nghĩa gốc dần không còn dùng nữa).""".trimIndent()
                                showWebViewUrl = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(prompt, "UTF-8")
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.secondary)
                        ) {
                            Text("📜 Lục Thư", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val clean = word.filter { isCjk(it) }.ifEmpty { word }
                                val enc = java.net.URLEncoder.encode(clean, "UTF-8")
                                showWebViewUrl = "https://hanzii.net/search/word/$enc?hl=vi"
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant, contentColor = colors.onSurfaceVariant)
                        ) {
                            Text("⛩️ Hanzii", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(mainScroll)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Word + mode header
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.onSurface.copy(alpha = 0.06f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentWordInfo = wordInfo
                        ?: deckWords.firstOrNull { it.hanzi == word }
                        ?: allDeckWords.firstOrNull { it.hanzi == word }
                        ?: StudyWord(hanzi = word, pinyin = "", meaning = word, rawVi = word, rawForeign = word)

                    val isCurrentStarred = remember(currentWordInfo, starredPairs, word) {
                        val vi = currentWordInfo.rawVi.trim().lowercase()
                        val foreign = currentWordInfo.rawForeign.trim().lowercase()
                        starredPairs.any {
                            it.vi.trim().lowercase() == vi && it.foreign.trim().lowercase() == foreign
                        }
                    }

                    fun toggleStarCurrent() {
                        val vi = currentWordInfo.rawVi.ifBlank { word }
                        val foreign = currentWordInfo.rawForeign.ifBlank { word }
                        viewModel.toggleStar(vi, foreign, "zh")
                        val willBeStarred = !isCurrentStarred
                        android.widget.Toast.makeText(
                            context,
                            if (willBeStarred) "⭐ Đã đánh dấu sao" else "Đã bỏ đánh dấu sao",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }

                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.tripleTap { toggleStarCurrent() }
                        ) {
                            HanziText(
                                blurred = blurChars,
                                text = word.ifBlank { "…" },
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            IconButton(onClick = { toggleStarCurrent() }) {
                                Icon(
                                    imageVector = if (isCurrentStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Đánh sao từ hiện tại",
                                    tint = if (isCurrentStarred) Color(0xFFFFD700) else colors.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary.copy(alpha = 0.1f))
                                    .clickable { if (word.isNotBlank()) viewModel.speakText(word, "zh") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.VolumeUp, "Nghe", tint = colors.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                        val sub = buildString {
                            if (!currentWordInfo.pinyin.isBlank()) append("[${currentWordInfo.pinyin}]  ")
                            if (!currentWordInfo.meaning.isBlank()) append(currentWordInfo.meaning)
                        }
                        if (hideSub) {
                            Text(
                                "●●●●●  ●●●●●●",
                                fontSize = 13.sp,
                                color = colors.onSurface.copy(alpha = 0.25f),
                                modifier = Modifier.blur(8.dp)
                            )
                        } else if (sub.isNotBlank()) {
                            Text(
                                sub,
                                fontSize = 13.sp,
                                color = colors.onSurface.copy(alpha = 0.6f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceVariant.copy(alpha = 0.7f))
                                .clickable { showTip = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Mẹo nhớ cá nhân",
                                tint = colors.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        val icon = if (blurMode == 0) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        val tint = when (blurMode) {
                            1 -> colors.primary
                            2 -> Color(0xFFFF6B6B)
                            3 -> Color(0xFFFF9800)
                            else -> colors.onSurface
                        }
                        val bg = when (blurMode) {
                            1 -> colors.primary.copy(alpha = 0.16f)
                            2 -> Color(0xFFFF6B6B).copy(alpha = 0.16f)
                            3 -> Color(0xFFFF9800).copy(alpha = 0.16f)
                            else -> colors.surfaceVariant.copy(alpha = 0.7f)
                        }
                        val desc = when (blurMode) {
                            1 -> "Đang mờ chữ Hán (Chạm để che phiên âm & nghĩa Tiếng Việt)"
                            2 -> "Đang che phiên âm & nghĩa Tiếng Việt (Chạm để che cả hai)"
                            3 -> "Đang che cả hai (Chạm để tắt làm mờ, hiện cả hai không mờ)"
                            else -> "Đang tắt làm mờ: hiện cả chữ Hán & Tiếng Việt (Chạm để mờ chữ Hán)"
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(bg)
                                .clickable {
                                    val next = if (blurMode >= 3) 0 else blurMode + 1
                                    viewModel.writingBlurMode.value = next
                                    viewModel.writingBlur.value = (next > 0)
                                    viewModel.saveWritingPrefs()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = desc,
                                tint = tint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Viết tự do", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = freeMode,
                            onCheckedChange = {
                                viewModel.writingFreeMode.value = it
                                viewModel.saveWritingPrefs()
                                showGhost = !it
                            }
                        )
                    }
                }
            }

            // Board
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                HanziBoard(
                    data = data,
                    board = board,
                    freeMode = freeMode,
                    showGhost = showGhost,
                    enabled = !autoWriting && data != null && !guidedComplete && board.result == null,
                    onStrokeFinished = { handleStroke(it) },
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )

                when (val s = load) {
                    is HanziLoad.Loading -> CircularProgressIndicator(color = colors.primary)
                    is HanziLoad.Failed -> Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(s.message, textAlign = TextAlign.Center, fontSize = 13.sp, color = colors.onSurface.copy(alpha = 0.7f))
                        if (currentChar != null) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = { reloadKey++ }) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Thử lại")
                            }
                        }
                    }
                    is HanziLoad.Ready -> {}
                }

                // Toast nhỏ trên bàn viết
                androidx.compose.animation.AnimatedVisibility(
                    visible = board.message != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.inverseSurface.copy(alpha = 0.92f))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(board.message ?: "", color = colors.inverseOnSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Stroke progress (guided)
            if (data != null && !freeMode) {
                Text(
                    text = if (guidedComplete) "Hoàn thành! ${data.strokeCount}/${data.strokeCount} nét"
                    else "Nét ${board.doneCount + 1} / ${data.strokeCount}  •  Viết theo thứ tự nét",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (guidedComplete) Color(0xFF22C55E) else colors.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else if (data != null && board.result == null) {
                Text(
                    text = "Đã viết ${board.userStrokes.size} nét  •  Viết xong chạm \"Chấm điểm\" (chữ có ${data.strokeCount} nét)",
                    fontSize = 12.sp,
                    color = colors.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleAction(Icons.Default.PlayArrow, "Xem nét mẫu", enabled = data != null) { showDemo = true }
                if (freeMode) {
                    CircleAction(Icons.Default.Undo, "Hoàn tác nét", enabled = board.userStrokes.isNotEmpty() && board.result == null) {
                        board.userStrokes.removeLastOrNull()
                    }
                }
                CircleAction(
                    Icons.Default.DeleteSweep, "Xóa nhanh", big = true, enabled = data != null
                ) { boardKey++ }
                if (freeMode) {
                    CircleAction(
                        Icons.Default.Check, "Chấm điểm", accent = true,
                        enabled = data != null && board.result == null && board.userStrokes.isNotEmpty()
                    ) { gradeFree() }
                }
                CircleAction(
                    if (showGhost) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    if (showGhost) "Ẩn bóng" else "Hiện bóng",
                    active = showGhost
                ) { showGhost = !showGhost }
            }

            // Free-write result
            AnimatedVisibility(
                visible = board.result != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                board.result?.let { r ->
                    ResultCard(
                        r,
                        onRetry = { boardKey++ },
                        onNext = { nextChar() }
                    )
                }
            }

            // Guided completion
            AnimatedVisibility(
                visible = guidedComplete,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF22C55E).copy(alpha = 0.14f)),
                    border = BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Tuyệt vời!", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF16A34A))
                            Text(
                                "Độ chính xác ${charResults[charIndex] ?: 100}%  •  ${board.totalMisses} lần viết sai",
                                fontSize = 12.sp,
                                color = colors.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        TextButton(onClick = { boardKey++ }) { Text("Viết lại") }
                        Button(onClick = { nextChar() }, shape = RoundedCornerShape(16.dp)) { Text("Tiếp") }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        // Bottom panel: characters in word + search
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (chars.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chars.forEachIndexed { i, c ->
                        val selected = i == charIndex
                        val bg by animateColorAsState(
                            if (selected) colors.primary else colors.surfaceVariant.copy(alpha = 0.7f),
                            label = "chipBg"
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(bg)
                                .clickable { charIndex = i }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                HanziText(
                                    blurred = blurChars,
                                    text = c,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) colors.onPrimary else colors.onSurface
                                )
                                charResults[i]?.let { sc ->
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "$sc",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) colors.onPrimary else scoreColor(sc / 100f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val position = deckWords.indexOfFirst { it.hanzi == word }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { prevChar() },
                    enabled = deckWords.isNotEmpty() || charIndex > 0,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.weight(0.9f).height(48.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Trước", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { showPicker = true },
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer
                    ),
                    modifier = Modifier.weight(1.05f).height(48.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.ViewList, contentDescription = "Danh sách", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (position >= 0) "${position + 1}/${deckWords.size}" else "Danh sách",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = {
                        autoWriting = !autoWriting
                        boardKey++
                    },
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (autoWriting) Color(0xFFFF5252) else colors.primary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1.05f).height(48.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(
                        if (autoWriting) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = if (autoWriting) "Dừng tự viết" else "Tự viết",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (autoWriting) "Dừng" else "Tự viết",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = { nextChar() },
                    enabled = deckWords.isNotEmpty() || charIndex < chars.size - 1,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.weight(0.9f).height(48.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("Sau", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (showDemo && data != null) {
        DemoSheet(
            data = data,
            speed = viewModel.writingSpeed.value,
            onSpeedChange = { viewModel.writingSpeed.value = it },
            onDismiss = {
                showDemo = false
                viewModel.saveWritingPrefs()
            }
        )
    }

    if (showSettings) WritingSettings(
        o = options,
        autoSpeak = viewModel.writingAutoSpeak.value,
        onSpeak = { viewModel.writingAutoSpeak.value = it; viewModel.saveWritingPrefs() },
        onDismiss = { showSettings = false }
    )
    if (showTip) MemoryTipDialog(word, { showTip = false })

    if (showPicker) {
        WordPickerSheet(
            blurMode = blurMode,
            onlyStarred = onlyStarred,
            onOnlyStarred = { onlyStarred = it },
            folders = folders,
            decks = decks,
            selectedDeckId = selectedDeckId,
            onDeckSelected = { selectedDeckId = it },
            words = deckWords,
            currentWord = word,
            starred = starredPairs,
            onToggleStar = { viewModel.toggleStar(it.rawVi, it.rawForeign, "zh") },
            onSetAllStars = { star ->
                viewModel.setStarForPairs(deckWords.map { it.rawVi to it.rawForeign }, "zh", star)
            },
            onCopy = {
                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("hanzi", it.hanzi))
                android.widget.Toast.makeText(context, "Đã sao chép: ${it.hanzi}", android.widget.Toast.LENGTH_SHORT).show()
            },
            onPick = {
                setWord(it.hanzi)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }

    if (showWebViewUrl != null) {
        WritingWebViewDialog(
            url = showWebViewUrl!!,
            onDismiss = { showWebViewUrl = null }
        )
    }
}

@Composable
private fun WritingWebViewDialog(
    url: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var loadedUrl by remember { mutableStateOf<String?>(url) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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
                            text = "Tra cứu chữ Hán",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (webViewRef?.canGoBack() == true) webViewRef?.goBack()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Trở về", modifier = Modifier.size(18.dp))
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
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Không mở được trình duyệt ngoài", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Mở ngoài", modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Đóng", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                val webViewInstance = this
                                webViewClient = WebViewClient()
                                webChromeClient = WebChromeClient()

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

                                    val originalUA = userAgentString
                                    val cleanUA = originalUA.replace("; wv", "").replace("Version/4.0 ", "")
                                    userAgentString = if (cleanUA.contains("Safari/537.36")) {
                                        cleanUA.substringBefore("Safari/537.36") + "Safari/537.36"
                                    } else {
                                        cleanUA
                                    }
                                }
                                loadUrl(url)
                            }
                        },
                        update = { webView ->
                            webViewRef = webView
                            if (url != loadedUrl) {
                                loadedUrl = url
                                webView.loadUrl(url)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Small pieces
// ---------------------------------------------------------------------------------------------

/** Chữ Hán có thể làm mờ; máy dưới Android 12 không hỗ trợ blur nên che bằng dấu chấm. */
@Composable
private fun HanziText(
    text: String,
    blurred: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    if (blurred && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
        Text("●".repeat(text.length.coerceAtLeast(1)), fontSize = fontSize * 0.6f, fontWeight = fontWeight, color = color.copy(alpha = 0.5f), modifier = modifier, maxLines = maxLines, overflow = overflow)
    } else {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            modifier = if (blurred) modifier.blur(14.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded) else modifier,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}

@Composable
private fun RoundIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun CircleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    big: Boolean = false,
    accent: Boolean = false,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val bg = when {
        big || accent -> colors.primary
        active -> colors.primary.copy(alpha = 0.16f)
        else -> colors.surfaceVariant.copy(alpha = 0.7f)
    }
    val fg = when {
        big || accent -> colors.onPrimary
        active -> colors.primary
        else -> colors.onSurface
    }
    val dim = if (big) 68.dp else 52.dp
    Box(
        modifier = Modifier
            .size(dim)
            .clip(CircleShape)
            .background(bg.copy(alpha = if (enabled) bg.alpha else bg.alpha * 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, description,
            tint = if (enabled) fg else fg.copy(alpha = 0.4f),
            modifier = Modifier.size(if (big) 32.dp else 24.dp)
        )
    }
}

@Composable
private fun ResultCard(result: FreeResult, onRetry: () -> Unit, onNext: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint = scoreColor(result.score / 100f)
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.5.dp, tint.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(76.dp)) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = tint.copy(alpha = 0.15f),
                        strokeWidth = 7.dp
                    )
                    CircularProgressIndicator(
                        progress = { result.score / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = tint,
                        strokeWidth = 7.dp,
                        strokeCap = StrokeCap.Round
                    )
                    Text("${result.score}", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = tint)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(result.label, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = tint)
                    Text(
                        "Số nét: ${result.userStrokeCount}/${result.refStrokeCount}",
                        fontSize = 12.sp,
                        color = colors.onSurface.copy(alpha = 0.65f)
                    )
                    Text(
                        "Nét xanh = đúng • cam = tạm được • đỏ = sai",
                        fontSize = 10.5.sp,
                        color = colors.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            MetricBar("Hình dạng nét", result.shapePercent)
            MetricBar("Hướng nét", result.directionPercent)
            MetricBar("Cân đối trong ô", result.balancePercent)

            result.notes.forEach {
                Text("• $it", fontSize = 12.5.sp, color = colors.onSurface.copy(alpha = 0.8f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Default.Replay, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Viết lại")
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Chữ tiếp theo")
                }
            }
        }
    }
}

@Composable
private fun MetricBar(label: String, percent: Int) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, color = colors.onSurface.copy(alpha = 0.7f))
            Text("$percent%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scoreColor(percent / 100f))
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(colors.onSurface.copy(alpha = 0.08f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(scoreColor(percent / 100f))
            )
        }
    }
}
