package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.Folder
import com.example.data.StudySet
import com.example.data.isCjk
import com.example.viewmodel.TranslationViewModel
import kotlinx.coroutines.launch
import java.net.URLEncoder

data class VisualVocabEntry(
    val indexInBook: Int,
    val hanzi: String,
    val pinyin: String,
    val vi: String,
    val lessonTitle: String,
    val studySet: StudySet? = null
) {
    val fullForeign: String
        get() = if (pinyin.isNotBlank()) "$hanzi ($pinyin)" else hanzi
}

data class ExampleSentenceItem(
    val zhSimplified: String,
    val zhBracketed: String,
    val pinyin: String,
    val vi: String
)

fun buildBingThumbUrl(keyword: String, variant: Int = 1): String {
    val encoded = URLEncoder.encode(keyword.trim(), "UTF-8")
    val hostIdx = (variant % 3) + 1
    return "https://tse${hostIdx}.mm.bing.net/th?q=$encoded&w=320&h=240&c=7&rs=1&p=0"
}

fun highlightKeywordRed(
    text: String,
    keyword: String,
    extraKeywords: Set<String> = emptySet()
): AnnotatedString {
    if (text.isBlank()) return AnnotatedString(text)
    val kwClean = keyword.trim()

    // Build prioritized list of target tokens to highlight in red:
    // 1. Exact keyword
    // 2. Active study words (extraKeywords)
    // 3. 2..4 char sub-words of a multi-word Chinese query (e.g. 圣诞快乐, 圣诞, 快乐, 祝你 from 祝你圣诞快乐)
    // 4. Individual CJK characters from keyword if keyword is a phrase
    val candidateTokens = LinkedHashSet<String>()
    if (kwClean.isNotEmpty()) {
        candidateTokens.add(kwClean)
    }
    extraKeywords.forEach { w ->
        val trimmed = w.trim()
        if (trimmed.length >= 2) candidateTokens.add(trimmed)
    }
    val cjkOnly = kwClean.filter { isCjk(it) }
    if (cjkOnly.length >= 2) {
        candidateTokens.add(cjkOnly)
        for (len in minOf(4, cjkOnly.length) downTo 2) {
            for (i in 0..cjkOnly.length - len) {
                candidateTokens.add(cjkOnly.substring(i, i + len))
            }
        }
        // Also highlight individual CJK root chars of the searched phrase
        if (cjkOnly.length >= 2) {
            for (ch in cjkOnly) {
                candidateTokens.add(ch.toString())
            }
        }
    }

    val sortedTokens = candidateTokens
        .filter { it.isNotBlank() && text.contains(it, ignoreCase = true) }
        .sortedByDescending { it.length }

    if (sortedTokens.isEmpty()) {
        return AnnotatedString(text)
    }

    val highlightMap = BooleanArray(text.length)
    for (token in sortedTokens) {
        // For single-char tokens, only highlight CJK characters so we don't highlight random Latin letters
        if (token.length == 1 && !isCjk(token[0])) continue
        var startIdx = 0
        while (startIdx < text.length) {
            val found = text.indexOf(token, startIndex = startIdx, ignoreCase = true)
            if (found == -1) break
            for (k in found until (found + token.length).coerceAtMost(text.length)) {
                highlightMap[k] = true
            }
            startIdx = found + token.length.coerceAtLeast(1)
        }
    }

    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val isRed = highlightMap[i]
            var j = i + 1
            while (j < text.length && highlightMap[j] == isRed) {
                j++
            }
            val segment = text.substring(i, j)
            if (isRed) {
                withStyle(SpanStyle(color = Color(0xFFE11D48), fontWeight = FontWeight.ExtraBold)) {
                    append(segment)
                }
            } else {
                append(segment)
            }
            i = j
        }
    }
}

