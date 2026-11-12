package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.LockscreenService
import com.example.PassiveStudyHelper
import com.example.VocabAppWidgetProvider
import com.example.data.Folder
import com.example.data.StudySet
import com.example.data.isCjk
import com.example.viewmodel.TranslationViewModel
import java.net.URLEncoder

data class EditableVocabItem(
    val vi: String,
    val hanzi: String,
    val pinyin: String
) {
    fun toRawLine(): String {
        val cleanVi = vi.trim()
        val cleanHanzi = hanzi.trim()
        val cleanPinyin = pinyin.trim().trim('(', ')', '（', '）')
        return if (cleanPinyin.isNotEmpty()) {
            "$cleanVi = $cleanHanzi ($cleanPinyin)"
        } else {
            "$cleanVi = $cleanHanzi"
        }
    }
}

fun parseEditableVocabItems(rawContent: String): List<EditableVocabItem> {
    return rawContent.split("\n").mapNotNull { line ->
        val trimmed = line.trim()
        if (!trimmed.contains("=") && !trimmed.contains("#")) return@mapNotNull null
        val parts = trimmed.split("=", "#").map { it.trim() }
        if (parts.size < 2 || parts[0].isEmpty() || parts[1].isEmpty()) return@mapNotNull null
        val p1 = parts[0]
        val p2 = parts[1]
        val hasZh1 = p1.any { isCjk(it) }
        val hasZh2 = p2.any { isCjk(it) }
        val (viSide, foreignSide) = if (hasZh1 && !hasZh2) p2 to p1 else p1 to p2
        val parenIdx = foreignSide.indexOfAny(charArrayOf('(', '（', '[', '【'))
        val hanzi = if (parenIdx != -1) foreignSide.substring(0, parenIdx).trim() else foreignSide.trim()
        val pinyin = if (parenIdx != -1) {
            foreignSide.substring(parenIdx).trim('(', ')', '（', '）', '[', ']', '【', '】', ' ')
        } else ""
        EditableVocabItem(vi = viSide, hanzi = hanzi, pinyin = pinyin)
    }
}

data class VocabSearchResult(
    val vi: String,
    val foreign: String,
    val setTitle: String,
    val studySet: StudySet
)

@Composable
fun ModernHomeDashboardBanner(
    totalFolders: Int,
    totalSets: Int,
    starredCount: Int,
    isPassiveActive: Boolean,
    isQuizletEnabled: Boolean,
    onOpenQuickLookup: () -> Unit,
    onOpenLiveVoiceTranslator: () -> Unit,
    onOpenQuickDualSave: () -> Unit,
    onOpenImageTranslator: () -> Unit,
    onOpenPassiveSettings: () -> Unit,
    onOpenVocabEditorForCurrent: () -> Unit,
    onToggleQuizletMode: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatMiniCard(
                modifier = Modifier.weight(1f),
                icon = "📂",
                value = "$totalFolders",
                label = "Thư mục sách"
            )
            StatMiniCard(
                modifier = Modifier.weight(1f),
                icon = "📚",
                value = "$totalSets",
                label = "Bài học"
            )
            StatMiniCard(
                modifier = Modifier.weight(1f),
                icon = "⭐",
                value = "$starredCount",
                label = "Từ gắn sao"
            )
        }

        // Row 1: Dịch Nói 2 Chiều (Live Voice) + Lưu Nhanh 2 Loại (Từ vựng & Ghi chú)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FeatureQuickCard(
                modifier = Modifier.weight(1f),
                title = "Dịch Nói 2 Chiều",
                subtitle = "Văn bản trực tiếp 🇻🇳↔🇨🇳",
                icon = Icons.Default.RecordVoiceOver,
                containerColor = Color(0xFF0284C7).copy(alpha = 0.16f),
                contentColor = Color(0xFF0284C7),
                onClick = onOpenLiveVoiceTranslator
            )
            FeatureQuickCard(
                modifier = Modifier.weight(1f),
                title = "Lưu Từ & Ghi Chú",
                subtitle = "2 Thư mục nhanh riêng",
                icon = Icons.Default.BookmarkAdd,
                containerColor = Color(0xFFD97706).copy(alpha = 0.16f),
                contentColor = Color(0xFFD97706),
                onClick = onOpenQuickDualSave
            )
        }

        // Row 2: Tra cứu & Dịch ảnh + Quản lý Thêm/Sửa/Xóa từ
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FeatureQuickCard(
                modifier = Modifier.weight(1f),
                title = "Tra cứu & Dịch ảnh",
                subtitle = "Pinyin • Câu • Camera",
                icon = Icons.Default.Translate,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onOpenQuickLookup
            )
            FeatureQuickCard(
                modifier = Modifier.weight(1f),
                title = "Quản lý từ vựng",
                subtitle = "Thêm • Sửa • Xóa",
                icon = Icons.Default.EditNote,
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onOpenVocabEditorForCurrent
            )
        }

        // Row 3: Học thụ động (Ghim thông báo không xóa) + Phân loại thẻ Quizlet
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FeatureQuickCard(
                modifier = Modifier.weight(1f),
                title = if (isPassiveActive) "Đang ghim thông báo" else "Ghim Thông Báo & Khóa",
                subtitle = "Không thể vuốt xóa",
                icon = Icons.Default.NotificationsActive,
                containerColor = if (isPassiveActive) Color(0xFF10B981).copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                contentColor = if (isPassiveActive) Color(0xFF10B981)
                else MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = onOpenPassiveSettings
            )
            FeatureQuickCard(
                modifier = Modifier.weight(1f),
                title = if (isQuizletEnabled) "Quizlet: ĐANG BẬT" else "Phân loại thẻ Quizlet",
                subtitle = if (isQuizletEnabled) "Đã nhớ / Chưa nhớ" else "Chạm để bật phân loại",
                icon = Icons.Default.Style,
                containerColor = if (isQuizletEnabled) Color(0xFF6366F1).copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isQuizletEnabled) Color(0xFF6366F1)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onToggleQuizletMode
            )
        }
    }
}

