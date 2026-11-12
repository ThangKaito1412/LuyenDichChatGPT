package com.example.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.BuiltinLibraryCatalog
import com.example.data.Folder
import com.example.data.StudySet
import com.example.viewmodel.TranslationViewModel

data class ParentBookCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val sealText: String,
    val coverQuery: String,
    val gradientColors: List<Color>,
    val matcher: (Folder) -> Boolean
)

data class BookCoverStyle(
    val sealText: String,
    val badgeText: String,
    val imageQuery: String,
    val colors: List<Color>
)

fun resolveBookCoverStyle(title: String): BookCoverStyle {
    val t = title.trim()
    return when {
        t.contains("Quyển 1") || t.startsWith("Q1 ") -> BookCoverStyle(
            sealText = "汉语一",
            badgeText = "GIÁO TRÌNH Q1",
            imageQuery = "汉语教程 课本 学习",
            colors = listOf(Color(0xFF1D4ED8), Color(0xFF0284C7))
        )
        t.contains("Quyển 2") || t.startsWith("Q2 ") -> BookCoverStyle(
            sealText = "汉语二",
            badgeText = "GIÁO TRÌNH Q2",
            imageQuery = "中国文化 汉字 书籍",
            colors = listOf(Color(0xFF047857), Color(0xFF10B981))
        )
        t.contains("Quyển 3") || t.startsWith("Q3 ") -> BookCoverStyle(
            sealText = "汉语三",
            badgeText = "GIÁO TRÌNH Q3",
            imageQuery = "商务汉语 面试 职场",
            colors = listOf(Color(0xFFB45309), Color(0xFFF59E0B))
        )
        t.contains("Chủ đề") || t.contains("Sách Ảnh") -> BookCoverStyle(
            sealText = "图解词典",
            badgeText = "SÁCH ẢNH CHỦ ĐỀ",
            imageQuery = "生活 物品 插画 图解",
            colors = listOf(Color(0xFF7C3AED), Color(0xFFEC4899))
        )
        t.contains("Công Xưởng") || t.contains("Công xưởng") -> BookCoverStyle(
            sealText = "工厂口语",
            badgeText = "1045 CÔNG XƯỞNG",
            imageQuery = "现代工厂 生产线 工程师",
            colors = listOf(Color(0xFF0F766E), Color(0xFF06B6D4))
        )
        t.contains("Hỏi Đáp") || t.contains("Hỏi đáp") -> BookCoverStyle(
            sealText = "日常会话",
            badgeText = "1000 HỎI ĐÁP",
            imageQuery = "日常对话 交流 咖啡馆",
            colors = listOf(Color(0xFFbe123c), Color(0xFFf43f5e))
        )
        t.contains("HSK 1") || t.contains("HSK 2") || t.contains("HSK 3") || t.contains("HSK 4") -> BookCoverStyle(
            sealText = "HSK 1-4",
            badgeText = "CHUẨN HSK 1-4",
            imageQuery = "汉语水平考试 词典 书籍",
            colors = listOf(Color(0xFF9A3412), Color(0xFFEA580C))
        )
        t.contains("HSK 5") || t.contains("HSK 6") -> BookCoverStyle(
            sealText = "HSK 5-6",
            badgeText = "CHUẨN HSK 5-6",
            imageQuery = "大学 图书馆 毕业 学术",
            colors = listOf(Color(0xFF4338CA), Color(0xFF6366F1))
        )
        t.contains("HSK") -> BookCoverStyle(
            sealText = "HSK 7-9",
            badgeText = "HSK CAO CẤP",
            imageQuery = "中国古典建筑 故宫 书法",
            colors = listOf(Color(0xFF78350F), Color(0xFFD97706))
        )
        t.contains("Từ vựng lưu nhanh") -> BookCoverStyle(
            sealText = "生词本",
            badgeText = "LƯU NHANH TỪ VỰNG",
            imageQuery = "单词卡 记忆 闪卡",
            colors = listOf(Color(0xFF0369A1), Color(0xFF0EA5E9))
        )
        t.contains("Ghi chú") -> BookCoverStyle(
            sealText = "笔记本",
            badgeText = "SỔ TAY GHI CHÚ",
            imageQuery = "笔记本 手账 钢笔",
            colors = listOf(Color(0xFF92400E), Color(0xFFF59E0B))
        )
        else -> BookCoverStyle(
            sealText = "自选集",
            badgeText = "TỰ TẠO",
            imageQuery = "中文学习 书桌",
            colors = listOf(Color(0xFF334155), Color(0xFF64748B))
        )
    }
}