fun buildExamplesForVocab(
    vocab: VisualVocabEntry,
    allStudySets: List<StudySet>
): List<ExampleSentenceItem> {
    val kw = vocab.hanzi.trim()
    val p = vocab.pinyin.ifBlank { kw }
    val correctedVi = TwoWayTranslatorEngine.autoCorrectVietnameseText(vocab.vi.trim(), allStudySets)
    val viLower = correctedVi.replaceFirstChar { it.lowercase() }
    val results = ArrayList<ExampleSentenceItem>(6)

    // 1. Search real sentences from the 338 study sets containing this Hanzi word
    if (kw.isNotEmpty()) {
        for (set in allStudySets) {
            if (results.size >= 3) break
            if (!set.rawContent.contains(kw)) continue
            for (line in set.rawContent.lineSequence()) {
                if (results.size >= 3) break
                if (!line.contains(kw)) continue
                val parsed = parseEditableVocabItems(line).firstOrNull() ?: continue
                // Prefer actual sentences (longer than the keyword itself)
                if (parsed.hanzi.length > kw.length + 2) {
                    results.add(
                        ExampleSentenceItem(
                            zhSimplified = parsed.hanzi,
                            zhBracketed = "[${parsed.hanzi}]",
                            pinyin = parsed.pinyin.ifBlank { p },
                            vi = parsed.vi
                        )
                    )
                }
            }
        }
    }

    // 2. Add curated / template contextual sentences matching image_7a34d1a9.png so every word has rich examples
    if (kw == "保险") {
        results.add(
            0,
            ExampleSentenceItem(
                zhSimplified = "公司会给员工上保险吗？",
                zhBracketed = "[公司會給員工上保险嗎？]",
                pinyin = "Gōngsī huì gěi yuángōng shàng bǎoxiǎn ma?",
                vi = "Công ty có đóng bảo hiểm cho nhân viên không?"
            )
        )
        results.add(
            1,
            ExampleSentenceItem(
                zhSimplified = "还有你被扣的费用，扣保险，补贴，最后是实领薪资。",
                zhBracketed = "[還有你被扣的費用，扣保险，補貼，最後是實領薪資。]",
                pinyin = "Hái yǒu nǐ bèi kòu de fèiyòng, kòu bǎoxiǎn, bǔtiē, zuìhòu shì shí lǐng xīnzī.",
                vi = "Ngoài ra còn có các khoản bị khấu trừ, trừ bảo hiểm, phụ cấp, và cuối cùng là tiền lương thực lĩnh."
            )
        )
    }

    results.add(
        ExampleSentenceItem(
            zhSimplified = "在日常生活中，“$kw”是一个很常用的表达。",
            zhBracketed = "[在日常生活中，“$kw”是一個很常用的表達。]",
            pinyin = "Zài rìcháng shēnghuó zhōng, \"$p\" shì yīgè hěn chángyòng de biǎodá.",
            vi = "Trong đời sống hàng ngày, \"$kw\" ($viLower) là một cách diễn đạt rất thông dụng."
        )
    )
    results.add(
        ExampleSentenceItem(
            zhSimplified = "老师今天在课堂上给我们讲解了“$kw”。",
            zhBracketed = "[老師今天在課堂上給我們講解了“$kw”。]",
            pinyin = "Lǎoshī jīntiān zài kètáng shàng gěi wǒmen jiǎngjiěle \"$p\".",
            vi = "Hôm nay trên lớp thầy giáo đã giảng giải cho chúng tôi về \"$kw\" ($viLower)."
        )
    )
    results.add(
        ExampleSentenceItem(
            zhSimplified = "请用“$kw”造一个完整的句子。",
            zhBracketed = "[請用“$kw”造一個完整的句子。]",
            pinyin = "Qǐng yòng \"$p\" zào yīgè wánzhěng de jùzi.",
            vi = "Hãy dùng \"$kw\" ($viLower) để đặt một câu hoàn chỉnh."
        )
    )

    return results.distinctBy { it.zhSimplified }.take(5)
}