@Composable
private fun StatMiniCard(
    modifier: Modifier = Modifier,
    icon: String,
    value: String,
    label: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = icon, fontSize = 18.sp)
            Column {
                Text(
                    text = value,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FeatureQuickCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.bounceClick(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = contentColor, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = contentColor.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun GlobalVocabularySearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    studySets: List<StudySet>,
    folders: List<Folder>,
    onSelectStudySet: (StudySet, Boolean) -> Unit,
    onEditStudySet: (StudySet) -> Unit,
    onSpeakWord: (String, String) -> Unit,
    onWriteWord: (String) -> Unit,
    onOpenVisualVocabDetail: (VisualVocabEntry, List<VisualVocabEntry>) -> Unit,
    onRequestMultiFolderSave: (vi: String, foreign: String) -> Unit,
    onOpenVoiceSearch: () -> Unit,
    onOpenCameraOrPhotoOcr: () -> Unit
) {
    val trimmedQuery = searchQuery.trim()
    var liveSentenceTranslation by remember { mutableStateOf<TwoWayTranslationResult?>(null) }
    var isTranslatingSentence by remember { mutableStateOf(false) }

    // Trigger 2-way translation automatically when user enters any word, Pinyin, or sentence
    LaunchedEffect(trimmedQuery) {
        if (trimmedQuery.length < 2) {
            liveSentenceTranslation = null
            isTranslatingSentence = false
        } else {
            kotlinx.coroutines.delay(350)
            isTranslatingSentence = true
            liveSentenceTranslation = TwoWayTranslatorEngine.translateTwoWay(trimmedQuery, studySets)
            isTranslatingSentence = false
        }
    }

    val matchingSets = remember(trimmedQuery, studySets) {
        if (trimmedQuery.length < 2) emptyList()
        else studySets.filter {
            TwoWayTranslatorEngine.matchesQueryWithPinyin(it.title, trimmedQuery)
        }.take(6)
    }

    val matchingWords = remember(trimmedQuery, studySets) {
        if (trimmedQuery.length < 2) emptyList()
        else {
            val results = ArrayList<VocabSearchResult>(30)
            for (set in studySets) {
                if (results.size >= 30) break
                val lines = set.rawContent.split("\n")
                for (line in lines) {
                    if (results.size >= 30) break
                    if ((line.contains("=") || line.contains("#")) &&
                        TwoWayTranslatorEngine.matchesQueryWithPinyin(line, trimmedQuery)
                    ) {
                        val parts = line.split("=", "#").map { it.trim() }
                        if (parts.size >= 2) {
                            val p1 = parts[0]
                            val p2 = parts[1]
                            val hasZh1 = p1.any { isCjk(it) }
                            val hasZh2 = p2.any { isCjk(it) }
                            val (viSide, foreignSide) = if (hasZh1 && !hasZh2) p2 to p1 else p1 to p2
                            results.add(
                                VocabSearchResult(
                                    vi = viSide,
                                    foreign = foreignSide,
                                    setTitle = set.title,
                                    studySet = set
                                )
                            )
                        }
                    }
                }
            }
            results
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "🔍 Tra chữ Hán, Pinyin (không dấu), câu hoặc dịch Việt ↔ Trung...",
                    fontSize = 12.5.sp
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = MaterialTheme.colorScheme.primary)
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Xóa tìm kiếm", modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(
                        onClick = onOpenVoiceSearch,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Nhập bằng giọng nói thông minh",
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenCameraOrPhotoOcr,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = "Dịch bằng Camera hoặc Thư viện ảnh",
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        AnimatedVisibility(
            visible = trimmedQuery.length >= 2,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Automatic 2-Way Sentence / Phrase Translation Card
                    if (isTranslatingSentence) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                            Text("Đang dịch nhanh 2 chiều Việt ↔ Trung...", fontSize = 11.5.sp)
                        }
                    } else {
                        liveSentenceTranslation?.let { trans ->
                            if (trans.translatedText.isNotBlank()) {
                                val viSide = if (trans.sourceLang == "vi") trans.sourceText else trans.translatedText
                                val zhSide = if (trans.sourceLang == "zh") trans.sourceText else trans.translatedText
                                val fullZhWithPinyin = if (trans.pinyin.isNotBlank() && !zhSide.contains("(")) {
                                    "$zhSide (${trans.pinyin})"
                                } else zhSide

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val visualEntry = VisualVocabEntry(
                                                indexInBook = 1,
                                                hanzi = zhSide,
                                                pinyin = trans.pinyin,
                                                vi = viSide,
                                                lessonTitle = "Tra cứu & Dịch 2 chiều"
                                            )
                                            onOpenVisualVocabDetail(visualEntry, emptyList())
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFFFFBEB)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFF59E0B))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (trans.sourceLang == "vi")
                                                    "🌐 DỊCH TỰ ĐỘNG 2 CHIỀU (VIỆT ➔ TRUNG) • Chạm xem Ảnh & Câu ví dụ"
                                                else
                                                    "🌐 DỊCH TỰ ĐỘNG 2 CHIỀU (TRUNG ➔ VIỆT) • Chạm xem Ảnh & Câu ví dụ",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF92400E),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                IconButton(
                                                    onClick = { onSpeakWord(zhSide, "zh") },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.VolumeUp, contentDescription = "Đọc tiếng Trung", tint = Color(0xFF9A3412), modifier = Modifier.size(17.dp))
                                                }
                                                IconButton(
                                                    onClick = { onRequestMultiFolderSave(viSide, fullZhWithPinyin) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.BookmarkAdd, contentDescription = "Lưu nhiều thư mục", tint = Color(0xFF0284C7), modifier = Modifier.size(17.dp))
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = zhSide,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF1C1917)
                                        )
                                        if (trans.pinyin.isNotBlank()) {
                                            Text(
                                                text = "[${trans.pinyin}]",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF9A3412)
                                            )
                                        }
                                        Text(
                                            text = "🇻🇳 $viSide",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF292524)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Matching Vocabulary & Sentences from Built-in Datasets
                    if (matchingWords.isNotEmpty()) {
                        val visualList = remember(matchingWords) {
                            matchingWords.mapIndexed { idx, m ->
                                val parsed = parseEditableVocabItems("${m.vi} = ${m.foreign}").firstOrNull()
                                VisualVocabEntry(
                                    indexInBook = idx + 1,
                                    hanzi = parsed?.hanzi ?: m.foreign,
                                    pinyin = parsed?.pinyin ?: "",
                                    vi = parsed?.vi ?: m.vi,
                                    lessonTitle = m.setTitle,
                                    studySet = m.studySet
                                )
                            }
                        }

                        Text(
                            text = "🔤 TỪ VỰNG & CÂU CÓ SẴN TRONG KHO (${matchingWords.size}) • Ấn vào từ để xem Ảnh & Câu ví dụ",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            matchingWords.forEachIndexed { idx, item ->
                                val vEntry = visualList[idx]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f))
                                        .clickable {
                                            onOpenVisualVocabDetail(vEntry, visualList)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = vEntry.hanzi,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.5.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (vEntry.pinyin.isNotBlank()) {
                                                Text(
                                                    text = "[${vEntry.pinyin}]",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFB45309)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "🇻🇳 ${vEntry.vi}",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "📖 ${item.setTitle} (Chạm xem ảnh minh họa & câu ví dụ)",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        IconButton(
                                            onClick = { onSpeakWord(vEntry.hanzi, item.studySet.language) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.VolumeUp,
                                                contentDescription = "Đọc",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onRequestMultiFolderSave(vEntry.vi, vEntry.fullForeign) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.BookmarkAdd,
                                                contentDescription = "Lưu nhiều thư mục",
                                                tint = Color(0xFFD97706),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                        val hanChars = vEntry.hanzi.filter { isCjk(it) }
                                        if (hanChars.isNotEmpty()) {
                                            IconButton(
                                                onClick = { onWriteWord(hanChars) },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Brush,
                                                    contentDescription = "Tập viết",
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Matching Lessons
                    if (matchingSets.isNotEmpty()) {
                        Text(
                            text = "📚 BÀI HỌC TÌM THẤY (${matchingSets.size})",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        matchingSets.forEach { set ->
                            val folderName = folders.find { it.id == set.folderId }?.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .clickable { onSelectStudySet(set, false) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(set.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    if (folderName != null) {
                                        Text(
                                            text = folderName,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    IconButton(
                                        onClick = { onEditStudySet(set) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Sửa từ", modifier = Modifier.size(16.dp))
                                    }
                                    Button(
                                        onClick = { onSelectStudySet(set, true) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Học ngay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

@Composable
fun QuickTranslationLookupDialog(
    studySets: List<StudySet>,
    onDismiss: () -> Unit,
    onSpeak: (String, String) -> Unit,
    onAddWordToCurrent: (String, String, String) -> Unit,
    onOpenWebUrl: (String) -> Unit,
    onOpenVisualVocabDetail: ((VisualVocabEntry, List<VisualVocabEntry>) -> Unit)? = null,
    onRequestMultiFolderSave: ((String, String) -> Unit)? = null,
    onOpenVoiceTranslator: (() -> Unit)? = null,
    onOpenCameraOcr: (() -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }
    var customVi by remember { mutableStateOf("") }
    var customHanzi by remember { mutableStateOf("") }
    var customPinyin by remember { mutableStateOf("") }
    var liveTranslation by remember { mutableStateOf<TwoWayTranslationResult?>(null) }
    var isTranslating by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            liveTranslation = null
            isTranslating = false
        } else {
            kotlinx.coroutines.delay(350)
            isTranslating = true
            liveTranslation = TwoWayTranslatorEngine.translateTwoWay(q, studySets)
            isTranslating = false
        }
    }

    val localMatches = remember(query, studySets) {
        val q = query.trim()
        if (q.isEmpty()) emptyList()
        else {
            val out = ArrayList<VocabSearchResult>(30)
            for (set in studySets) {
                if (out.size >= 30) break
                for (line in set.rawContent.split("\n")) {
                    if (out.size >= 30) break
                    if ((line.contains("=") || line.contains("#")) &&
                        TwoWayTranslatorEngine.matchesQueryWithPinyin(line, q)
                    ) {
                        val parts = line.split("=", "#").map { it.trim() }
                        if (parts.size >= 2) {
                            val p1 = parts[0]
                            val p2 = parts[1]
                            val hasZh1 = p1.any { isCjk(it) }
                            val hasZh2 = p2.any { isCjk(it) }
                            val (viSide, foreignSide) = if (hasZh1 && !hasZh2) p2 to p1 else p1 to p2
                            out.add(VocabSearchResult(viSide, foreignSide, set.title, set))
                        }
                    }
                }
            }
            out
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Tra cứu & Dịch 2 chiều", fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
                }
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
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Nhập chữ Hán, Pinyin không dấu, từ hoặc câu...", fontSize = 12.5.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (query.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        val hasZh = query.any { isCjk(it) }
                                        onSpeak(query, if (hasZh) "zh" else "vi")
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = "Phát âm", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { query = "" },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Xóa", modifier = Modifier.size(18.dp))
                                }
                            }
                            if (onOpenVoiceTranslator != null) {
                                IconButton(
                                    onClick = {
                                        onDismiss()
                                        onOpenVoiceTranslator()
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = "Dịch nói", tint = Color(0xFF0284C7), modifier = Modifier.size(18.dp))
                                }
                            }
                            if (onOpenCameraOcr != null) {
                                IconButton(
                                    onClick = {
                                        onDismiss()
                                        onOpenCameraOcr()
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = "Dịch ảnh", tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                )

                // Live 2-Way Sentence Translation Box
                if (isTranslating) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    liveTranslation?.let { trans ->
                        if (trans.translatedText.isNotBlank()) {
                            val viSide = if (trans.sourceLang == "vi") trans.sourceText else trans.translatedText
                            val zhSide = if (trans.sourceLang == "zh") trans.sourceText else trans.translatedText
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onOpenVisualVocabDetail?.invoke(
                                            VisualVocabEntry(1, zhSide, trans.pinyin, viSide, "Tra cứu trực quan"),
                                            emptyList()
                                        )
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = if (trans.sourceLang == "vi") "🇻🇳 Việt ➔ 🇨🇳 Trung (Chạm mở chi tiết ảnh & ví dụ)" else "🇨🇳 Trung ➔ 🇻🇳 Việt (Chạm mở chi tiết ảnh & ví dụ)",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF92400E)
                                    )
                                    Text(zhSide, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Color(0xFF1C1917))
                                    if (trans.pinyin.isNotBlank()) {
                                        Text("[${trans.pinyin}]", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9A3412))
                                    }
                                    Text("🇻🇳 $viSide", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF292524))
                                }
                            }
                        }
                    }
                }

                // Online Quick Translation Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val encoded = URLEncoder.encode(query.ifBlank { "你好" }, "UTF-8")
                    AssistChip(
                        onClick = {
                            onOpenWebUrl("https://translate.google.com/?sl=auto&tl=zh-CN&text=$encoded&op=translate")
                        },
                        label = { Text("🌐 Google Dịch", fontSize = 11.5.sp) }
                    )
                    AssistChip(
                        onClick = {
                            onOpenWebUrl("https://hanzii.net/search/word/$encoded?hl=vi")
                        },
                        label = { Text("⛩️ Từ điển Hanzii", fontSize = 11.5.sp) }
                    )
                    AssistChip(
                        onClick = {
                            onOpenWebUrl("https://www.bing.com/images/search?q=$encoded")
                        },
                        label = { Text("🖼️ Bing Ảnh", fontSize = 11.5.sp) }
                    )
                }

                if (query.trim().isNotEmpty()) {
                    Text(
                        text = "Kết quả trong Kho Sách (${localMatches.size}) • Ấn vào từ để xem Ảnh & Câu ví dụ:",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (localMatches.isNotEmpty()) {
                        val vList = remember(localMatches) {
                            localMatches.mapIndexed { i, m ->
                                val parsed = parseEditableVocabItems("${m.vi} = ${m.foreign}").firstOrNull()
                                VisualVocabEntry(
                                    indexInBook = i + 1,
                                    hanzi = parsed?.hanzi ?: m.foreign,
                                    pinyin = parsed?.pinyin ?: "",
                                    vi = parsed?.vi ?: m.vi,
                                    lessonTitle = m.setTitle,
                                    studySet = m.studySet
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 210.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            localMatches.forEachIndexed { idx, m ->
                                val vItem = vList[idx]
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onOpenVisualVocabDetail?.invoke(vItem, vList)
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(m.foreign, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("🇻🇳 ${m.vi}", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary)
                                            Text("📚 ${m.setTitle}", fontSize = 10.sp, color = Color.Gray)
                                        }
                                        Row {
                                            IconButton(
                                                onClick = { onSpeak(vItem.hanzi, "zh") },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Default.VolumeUp, contentDescription = "Đọc", modifier = Modifier.size(17.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (onRequestMultiFolderSave != null) {
                                                        onRequestMultiFolderSave(m.vi, m.foreign)
                                                    } else {
                                                        onAddWordToCurrent(m.vi, m.foreign, "")
                                                        Toast.makeText(context, "Đã thêm vào danh sách luyện tập!", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Default.BookmarkAdd, contentDescription = "Lưu thư mục", modifier = Modifier.size(17.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()
                Text(
                    text = "➕ Thêm nhanh từ mới vào đề đang học:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                OutlinedTextField(
                    value = customHanzi,
                    onValueChange = { customHanzi = it },
                    label = { Text("Chữ Hán / Ngoại ngữ (VD: 你好)", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = customPinyin,
                    onValueChange = { customPinyin = it },
                    label = { Text("Phiên âm Pinyin (VD: nǐ hǎo)", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = customVi,
                    onValueChange = { customVi = it },
                    label = { Text("Nghĩa Tiếng Việt (VD: Xin chào)", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (customVi.isBlank() || customHanzi.isBlank()) {
                            Toast.makeText(context, "Vui lòng nhập đủ Chữ Hán và Nghĩa Tiếng Việt", Toast.LENGTH_SHORT).show()
                        } else {
                            onAddWordToCurrent(customVi.trim(), customHanzi.trim(), customPinyin.trim())
                            customVi = ""
                            customHanzi = ""
                            customPinyin = ""
                            Toast.makeText(context, "Đã thêm từ mới vào bộ đề!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Thêm vào bộ đề hiện tại", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun VocabularyCrudManagerDialog(
    title: String,
    initialRawContent: String,
    onDismiss: () -> Unit,
    onSave: (newTitle: String, newRawContent: String) -> Unit,
    onSpeak: (String) -> Unit
) {
    val context = LocalContext.current
    var editedTitle by remember { mutableStateOf(title) }
    var itemsList by remember {
        mutableStateOf(parseEditableVocabItems(initialRawContent))
    }
    var filterText by remember { mutableStateOf("") }

    // New item form state
    var newVi by remember { mutableStateOf("") }
    var newHanzi by remember { mutableStateOf("") }
    var newPinyin by remember { mutableStateOf("") }
    var showAddForm by remember { mutableStateOf(false) }

    // Editing item state
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editVi by remember { mutableStateOf("") }
    var editHanzi by remember { mutableStateOf("") }
    var editPinyin by remember { mutableStateOf("") }

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
            tonalElevation = 6.dp
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
                            text = "Quản lý & Chỉnh sửa Từ vựng",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Tổng số: ${itemsList.size} mục từ vựng • Thêm, sửa, xóa trực tiếp",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Title & Search bar
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    label = { Text("Tên bài học / bộ dữ liệu", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        placeholder = { Text("Tìm từ trong bài này...", fontSize = 12.5.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (filterText.isNotEmpty()) {
                                IconButton(onClick = { filterText = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Xóa", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )
                    Button(
                        onClick = { showAddForm = !showAddForm },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showAddForm) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(if (showAddForm) Icons.Default.ExpandLess else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Thêm từ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Expandable Add Form
                AnimatedVisibility(visible = showAddForm) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("➕ Thêm từ vựng mới vào bộ dữ liệu:", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = newHanzi,
                                    onValueChange = { newHanzi = it },
                                    label = { Text("Chữ Hán / Từ mới", fontSize = 11.5.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                OutlinedTextField(
                                    value = newPinyin,
                                    onValueChange = { newPinyin = it },
                                    label = { Text("Pinyin", fontSize = 11.5.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            OutlinedTextField(
                                value = newVi,
                                onValueChange = { newVi = it },
                                label = { Text("Nghĩa Tiếng Việt", fontSize = 11.5.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showAddForm = false }) {
                                    Text("Đóng")
                                }
                                Button(
                                    onClick = {
                                        if (newVi.isBlank() || newHanzi.isBlank()) {
                                            Toast.makeText(context, "Vui lòng nhập Chữ Hán và Nghĩa Tiếng Việt", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val added = EditableVocabItem(
                                                vi = newVi.trim(),
                                                hanzi = newHanzi.trim(),
                                                pinyin = newPinyin.trim()
                                            )
                                            itemsList = listOf(added) + itemsList
                                            newVi = ""
                                            newHanzi = ""
                                            newPinyin = ""
                                            Toast.makeText(context, "Đã thêm từ mới lên đầu danh sách!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Lưu từ mới", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Vocabulary List
                val indexedItems = remember(itemsList, filterText) {
                    val q = filterText.trim()
                    itemsList.mapIndexed { index, item -> index to item }.filter { (_, item) ->
                        q.isEmpty() ||
                            item.vi.contains(q, ignoreCase = true) ||
                            item.hanzi.contains(q, ignoreCase = true) ||
                            item.pinyin.contains(q, ignoreCase = true)
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(indexedItems, key = { "${it.first}_${it.second.hanzi}_${it.second.vi}" }) { (realIdx, item) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            if (editingIndex == realIdx) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = editHanzi,
                                            onValueChange = { editHanzi = it },
                                            label = { Text("Chữ Hán", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = editPinyin,
                                            onValueChange = { editPinyin = it },
                                            label = { Text("Pinyin", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    OutlinedTextField(
                                        value = editVi,
                                        onValueChange = { editVi = it },
                                        label = { Text("Nghĩa Tiếng Việt", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { editingIndex = null }) {
                                            Text("Hủy")
                                        }
                                        Button(
                                            onClick = {
                                                if (editVi.isNotBlank() && editHanzi.isNotBlank()) {
                                                    val mut = itemsList.toMutableList()
                                                    mut[realIdx] = EditableVocabItem(
                                                        vi = editVi.trim(),
                                                        hanzi = editHanzi.trim(),
                                                        pinyin = editPinyin.trim()
                                                    )
                                                    itemsList = mut
                                                    editingIndex = null
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Cập nhật")
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "#${realIdx + 1}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = item.hanzi,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (item.pinyin.isNotEmpty()) {
                                                Text(
                                                    text = "(${item.pinyin})",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "🇻🇳 ${item.vi}",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onSpeak(item.hanzi) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.VolumeUp,
                                                contentDescription = "Nghe",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                editingIndex = realIdx
                                                editVi = item.vi
                                                editHanzi = item.hanzi
                                                editPinyin = item.pinyin
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Sửa",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                val mut = itemsList.toMutableList()
                                                mut.removeAt(realIdx)
                                                itemsList = mut
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Xóa",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Save Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Hủy")
                    }
                    Button(
                        onClick = {
                            val newRaw = itemsList.joinToString("\n") { it.toRawLine() }
                            onSave(editedTitle.trim().ifEmpty { title }, newRaw)
                        },
                        modifier = Modifier.weight(1.6f).height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lưu thay đổi (${itemsList.size} từ)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PassiveStudyControlDialog(
    viewModel: TranslationViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isPinned = viewModel.isPassiveNotifEnabled.value
    val isLockscreen = viewModel.isLockscreenEnabled.value
    val intervalSec = viewModel.passiveAutoIntervalSec.value
    val autoSpeak = viewModel.passiveAutoSpeak.value
    val popupOnLock = viewModel.lockscreenPopupEnabled.value

    var previewTick by remember { mutableStateOf(0) }
    val currentWordState = remember(previewTick, viewModel.rawTextContent.value) {
        PassiveStudyHelper.getCurrentWordState(context)
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.togglePassiveNotification(context, true)
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Học Thụ Động & Màn Hình Khóa", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live Preview of Pinned Notification / Widget Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📌 ${currentWordState.deckTitle}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA5B4FC),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${currentWordState.index + 1}/${currentWordState.total}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentWordState.foreign,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "🇻🇳 ${currentWordState.vi}",
                            fontSize = 13.5.sp,
                            color = Color(0xFFFDE68A),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    PassiveStudyHelper.stepWord(context, -1)
                                    LockscreenService.notifyWordChanged(context)
                                    previewTick++
                                },
                                modifier = Modifier.weight(1f).height(34.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("⏮ Trước", fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    viewModel.speakText(currentWordState.foreign, currentWordState.language)
                                },
                                modifier = Modifier.weight(1f).height(34.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Text("🔊 Đọc", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    PassiveStudyHelper.stepWord(context, 1)
                                    LockscreenService.notifyWordChanged(context)
                                    previewTick++
                                },
                                modifier = Modifier.weight(1f).height(34.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("Tiếp ⏭", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // 1. Pinned Ongoing Notification toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "📌 Ghim từ vựng trên Thanh thông báo",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                        Text(
                            text = "Thông báo ghim cố định (không bị vuốt xóa), hiển thị cả ở Trung tâm thông báo & Màn hình khóa",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = isPinned,
                        onCheckedChange = { enable ->
                            if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.togglePassiveNotification(context, enable)
                            }
                        }
                    )
                }

                // 2. Lockscreen Display toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "🔒 Hiển thị Luyện Dịch lên Màn hình khóa",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                        Text(
                            text = "Hiện từ vựng trực tiếp khi mở sáng màn hình khóa",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = isLockscreen,
                        onCheckedChange = { viewModel.toggleLockscreen(context) }
                    )
                }

                // 3. Auto-rotate interval
                Text(
                    text = "⏱️ Tự động đổi từ vựng mới trên thông báo & Widget:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        0 to "Đổi thủ công",
                        15 to "15 giây",
                        30 to "30 giây",
                        60 to "1 phút",
                        180 to "3 phút"
                    ).forEach { (sec, label) ->
                        FilterChip(
                            selected = intervalSec == sec,
                            onClick = { viewModel.updatePassiveSettings(context, intervalSec = sec) },
                            label = { Text(label, fontSize = 11.5.sp) }
                        )
                    }
                }

                // 4. Auto-speak on notification change
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "🔊 Tự phát âm khi bấm Tiếp/Trước trên thông báo",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoSpeak,
                        onCheckedChange = { viewModel.updatePassiveSettings(context, autoSpeak = it) }
                    )
                }

                HorizontalDivider()

                // 5. Add Home Screen AppWidget button
                Button(
                    onClick = { VocabAppWidgetProvider.requestPinWidget(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ghim Tiện Ích (Widget) ra Màn hình chính", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Quizlet-style Top Counter Pills matching user screenshot image_eade05aa.png:
 * Left orange outlined pill (stillLearningCount) & Right green outlined pill (memorizedCount)
 * plus quick classification buttons.
 */
@Composable
fun QuizletSortingTopBar(
    stillLearningCount: Int,
    memorizedCount: Int,
    onMarkStillLearning: () -> Unit,
    onMarkMemorized: () -> Unit,
    onOpenQuizletOptions: () -> Unit
) {
    val orangeColor = Color(0xFFFF7A2F)
    val greenColor = Color(0xFF18AE79)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Orange Half-Pill (matching image_eade05aa.png)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 22.dp, bottomEnd = 22.dp))
                .background(orangeColor.copy(alpha = 0.12f))
                .border(
                    width = 1.8.dp,
                    color = orangeColor,
                    shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 22.dp, bottomEnd = 22.dp)
                )
                .clickable { onMarkStillLearning() }
                .padding(horizontal = 18.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "$stillLearningCount",
                    color = orangeColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Chưa nhớ",
                    color = orangeColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            }
        }

        // Center Quizlet Options button
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .clickable { onOpenQuizletOptions() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Tùy chọn Quizlet",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "Tùy chọn thẻ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Right Green Half-Pill (matching image_eade05aa.png)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp, topEnd = 4.dp, bottomEnd = 4.dp))
                .background(greenColor.copy(alpha = 0.12f))
                .border(
                    width = 1.8.dp,
                    color = greenColor,
                    shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp, topEnd = 4.dp, bottomEnd = 4.dp)
                )
                .clickable { onMarkMemorized() }
                .padding(horizontal = 18.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Đã nhớ",
                    color = greenColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
                Text(
                    text = "$memorizedCount",
                    color = greenColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

/**
 * Quizlet Options Modal matching screenshot /public/assets/aistudio/image_e71c9685.png
 */
@Composable
fun QuizletOptionsModal(
    viewModel: TranslationViewModel,
    onDismiss: () -> Unit
) {
    val isSorting = viewModel.quizletSortingEnabled.value
    val onlyStarred = viewModel.practiceOnlyStarred.value
    val ttsEnabled = viewModel.quizletTtsEnabled.value
    val isShuffle = viewModel.isShuffle.value
    val frontLang = viewModel.quizletFrontLang.value
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF0B0C2A),
            contentColor = Color.White,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Bar: X + "Tùy chọn"
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.White)
                    }
                    Text(
                        text = "Tùy chọn",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Card 1: "Chung"
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF24294D),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Chung",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        // Phương pháp học
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setQuizletSortingEnabled(!isSorting) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Phương pháp\nhọc",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isSorting) "Phân loại cơ bản" else "Lướt tự do",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF93C5FD)
                                )
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color(0xFF93C5FD)
                                )
                            }
                        }

                        // Chỉ học thuật ngữ có gắn sao
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Chỉ học thuật ngữ có gắn sao",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = onlyStarred,
                                onCheckedChange = {
                                    viewModel.practiceOnlyStarred.value = it
                                    viewModel.resetQuizletProgress()
                                }
                            )
                        }

                        // Chuyển văn bản thành lời nói
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Chuyển văn bản thành lời nói",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = ttsEnabled,
                                onCheckedChange = { viewModel.setQuizletTtsEnabled(it) }
                            )
                        }

                        // Trộn thẻ
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Trộn thẻ",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = isShuffle,
                                onCheckedChange = {
                                    viewModel.isShuffle.value = it
                                    viewModel.resetQuizletProgress()
                                }
                            )
                        }
                    }
                }

                // Card 2: "Thiết lập thẻ ghi nhớ"
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF24294D),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Thiết lập thẻ ghi nhớ",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Mặt trước",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xFF171B36))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val isForeignFront = frontLang != "vi"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isForeignFront) Color(0xFF2E3563) else Color.Transparent)
                                    .clickable { viewModel.setQuizletFrontLang("zh") }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Tiếng Trung (Giản thể)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (!isForeignFront) Color(0xFF2E3563) else Color.Transparent)
                                    .clickable { viewModel.setQuizletFrontLang("vi") }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Tiếng Việt",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Card 3: "Đặt lại Thẻ ghi nhớ"
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF24294D),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.resetQuizletProgress()
                            Toast.makeText(context, "Đã đặt lại Thẻ ghi nhớ!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                ) {
                    Text(
                        text = "Đặt lại Thẻ ghi nhớ",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF7A7A),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)
                    )
                }
            }
        }
    }
}

/**
 * End-of-deck Quizlet Completion Dialog asking whether to continue reviewing unmemorized cards
 * or reset all flashcards.
 */
@Composable
fun QuizletDeckCompletionDialog(
    memorizedCount: Int,
    stillLearningCount: Int,
    onContinueUnmemorized: () -> Unit,
    onResetDeck: () -> Unit,
    onBackToHome: () -> Unit
) {
    val total = (memorizedCount + stillLearningCount).coerceAtLeast(1)
    val percent = (memorizedCount * 100) / total

    AlertDialog(
        onDismissRequest = {},
        shape = RoundedCornerShape(28.dp),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("🎉 Hoàn thành lượt thẻ ghi nhớ!", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tỷ lệ đã nhớ: $percent% ($memorizedCount/$total thuật ngữ)",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF7A2F).copy(alpha = 0.14f)),
                        border = BorderStroke(1.5.dp, Color(0xFFFF7A2F))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Đang học (Chưa nhớ)", fontSize = 11.sp, color = Color(0xFFFF7A2F), fontWeight = FontWeight.Bold)
                            Text("$stillLearningCount", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF7A2F))
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF18AE79).copy(alpha = 0.14f)),
                        border = BorderStroke(1.5.dp, Color(0xFF18AE79))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Đã biết (Đã nhớ)", fontSize = 11.sp, color = Color(0xFF18AE79), fontWeight = FontWeight.Bold)
                            Text("$memorizedCount", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF18AE79))
                        }
                    }
                }

                if (stillLearningCount > 0) {
                    Button(
                        onClick = onContinueUnmemorized,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7A2F))
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tiếp tục ôn $stillLearningCount thuật ngữ chưa nhớ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color.White
                        )
                    }
                }

                OutlinedButton(
                    onClick = onResetDeck,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Đặt lại Thẻ ghi nhớ (Học lại từ đầu)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                TextButton(
                    onClick = onBackToHome,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Về màn hình chính", fontSize = 12.5.sp)
                }
            }
        },
        confirmButton = {}
    )
}