@Composable
fun BookCoverThumbnail(
    title: String,
    modifier: Modifier = Modifier,
    widthDp: Int = 66,
    heightDp: Int = 86
) {
    val context = LocalContext.current
    val style = remember(title) { resolveBookCoverStyle(title) }

    Box(
        modifier = modifier
            .width(widthDp.dp)
            .height(heightDp.dp)
            .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 12.dp, bottomEnd = 12.dp))
            .background(Brush.linearGradient(style.colors))
            .border(
                1.dp,
                Color.White.copy(alpha = 0.28f),
                RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 12.dp, bottomEnd = 12.dp)
            )
    ) {
        // Subtle background image overlay
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(buildBingThumbUrl(style.imageQuery, 1))
                .crossfade(true)
                .build(),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            alpha = 0.34f,
            modifier = Modifier.fillMaxSize()
        )

        // Book Spine Stripe on Left
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(6.dp)
                .background(Color.Black.copy(alpha = 0.28f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 9.dp, end = 5.dp, top = 6.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.45f)
            ) {
                Text(
                    text = style.badgeText,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                )
            }

            Text(
                text = style.sealText,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )

            Text(
                text = title.replace(Regex("^[\\p{So}\\p{Cn}\\s]+"), "").take(16),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.95f),
                maxLines = 2,
                lineHeight = 10.sp,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun HierarchicalBookLibraryCard(
    folders: List<Folder>,
    userDecks: List<StudySet>,
    allStudySets: List<StudySet>,
    viewModel: TranslationViewModel,
    onSelectDeckForSetup: (StudySet) -> Unit,
    onStartDeckWithConfig: (StudySet, Int, Boolean) -> Unit,
    onEditDeckVocab: (StudySet) -> Unit,
    onAssignFolderForDeck: (StudySet) -> Unit,
    onOpenCreateFolder: () -> Unit,
    onOpenManageFolders: () -> Unit,
    onOpenQuickDualSave: () -> Unit,
    onOpenVisualBookDictionary: (title: String, sets: List<StudySet>) -> Unit,
    onOpenVisualWordDetail: (VisualVocabEntry, List<VisualVocabEntry>) -> Unit
) {
    val context = LocalContext.current

    // 2 Display Modes for Folder & Book Management: true = Có ảnh minh họa (Illustrated Cover Cards), false = Dạng danh sách (Compact List)
    var isIllustratedFolderMode by remember { mutableStateOf(true) }

    // Hierarchical Navigation State:
    // Level 1: Parent Category ID ("giaotrinh", "hsk", "chude", "giaotiep", "canhan")
    // Level 2: Selected Folder ID (Sub-book / HSK level / Custom folder)
    // Level 3: Individual Lessons (StudySet) inside the selected sub-folder
    var selectedCategoryId by remember { mutableStateOf("giaotrinh") }
    var selectedSubFolderId by remember { mutableStateOf<Long?>(null) }
    var hskSubLevelFilter by remember { mutableStateOf("ALL") } // "ALL", "HSK 1", "HSK 2", "HSK 3", "HSK 4", "HSK 5", "HSK 6", "HSK 6+", "HSK 7-9"
    var localSearchQuery by remember { mutableStateOf("") }
    var visibleLessonLimit by remember(selectedCategoryId, selectedSubFolderId, hskSubLevelFilter, localSearchQuery) {
        mutableStateOf(16)
    }

    // Confirmation dialog state before deleting a user-created StudySet or Folder
    var studySetPendingDelete by remember { mutableStateOf<StudySet?>(null) }
    var studySetPendingLaunchConfig by remember { mutableStateOf<StudySet?>(null) }

    val categories = remember {
        listOf(
            ParentBookCategory(
                id = "giaotrinh",
                title = "📚 Tủ Sách Giáo Trình",
                subtitle = "Quyển 1 • Quyển 2 • Quyển 3 (48 Bài)",
                sealText = "标准教程",
                coverQuery = "汉语教程 课本",
                gradientColors = listOf(Color(0xFF1D4ED8), Color(0xFF0284C7)),
                matcher = { f -> f.name.contains("Giáo trình") }
            ),
            ParentBookCategory(
                id = "hsk",
                title = "🏆 Từ Vựng HSK 1 – 9",
                subtitle = "Phân cấp HSK 1 đến HSK 9 (137 Bài)",
                sealText = "HSK 词库",
                coverQuery = "HSK 汉语水平考试",
                gradientColors = listOf(Color(0xFF9A3412), Color(0xFFD97706)),
                matcher = { f -> f.name.contains("HSK") }
            ),
            ParentBookCategory(
                id = "chude",
                title = "🖼️ Sách Ảnh Chủ Đề",
                subtitle = "112 Chủ đề Từ vựng Minh họa",
                sealText = "图解词典",
                coverQuery = "图解词典 插画",
                gradientColors = listOf(Color(0xFF6D28D9), Color(0xFFDB2777)),
                matcher = { f -> f.name.contains("Chủ đề") || f.name.contains("Sách Ảnh") }
            ),
            ParentBookCategory(
                id = "giaotiep",
                title = "🏭 Giao Tiếp & Công Xưởng",
                subtitle = "1045 Công xưởng • 1000 Hỏi đáp",
                sealText = "实用口语",
                coverQuery = "工厂 职场 对话",
                gradientColors = listOf(Color(0xFF0F766E), Color(0xFF0891B2)),
                matcher = { f -> f.name.contains("Công Xưởng") || f.name.contains("Hỏi Đáp") }
            ),
            ParentBookCategory(
                id = "canhan",
                title = "🗂️ Thư Mục Riêng & Ghi Chú",
                subtitle = "Lưu nhanh Từ vựng • Sổ tay Ghi chú",
                sealText = "个人书架",
                coverQuery = "笔记本 单词卡",
                gradientColors = listOf(Color(0xFF334155), Color(0xFF0F172A)),
                matcher = { f ->
                    !f.name.contains("Giáo trình") &&
                        !f.name.contains("HSK") &&
                        !f.name.contains("Chủ đề") &&
                        !f.name.contains("Công Xưởng") &&
                        !f.name.contains("Hỏi Đáp")
                }
            )
        )
    }

    val activeCategory = categories.find { it.id == selectedCategoryId } ?: categories.first()
    val subFoldersInCategory = remember(folders, activeCategory) {
        folders.filter(activeCategory.matcher)
    }

    LaunchedEffect(selectedCategoryId, subFoldersInCategory) {
        if (subFoldersInCategory.isNotEmpty() && subFoldersInCategory.none { it.id == selectedSubFolderId }) {
            selectedSubFolderId = subFoldersInCategory.first().id
        }
        if (selectedCategoryId != "hsk") {
            hskSubLevelFilter = "ALL"
        }
    }

    val activeSubFolder = subFoldersInCategory.find { it.id == selectedSubFolderId } ?: subFoldersInCategory.firstOrNull()

    val setsInActiveSubFolder = remember(userDecks, activeSubFolder, selectedCategoryId, hskSubLevelFilter, localSearchQuery) {
        val baseList = if (activeSubFolder != null) {
            userDecks.filter { it.folderId == activeSubFolder.id }
        } else if (selectedCategoryId == "canhan") {
            userDecks.filter { it.folderId == null }
        } else {
            emptyList()
        }

        val afterHskFilter = if (selectedCategoryId == "hsk" && hskSubLevelFilter != "ALL") {
            val allHskFolderIds = subFoldersInCategory.map { it.id }.toSet()
            userDecks.filter {
                allHskFolderIds.contains(it.folderId) && it.title.startsWith(hskSubLevelFilter)
            }
        } else {
            baseList
        }

        val q = localSearchQuery.trim()
        if (q.isEmpty()) afterHskFilter
        else afterHskFilter.filter {
            TwoWayTranslatorEngine.matchesQueryWithPinyin("${it.title}\n${it.rawContent}", q)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Header + 2-State View Switcher (Có ảnh minh họa | Dạng danh sách)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📚 TỦ SÁCH & QUẢN LÝ THƯ MỤC ĐA CẤP",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Thư mục lớn ➔ Bộ sách có ảnh bìa ➔ Bài học lẻ • Bảo vệ dữ liệu gốc",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onOpenQuickDualSave,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.BookmarkAdd,
                            contentDescription = "Lưu nhanh 2 loại",
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenCreateFolder,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.CreateNewFolder,
                            contentDescription = "Tạo thư mục mới",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenManageFolders,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.FolderCopy,
                            contentDescription = "Quản lý thư mục",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // View Mode Toggle Row + Study Word Count Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        modifier = Modifier.clickable { isIllustratedFolderMode = true },
                        shape = RoundedCornerShape(9.dp),
                        color = if (isIllustratedFolderMode) MaterialTheme.colorScheme.primary else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.GridView,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (isIllustratedFolderMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Có ảnh minh họa",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isIllustratedFolderMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.clickable { isIllustratedFolderMode = false },
                        shape = RoundedCornerShape(9.dp),
                        color = if (!isIllustratedFolderMode) MaterialTheme.colorScheme.primary else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.FormatListBulleted,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (!isIllustratedFolderMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Dạng danh sách",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isIllustratedFolderMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Current Word Limit & Order Badge
                val limitVal = viewModel.studyWordCountLimit.value
                val orderLabel = if (viewModel.isShuffle.value) "Ngẫu nhiên" else "Theo thứ tự"
                val countLabel = if (limitVal <= 0) "Tất cả từ" else "$limitVal từ/bài"
                Surface(
                    modifier = Modifier.clickable {
                        val firstDeck = setsInActiveSubFolder.firstOrNull() ?: userDecks.firstOrNull()
                        if (firstDeck != null) {
                            studySetPendingLaunchConfig = firstDeck
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "🎯 $countLabel • $orderLabel",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }

            // LEVEL 1: Parent Category Shelf (Thư mục cha)
            Text(
                text = "CẤP 1: CHỌN TỦ SÁCH / CHUYÊN MỤC LỚN",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = cat.id == selectedCategoryId
                    val folderCount = folders.count(cat.matcher)
                    if (isIllustratedFolderMode) {
                        Card(
                            modifier = Modifier
                                .width(195.dp)
                                .clickable { selectedCategoryId = cat.id },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            border = BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                BookCoverThumbnail(
                                    title = cat.title,
                                    widthDp = 52,
                                    heightDp = 68
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cat.title,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$folderCount bộ sách",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    } else {
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategoryId = cat.id },
                            label = {
                                Text(
                                    "${cat.title} ($folderCount)",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }
            }

            // LEVEL 2: Sub-Folders / Books inside the selected Parent Category
            Text(
                text = "CẤP 2: CHỌN BỘ SÁCH / THƯ MỤC CON TRONG '${activeCategory.title.uppercase()}'",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                subFoldersInCategory.forEach { subFolder ->
                    val setsInThisFolder = userDecks.filter { it.folderId == subFolder.id }
                    val isSelected = activeSubFolder?.id == subFolder.id && hskSubLevelFilter == "ALL"
                    val isProtected = BuiltinLibraryCatalog.isProtectedFolder(subFolder.name)

                    if (isIllustratedFolderMode) {
                        Card(
                            modifier = Modifier
                                .width(235.dp)
                                .clickable {
                                    selectedSubFolderId = subFolder.id
                                    hskSubLevelFilter = "ALL"
                                },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                BookCoverThumbnail(
                                    title = subFolder.name,
                                    widthDp = 58,
                                    heightDp = 78
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = subFolder.name,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${setsInThisFolder.size} bài",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (isProtected) {
                                            Text(
                                                text = "🔒 Mặc định",
                                                fontSize = 9.5.sp,
                                                color = Color(0xFF059669),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        modifier = Modifier.clickable {
                                            onOpenVisualBookDictionary(subFolder.name, setsInThisFolder)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFFEF3C7),
                                        border = BorderStroke(1.dp, Color(0xFFF59E0B))
                                    ) {
                                        Text(
                                            text = "📖 Từ điển Trực quan",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF92400E),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedSubFolderId = subFolder.id
                                hskSubLevelFilter = "ALL"
                            },
                            label = {
                                Text(
                                    "${subFolder.name} (${setsInThisFolder.size})",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }
            }

            // Extra Level Sub-Pills when in HSK Category (HSK 1, HSK 2, HSK 3, HSK 4, HSK 5, HSK 6, HSK 6+, HSK 7-9)
            if (selectedCategoryId == "hsk") {
                val hskLevels = listOf("ALL", "HSK 1 -", "HSK 2 -", "HSK 3 -", "HSK 4 -", "HSK 5 -", "HSK 6 -", "HSK 6+", "HSK 7-9")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    hskLevels.forEach { lvl ->
                        val isSel = hskSubLevelFilter == lvl
                        FilterChip(
                            selected = isSel,
                            onClick = { hskSubLevelFilter = lvl },
                            label = {
                                Text(
                                    text = if (lvl == "ALL") "Tất cả trong nhóm" else lvl.removeSuffix(" -"),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }
            }

            // Active Book / Folder Quick Bar: Open Full Visual Dictionary (matching image_c4fef58f.png & image_f70a4fa7.png)
            if (setsInActiveSubFolder.isNotEmpty()) {
                val activeTitle = if (selectedCategoryId == "hsk" && hskSubLevelFilter != "ALL") {
                    "Từ vựng ${hskSubLevelFilter.removeSuffix(" -")}"
                } else {
                    activeSubFolder?.name ?: activeCategory.title
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onOpenVisualBookDictionary(activeTitle, setsInActiveSubFolder)
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFFFBEB),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🖼️ Mở Từ Điển Trực Quan & Tra Cứu: $activeTitle",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = "Xem dạng Thẻ có ảnh minh họa hoặc Bảng danh sách • Ấn vào từ để xem Ảnh & Câu ví dụ",
                                fontSize = 10.5.sp,
                                color = Color(0xFFB45309)
                            )
                        }
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = Color(0xFF92400E),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Search / Filter inside active Book / Folder
            OutlinedTextField(
                value = localSearchQuery,
                onValueChange = { localSearchQuery = it },
                placeholder = {
                    Text(
                        "🔍 Lọc bài học hoặc tìm từ (chữ Hán, Pinyin không dấu, Tiếng Việt) trong mục này...",
                        fontSize = 12.sp
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (localSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { localSearchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Xóa", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            )

            // LEVEL 3: Individual Lessons (StudySet) inside the active Book/Sub-folder
            Text(
                text = "CẤP 3: DANH SÁCH BÀI HỌC TRONG BỘ SÁCH (${setsInActiveSubFolder.size} BÀI)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            if (setsInActiveSubFolder.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Chưa có bài học nào trong thư mục này.",
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                val displayedSets = setsInActiveSubFolder.take(visibleLessonLimit)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    displayedSets.forEach { deck ->
                        val wordCount = remember(deck.rawContent) {
                            deck.rawContent.count { it == '\n' } + 1
                        }
                        val isProtectedSet = remember(deck, folders) {
                            BuiltinLibraryCatalog.isProtectedStudySet(deck, folders)
                        }
                        val isCurrentlyLoaded = viewModel.deckTitle.value == deck.title

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectDeckForSetup(deck) },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrentlyLoaded)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            border = BorderStroke(
                                if (isCurrentlyLoaded) 1.8.dp else 1.dp,
                                if (isCurrentlyLoaded) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (isIllustratedFolderMode) {
                                        BookCoverThumbnail(
                                            title = deck.title,
                                            widthDp = 48,
                                            heightDp = 64
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = deck.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                                            ) {
                                                Text(
                                                    text = "$wordCount từ/câu",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            if (isProtectedSet) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFD1FAE5)
                                                ) {
                                                    Text(
                                                        text = "🔒 Sách gốc",
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF065F46),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Button: Chọn số từ & Học
                                    Button(
                                        onClick = { studySetPendingLaunchConfig = deck },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Học", modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Học", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Lesson Action Toolbar: [Từ điển ảnh] [Thêm/Sửa từ] [Tập viết] [Sao chép] [Di chuyển] [Xóa (nếu không phải sách gốc)]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            modifier = Modifier.clickable {
                                                onOpenVisualBookDictionary(deck.title, listOf(deck))
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFFFFBEB),
                                            border = BorderStroke(1.dp, Color(0xFFF59E0B))
                                        ) {
                                            Text(
                                                text = "🖼️ Từ điển ảnh",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF92400E),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        Surface(
                                            modifier = Modifier.clickable { onEditDeckVocab(deck) },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        ) {
                                            Text(
                                                text = "✏️ Sửa / Thêm từ",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                viewModel.rawTextContent.value = deck.rawContent
                                                viewModel.targetLanguage.value = deck.language
                                                viewModel.deckTitle.value = deck.title
                                                viewModel.openWriting()
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Brush,
                                                contentDescription = "Tập viết",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(android.content.ClipData.newPlainText(deck.title, deck.rawContent))
                                                Toast.makeText(context, "Đã sao chép: ${deck.title}", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Sao chép",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        if (!isProtectedSet) {
                                            IconButton(
                                                onClick = { onAssignFolderForDeck(deck) },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DriveFileMove,
                                                    contentDescription = "Chuyển thư mục",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { studySetPendingDelete = deck },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Xóa bài học",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (setsInActiveSubFolder.size > visibleLessonLimit) {
                        OutlinedButton(
                            onClick = { visibleLessonLimit += 25 },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = "Xem thêm (${setsInActiveSubFolder.size - visibleLessonLimit} bài học nữa)...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Deleting a User StudySet (with System Notification)
    studySetPendingDelete?.let { targetDeck ->
        AlertDialog(
            onDismissRequest = { studySetPendingDelete = null },
            title = { Text("⚠️ Xác nhận xóa bài học", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    "Bạn có chắc chắn muốn xóa bài học '${targetDeck.title}' không? Hệ thống sẽ gửi thông báo xác nhận sau khi xóa.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStudySet(targetDeck) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                        studySetPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa bài học", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { studySetPendingDelete = null }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Launch Config Dialog: Choose word count & sequential/random order before starting
    studySetPendingLaunchConfig?.let { deckToStart ->
        LessonStudyCountSelectorDialog(
            studySet = deckToStart,
            currentLimit = viewModel.studyWordCountLimit.value,
            isRandom = viewModel.isShuffle.value,
            onDismiss = { studySetPendingLaunchConfig = null },
            onConfirmStart = { chosenLimit, chosenRandom ->
                studySetPendingLaunchConfig = null
                onStartDeckWithConfig(deckToStart, chosenLimit, chosenRandom)
            }
        )
    }
}