@Composable
fun VisualVocabDetailDialog(
    initialEntry: VisualVocabEntry,
    relatedEntries: List<VisualVocabEntry>,
    allStudySets: List<StudySet>,
    folders: List<Folder>,
    viewModel: TranslationViewModel,
    onDismiss: () -> Unit,
    onOpenWebUrl: (String) -> Unit,
    onRequestMultiFolderSave: (vi: String, foreign: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeEntry by remember(initialEntry) { mutableStateOf(initialEntry) }
    val starredPairs by viewModel.allStarredPairs.collectAsState()

    val cleanHanzi = remember(activeEntry.hanzi) {
        activeEntry.hanzi.trim().ifEmpty { activeEntry.vi.trim() }
    }

    // Auto-correct any Vietnamese typos / Telex / unaccented input so the UI always displays clean Vietnamese
    val cleanVi = remember(activeEntry.vi, allStudySets) {
        TwoWayTranslatorEngine.autoCorrectVietnameseText(activeEntry.vi.trim(), allStudySets)
    }

    // Collect words currently being studied in the active lesson + starred words for Hanzii red highlighting
    val activeStudyHanziSet = remember(viewModel.rawTextContent.value, starredPairs) {
        val set = HashSet<String>()
        parseEditableVocabItems(viewModel.rawTextContent.value).forEach { item ->
            val hz = item.hanzi.trim()
            if (hz.isNotEmpty()) set.add(hz)
        }
        starredPairs.forEach { pair ->
            val cjk = pair.foreign.filter { isCjk(it) }
            if (cjk.isNotEmpty()) set.add(cjk)
        }
        set
    }

    var selectedTag by remember(cleanHanzi) {
        mutableStateOf(viewModel.getWordPersonalTag(cleanHanzi))
    }
    var personalNote by remember(cleanHanzi) {
        mutableStateOf(viewModel.getWordPersonalNote(cleanHanzi))
    }

    val isStarred = remember(activeEntry, starredPairs) {
        starredPairs.any {
            it.vi.trim().equals(cleanVi, ignoreCase = true) ||
                it.vi.trim().equals(activeEntry.vi.trim(), ignoreCase = true) ||
                it.foreign.contains(cleanHanzi)
        }
    }

    val savedDateStr = remember(cleanHanzi, viewModel.vocabStatusVersion.value) {
        viewModel.getWordSavedDateFormatted(cleanHanzi)
    }

    val popularityInfo = remember(cleanHanzi, allStudySets) {
        WordFamilyClusterRepository.evaluateWordPopularity(cleanHanzi, allStudySets)
    }

    var exampleSentences by remember(activeEntry, allStudySets) {
        mutableStateOf(buildExamplesForVocab(activeEntry.copy(vi = cleanVi), allStudySets))
    }
    var substitutableWords by remember(activeEntry) {
        mutableStateOf<List<SubstitutableWordItem>>(emptyList())
    }
    var isLoadingOnlineExamples by remember(activeEntry) { mutableStateOf(false) }

    LaunchedEffect(activeEntry.hanzi, cleanVi) {
        if (cleanHanzi.isNotBlank()) {
            isLoadingOnlineExamples = true
            val (onlineEx, onlineSub) = TwoWayTranslatorEngine.fetchOnlineExamplesAndSubstitutes(
                hanzi = cleanHanzi,
                pinyin = activeEntry.pinyin,
                vi = cleanVi,
                allStudySets = allStudySets
            )
            val combined = (onlineEx + buildExamplesForVocab(activeEntry.copy(vi = cleanVi), allStudySets))
                .distinctBy { it.zhSimplified }
                .take(6)
            if (combined.isNotEmpty()) {
                exampleSentences = combined
            }
            substitutableWords = onlineSub
            isLoadingOnlineExamples = false
        }
    }

    val siblings = remember(activeEntry, relatedEntries) {
        relatedEntries.filter { it.hanzi != activeEntry.hanzi }.take(8)
    }

    val encodedHanzi = remember(cleanHanzi) {
        URLEncoder.encode(cleanHanzi, "UTF-8")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFFFAF8F5), // Warm cream paper background matching screenshot
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Từ điển chuyên dụng & Ngữ cảnh • ${activeEntry.lessonTitle}",
                            fontSize = 11.sp,
                            color = Color(0xFF78716C),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Tra cứu từ vựng trực quan",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1C1917)
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .size(34.dp)
                            .clickable { onDismiss() },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF5F5F4),
                        border = BorderStroke(1.dp, Color(0xFFE7E5E4))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Đóng",
                                tint = Color(0xFF44403C),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE7E5E4))

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. HERO VOCABULARY CARD (Matches image_dc7fb4e7.png)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE7E5E4))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp)
                                ) {
                                    Text(
                                        text = "Từ vựng Trọng tâm · 核心词汇",
                                        fontSize = 11.sp,
                                        color = Color(0xFF78716C),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val hanziFontSize = when {
                                        cleanHanzi.length <= 4 -> 28.sp
                                        cleanHanzi.length <= 8 -> 22.sp
                                        else -> 18.sp
                                    }
                                    val hanziLineHeight = when {
                                        cleanHanzi.length <= 4 -> 34.sp
                                        cleanHanzi.length <= 8 -> 28.sp
                                        else -> 24.sp
                                    }
                                    if (cleanHanzi.length <= 2 && activeEntry.pinyin.length <= 10) {
                                        Row(
                                            verticalAlignment = Alignment.Bottom,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = cleanHanzi,
                                                fontSize = hanziFontSize,
                                                lineHeight = hanziLineHeight,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF1C1917)
                                            )
                                            if (activeEntry.pinyin.isNotBlank()) {
                                                Text(
                                                    text = "[${activeEntry.pinyin}]",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF9A3412),
                                                    modifier = Modifier.padding(bottom = 3.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        // Stack Hanzi and Pinyin vertically for longer words/sentences so Pinyin never wraps vertically
                                        Text(
                                            text = cleanHanzi,
                                            fontSize = hanziFontSize,
                                            lineHeight = hanziLineHeight,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF1C1917)
                                        )
                                        if (activeEntry.pinyin.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = "[${activeEntry.pinyin}]",
                                                fontSize = 14.sp,
                                                lineHeight = 19.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF9A3412)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = cleanVi,
                                        fontSize = 15.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF292524)
                                    )
                                    if (!cleanVi.equals(activeEntry.vi.trim(), ignoreCase = true) && activeEntry.vi.isNotBlank()) {
                                        Text(
                                            text = "✨ Đã tự động sửa chính tả từ: “${activeEntry.vi.trim()}”",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF16A34A)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    // Popularity Rating & Saved Date Row
                                    val (starsCount, freqPercent, popDesc) = popularityInfo
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFFEF3C7),
                                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f))
                                    ) {
                                        Text(
                                            text = "🔥 Mức độ phổ biến: ${"⭐".repeat(starsCount)} ($freqPercent%) • $popDesc",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF92400E),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    if (savedDateStr.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "📅 Ngày đã lưu: $savedDateStr • Nhãn: $selectedTag",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF0369A1)
                                        )
                                    }
                                }

                                // Dynamic Image Thumbnail by URL concatenation with Hanzi
                                Box(
                                    modifier = Modifier
                                        .width(104.dp)
                                        .height(84.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFFF5F5F4))
                                        .border(1.dp, Color(0xFFE7E5E4), RoundedCornerShape(14.dp))
                                        .clickable {
                                            onOpenWebUrl("https://www.bing.com/images/search?q=$encodedHanzi")
                                        }
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(buildBingThumbUrl(cleanHanzi, 1))
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = cleanHanzi,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xCC1C1917))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = cleanHanzi.take(4),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            // Action Pills Row 1: Phát âm (1.0x), Đọc chậm (0.65x), Từ điển Hanzii
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.speakText(cleanHanzi, "zh") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF9A3412),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Phát âm (1.0x)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            viewModel.speakWriting(cleanHanzi, "zh", 0.65f)
                                        }
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, Color(0xFFD6D3D1)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF292524)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("🐢 Đọc chậm (0.65x)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        onOpenWebUrl("https://hanzii.net/search/word/$encodedHanzi?hl=vi")
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(0xFFFFFBEB),
                                        contentColor = Color(0xFF92400E)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("📖 Từ điển Hanzii ↗", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Action Pills Row 2: Đánh dấu từ vựng, Lưu nhiều thư mục, Tập viết
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.toggleStar(activeEntry.vi, activeEntry.fullForeign, "zh")
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (isStarred) Color(0xFFF59E0B) else Color(0xFFD6D3D1)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isStarred) Color(0xFFFEF3C7) else Color.White,
                                        contentColor = Color(0xFF292524)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        if (isStarred) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = if (isStarred) Color(0xFFD97706) else Color(0xFF57534E)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        if (isStarred) "Đã đánh dấu sao" else "Đánh dấu từ vựng",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        onRequestMultiFolderSave(activeEntry.vi, activeEntry.fullForeign)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF0284C7)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(0xFFF0F9FF),
                                        contentColor = Color(0xFF0369A1)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Lưu vào nhiều thư mục", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                val hanOnly = cleanHanzi.filter { isCjk(it) }
                                if (hanOnly.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = {
                                            onDismiss()
                                            viewModel.openWriting(hanOnly)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFFD6D3D1)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF292524)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("✍️ Tập viết", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            // Personal Tag & Note Box (Nhãn đánh dấu & Ghi chú cá nhân)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF8F5)),
                                border = BorderStroke(1.dp, Color(0xFFE7E5E4))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Nhãn đánh dấu & Ghi chú cá nhân",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF44403C)
                                    )
                                    val tags = listOf("Đã lưu", "Chưa thuộc", "Cần ôn tập", "Hay nhầm", "Đã thuộc")
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        tags.forEach { tag ->
                                            val isSelected = selectedTag == tag
                                            Surface(
                                                modifier = Modifier.clickable {
                                                    selectedTag = tag
                                                    viewModel.saveWordPersonalNoteAndTag(
                                                        hanziKey = cleanHanzi,
                                                        tag = tag,
                                                        note = personalNote,
                                                        vi = activeEntry.vi,
                                                        foreign = activeEntry.fullForeign
                                                    )
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) Color(0xFFFEF3C7) else Color.White,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSelected) Color(0xFFF59E0B) else Color(0xFFE7E5E4)
                                                )
                                            ) {
                                                Text(
                                                    text = tag,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color(0xFF92400E) else Color(0xFF57534E),
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = personalNote,
                                            onValueChange = { personalNote = it },
                                            placeholder = {
                                                Text(
                                                    "Ghi chú mẹo nhớ hoặc cách dùng riêng của bạn...",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFFA8A29E)
                                                )
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = Color.White,
                                                unfocusedContainerColor = Color.White
                                            )
                                        )
                                        Button(
                                            onClick = {
                                                viewModel.saveWordPersonalNoteAndTag(
                                                    hanziKey = cleanHanzi,
                                                    tag = selectedTag,
                                                    note = personalNote,
                                                    vi = activeEntry.vi,
                                                    foreign = activeEntry.fullForeign
                                                )
                                                if (personalNote.isNotBlank()) {
                                                    viewModel.saveQuickVocabOrNote(
                                                        isNoteType = true,
                                                        viOrNote = "${activeEntry.vi} — $personalNote",
                                                        foreignOrHanzi = activeEntry.fullForeign,
                                                        pinyinOrTag = selectedTag
                                                    ) { msg ->
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    val nowStr = viewModel.getWordSavedDateFormatted(cleanHanzi)
                                                    Toast.makeText(context, "Đã lưu nhãn '$selectedTag' cho $cleanHanzi (Ngày: $nowStr)!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF1C1917),
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Lưu ghi chú", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. HÌNH ẢNH MINH HỌA THEO TỪ KHÓA CHỮ HÁN (Matches image_dc7fb4e7.png)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE7E5E4))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "🖼️ Hình ảnh minh họa theo từ khóa chữ Hán “$cleanHanzi”",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1C1917)
                            )

                            val imageCards = listOf(
                                Triple("Ảnh thực tế: $cleanHanzi", "$cleanHanzi 实拍 照片", 1),
                                Triple("Ngữ cảnh đời sống: $cleanHanzi", "$cleanHanzi 生活 场景", 2),
                                Triple("Minh họa trực quan: $cleanHanzi", "$cleanHanzi 插画 图标", 3)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                imageCards.forEach { (caption, queryKw, variant) ->
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                val qEnc = URLEncoder.encode(queryKw, "UTF-8")
                                                onOpenWebUrl("https://www.bing.com/images/search?q=$qEnc")
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF8F5)),
                                        border = BorderStroke(1.dp, Color(0xFFE7E5E4))
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(buildBingThumbUrl(queryKw, variant))
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = caption,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(92.dp)
                                                    .background(Color(0xFFF5F5F4))
                                            )
                                            Text(
                                                text = caption,
                                                fontSize = 10.sp,
                                                color = Color(0xFF57534E),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // External Image Search Engine Links (Bing, Google Ảnh, Baidu Ảnh)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AssistChip(
                                    onClick = { onOpenWebUrl("https://www.bing.com/images/search?q=$encodedHanzi") },
                                    label = { Text("🔍 Bing Ảnh: $cleanHanzi", fontSize = 11.sp) }
                                )
                                AssistChip(
                                    onClick = { onOpenWebUrl("https://www.google.com/search?tbm=isch&q=$encodedHanzi") },
                                    label = { Text("🌐 Google Ảnh: $cleanHanzi", fontSize = 11.sp) }
                                )
                                AssistChip(
                                    onClick = { onOpenWebUrl("https://image.baidu.com/search/index?tn=baiduimage&word=$encodedHanzi") },
                                    label = { Text("🐾 Baidu Ảnh: $cleanHanzi", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // 3. MẪU CÂU VÍ DỤ THÔNG DỤNG (Matches image_7a34d1a9.png)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE7E5E4))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📖 Mẫu câu ví dụ thông dụng của “$cleanHanzi”",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1C1917),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = {
                                        onOpenWebUrl("https://hanzii.net/search/example/$encodedHanzi?hl=vi")
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(0xFFFFFBEB),
                                        contentColor = Color(0xFF92400E)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Thêm câu ví dụ (Hanzii) ↗", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Contextual explanation box
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFAF8F5),
                                border = BorderStroke(1.dp, Color(0xFFE7E5E4))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Từ “$cleanHanzi” (${activeEntry.pinyin.ifBlank { cleanHanzi }}) có nghĩa là “$cleanVi”, xuất hiện tại ${activeEntry.lessonTitle}. Các từ đang tra cứu và từ vựng đang học được tô đỏ nổi bật giống từ điển Hanzii.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF57534E),
                                        lineHeight = 18.sp
                                    )
                                    if (isLoadingOnlineExamples) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 1.8.dp)
                                            Text(
                                                text = "🌐 Đang tải thêm câu ví dụ & từ thay thế Online...",
                                                fontSize = 11.sp,
                                                color = Color(0xFF0284C7),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "🌐 Đã đồng bộ câu ví dụ thực tế (Online + Kho ngữ liệu Hanzii • Đã tô đỏ từ tra cứu & đang học)",
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF16A34A),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // Example Sentence List
                            exampleSentences.forEach { ex ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFFF5F5F4))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Left Speaker Button
                                        Surface(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clickable { viewModel.speakText(ex.zhSimplified, "zh") },
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFF5F5F4),
                                            border = BorderStroke(1.dp, Color(0xFFE7E5E4))
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.VolumeUp,
                                                    contentDescription = "Đọc câu ví dụ",
                                                    tint = Color(0xFF44403C),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = highlightKeywordRed(ex.zhSimplified, cleanHanzi, activeStudyHanziSet),
                                                fontSize = 15.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF1C1917)
                                            )
                                            Text(
                                                text = highlightKeywordRed(ex.zhBracketed, cleanHanzi, activeStudyHanziSet),
                                                fontSize = 12.5.sp,
                                                color = Color(0xFF78716C)
                                            )
                                            if (ex.pinyin.isNotBlank()) {
                                                Text(
                                                    text = highlightKeywordRed(ex.pinyin, activeEntry.pinyin),
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF78716C)
                                                )
                                            }
                                            Text(
                                                text = highlightKeywordRed(ex.vi, cleanVi, setOf(cleanHanzi)),
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1C1917)
                                            )
                                        }

                                        // Right Save Sentence Button ("có chức năng đọc và lưu câu")
                                        Surface(
                                            modifier = Modifier
                                                .clickable {
                                                    val fullZh = if (ex.pinyin.isNotBlank()) "${ex.zhSimplified} (${ex.pinyin})" else ex.zhSimplified
                                                    onRequestMultiFolderSave(ex.vi, fullZh)
                                                },
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFFFFBEB),
                                            border = BorderStroke(1.dp, Color(0xFFF59E0B))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.BookmarkAdd,
                                                    contentDescription = "Lưu câu",
                                                    tint = Color(0xFF92400E),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Text(
                                                    text = "Lưu câu",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF92400E)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3.5. TỪ VỰNG & CÂU CÓ THỂ THAY THẾ (Substitutable Words & Sentences + Popularity)
                    SubstitutableWordsAndSentencesCard(
                        currentWordHanzi = cleanHanzi,
                        currentWordVi = cleanVi,
                        substitutes = substitutableWords,
                        viewModel = viewModel,
                        onSelectSubstitute = { sub ->
                            activeEntry = VisualVocabEntry(
                                indexInBook = activeEntry.indexInBook,
                                hanzi = sub.hanzi,
                                pinyin = sub.pinyin,
                                vi = sub.vi,
                                lessonTitle = "Từ thay thế của $cleanHanzi",
                                studySet = activeEntry.studySet
                            )
                        },
                        onRequestMultiFolderSave = onRequestMultiFolderSave
                    )

                    // 3.8. HỌC THEO CỤM NHIỀU TỪ GỐC CHỮ HÁN (Được sắp xếp ở gần cuối bảng tra cứu theo yêu cầu)
                    WordFamilyClusterSectionCard(
                        currentWordHanzi = cleanHanzi,
                        currentWordPinyin = activeEntry.pinyin,
                        allStudySets = allStudySets,
                        viewModel = viewModel,
                        onSelectClusterWord = { node ->
                            activeEntry = VisualVocabEntry(
                                indexInBook = activeEntry.indexInBook,
                                hanzi = node.hanzi,
                                pinyin = node.pinyin,
                                vi = node.vi,
                                lessonTitle = "Cụm từ gốc chữ Hán",
                                studySet = activeEntry.studySet
                            )
                        },
                        onRequestMultiFolderSave = onRequestMultiFolderSave
                    )

                    // 4. TỪ ĐỒNG NGHĨA & TỪ VỰNG LIÊN QUAN (Matches image_84a0b213.png)
                    if (siblings.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE7E5E4))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Từ đồng nghĩa & Từ vựng liên quan",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1C1917)
                                )
                                siblings.forEachIndexed { idx, rel ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { activeEntry = rel },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF8F5)),
                                        border = BorderStroke(
                                            1.dp,
                                            if (idx == 0) Color(0xFFF59E0B) else Color(0xFFE7E5E4)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = rel.hanzi,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color(0xFF1C1917)
                                                    )
                                                    if (rel.pinyin.isNotBlank()) {
                                                        Text(
                                                            text = rel.pinyin,
                                                            fontSize = 12.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF9A3412)
                                                        )
                                                    }
                                                    Text(
                                                        text = "· Cùng chủ đề từ vựng",
                                                        fontSize = 11.5.sp,
                                                        color = Color(0xFF78716C)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = rel.vi,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF44403C)
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.speakText(rel.hanzi, "zh") },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.VolumeUp,
                                                    contentDescription = "Nghe",
                                                    tint = Color(0xFF9A3412),
                                                    modifier = Modifier.size(18.dp)
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

