package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.TranslationPair
import com.example.data.isCjk
import com.example.viewmodel.TranslationViewModel

@Composable
fun SavedAndTaggedWordsManagerDialog(
    viewModel: TranslationViewModel,
    onDismiss: () -> Unit,
    onOpenVisualVocabDetail: (VisualVocabEntry, List<VisualVocabEntry>) -> Unit,
    onRequestMultiFolderSave: (vi: String, foreign: String) -> Unit
) {
    val context = LocalContext.current
    val starredPairs by viewModel.allStarredPairs.collectAsState()
    val statusVersion = viewModel.vocabStatusVersion.value

    var selectedBucket by remember { mutableStateOf("⭐ Đã gắn sao") }
    var searchFilter by remember { mutableStateOf("") }
    var showQuickAddRow by remember { mutableStateOf(false) }
    var newHanzi by remember { mutableStateOf("") }
    var newPinyin by remember { mutableStateOf("") }
    var newVi by remember { mutableStateOf("") }

    val buckets = listOf(
        "⭐ Đã gắn sao",
        "⏳ Chưa thuộc",
        "🔄 Cần ôn tập",
        "⚠️ Hay nhầm",
        "📌 Đã lưu",
        "✅ Đã thuộc",
        "📋 Tất cả"
    )

    val allRecords = remember(statusVersion, starredPairs, viewModel.allStudySets.collectAsState().value) {
        val tagged = viewModel.getAllTaggedVocabRecords().toMutableList()
        val existingKeys = tagged.map { it.key }.toHashSet()
        // Include any starredPairs not yet in tagged records
        for (star in starredPairs) {
            val cleanKey = star.foreign.substringBefore("(").trim().ifEmpty { star.foreign.trim() }
            if (cleanKey.isNotEmpty() && !existingKeys.contains(cleanKey)) {
                val ts = viewModel.getWordSavedTimestamp(cleanKey)
                tagged.add(
                    TranslationViewModel.TaggedVocabRecord(
                        key = cleanKey,
                        vi = star.vi,
                        foreign = star.foreign,
                        tag = "Đã lưu",
                        note = "",
                        savedTimestamp = ts,
                        formattedDate = if (ts > 0L) viewModel.formatSavedTimestamp(ts) else "Đã gắn sao"
                    )
                )
            }
        }
        tagged.sortedByDescending { it.savedTimestamp }
    }

    val filteredRecords = remember(allRecords, selectedBucket, searchFilter, starredPairs) {
        val byBucket = when (selectedBucket) {
            "⭐ Đã gắn sao" -> allRecords.filter { rec ->
                starredPairs.any {
                    it.vi.trim().equals(rec.vi.trim(), ignoreCase = true) ||
                        it.foreign.contains(rec.key)
                }
            }
            "⏳ Chưa thuộc" -> allRecords.filter { it.tag == "Chưa thuộc" }
            "🔄 Cần ôn tập" -> allRecords.filter { it.tag == "Cần ôn tập" }
            "⚠️ Hay nhầm" -> allRecords.filter { it.tag == "Hay nhầm" }
            "📌 Đã lưu" -> allRecords.filter { it.tag == "Đã lưu" }
            "✅ Đã thuộc" -> allRecords.filter { it.tag == "Đã thuộc" }
            else -> allRecords
        }
        val q = searchFilter.trim()
        if (q.isEmpty()) byBucket
        else byBucket.filter {
            TwoWayTranslatorEngine.matchesQueryWithPinyin("${it.vi} ${it.foreign} ${it.note}", q)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🗂️ Quản Lý Đã Lưu • Chưa Thuộc • Cần Ôn Tập",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Lưu trữ các mục gắn sao ⭐, chưa thuộc, cần ôn tập, hay nhầm kèm ngày lưu chi tiết",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }

                // Bucket Filter Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    buckets.forEach { b ->
                        val count = when (b) {
                            "⭐ Đã gắn sao" -> allRecords.count { rec ->
                                starredPairs.any {
                                    it.vi.trim().equals(rec.vi.trim(), ignoreCase = true) ||
                                        it.foreign.contains(rec.key)
                                }
                            }
                            "⏳ Chưa thuộc" -> allRecords.count { it.tag == "Chưa thuộc" }
                            "🔄 Cần ôn tập" -> allRecords.count { it.tag == "Cần ôn tập" }
                            "⚠️ Hay nhầm" -> allRecords.count { it.tag == "Hay nhầm" }
                            "📌 Đã lưu" -> allRecords.count { it.tag == "Đã lưu" }
                            "✅ Đã thuộc" -> allRecords.count { it.tag == "Đã thuộc" }
                            else -> allRecords.size
                        }
                        val isSel = selectedBucket == b
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedBucket = b },
                            label = {
                                Text(
                                    text = "$b ($count)",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }

                // Search + Quick Add + Practice Now Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchFilter,
                        onValueChange = { searchFilter = it },
                        placeholder = { Text("Tìm từ hoặc câu đã lưu...", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    OutlinedButton(
                        onClick = { showQuickAddRow = !showQuickAddRow },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Thêm mục", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    if (filteredRecords.isNotEmpty()) {
                        Button(
                            onClick = {
                                val pairs = filteredRecords.map { TranslationPair(it.vi, it.foreign) }
                                val ok = viewModel.startPracticeWithCustomList(pairs, selectedBucket)
                                if (ok) onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ôn ngay (${filteredRecords.size})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Quick Add Form into current bucket
                AnimatedVisibility(visible = showQuickAddRow) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val targetTag = when (selectedBucket) {
                                "⏳ Chưa thuộc" -> "Chưa thuộc"
                                "🔄 Cần ôn tập" -> "Cần ôn tập"
                                "⚠️ Hay nhầm" -> "Hay nhầm"
                                "✅ Đã thuộc" -> "Đã thuộc"
                                else -> "Đã lưu"
                            }
                            Text(
                                text = "➕ Thêm từ/câu mới vào mục '$targetTag' (kèm ngày lưu hôm nay):",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = newHanzi,
                                    onValueChange = { newHanzi = it },
                                    label = { Text("Chữ Hán", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = newPinyin,
                                    onValueChange = { newPinyin = it },
                                    label = { Text("Pinyin", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newVi,
                                    onValueChange = { newVi = it },
                                    label = { Text("Nghĩa Tiếng Việt", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        if (newHanzi.isNotBlank() && newVi.isNotBlank()) {
                                            val fullForeign = if (newPinyin.isNotBlank()) "${newHanzi.trim()} (${newPinyin.trim()})" else newHanzi.trim()
                                            viewModel.saveWordPersonalNoteAndTag(
                                                hanziKey = newHanzi.trim(),
                                                tag = targetTag,
                                                note = "",
                                                vi = newVi.trim(),
                                                foreign = fullForeign
                                            )
                                            if (selectedBucket == "⭐ Đã gắn sao") {
                                                viewModel.starPairOnly(newVi.trim(), fullForeign, "zh")
                                            }
                                            newHanzi = ""
                                            newPinyin = ""
                                            newVi = ""
                                            showQuickAddRow = false
                                            Toast.makeText(context, "Đã lưu vào '$targetTag'!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Lưu", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // List of Saved / Tagged Records
                if (filteredRecords.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Bookmarks,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Chưa có mục nào trong '$selectedBucket'.\nKhi bạn đánh sao ⭐, lưu từ/câu vào thư mục hoặc vuốt thẻ Quizlet (Chưa nhớ / Đã nhớ), các từ sẽ tự động lưu tại đây kèm ngày lưu!",
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredRecords, key = { it.key }) { rec ->
                            val isStarred = starredPairs.any {
                                it.vi.trim().equals(rec.vi.trim(), ignoreCase = true) ||
                                    it.foreign.contains(rec.key)
                            }
                            val parsed = remember(rec.vi, rec.foreign) {
                                parseEditableVocabItems("${rec.vi} = ${rec.foreign}").firstOrNull()
                            }
                            val hanziDisplay = parsed?.hanzi ?: rec.key
                            val pinyinDisplay = parsed?.pinyin ?: ""

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onOpenVisualVocabDetail(
                                            VisualVocabEntry(
                                                indexInBook = 1,
                                                hanzi = hanziDisplay,
                                                pinyin = pinyinDisplay,
                                                vi = rec.vi,
                                                lessonTitle = "Mục ${rec.tag} • ${rec.formattedDate}"
                                            ),
                                            emptyList()
                                        )
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = hanziDisplay,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (pinyinDisplay.isNotBlank()) {
                                                    Text(
                                                        text = "[$pinyinDisplay]",
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFB45309)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "🇻🇳 ${rec.vi}",
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (rec.note.isNotBlank()) {
                                                Text(
                                                    text = "📝 Ghi chú: ${rec.note}",
                                                    fontSize = 11.5.sp,
                                                    color = Color(0xFFD97706)
                                                )
                                            }
                                            Text(
                                                text = "📅 Ngày lưu: ${rec.formattedDate}",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            IconButton(
                                                onClick = { viewModel.toggleStar(rec.vi, rec.foreign, "zh") },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                                    contentDescription = "Gắn sao",
                                                    tint = if (isStarred) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.speakText(hanziDisplay, "zh") },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.VolumeUp,
                                                    contentDescription = "Nghe",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { onRequestMultiFolderSave(rec.vi, rec.foreign) },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.CreateNewFolder,
                                                    contentDescription = "Lưu vào thư mục",
                                                    tint = Color(0xFF0284C7),
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.removeWordStatusRecord(rec.key) },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Xóa khỏi danh sách",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Quick status switcher row
                                    val statusOptions = listOf("Chưa thuộc", "Cần ôn tập", "Hay nhầm", "Đã lưu", "Đã thuộc")
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        statusOptions.forEach { st ->
                                            val isCurrent = rec.tag == st
                                            Surface(
                                                modifier = Modifier.clickable {
                                                    viewModel.saveWordPersonalNoteAndTag(
                                                        hanziKey = rec.key,
                                                        tag = st,
                                                        note = rec.note,
                                                        vi = rec.vi,
                                                        foreign = rec.foreign
                                                    )
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isCurrent) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isCurrent) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                                )
                                            ) {
                                                Text(
                                                    text = st,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isCurrent) Color(0xFF92400E) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Outside Vocabulary List Card displayed directly on the Home Screen (SetupScreenView)
 * so the user can view the active list and toggle Star (⭐) or save to folders without entering Practice!
 */
@Composable
fun OutsideDeckWordListCard(
    deckTitle: String,
    rawContent: String,
    viewModel: TranslationViewModel,
    onOpenVisualVocabDetail: (VisualVocabEntry, List<VisualVocabEntry>) -> Unit,
    onRequestMultiFolderSave: (vi: String, foreign: String) -> Unit
) {
    val starredPairs by viewModel.allStarredPairs.collectAsState()
    var isExpanded by remember { mutableStateOf(true) }
    var visibleCount by remember(rawContent) { mutableStateOf(15) }
    var filterQuery by remember { mutableStateOf("") }

    val parsedItems = remember(rawContent) {
        parseEditableVocabItems(rawContent).mapIndexed { idx, item ->
            VisualVocabEntry(
                indexInBook = idx + 1,
                hanzi = item.hanzi,
                pinyin = item.pinyin,
                vi = item.vi,
                lessonTitle = deckTitle.ifBlank { "Danh sách đang chọn" }
            )
        }
    }

    val filteredItems = remember(parsedItems, filterQuery) {
        val q = filterQuery.trim()
        if (q.isEmpty()) parsedItems
        else parsedItems.filter {
            TwoWayTranslatorEngine.matchesQueryWithPinyin("${it.hanzi} ${it.pinyin} ${it.vi}", q)
        }
    }

    if (parsedItems.isEmpty()) return

    val starredInDeckCount = remember(parsedItems, starredPairs) {
        parsedItems.count { entry ->
            starredPairs.any {
                it.vi.trim().equals(entry.vi.trim(), ignoreCase = true) ||
                    (entry.hanzi.isNotBlank() && it.foreign.contains(entry.hanzi))
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "⭐ DANH SÁCH TỪ VỰNG NGOÀI TRANG HỌC (${parsedItems.size} TỪ)",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${deckTitle.ifBlank { "Bài đang chọn" }} • Đã gắn sao: $starredInDeckCount/${parsedItems.size} từ (Chạm ⭐ để đánh dấu ngay)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val allStarred = starredInDeckCount == parsedItems.size && parsedItems.isNotEmpty()
                    Surface(
                        modifier = Modifier.clickable {
                            val pairs = parsedItems.map { it.vi to it.fullForeign }
                            viewModel.setStarForPairs(pairs, viewModel.targetLanguage.value, !allStarred)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (allStarred) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (allStarred) Color(0xFFF59E0B) else Color.Transparent)
                    ) {
                        Text(
                            text = if (allStarred) "⭐ Bỏ sao hết" else "⭐ Sao tất cả",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (allStarred) Color(0xFF92400E) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Thu gọn / Mở rộng"
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        placeholder = { Text("Lọc nhanh từ trong danh sách ngoài...", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(17.dp)) },
                        trailingIcon = {
                            if (filterQuery.isNotEmpty()) {
                                IconButton(onClick = { filterQuery = "" }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Xóa", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )

                    val displayed = filteredItems.take(visibleCount)
                    displayed.forEach { item ->
                        val isStarred = starredPairs.any {
                            it.vi.trim().equals(item.vi.trim(), ignoreCase = true) ||
                                (item.hanzi.isNotBlank() && it.foreign.contains(item.hanzi))
                        }
                        val savedDate = remember(item.hanzi, viewModel.vocabStatusVersion.value) {
                            viewModel.getWordSavedDateFormatted(item.hanzi)
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenVisualVocabDetail(item, parsedItems) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isStarred) Color(0xFFFFFBEB) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                1.dp,
                                if (isStarred) Color(0xFFF59E0B).copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Left 1-Tap Star Button right on the outside list
                                IconButton(
                                    onClick = { viewModel.toggleStar(item.vi, item.fullForeign, viewModel.targetLanguage.value) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Đánh sao ngoài danh sách",
                                        tint = if (isStarred) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${item.indexInBook}. ${item.hanzi}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (item.pinyin.isNotBlank()) {
                                            Text(
                                                text = "[${item.pinyin}]",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB45309)
                                            )
                                        }
                                    }
                                    Text(
                                        text = item.vi,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (savedDate.isNotBlank()) {
                                        Text(
                                            text = "📅 Đã lưu: $savedDate",
                                            fontSize = 10.sp,
                                            color = Color(0xFF0369A1)
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    IconButton(
                                        onClick = { viewModel.speakText(item.hanzi, "zh") },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.VolumeUp,
                                            contentDescription = "Nghe",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRequestMultiFolderSave(item.vi, item.fullForeign) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.BookmarkAdd,
                                            contentDescription = "Lưu vào thư mục",
                                            tint = Color(0xFFD97706),
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (filteredItems.size > visibleCount) {
                        OutlinedButton(
                            onClick = { visibleCount += 25 },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Xem thêm (${filteredItems.size - visibleCount} từ trong bài)...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