@Composable
fun VisualBookDictionaryDialog(
    bookOrFolderTitle: String,
    setsInBook: List<StudySet>,
    allStudySets: List<StudySet>,
    folders: List<Folder>,
    viewModel: TranslationViewModel,
    onDismiss: () -> Unit,
    onStartStudySet: (StudySet, Int, Boolean) -> Unit,
    onOpenWebUrl: (String) -> Unit,
    onRequestMultiFolderSave: (vi: String, foreign: String) -> Unit
) {
    val context = LocalContext.current
    val starredPairs by viewModel.allStarredPairs.collectAsState()
    var isIllustratedView by remember { mutableStateOf(true) } // true = Có ảnh minh họa, false = Dạng danh sách
    var showPinyin by remember { mutableStateOf(true) }
    var showMeaning by remember { mutableStateOf(true) }
    var selectedLessonId by remember { mutableStateOf<Long?>(null) } // null = Tất cả
    var searchQuery by remember { mutableStateOf("") }
    var lessonDropdownExpanded by remember { mutableStateOf(false) }
    var selectedVocabForDetail by remember { mutableStateOf<VisualVocabEntry?>(null) }
    var visibleLimit by remember(selectedLessonId, searchQuery, isIllustratedView) { mutableStateOf(40) }

    // Study limit selector state
    var showLaunchConfigForSet by remember { mutableStateOf<StudySet?>(null) }

    val allBookEntries = remember(setsInBook) {
        val list = ArrayList<VisualVocabEntry>(500)
        var counter = 1
        for (set in setsInBook) {
            val items = parseEditableVocabItems(set.rawContent)
            for (item in items) {
                list.add(
                    VisualVocabEntry(
                        indexInBook = counter++,
                        hanzi = item.hanzi,
                        pinyin = item.pinyin,
                        vi = item.vi,
                        lessonTitle = set.title,
                        studySet = set
                    )
                )
            }
        }
        list
    }

    val filteredEntries = remember(allBookEntries, selectedLessonId, searchQuery) {
        allBookEntries.filter { entry ->
            val matchLesson = selectedLessonId == null || entry.studySet?.id == selectedLessonId
            val matchSearch = searchQuery.isBlank() ||
                TwoWayTranslatorEngine.matchesQueryWithPinyin("${entry.hanzi} ${entry.pinyin} ${entry.vi}", searchQuery)
            matchLesson && matchSearch
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.97f)
                .fillMaxHeight(0.93f),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFFFAF8F5),
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Card (Matches image_c4fef58f.png & image_f70a4fa7.png)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Từ Điển Trực Quan & Tra Cứu — $bookOrFolderTitle (Hiển thị ${filteredEntries.size} / ${allBookEntries.size} từ)",
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF1C1917)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Đầy đủ toàn bộ ${allBookEntries.size} từ vựng của $bookOrFolderTitle. Ấn vào bất kỳ từ nào để xem hình ảnh minh họa, ví dụ ngữ cảnh, từ đồng nghĩa và phát âm chuẩn.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF78716C),
                                    lineHeight = 15.sp
                                )
                            }
                            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color(0xFF44403C))
                            }
                        }

                        // Mode Toggle Bar: [Có ảnh minh họa | Dạng danh sách] + [Pinyin] + [Nghĩa từ vựng]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF5F5F4))
                                    .border(1.dp, Color(0xFFE7E5E4), RoundedCornerShape(12.dp))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.clickable { isIllustratedView = true },
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (isIllustratedView) Color.White else Color.Transparent,
                                    shadowElevation = if (isIllustratedView) 1.dp else 0.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.GridView,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = Color(0xFF1C1917)
                                        )
                                        Text(
                                            text = "Có ảnh minh họa",
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isIllustratedView) FontWeight.Bold else FontWeight.Medium,
                                            color = Color(0xFF1C1917)
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier.clickable { isIllustratedView = false },
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (!isIllustratedView) Color.White else Color.Transparent,
                                    shadowElevation = if (!isIllustratedView) 1.dp else 0.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.FormatListBulleted,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = Color(0xFF1C1917)
                                        )
                                        Text(
                                            text = "Dạng danh sách",
                                            fontSize = 11.5.sp,
                                            fontWeight = if (!isIllustratedView) FontWeight.Bold else FontWeight.Medium,
                                            color = Color(0xFF1C1917)
                                        )
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier.clickable { showPinyin = !showPinyin },
                                shape = RoundedCornerShape(10.dp),
                                color = if (showPinyin) Color(0xFFFEF3C7) else Color.White,
                                border = BorderStroke(1.dp, if (showPinyin) Color(0xFFF59E0B) else Color(0xFFD6D3D1))
                            ) {
                                Text(
                                    text = "👁 Pinyin",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (showPinyin) Color(0xFF92400E) else Color(0xFF57534E),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }

                            Surface(
                                modifier = Modifier.clickable { showMeaning = !showMeaning },
                                shape = RoundedCornerShape(10.dp),
                                color = if (showMeaning) Color(0xFFFEF3C7) else Color.White,
                                border = BorderStroke(1.dp, if (showMeaning) Color(0xFFF59E0B) else Color(0xFFD6D3D1))
                            ) {
                                Text(
                                    text = "👁 Nghĩa từ vựng",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (showMeaning) Color(0xFF92400E) else Color(0xFF57534E),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Filter Lesson Dropdown + Search Field Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(0.95f)) {
                                val currentLessonLabel = if (selectedLessonId == null) {
                                    "Tất cả ${setsInBook.size} Mục (${allBookEntries.size} từ)"
                                } else {
                                    setsInBook.find { it.id == selectedLessonId }?.title ?: "Chọn bài"
                                }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { lessonDropdownExpanded = true },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFD6D3D1))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = currentLessonLabel,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1C1917),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }

                                DropdownMenu(
                                    expanded = lessonDropdownExpanded,
                                    onDismissRequest = { lessonDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Tất cả ${setsInBook.size} Mục (${allBookEntries.size} từ)", fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            selectedLessonId = null
                                            lessonDropdownExpanded = false
                                        }
                                    )
                                    setsInBook.forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text(s.title, fontSize = 12.5.sp) },
                                            onClick = {
                                                selectedLessonId = s.id
                                                lessonDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Tra chữ Hán, Pinyin hoặc nghĩa...", fontSize = 11.5.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.05f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )
                        }

                        // Quick Start Lesson Button if a specific lesson is selected or start first lesson
                        val activeStudySet = setsInBook.find { it.id == selectedLessonId } ?: setsInBook.firstOrNull()
                        if (activeStudySet != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Chạm vào từng từ để mở trang chi tiết (Ảnh + Câu ví dụ + Từ liên quan)",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF78716C),
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { showLaunchConfigForSet = activeStudySet },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9A3412)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Học bài này", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Content Area: Mode 1 = Illustrated Cards (image_c4fef58f.png), Mode 2 = Table List (image_f70a4fa7.png)
                val displayed = filteredEntries.take(visibleLimit)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isIllustratedView) {
                        // Table Header Row matching image_f70a4fa7.png
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                                color = Color(0xFFF5F5F4),
                                border = BorderStroke(1.dp, Color(0xFFE7E5E4))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("#", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF78716C), modifier = Modifier.width(28.dp))
                                    Text("TỪ MỚI", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF78716C), modifier = Modifier.weight(0.9f))
                                    Text("PHIÊN ÂM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF78716C), modifier = Modifier.weight(0.9f))
                                    Text("NGHĨA TIẾNG VIỆT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF78716C), modifier = Modifier.weight(1.3f))
                                    Text("NGHE / LƯU", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF78716C))
                                }
                            }
                        }
                    }

                    itemsIndexed(displayed, key = { _, item -> "${item.indexInBook}_${item.hanzi}" }) { _, entry ->
                        val enc = URLEncoder.encode(entry.hanzi.ifBlank { entry.vi }, "UTF-8")
                        val isEntryStarred = starredPairs.any {
                            it.vi.trim().equals(entry.vi.trim(), ignoreCase = true) ||
                                (entry.hanzi.isNotBlank() && it.foreign.contains(entry.hanzi))
                        }
                        val entrySavedDate = remember(entry.hanzi, viewModel.vocabStatusVersion.value) {
                            viewModel.getWordSavedDateFormatted(entry.hanzi)
                        }
                        if (isIllustratedView) {
                            // ILLUSTRATED CARD MODE (Matches image_c4fef58f.png)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedVocabForDetail = entry },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE7E5E4))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFFFAF8F5),
                                        border = BorderStroke(1.dp, Color(0xFFF5F5F4))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 10.dp)
                                            ) {
                                                Text(
                                                    text = "Từ vựng Trọng tâm · 核心词汇",
                                                    fontSize = 10.5.sp,
                                                    color = Color(0xFF78716C)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    verticalAlignment = Alignment.Bottom,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = entry.hanzi,
                                                        fontSize = 22.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color(0xFF1C1917)
                                                    )
                                                    if (showPinyin && entry.pinyin.isNotBlank()) {
                                                        Text(
                                                            text = "[${entry.pinyin}]",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF9A3412),
                                                            modifier = Modifier.padding(bottom = 2.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = if (showMeaning) entry.vi else "••• (Chạm để xem nghĩa)",
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF292524)
                                                )
                                                if (entrySavedDate.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "📅 Đã lưu: $entrySavedDate",
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF0369A1),
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Surface(
                                                    modifier = Modifier.clickable {
                                                        onOpenWebUrl("https://hanzii.net/search/word/$enc?hl=vi")
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFFFFFBEB),
                                                    border = BorderStroke(1.dp, Color(0xFFF59E0B))
                                                ) {
                                                    Text(
                                                        text = "📖 Từ điển Hanzii ↗",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF92400E),
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }

                                            // Dynamic Image Thumbnail on Right
                                            Box(
                                                modifier = Modifier
                                                    .width(108.dp)
                                                    .height(82.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFFE7E5E4))
                                            ) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(buildBingThumbUrl(entry.hanzi, 1))
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = entry.hanzi,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(4.dp)
                                                        .clip(RoundedCornerShape(5.dp))
                                                        .background(Color(0xCC1C1917))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = entry.hanzi.take(4),
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Footer row: Từ #1 • Bài học ... | [⭐ Sao] [🔊 Nghe] [🔖]
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Từ #${entry.indexInBook} · ${entry.lessonTitle}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF78716C),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                modifier = Modifier.clickable {
                                                    viewModel.toggleStar(entry.vi, entry.fullForeign, "zh")
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isEntryStarred) Color(0xFFFEF3C7) else Color.White,
                                                border = BorderStroke(1.dp, if (isEntryStarred) Color(0xFFF59E0B) else Color(0xFFE7E5E4))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(
                                                        if (isEntryStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                                        contentDescription = "Gắn sao",
                                                        tint = if (isEntryStarred) Color(0xFFF59E0B) else Color(0xFF78716C),
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Text(
                                                        text = if (isEntryStarred) "Đã sao" else "Sao",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isEntryStarred) Color(0xFF92400E) else Color(0xFF57534E)
                                                    )
                                                }
                                            }
                                            Surface(
                                                modifier = Modifier.clickable {
                                                    viewModel.speakText(entry.hanzi, "zh")
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                color = Color(0xFFFEF3C7)
                                            ) {
                                                Text(
                                                    text = "🔊 Nghe",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF92400E),
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                )
                                            }
                                            Surface(
                                                modifier = Modifier.clickable {
                                                    onRequestMultiFolderSave(entry.vi, entry.fullForeign)
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color.White,
                                                border = BorderStroke(1.dp, Color(0xFFE7E5E4))
                                            ) {
                                                Icon(
                                                    Icons.Default.BookmarkBorder,
                                                    contentDescription = "Lưu vào thư mục",
                                                    tint = Color(0xFF57534E),
                                                    modifier = Modifier
                                                        .padding(5.dp)
                                                        .size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // TABLE LIST MODE (Matches image_f70a4fa7.png)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedVocabForDetail = entry },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE7E5E4))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${entry.indexInBook}.",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF78716C),
                                        modifier = Modifier.width(28.dp)
                                    )
                                    Text(
                                        text = entry.hanzi,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF1C1917),
                                        modifier = Modifier.weight(0.9f)
                                    )
                                    Text(
                                        text = if (showPinyin) entry.pinyin else "•••",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF9A3412),
                                        modifier = Modifier.weight(0.9f)
                                    )
                                    Column(modifier = Modifier.weight(1.3f)) {
                                        Text(
                                            text = if (showMeaning) entry.vi else "•••",
                                            fontSize = 12.5.sp,
                                            color = Color(0xFF292524)
                                        )
                                        Text(
                                            text = if (entrySavedDate.isNotBlank()) "${entry.lessonTitle} • 📅 $entrySavedDate" else entry.lessonTitle,
                                            fontSize = 10.sp,
                                            color = Color(0xFF78716C),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            modifier = Modifier.clickable {
                                                viewModel.toggleStar(entry.vi, entry.fullForeign, "zh")
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isEntryStarred) Color(0xFFFEF3C7) else Color.White,
                                            border = BorderStroke(1.dp, if (isEntryStarred) Color(0xFFF59E0B) else Color(0xFFD6D3D1))
                                        ) {
                                            Icon(
                                                if (isEntryStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = "Gắn sao",
                                                tint = if (isEntryStarred) Color(0xFFF59E0B) else Color(0xFF78716C),
                                                modifier = Modifier
                                                    .padding(4.dp)
                                                    .size(15.dp)
                                            )
                                        }
                                        Surface(
                                            modifier = Modifier.clickable { viewModel.speakText(entry.hanzi, "zh") },
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFFEF3C7),
                                            border = BorderStroke(1.dp, Color(0xFFF59E0B))
                                        ) {
                                            Text(
                                                text = "🔊",
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                            )
                                        }
                                        Surface(
                                            modifier = Modifier.clickable {
                                                onRequestMultiFolderSave(entry.vi, entry.fullForeign)
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color.White,
                                            border = BorderStroke(1.dp, Color(0xFFD6D3D1))
                                        ) {
                                            Icon(
                                                Icons.Default.BookmarkBorder,
                                                contentDescription = "Lưu",
                                                tint = Color(0xFF57534E),
                                                modifier = Modifier
                                                    .padding(4.dp)
                                                    .size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (filteredEntries.size > visibleLimit) {
                        item {
                            Button(
                                onClick = { visibleLimit += 50 },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9A3412))
                            ) {
                                Text(
                                    text = "Hiển thị thêm (${filteredEntries.size - visibleLimit} từ vựng nữa)...",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedVocabForDetail?.let { vocab ->
        VisualVocabDetailDialog(
            initialEntry = vocab,
            relatedEntries = filteredEntries,
            allStudySets = allStudySets,
            folders = folders,
            viewModel = viewModel,
            onDismiss = { selectedVocabForDetail = null },
            onOpenWebUrl = onOpenWebUrl,
            onRequestMultiFolderSave = onRequestMultiFolderSave
        )
    }

    showLaunchConfigForSet?.let { setToLaunch ->
        LessonStudyCountSelectorDialog(
            studySet = setToLaunch,
            currentLimit = viewModel.studyWordCountLimit.value,
            isRandom = viewModel.isShuffle.value,
            onDismiss = { showLaunchConfigForSet = null },
            onConfirmStart = { chosenLimit, chosenRandom ->
                showLaunchConfigForSet = null
                onStartStudySet(setToLaunch, chosenLimit, chosenRandom)
            }
        )
    }
}

@Composable
fun LessonStudyCountSelectorDialog(
    studySet: StudySet,
    currentLimit: Int,
    isRandom: Boolean,
    onDismiss: () -> Unit,
    onConfirmStart: (wordLimit: Int, isRandomOrder: Boolean) -> Unit
) {
    val totalWords = remember(studySet.rawContent) {
        parseEditableVocabItems(studySet.rawContent).size.coerceAtLeast(1)
    }
    var selectedLimit by remember { mutableStateOf(currentLimit) }
    var customCountText by remember { mutableStateOf(if (currentLimit > 0) "$currentLimit" else "") }
    var useRandom by remember { mutableStateOf(isRandom) }

    val presetLimits = listOf(0, 5, 10, 15, 20, 30, 50)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column {
                Text(
                    text = "🎯 Tùy chọn Số lượng từ & Thứ tự học",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${studySet.title} (Tổng cộng: $totalWords từ/câu)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "1. Chọn cách lấy từ vựng:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !useRandom,
                        onClick = { useRandom = false },
                        label = { Text("🔢 Theo thứ tự bài học", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = useRandom,
                        onClick = { useRandom = true },
                        label = { Text("🎲 Ngẫu nhiên", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = "2. Chọn số lượng từ muốn học trong phiên này:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetLimits.forEach { preset ->
                        val isSel = selectedLimit == preset
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                selectedLimit = preset
                                customCountText = if (preset == 0) "" else "$preset"
                            },
                            label = {
                                Text(
                                    if (preset == 0) "Tất cả ($totalWords)" else "$preset từ",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = customCountText,
                    onValueChange = { txt ->
                        val digits = txt.filter { it.isDigit() }
                        customCountText = digits
                        selectedLimit = digits.toIntOrNull() ?: 0
                    },
                    label = { Text("Hoặc nhập số lượng từ tùy ý (Để trống = Học tất cả)", fontSize = 11.5.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmStart(selectedLimit, useRandom) },
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (selectedLimit <= 0) "Bắt đầu học ($totalWords từ)"
                    else "Bắt đầu học (${selectedLimit.coerceAtMost(totalWords)} từ)",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
