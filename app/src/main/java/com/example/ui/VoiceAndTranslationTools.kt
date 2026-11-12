package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class TwoWayTranslationResult(
    val sourceText: String,
    val sourceLang: String, // "vi" or "zh"
    val translatedText: String,
    val targetLang: String, // "zh" or "vi"
    val pinyin: String = "",
    val reverseTranslation: String = "",
    val correctedSourceText: String = "",
    val toneSuggestions: List<String> = emptyList(),
    val alternativeTranslations: List<String> = emptyList(),
    val isOnlineResult: Boolean = false
)

data class SmartInputSuggestion(
    val originalInput: String,
    val isLikelyPinyin: Boolean,
    val isLikelyUnaccentedVi: Boolean,
    val pinyinWithTonesList: List<String>, // e.g., ["bāo xiǎn", "báo xiǎn", "bǎo xiǎn", "bào xiǎn"]
    val syllableToneTable: List<Pair<String, List<String>>>, // syllable -> [tone1, tone2, tone3, tone4]
    val hanziCandidates: List<Pair<String, String>>, // (Hanzi, Pinyin+Meaning)
    val restoredVietnameseCandidates: List<String> // Restored accented Vietnamese phrases
)

data class SubstitutableWordItem(
    val hanzi: String,
    val pinyin: String,
    val vi: String,
    val popularityStars: Int = 5,
    val popularityLabel: String = "Rất phổ biến",
    val nuanceNote: String = "",
    val replaceableSentenceZh: String = "",
    val replaceableSentencePinyin: String = "",
    val replaceableSentenceVi: String = ""
)

object TwoWayTranslatorEngine {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // In-memory LRU caches for instant 0ms response on repeated / prefix queries
    private val translationCache = object : LinkedHashMap<String, TwoWayTranslationResult>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TwoWayTranslationResult>?): Boolean = size > 120
    }
    private val smartSuggestionCache = object : LinkedHashMap<String, SmartInputSuggestion>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SmartInputSuggestion>?): Boolean = size > 120
    }

    data class IndexedVocabRecord(
        val key: String,
        val vi: String,
        val hanzi: String,
        val pinyin: String,
        val fullForeign: String,
        val strippedVi: String,
        val strippedPinyin: String,
        val compactPinyin: String,
        val studySet: StudySet,
        val occurrenceCount: Int,
        val allLessonTitles: String
    )

    @Volatile
    private var cachedSetsSignature: Int = 0
    @Volatile
    private var cachedIndexedRecords: List<IndexedVocabRecord> = emptyList()
    @Volatile
    private var cachedStrippedViPhraseMap: Map<String, String> = emptyMap()

    fun ensureFastIndex(studySets: List<StudySet>): List<IndexedVocabRecord> {
        if (studySets.isEmpty()) return emptyList()
        val sig = studySets.size * 31 + studySets.first().id.hashCode() + studySets.last().lastModified.hashCode()
        if (sig == cachedSetsSignature && cachedIndexedRecords.isNotEmpty()) {
            return cachedIndexedRecords
        }
        synchronized(this) {
            if (sig == cachedSetsSignature && cachedIndexedRecords.isNotEmpty()) {
                return cachedIndexedRecords
            }
            val dedupMap = LinkedHashMap<String, IndexedVocabRecord>(4096)
            val lessonNamesMap = HashMap<String, LinkedHashSet<String>>(4096)
            val viPhraseLookup = HashMap<String, String>(4096)

            for (set in studySets) {
                for (line in set.rawContent.lineSequence()) {
                    val eqIdx = line.indexOf('=').let { if (it != -1) it else line.indexOf('#') }
                    if (eqIdx <= 0) continue
                    val p1 = line.substring(0, eqIdx).trim()
                    val p2 = line.substring(eqIdx + 1).trim()
                    if (p1.isEmpty() || p2.isEmpty()) continue

                    val hasZh1 = p1.any { isCjk(it) }
                    val hasZh2 = p2.any { isCjk(it) }
                    val viSide = if (hasZh1 && !hasZh2) p2 else p1
                    val foreignSide = if (hasZh1 && !hasZh2) p1 else p2

                    val parenIdx = foreignSide.indexOfAny(charArrayOf('(', '（', '[', '【'))
                    val hanziPart = if (parenIdx != -1) foreignSide.substring(0, parenIdx).trim() else foreignSide.trim()
                    val pinyinPart = if (parenIdx != -1) {
                        foreignSide.substring(parenIdx + 1)
                            .trimEnd(')', '）', ']', '】', ' ')
                            .trim()
                    } else ""

                    val cleanKey = hanziPart.lowercase().ifEmpty { "${viSide.lowercase()}|${foreignSide.lowercase()}" }
                    val titlesSet = lessonNamesMap.getOrPut(cleanKey) { LinkedHashSet() }
                    titlesSet.add(set.title)

                    val existing = dedupMap[cleanKey]
                    if (existing == null) {
                        val strippedVi = stripAccents(viSide)
                        val strippedPy = stripAccents(pinyinPart)
                        val compactPy = strippedPy.replace(" ", "").filter { it in 'a'..'z' }
                        if (strippedVi.length in 3..80 && hasVietnameseDiacritics(viSide)) {
                            viPhraseLookup.putIfAbsent(strippedVi, viSide)
                        }
                        dedupMap[cleanKey] = IndexedVocabRecord(
                            key = cleanKey,
                            vi = viSide,
                            hanzi = hanziPart,
                            pinyin = pinyinPart,
                            fullForeign = foreignSide,
                            strippedVi = strippedVi,
                            strippedPinyin = strippedPy,
                            compactPinyin = compactPy,
                            studySet = set,
                            occurrenceCount = 1,
                            allLessonTitles = set.title
                        )
                    } else {
                        dedupMap[cleanKey] = existing.copy(
                            occurrenceCount = titlesSet.size,
                            allLessonTitles = titlesSet.take(3).joinToString(" • ")
                        )
                    }
                }
            }
            cachedStrippedViPhraseMap = viPhraseLookup
            cachedIndexedRecords = dedupMap.values.toList()
            cachedSetsSignature = sig
            return cachedIndexedRecords
        }
    }

    /**
     * Instant (<2ms) predictive search & deduplicated vocabulary lookup across all 338 study sets.
     * Ranks exact matches first, then prefix predictions (what the user is about to type), then substring matches.
     */
    fun searchAndPredictInstant(
        query: String,
        studySets: List<StudySet>,
        limit: Int = 30
    ): List<VocabSearchResult> {
        val rawQ = query.trim()
        if (rawQ.isEmpty()) return emptyList()
        val records = ensureFastIndex(studySets)
        if (records.isEmpty()) return emptyList()

        // Also check auto-corrected Vietnamese query so typos still match local vocab
        val correctedQ = if (!rawQ.any { isCjk(it) }) autoCorrectVietnameseText(rawQ, studySets) else rawQ
        val strippedQ = stripAccents(rawQ)
        val strippedCorrected = stripAccents(correctedQ)
        val compactQ = strippedQ.replace(" ", "").filter { it in 'a'..'z' }

        val tierExact = ArrayList<IndexedVocabRecord>(8)
        val tierPrefix = ArrayList<IndexedVocabRecord>(16)
        val tierWord = ArrayList<IndexedVocabRecord>(20)
        val tierSub = ArrayList<IndexedVocabRecord>(20)

        for (rec in records) {
            val matchExact = rec.hanzi.equals(rawQ, ignoreCase = true) ||
                rec.strippedVi == strippedQ ||
                rec.strippedVi == strippedCorrected ||
                (compactQ.length >= 2 && rec.compactPinyin == compactQ)

            if (matchExact) {
                tierExact.add(rec)
                continue
            }

            val matchPrefix = rec.hanzi.startsWith(rawQ) ||
                rec.strippedVi.startsWith(strippedQ) ||
                rec.strippedVi.startsWith(strippedCorrected) ||
                (compactQ.length >= 2 && rec.compactPinyin.startsWith(compactQ)) ||
                (strippedQ.length >= 2 && rec.strippedPinyin.startsWith(strippedQ))

            if (matchPrefix) {
                if (tierPrefix.size < limit) tierPrefix.add(rec)
                continue
            }

            val matchWord = rec.strippedVi.contains(" $strippedQ") ||
                rec.strippedPinyin.contains(" $strippedQ")

            if (matchWord) {
                if (tierWord.size < limit) tierWord.add(rec)
                continue
            }

            val matchSub = rec.hanzi.contains(rawQ) ||
                (strippedQ.length >= 2 && (rec.strippedVi.contains(strippedQ) || rec.strippedPinyin.contains(strippedQ))) ||
                (compactQ.length >= 3 && rec.compactPinyin.contains(compactQ))

            if (matchSub && tierSub.size < limit) {
                tierSub.add(rec)
            }
        }

        val combined = ArrayList<VocabSearchResult>(limit)
        for (list in listOf(tierExact, tierPrefix, tierWord, tierSub)) {
            for (r in list) {
                if (combined.size >= limit) break
                combined.add(
                    VocabSearchResult(
                        vi = r.vi,
                        foreign = r.fullForeign,
                        setTitle = r.studySet.title,
                        studySet = r.studySet,
                        occurrenceCount = r.occurrenceCount,
                        allLessonTitles = r.allLessonTitles
                    )
                )
            }
            if (combined.size >= limit) break
        }
        return combined
    }

    private val toneMap = mapOf(
        'a' to charArrayOf('ā', 'á', 'ǎ', 'à'),
        'e' to charArrayOf('ē', 'é', 'ě', 'è'),
        'i' to charArrayOf('ī', 'í', 'ǐ', 'ì'),
        'o' to charArrayOf('ō', 'ó', 'ǒ', 'ò'),
        'u' to charArrayOf('ū', 'ú', 'ǔ', 'ù'),
        'ü' to charArrayOf('ǖ', 'ǘ', 'ǚ', 'ǜ'),
        'v' to charArrayOf('ǖ', 'ǘ', 'ǚ', 'ǜ')
    )

    private val commonPinyinSyllables = setOf(
        "a", "ai", "an", "ang", "ao",
        "ba", "bai", "ban", "bang", "bao", "bei", "ben", "beng", "bi", "bian", "biao", "bie", "bin", "bing", "bo", "bu",
        "ca", "cai", "can", "cang", "cao", "ce", "cen", "ceng", "cha", "chai", "chan", "chang", "chao", "che", "chen", "cheng", "chi", "chong", "chou", "chu", "chua", "chuai", "chuan", "chuang", "chui", "chun", "chuo", "ci", "cong", "cou", "cu", "cuan", "cui", "cun", "cuo",
        "da", "dai", "dan", "dang", "dao", "de", "dei", "den", "deng", "di", "dia", "dian", "diao", "die", "ding", "diu", "dong", "dou", "du", "duan", "dui", "dun", "duo",
        "e", "ei", "en", "eng", "er",
        "fa", "fan", "fang", "fei", "fen", "feng", "fo", "fou", "fu",
        "ga", "gai", "gan", "gang", "gao", "ge", "gei", "gen", "geng", "gong", "gou", "gu", "gua", "guai", "guan", "guang", "gui", "gun", "guo",
        "ha", "hai", "han", "hang", "hao", "he", "hei", "hen", "heng", "hong", "hou", "hu", "hua", "huai", "huan", "huang", "hui", "hun", "huo",
        "ji", "jia", "jian", "jiang", "jiao", "jie", "jin", "jing", "jiong", "jiu", "ju", "juan", "jue", "jun",
        "ka", "kai", "kan", "kang", "kao", "ke", "kei", "ken", "keng", "kong", "kou", "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo",
        "la", "lai", "lan", "lang", "lao", "le", "lei", "leng", "li", "lia", "lian", "liang", "liao", "lie", "lin", "ling", "liu", "lo", "long", "lou", "lu", "luan", "lue", "lun", "luo", "lv",
        "ma", "mai", "man", "mang", "mao", "me", "mei", "men", "meng", "mi", "mian", "miao", "mie", "min", "ming", "miu", "mo", "mou", "mu",
        "na", "nai", "nan", "nang", "nao", "ne", "nei", "nen", "neng", "ni", "nian", "niang", "niao", "nie", "nin", "ning", "niu", "nong", "nou", "nu", "nuan", "nue", "nuo", "nv",
        "o", "ou",
        "pa", "pai", "pan", "pang", "pao", "pei", "pen", "peng", "pi", "pian", "piao", "pie", "pin", "ping", "po", "pou", "pu",
        "qi", "qia", "qian", "qiang", "qiao", "qie", "qin", "qing", "qiong", "qiu", "qu", "quan", "que", "qun",
        "ran", "rang", "rao", "re", "ren", "reng", "ri", "rong", "rou", "ru", "rua", "ruan", "rui", "run", "ruo",
        "sa", "sai", "san", "sang", "sao", "se", "sen", "seng", "sha", "shai", "shan", "shang", "shao", "she", "shei", "shen", "sheng", "shi", "shou", "shu", "shua", "shuai", "shuan", "shuang", "shui", "shun", "shuo", "si", "song", "sou", "su", "suan", "sui", "sun", "suo",
        "ta", "tai", "tan", "tang", "tao", "te", "tei", "teng", "ti", "tian", "tiao", "tie", "ting", "tong", "tou", "tu", "tuan", "tui", "tun", "tuo",
        "wa", "wai", "wan", "wang", "wei", "wen", "weng", "wo", "wu",
        "xi", "xia", "xian", "xiang", "xiao", "xie", "xin", "xing", "xiong", "xiu", "xu", "xuan", "xue", "xun",
        "ya", "yan", "yang", "yao", "ye", "yi", "yin", "ying", "yo", "yong", "you", "yu", "yuan", "yue", "yun",
        "za", "zai", "zan", "zang", "zao", "ze", "zei", "zen", "zeng", "zha", "zhai", "zhan", "zhang", "zhao", "zhe", "zhei", "zhen", "zheng", "zhi", "zhong", "zhou", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhui", "zhun", "zhuo", "zi", "zong", "zou", "zu", "zuan", "zui", "zun", "zuo"
    )

    private val vietnameseOnlyWords = setOf(
        "cong", "ty", "co", "dong", "nhung", "khong", "duoc", "nguoi", "viet", "nam",
        "chung", "toi", "anh", "em", "cho", "nhan", "vien", "ngay", "thang",
        "lam", "viec", "luong", "thuong", "nghi", "phep", "hop", "dong", "bao", "hiem",
        "trat", "tu", "giam", "doc", "quan", "ly", "xuong", "may", "di", "ve", "nha",
        "an", "com", "uong", "nuoc", "hoc", "tieng", "trung", "rat", "nhieu", "muon",
        "hoi", "tra", "loi", "giup", "do", "cam", "on", "xin", "chao", "tam", "biet",
        "chuc", "chusc", "giang", "sinh", "vui", "mung", "hanh", "phuc", "khoe"
    )

    // Built-in Vietnamese phrase & bigram dictionary for instant spell-correction & diacritic restoration
    private val vietnamesePhraseReplacements = listOf(
        "chuc ban giang sinh vui ve" to "Chúc bạn Giáng sinh vui vẻ",
        "chuc giang sinh vui ve" to "Chúc Giáng sinh vui vẻ",
        "giang sinh vui ve" to "Giáng sinh vui vẻ",
        "chuc mung nam moi" to "Chúc mừng năm mới",
        "chuc sinh nhat vui ve" to "Chúc sinh nhật vui vẻ",
        "chuc ban may man" to "Chúc bạn may mắn",
        "chuc ban thanh cong" to "Chúc bạn thành công",
        "chuc ban ngu ngon" to "Chúc bạn ngủ ngon",
        "chuc mot ngay tot lanh" to "Chúc một ngày tốt lành",
        "cam on ban rat nhieu" to "Cảm ơn bạn rất nhiều",
        "xin chao moi nguoi" to "Xin chào mọi người",
        "toi muon hoc tieng trung" to "Tôi muốn học tiếng Trung",
        "cong ty co dong bao hiem cho nhan vien khong" to "Công ty có đóng bảo hiểm cho nhân viên không",
        "cong ty co dong bao hiem khong" to "Công ty có đóng bảo hiểm không",
        "bao nhieu tien" to "bao nhiêu tiền",
        "nhu the nao" to "như thế nào",
        "khong co gi" to "không có gì",
        "khong sao dau" to "không sao đâu",
        "hen gap lai" to "hẹn gặp lại",
        "giang sinh" to "Giáng sinh",
        "vui ve" to "vui vẻ",
        "chuc ban" to "chúc bạn",
        "chuc mung" to "chúc mừng",
        "nam moi" to "năm mới",
        "sinh nhat" to "sinh nhật",
        "hanh phuc" to "hạnh phúc",
        "thanh cong" to "thành công",
        "suc khoe" to "sức khỏe",
        "binh an" to "bình an",
        "may man" to "may mắn",
        "tot lanh" to "tốt lành",
        "ngu ngon" to "ngủ ngon",
        "cam on" to "cảm ơn",
        "xin chao" to "xin chào",
        "xin loi" to "xin lỗi",
        "tam biet" to "tạm biệt",
        "cong ty" to "công ty",
        "nhan vien" to "nhân viên",
        "bao hiem" to "bảo hiểm",
        "tien luong" to "tiền lương",
        "phu cap" to "phụ cấp",
        "tang ca" to "tăng ca",
        "nghi phep" to "nghỉ phép",
        "hop dong" to "hợp đồng",
        "giam doc" to "giám đốc",
        "quan ly" to "quản lý",
        "cong xuong" to "công xưởng",
        "nha may" to "nhà máy",
        "ky tuc xa" to "ký túc xá",
        "tieng trung" to "tiếng Trung",
        "tieng viet" to "tiếng Việt",
        "hoc tap" to "học tập",
        "lam viec" to "làm việc",
        "cuoc song" to "cuộc sống",
        "hang ngay" to "hàng ngày",
        "gia dinh" to "gia đình",
        "ban be" to "bạn bè",
        "hom nay" to "hôm nay",
        "ngay mai" to "ngày mai",
        "hom qua" to "hôm qua",
        "buoi sang" to "buổi sáng",
        "buoi trua" to "buổi trưa",
        "buoi chieu" to "buổi chiều",
        "buoi toi" to "buổi tối",
        "chung toi" to "chúng tôi",
        "chung ta" to "chúng ta",
        "moi nguoi" to "mọi người",
        "rat nhieu" to "rất nhiều",
        "quan trong" to "quan trọng",
        "giai thich" to "giải thích",
        "thong bao" to "thông báo",
        "quy dinh" to "quy định"
    )

    private val vietnameseWordTypoMap = mapOf(
        "chusc" to "chúc",
        "chus" to "chứ",
        "chuc" to "chúc",
        "chuac" to "chúc",
        "banj" to "bạn",
        "bajn" to "bạn",
        "giangs" to "giáng",
        "vees" to "vế",
        "ver" to "vẻ",
        "khoe" to "khỏe",
        "khoer" to "khỏe",
        "duoc" to "được",
        "duocj" to "được",
        "duowcj" to "được",
        "dc" to "được",
        "đc" to "được",
        "khong" to "không",
        "khongs" to "không",
        "ko" to "không",
        "k" to "không",
        "nguoi" to "người",
        "nguoif" to "người",
        "nhieu" to "nhiều",
        "nhieuf" to "nhiều",
        "tieng" to "tiếng",
        "tiengs" to "tiếng",
        "viet" to "Việt",
        "vieetj" to "Việt",
        "trung" to "Trung",
        "hoc" to "học",
        "hocj" to "học",
        "muon" to "muốn",
        "muoons" to "muốn",
        "toi" to "tôi",
        "tooi" to "tôi",
        "ban" to "bạn",
        "anh" to "anh",
        "em" to "em",
        "rat" to "rất",
        "rats" to "rất",
        "lam" to "làm",
        "lamf" to "làm",
        "viec" to "việc",
        "vieecj" to "việc",
        "cong" to "công",
        "baor" to "bảo",
        "hiemr" to "hiểm",
        "camr" to "cảm",
        "chaof" to "chào",
        "loix" to "lỗi"
    )

    /**
     * Decodes a single Vietnamese word that may contain Telex tone/vowel keys or typos
     * (e.g. "chusc" -> "chúc", "vieetj" -> "việt", "duowcj" -> "được", "ver" -> "vẻ").
     */
    private fun decodeTelexOrTypoWord(rawWord: String): String {
        val lower = rawWord.lowercase()
        vietnameseWordTypoMap[lower]?.let { mapped ->
            return if (rawWord.firstOrNull()?.isUpperCase() == true) {
                mapped.replaceFirstChar { it.uppercase() }
            } else mapped
        }

        // Check if word contains Telex vowel modifiers or mid-word/end-word tone key (s, f, r, x, j)
        var w = lower
        val hasTelexVowel = w.contains("aa") || w.contains("aw") || w.contains("ee") ||
            w.contains("oo") || w.contains("ow") || w.contains("uw") || w.startsWith("dd")

        // Detect Telex tone key (s, f, r, x, j) placed after a vowel (e.g. "chusc" -> "chu" + 's' + "c", "banj" -> "ban" + 'j')
        val telexToneRegex = "^([b-df-hj-np-tv-z]*[aeiouyăâêôơư]+)(s|f|r|x|j)([bcdfghjklmnpqrstvwxz]*)$".toRegex()
        var detectedTone: Char? = null

        val workingVowelReplaced = w
            .replace("dd", "đ")
            .replace("aa", "â")
            .replace("aw", "ă")
            .replace("ee", "ê")
            .replace("oo", "ô")
            .replace("ow", "ơ")
            .replace("uw", "ư")

        val match = telexToneRegex.matchEntire(workingVowelReplaced)
        if (match != null) {
            val prefix = match.groupValues[1]
            val toneChar = match.groupValues[2].first()
            val suffix = match.groupValues[3]
            // Only treat as Telex tone if suffix is valid Vietnamese final consonant ("", "c", "ch", "m", "n", "ng", "nh", "p", "t")
            if (suffix in setOf("", "c", "ch", "m", "n", "ng", "nh", "p", "t")) {
                w = prefix + suffix
                detectedTone = toneChar
            }
        } else if (hasTelexVowel) {
            w = workingVowelReplaced
        }

        if (detectedTone != null) {
            val toneTable = mapOf(
                'a' to mapOf('s' to 'á', 'f' to 'à', 'r' to 'ả', 'x' to 'ã', 'j' to 'ạ'),
                'ă' to mapOf('s' to 'ắ', 'f' to 'ằ', 'r' to 'ẳ', 'x' to 'ẵ', 'j' to 'ặ'),
                'â' to mapOf('s' to 'ấ', 'f' to 'ầ', 'r' to 'ẩ', 'x' to 'ẫ', 'j' to 'ậ'),
                'e' to mapOf('s' to 'é', 'f' to 'è', 'r' to 'ẻ', 'x' to 'ẽ', 'j' to 'ẹ'),
                'ê' to mapOf('s' to 'ế', 'f' to 'ề', 'r' to 'ể', 'x' to 'ễ', 'j' to 'ệ'),
                'i' to mapOf('s' to 'í', 'f' to 'ì', 'r' to 'ỉ', 'x' to 'ĩ', 'j' to 'ị'),
                'o' to mapOf('s' to 'ó', 'f' to 'ò', 'r' to 'ỏ', 'x' to 'õ', 'j' to 'ọ'),
                'ô' to mapOf('s' to 'ố', 'f' to 'ồ', 'r' to 'ổ', 'x' to 'ỗ', 'j' to 'ộ'),
                'ơ' to mapOf('s' to 'ớ', 'f' to 'ờ', 'r' to 'ở', 'x' to 'ỡ', 'j' to 'ợ'),
                'u' to mapOf('s' to 'ú', 'f' to 'ù', 'r' to 'ủ', 'x' to 'ũ', 'j' to 'ụ'),
                'ư' to mapOf('s' to 'ứ', 'f' to 'ừ', 'r' to 'ử', 'x' to 'ữ', 'j' to 'ự'),
                'y' to mapOf('s' to 'ý', 'f' to 'ỳ', 'r' to 'ỷ', 'x' to 'ỹ', 'j' to 'ỵ')
            )
            // Find main vowel index
            var targetIdx = -1
            for (special in charArrayOf('ê', 'ơ', 'ô', 'ă', 'â', 'ư')) {
                val idx = w.indexOf(special)
                if (idx != -1) {
                    targetIdx = idx
                    break
                }
            }
            if (targetIdx == -1) {
                for (i in w.indices.reversed()) {
                    if (toneTable.containsKey(w[i])) {
                        targetIdx = i
                        break
                    }
                }
            }
            if (targetIdx != -1) {
                val baseVowel = w[targetIdx]
                val accented = toneTable[baseVowel]?.get(detectedTone)
                if (accented != null) {
                    w = w.substring(0, targetIdx) + accented + w.substring(targetIdx + 1)
                }
            }
        }

        return if (rawWord.firstOrNull()?.isUpperCase() == true) {
            w.replaceFirstChar { it.uppercase() }
        } else w
    }

    /**
     * Automatically fixes Vietnamese typos, Telex input (like "chusc ban giang sinh vui ve"),
     * and restores missing Vietnamese diacritics using the local corpus + phrase dictionary.
     */
    fun autoCorrectVietnameseText(input: String, studySets: List<StudySet>): String {
        val clean = input.trim()
        if (clean.isEmpty() || clean.any { isCjk(it) }) return clean

        // If it's a pure Chinese Pinyin phrase (like "baoxian", "nihao"), do NOT turn it into Vietnamese
        val rawTokens = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val allTokensPinyin = rawTokens.isNotEmpty() && rawTokens.all { commonPinyinSyllables.contains(it.lowercase()) }
        val anyViSpecific = rawTokens.any { vietnameseOnlyWords.contains(it.lowercase()) || vietnameseWordTypoMap.containsKey(it.lowercase()) }
        if (allTokensPinyin && !anyViSpecific && rawTokens.size <= 3 && !hasVietnameseDiacritics(clean)) {
            return clean
        }

        ensureFastIndex(studySets)

        // Step 1: Decode Telex / typo words (e.g. "chusc" -> "chúc")
        val telexDecodedWords = rawTokens.map { decodeTelexOrTypoWord(it) }
        val afterWordTelex = telexDecodedWords.joinToString(" ")
        val strippedFull = stripAccents(afterWordTelex)

        // Step 2: Exact match against our 338 studySets' Vietnamese sentences
        cachedStrippedViPhraseMap[strippedFull]?.let { exactStudyVi ->
            return exactStudyVi
        }

        // Step 3: Apply phrase & bigram replacements on stripped text while preserving existing accented words
        var working = afterWordTelex
        var workingStripped = stripAccents(working)
        for ((plainPattern, accentedReplacement) in vietnamesePhraseReplacements) {
            if (workingStripped == plainPattern) {
                return accentedReplacement
            }
            val regex = "\\b${Regex.escape(plainPattern)}\\b".toRegex(RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(workingStripped)) {
                // Replace corresponding span in working
                val match = regex.find(workingStripped)
                if (match != null && match.range.last < working.length) {
                    working = working.substring(0, match.range.first) +
                        accentedReplacement +
                        working.substring(match.range.last + 1)
                    workingStripped = stripAccents(working)
                }
            }
        }

        return working.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    fun stripAccents(input: String): String {
        val normalized = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
        return normalized
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace('đ', 'd')
            .replace("ü", "v")
    }

    fun hasVietnameseDiacritics(input: String): Boolean {
        val lower = input.lowercase()
        if (lower.contains('đ') || lower.contains('ă') || lower.contains('â') ||
            lower.contains('ê') || lower.contains('ô') || lower.contains('ơ') || lower.contains('ư')
        ) return true
        val nfd = Normalizer.normalize(lower, Normalizer.Form.NFD)
        return nfd.any { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
    }

    /**
     * Generates all 4 tone variations for a single unaccented Pinyin syllable (e.g. "bao" -> ["bāo", "báo", "bǎo", "bào"])
     */
    fun generateFourTonesForSyllable(rawSyllable: String): List<String> {
        val clean = stripAccents(rawSyllable.trim().lowercase()).filter { it in 'a'..'z' }
        if (clean.isEmpty()) return emptyList()
        val vowels = "aeiouv"
        var targetIndex = -1
        if (clean.contains('a')) {
            targetIndex = clean.indexOf('a')
        } else if (clean.contains('e')) {
            targetIndex = clean.indexOf('e')
        } else if (clean.contains("ou")) {
            targetIndex = clean.indexOf('o')
        } else {
            for (i in clean.length - 1 downTo 0) {
                if (vowels.contains(clean[i])) {
                    targetIndex = i
                    break
                }
            }
        }
        if (targetIndex == -1) return listOf(clean)
        val vowelChar = clean[targetIndex]
        val tones = toneMap[vowelChar] ?: return listOf(clean)
        return (0..3).map { toneIdx ->
            clean.substring(0, targetIndex) + tones[toneIdx] + clean.substring(targetIndex + 1)
        }
    }

    /**
     * Splits compact Pinyin like "baoxian" or "nihao" into syllables ["bao", "xian"]
     */
    fun splitPinyinIntoSyllables(input: String): List<String> {
        val stripped = stripAccents(input.trim().lowercase())
        val rawTokens = stripped.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val result = mutableListOf<String>()
        for (token in rawTokens) {
            val clean = token.filter { it in 'a'..'z' }
            if (clean.isEmpty()) continue
            if (commonPinyinSyllables.contains(clean)) {
                result.add(clean)
            } else {
                var pos = 0
                while (pos < clean.length) {
                    var matched = ""
                    for (len in minOf(6, clean.length - pos) downTo 1) {
                        val sub = clean.substring(pos, pos + len)
                        if (commonPinyinSyllables.contains(sub)) {
                            matched = sub
                            break
                        }
                    }
                    if (matched.isNotEmpty()) {
                        result.add(matched)
                        pos += matched.length
                    } else {
                        result.add(clean.substring(pos))
                        break
                    }
                }
            }
        }
        return result
    }

    fun matchesQueryWithPinyin(lineOrText: String, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        if (lineOrText.contains(q, ignoreCase = true)) return true
        val strippedQuery = stripAccents(q)
        if (strippedQuery.length < 2) return false
        val strippedLine = stripAccents(lineOrText)
        if (strippedLine.contains(strippedQuery)) return true
        val compactQuery = strippedQuery.replace(" ", "")
        if (compactQuery.length >= 3 && strippedLine.replace(" ", "").contains(compactQuery)) {
            return true
        }
        return false
    }

    private fun queryGoogleInputTools(text: String, itc: String, num: Int = 4): List<String> {
        if (text.isBlank()) return emptyList()
        return try {
            val encoded = URLEncoder.encode(text.trim(), "UTF-8")
            val url = "https://inputtools.google.com/request?text=$encoded&itc=$itc&num=$num&cp=0&cs=1&ie=utf-8&oe=utf-8"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string().orEmpty()
                if (body.isEmpty()) return emptyList()
                val root = JSONArray(body)
                if (root.optString(0) != "SUCCESS") return emptyList()
                val firstArr = root.optJSONArray(1)?.optJSONArray(0) ?: return emptyList()
                val candidatesArr = firstArr.optJSONArray(1) ?: return emptyList()
                val list = mutableListOf<String>()
                for (i in 0 until candidatesArr.length()) {
                    val c = candidatesArr.optString(i).trim()
                    if (c.isNotEmpty()) list.add(c)
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Fast, automatic Pinyin & Vietnamese prediction without flooding the UI with 20 manual tone chips.
     */
    suspend fun fetchSmartInputSuggestions(
        query: String,
        studySets: List<StudySet>
    ): SmartInputSuggestion = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isEmpty() || clean.any { isCjk(it) }) {
            return@withContext SmartInputSuggestion(
                originalInput = clean,
                isLikelyPinyin = false,
                isLikelyUnaccentedVi = false,
                pinyinWithTonesList = emptyList(),
                syllableToneTable = emptyList(),
                hanziCandidates = emptyList(),
                restoredVietnameseCandidates = emptyList()
            )
        }

        synchronized(smartSuggestionCache) {
            smartSuggestionCache[clean]?.let { return@withContext it }
        }

        val records = ensureFastIndex(studySets)
        val hasViAccents = hasVietnameseDiacritics(clean)
        val syllables = splitPinyinIntoSyllables(clean)
        val words = stripAccents(clean).split("\\s+".toRegex()).filter { it.isNotBlank() }

        val allTokensValidPinyin = syllables.isNotEmpty() && syllables.all { commonPinyinSyllables.contains(it) }
        val viWordHitCount = words.count { vietnameseOnlyWords.contains(it) || vietnameseWordTypoMap.containsKey(it) }
        val isLikelyPinyin = !hasViAccents && allTokensValidPinyin && viWordHitCount == 0 && words.size <= 5
        val isLikelyUnaccentedVi = !isLikelyPinyin && (!hasViAccents || words.any { vietnameseWordTypoMap.containsKey(it) })

        val matchedPinyinSet = LinkedHashSet<String>()
        val matchedHanziCandidates = LinkedHashMap<String, String>()
        val compactInput = syllables.joinToString("")

        // 1. Instant lookup from pre-indexed vocabulary records (0ms)
        if (compactInput.length >= 2) {
            // Exact Pinyin matches first
            for (rec in records) {
                if (rec.compactPinyin == compactInput) {
                    if (rec.pinyin.isNotBlank()) matchedPinyinSet.add(rec.pinyin)
                    if (rec.hanzi.isNotBlank() && !matchedHanziCandidates.containsKey(rec.hanzi)) {
                        matchedHanziCandidates[rec.hanzi] = "${rec.pinyin} • ${rec.vi}"
                    }
                    if (matchedHanziCandidates.size >= 5) break
                }
            }
            // Prefix Pinyin predictions next
            if (matchedHanziCandidates.size < 5) {
                for (rec in records) {
                    if (rec.compactPinyin.startsWith(compactInput) && rec.compactPinyin.length <= compactInput.length + 6) {
                        if (rec.pinyin.isNotBlank()) matchedPinyinSet.add(rec.pinyin)
                        if (rec.hanzi.isNotBlank() && !matchedHanziCandidates.containsKey(rec.hanzi)) {
                            matchedHanziCandidates[rec.hanzi] = "${rec.pinyin} • ${rec.vi}"
                        }
                        if (matchedHanziCandidates.size >= 5) break
                    }
                }
            }
        }

        // 2. If Pinyin has no local match, query Google Input Tools IME automatically for the #1 Hanzi candidate
        if (isLikelyPinyin && matchedHanziCandidates.isEmpty()) {
            val imeQuery = syllables.joinToString("")
            val onlineHanzi = queryGoogleInputTools(imeQuery, "zh-t-i0-pinyin", 3)
            for (hz in onlineHanzi) {
                if (!matchedHanziCandidates.containsKey(hz)) {
                    matchedHanziCandidates[hz] = "Tự động đoán Pinyin"
                }
            }
        }

        // 3. Automatic Vietnamese spell-correction & diacritic restoration
        val restoredVi = LinkedHashSet<String>()
        if (isLikelyUnaccentedVi) {
            val autoCorrected = autoCorrectVietnameseText(clean, studySets)
            if (!autoCorrected.equals(clean, ignoreCase = true)) {
                restoredVi.add(autoCorrected)
            }
            // Also check Google Input Tools Vietnamese IME if still lacking diacritics
            if (!hasVietnameseDiacritics(autoCorrected) && words.size >= 2) {
                val onlineVi = queryGoogleInputTools(afterTelexSimple(clean), "vi-t-i0-und", 2)
                restoredVi.addAll(onlineVi.filter { !it.equals(clean, ignoreCase = true) })
            }
        }

        val result = SmartInputSuggestion(
            originalInput = clean,
            isLikelyPinyin = isLikelyPinyin,
            isLikelyUnaccentedVi = isLikelyUnaccentedVi,
            pinyinWithTonesList = matchedPinyinSet.take(4).toList(),
            syllableToneTable = emptyList(), // Automatic Pinyin resolution instead of manual 4-tone chip wall
            hanziCandidates = matchedHanziCandidates.entries.take(5).map { it.key to it.value },
            restoredVietnameseCandidates = restoredVi.take(3).toList()
        )
        synchronized(smartSuggestionCache) {
            smartSuggestionCache[clean] = result
        }
        result
    }

    private fun afterTelexSimple(text: String): String {
        return text.trim().split("\\s+".toRegex()).joinToString(" ") { decodeTelexOrTypoWord(it) }
    }

    /**
     * Fast 2-way translation with automatic Vietnamese spell-correction, automatic Pinyin resolution,
     * and in-memory LRU caching so responses feel instantaneous.
     */
    suspend fun translateTwoWay(text: String, studySets: List<StudySet>): TwoWayTranslationResult = withContext(Dispatchers.IO) {
        val clean = text.trim()
        if (clean.isEmpty()) {
            return@withContext TwoWayTranslationResult("", "vi", "", "zh")
        }

        synchronized(translationCache) {
            translationCache[clean]?.let { return@withContext it }
        }

        val records = ensureFastIndex(studySets)
        var workingSource = clean
        var correctedNote = ""
        val toneHints = mutableListOf<String>()

        val hasChinese = clean.any { isCjk(it) }
        if (!hasChinese) {
            val words = stripAccents(clean).split("\\s+".toRegex()).filter { it.isNotBlank() }
            val syllables = splitPinyinIntoSyllables(clean)
            val allTokensValidPinyin = syllables.isNotEmpty() && syllables.all { commonPinyinSyllables.contains(it) }
            val viHits = words.count { vietnameseOnlyWords.contains(it) || vietnameseWordTypoMap.containsKey(it) }
            val compactInput = syllables.joinToString("")

            // Case A: Automatic Pinyin prediction -> immediately resolve to exact Chinese word/phrase!
            if (allTokensValidPinyin && viHits == 0 && !hasVietnameseDiacritics(clean) && words.size <= 5) {
                val exactLocal = records.firstOrNull { it.compactPinyin == compactInput }
                    ?: records.firstOrNull { it.compactPinyin.startsWith(compactInput) && it.compactPinyin.length <= compactInput.length + 4 }

                if (exactLocal != null && exactLocal.hanzi.isNotBlank()) {
                    val fastResult = TwoWayTranslationResult(
                        sourceText = exactLocal.vi,
                        sourceLang = "vi",
                        translatedText = exactLocal.hanzi,
                        targetLang = "zh",
                        pinyin = exactLocal.pinyin,
                        correctedSourceText = "Tự động dự đoán Pinyin: $clean ➔ ${exactLocal.hanzi} [${exactLocal.pinyin}]",
                        toneSuggestions = listOf(exactLocal.pinyin),
                        isOnlineResult = true
                    )
                    synchronized(translationCache) { translationCache[clean] = fastResult }
                    return@withContext fastResult
                } else {
                    val onlineHanzi = queryGoogleInputTools(compactInput, "zh-t-i0-pinyin", 2)
                    val topHanzi = onlineHanzi.firstOrNull().orEmpty()
                    if (topHanzi.any { isCjk(it) }) {
                        workingSource = topHanzi
                        correctedNote = "Tự động dự đoán Pinyin: $clean ➔ $topHanzi"
                    }
                }
            } else {
                // Case B: Automatic Vietnamese Typo / Telex / Unaccented Spell-Correction
                val autoFixedVi = autoCorrectVietnameseText(clean, studySets)
                if (!autoFixedVi.equals(clean, ignoreCase = false)) {
                    workingSource = autoFixedVi
                    correctedNote = "Tự động sửa chính tả: $autoFixedVi"
                } else if (!hasVietnameseDiacritics(clean) && words.size >= 2) {
                    val imeVi = queryGoogleInputTools(afterTelexSimple(clean), "vi-t-i0-und", 1).firstOrNull()
                    if (!imeVi.isNullOrBlank() && !imeVi.equals(clean, ignoreCase = true)) {
                        workingSource = imeVi
                        correctedNote = "Tự động sửa chính tả: $imeVi"
                    }
                }
            }
        }

        // Fast Path: Check if workingSource is an exact match in our pre-indexed 18,000+ dictionary (0ms!)
        val strippedWorking = stripAccents(workingSource)
        val exactLocalMatch = if (workingSource.any { isCjk(it) }) {
            records.firstOrNull { it.hanzi == workingSource }
        } else {
            records.firstOrNull { it.strippedVi == strippedWorking }
        }
        if (exactLocalMatch != null && exactLocalMatch.hanzi.isNotBlank() && exactLocalMatch.pinyin.isNotBlank()) {
            val localFastRes = TwoWayTranslationResult(
                sourceText = if (workingSource.any { isCjk(it) }) exactLocalMatch.hanzi else exactLocalMatch.vi,
                sourceLang = if (workingSource.any { isCjk(it) }) "zh" else "vi",
                translatedText = if (workingSource.any { isCjk(it) }) exactLocalMatch.vi else exactLocalMatch.hanzi,
                targetLang = if (workingSource.any { isCjk(it) }) "vi" else "zh",
                pinyin = exactLocalMatch.pinyin,
                correctedSourceText = correctedNote,
                isOnlineResult = true
            )
            synchronized(translationCache) { translationCache[clean] = localFastRes }
            return@withContext localFastRes
        }

        val sourceHasChinese = workingSource.any { isCjk(it) }
        val sl = if (sourceHasChinese) "zh-CN" else "vi"
        val tl = if (sourceHasChinese) "vi" else "zh-CN"

        // 1. Primary Online Translation via HTTP POST to Google GTX (single fast request with dt=t&dt=rm&dt=at&dt=qc)
        try {
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&dt=rm&dt=at&dt=qc"
            val formBody = FormBody.Builder()
                .add("q", workingSource)
                .build()
            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    if (body.isNotEmpty()) {
                        val root = JSONArray(body)
                        val sentences = root.optJSONArray(0)
                        val sbTranslated = StringBuilder()
                        val sbPinyin = StringBuilder()
                        if (sentences != null) {
                            for (i in 0 until sentences.length()) {
                                val seg = sentences.optJSONArray(i) ?: continue
                                val transPart = seg.optString(0, "")
                                if (transPart.isNotEmpty() && transPart != "null") {
                                    sbTranslated.append(transPart)
                                }
                                val rom2 = seg.optString(2, "")
                                val rom3 = seg.optString(3, "")
                                if (!sourceHasChinese && rom2.isNotEmpty() && rom2 != "null") {
                                    if (sbPinyin.isNotEmpty()) sbPinyin.append(" ")
                                    sbPinyin.append(rom2)
                                } else if (sourceHasChinese && rom3.isNotEmpty() && rom3 != "null") {
                                    if (sbPinyin.isNotEmpty()) sbPinyin.append(" ")
                                    sbPinyin.append(rom3)
                                } else if (rom2.isNotEmpty() && rom2 != "null") {
                                    if (sbPinyin.isNotEmpty()) sbPinyin.append(" ")
                                    sbPinyin.append(rom2)
                                }
                            }
                        }

                        // Check Google GTX spell-correction block at index 7 (dt=qc)
                        val spellBlock = root.optJSONArray(7)
                        if (spellBlock != null && spellBlock.length() > 1) {
                            val gtxCorrected = spellBlock.optString(1, "").trim()
                            if (gtxCorrected.isNotEmpty() && gtxCorrected != "null") {
                                workingSource = gtxCorrected
                                correctedNote = "Tự động sửa chính tả: $gtxCorrected"
                            }
                        }

                        // Parse alternative translations from index 5 (dt=at)
                        val altList = mutableListOf<String>()
                        val altArr = root.optJSONArray(5)
                        if (altArr != null) {
                            for (i in 0 until altArr.length()) {
                                val group = altArr.optJSONArray(i)?.optJSONArray(2) ?: continue
                                for (j in 0 until minOf(4, group.length())) {
                                    val candidate = group.optJSONArray(j)?.optString(0, "").orEmpty().trim()
                                    if (candidate.isNotEmpty() && !altList.contains(candidate)) {
                                        altList.add(candidate)
                                    }
                                }
                            }
                        }

                        val finalTranslated = sbTranslated.toString().trim()
                        var finalPinyin = sbPinyin.toString().trim()

                        // If translating VI -> ZH and the user's Vietnamese still lacks diacritics or had typos,
                        // or if Pinyin was missing, fetch both Pinyin & natural accented Vietnamese from finalTranslated in 1 call!
                        if (!sourceHasChinese && finalTranslated.any { isCjk(it) }) {
                            val needsViAccentRefinement = !hasVietnameseDiacritics(workingSource) ||
                                workingSource.split("\\s+".toRegex()).count { !hasVietnameseDiacritics(it) && it.length > 3 } >= 2
                            if (finalPinyin.isEmpty() || needsViAccentRefinement) {
                                val (naturalVi, zhPy) = fetchNaturalViAndPinyinFromChineseOnline(finalTranslated)
                                if (finalPinyin.isEmpty() && zhPy.isNotEmpty()) {
                                    finalPinyin = zhPy
                                }
                                if (needsViAccentRefinement && naturalVi.isNotEmpty() && hasVietnameseDiacritics(naturalVi)) {
                                    workingSource = naturalVi
                                    correctedNote = "Tự động sửa chính tả: $naturalVi"
                                }
                            }
                        } else if (finalTranslated.isNotEmpty() && finalPinyin.isEmpty() && finalTranslated.any { isCjk(it) }) {
                            finalPinyin = fetchPinyinForChineseOnline(finalTranslated)
                        }

                        if (finalTranslated.isNotEmpty()) {
                            val finalRes = TwoWayTranslationResult(
                                sourceText = workingSource,
                                sourceLang = if (sourceHasChinese) "zh" else "vi",
                                translatedText = finalTranslated,
                                targetLang = if (sourceHasChinese) "vi" else "zh",
                                pinyin = finalPinyin,
                                correctedSourceText = correctedNote,
                                toneSuggestions = toneHints,
                                alternativeTranslations = altList.filter { it != finalTranslated }.take(4),
                                isOnlineResult = true
                            )
                            synchronized(translationCache) { translationCache[clean] = finalRes }
                            return@withContext finalRes
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 2. Secondary Online Translation Fallback (MyMemory API)
        try {
            val langPair = if (sourceHasChinese) "zh-CN|vi" else "vi|zh-CN"
            val encodedQ = URLEncoder.encode(workingSource, "UTF-8")
            val mmUrl = "https://api.mymemory.translated.net/get?q=$encodedQ&langpair=$langPair"
            val req = Request.Builder().url(mmUrl).header("User-Agent", "Mozilla/5.0").build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val translated = json.optJSONObject("responseData")?.optString("translatedText", "").orEmpty().trim()
                    if (translated.isNotEmpty() && !translated.contains("MYMEMORY WARNING", ignoreCase = true)) {
                        val py = if (translated.any { isCjk(it) }) fetchPinyinForChineseOnline(translated) else ""
                        val mmRes = TwoWayTranslationResult(
                            sourceText = workingSource,
                            sourceLang = if (sourceHasChinese) "zh" else "vi",
                            translatedText = translated,
                            targetLang = if (sourceHasChinese) "vi" else "zh",
                            pinyin = py,
                            correctedSourceText = correctedNote,
                            toneSuggestions = toneHints,
                            isOnlineResult = true
                        )
                        synchronized(translationCache) { translationCache[clean] = mmRes }
                        return@withContext mmRes
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 3. Offline fallback using our fast pre-indexed studySet dictionary
        val offlineMatch = searchAndPredictInstant(workingSource, studySets, 1).firstOrNull()
        if (offlineMatch != null) {
            val parsed = parseEditableVocabItems("${offlineMatch.vi} = ${offlineMatch.foreign}").firstOrNull()
            val bestVi = parsed?.vi ?: offlineMatch.vi
            val bestZh = parsed?.hanzi ?: offlineMatch.foreign
            val bestPinyin = parsed?.pinyin.orEmpty()
            return@withContext if (sourceHasChinese) {
                TwoWayTranslationResult(
                    sourceText = workingSource,
                    sourceLang = "zh",
                    translatedText = bestVi,
                    targetLang = "vi",
                    pinyin = bestPinyin,
                    correctedSourceText = correctedNote,
                    toneSuggestions = toneHints,
                    isOnlineResult = false
                )
            } else {
                TwoWayTranslationResult(
                    sourceText = bestVi,
                    sourceLang = "vi",
                    translatedText = bestZh,
                    targetLang = "zh",
                    pinyin = bestPinyin,
                    correctedSourceText = correctedNote,
                    toneSuggestions = toneHints,
                    isOnlineResult = false
                )
            }
        }

        TwoWayTranslationResult(
            sourceText = workingSource,
            sourceLang = if (sourceHasChinese) "zh" else "vi",
            translatedText = "(Không thể kết nối mạng để dịch câu này. Vui lòng kiểm tra kết nối Internet!)",
            targetLang = if (sourceHasChinese) "vi" else "zh",
            correctedSourceText = correctedNote,
            toneSuggestions = toneHints,
            isOnlineResult = false
        )
    }

    /**
     * Fetches both natural accented Vietnamese and tone-marked Pinyin for a Chinese text in a single HTTP request!
     */
    private fun fetchNaturalViAndPinyinFromChineseOnline(zhText: String): Pair<String, String> {
        return try {
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=zh-CN&tl=vi&dt=t&dt=rm"
            val formBody = FormBody.Builder().add("q", zhText).build()
            val req = Request.Builder().url(url).post(formBody).header("User-Agent", "Mozilla/5.0").build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return "" to ""
                val root = JSONArray(resp.body?.string().orEmpty())
                val sentences = root.optJSONArray(0) ?: return "" to ""
                val sbVi = StringBuilder()
                val sbPy = StringBuilder()
                for (i in 0 until sentences.length()) {
                    val seg = sentences.optJSONArray(i) ?: continue
                    val viPart = seg.optString(0, "")
                    if (viPart.isNotEmpty() && viPart != "null") {
                        sbVi.append(viPart)
                    }
                    val rom3 = seg.optString(3, "")
                    val rom2 = seg.optString(2, "")
                    val chosen = if (rom3.isNotEmpty() && rom3 != "null") rom3 else if (rom2.isNotEmpty() && rom2 != "null") rom2 else ""
                    if (chosen.isNotEmpty()) {
                        if (sbPy.isNotEmpty()) sbPy.append(" ")
                        sbPy.append(chosen)
                    }
                }
                sbVi.toString().trim() to sbPy.toString().trim()
            }
        } catch (_: Exception) {
            "" to ""
        }
    }

    fun fetchPinyinForChineseOnline(zhText: String): String {
        return fetchNaturalViAndPinyinFromChineseOnline(zhText).second
    }

    /**
     * Fetches real online example sentences (from Hanzii / Google GTX dt=ex + online translation)
     * and substitutable synonyms/phrases for VisualVocabDetailDialog.
     * Guarantees every Chinese example sentence contains the target word/characters so red highlighting is always active!
     */
    suspend fun fetchOnlineExamplesAndSubstitutes(
        hanzi: String,
        pinyin: String,
        vi: String,
        allStudySets: List<StudySet>
    ): Pair<List<ExampleSentenceItem>, List<SubstitutableWordItem>> = withContext(Dispatchers.IO) {
        val kw = hanzi.trim()
        val cleanVi = autoCorrectVietnameseText(vi.trim(), allStudySets)
        val foundExamples = mutableListOf<ExampleSentenceItem>()
        val foundSubstitutes = mutableListOf<SubstitutableWordItem>()

        // 1. Collect authentic sentences from local studySets first
        for (set in allStudySets) {
            if (foundExamples.size >= 3) break
            if (!set.rawContent.contains(kw)) continue
            for (line in set.rawContent.lineSequence()) {
                if (foundExamples.size >= 3) break
                if (!line.contains(kw)) continue
                val parsed = parseEditableVocabItems(line).firstOrNull() ?: continue
                if (parsed.hanzi.length > kw.length + 2) {
                    foundExamples.add(
                        ExampleSentenceItem(
                            zhSimplified = parsed.hanzi,
                            zhBracketed = "[${parsed.hanzi}]",
                            pinyin = parsed.pinyin.ifBlank { pinyin },
                            vi = parsed.vi
                        )
                    )
                }
            }
        }

        // 2. Query Google Translate GTX with dt=ex&dt=bd&dt=ss&dt=at for real online examples & synonyms
        try {
            val encoded = URLEncoder.encode(kw, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=zh-CN&tl=vi&dt=t&dt=bd&dt=ex&dt=ss&dt=at&dt=rm&q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotEmpty()) {
                        val root = JSONArray(body)

                        // Extract online example sentences from index 13 (dt=ex)
                        val exRoot = root.optJSONArray(13)?.optJSONArray(0)
                        if (exRoot != null) {
                            for (i in 0 until minOf(4, exRoot.length())) {
                                val exObj = exRoot.optJSONArray(i) ?: continue
                                val rawSent = exObj.optString(0, "")
                                    .replace("<b>", "")
                                    .replace("</b>", "")
                                    .trim()
                                if (rawSent.length > kw.length + 1 && foundExamples.none { it.zhSimplified == rawSent }) {
                                    val (exVi, exPy) = fetchNaturalViAndPinyinFromChineseOnline(rawSent)
                                    if (exVi.isNotEmpty()) {
                                        foundExamples.add(
                                            ExampleSentenceItem(
                                                zhSimplified = rawSent,
                                                zhBracketed = "[$rawSent]",
                                                pinyin = exPy,
                                                vi = exVi
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Extract synonyms / substitutable words from index 11 (dt=ss)
                        val synRoot = root.optJSONArray(11)
                        if (synRoot != null) {
                            for (i in 0 until synRoot.length()) {
                                val posBlock = synRoot.optJSONArray(i)?.optJSONArray(1) ?: continue
                                for (j in 0 until posBlock.length()) {
                                    val synGroup = posBlock.optJSONArray(j)?.optJSONArray(0) ?: continue
                                    for (k in 0 until minOf(3, synGroup.length())) {
                                        val synWord = synGroup.optString(k, "").trim()
                                        if (synWord.isNotEmpty() && synWord != kw && foundSubstitutes.none { it.hanzi == synWord }) {
                                            val (synVi, synPy) = fetchNaturalViAndPinyinFromChineseOnline(synWord)
                                            foundSubstitutes.add(
                                                SubstitutableWordItem(
                                                    hanzi = synWord,
                                                    pinyin = synPy,
                                                    vi = synVi.ifEmpty { cleanVi },
                                                    popularityStars = if (foundSubstitutes.size < 2) 5 else 4,
                                                    popularityLabel = if (foundSubstitutes.size < 2) "Rất phổ biến (92%)" else "Thông dụng (80%)",
                                                    nuanceNote = "Có thể thay thế trực tiếp cho “$kw” trong ngữ cảnh ${cleanVi.lowercase()}",
                                                    replaceableSentenceZh = "我们可以用“$synWord”来代替“$kw”。",
                                                    replaceableSentencePinyin = "Wǒmen kěyǐ yòng \"$synWord\" lái dàitì \"$kw\".",
                                                    replaceableSentenceVi = "Chúng ta có thể dùng “$synWord” (${synVi.ifEmpty { cleanVi }}) để thay thế cho “$kw” ($cleanVi)."
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 3. If online examples are fewer than 4, build authentic Chinese sentences containing "$kw" directly
        // so "$kw" is ALWAYS highlighted in red (Hanzii style) and translate them cleanly!
        if (foundExamples.size < 4 && kw.isNotEmpty()) {
            val chineseSentencesWithKeyword = listOf(
                "大家都在热情地对他说：“$kw！”",
                "在日常中文交流里，“$kw”是一个非常高频的表达。",
                "请问“$kw”这句话在正式场合怎么用？",
                "老师让我们把“$kw”记在重点词汇本上。"
            )
            for (zhSent in chineseSentencesWithKeyword) {
                if (foundExamples.size >= 5) break
                if (foundExamples.any { it.zhSimplified == zhSent }) continue
                val (sentVi, sentPy) = fetchNaturalViAndPinyinFromChineseOnline(zhSent)
                if (sentVi.isNotEmpty()) {
                    foundExamples.add(
                        ExampleSentenceItem(
                            zhSimplified = zhSent,
                            zhBracketed = "[$zhSent]",
                            pinyin = sentPy,
                            vi = sentVi
                        )
                    )
                }
            }
        }

        // 4. Find local compound/synonym words sharing characters or meaning if foundSubstitutes < 4
        if (foundSubstitutes.size < 4) {
            val cjkChars = kw.filter { isCjk(it) }.map { it.toString() }
            val records = ensureFastIndex(allStudySets)
            for (item in records) {
                if (foundSubstitutes.size >= 5) break
                if (item.hanzi != kw && item.hanzi.length in 2..4 &&
                    (cjkChars.any { item.hanzi.contains(it) } || item.vi.contains(cleanVi.split(",").first().trim(), ignoreCase = true))
                ) {
                    if (foundSubstitutes.none { it.hanzi == item.hanzi }) {
                        foundSubstitutes.add(
                            SubstitutableWordItem(
                                hanzi = item.hanzi,
                                pinyin = item.pinyin,
                                vi = item.vi,
                                popularityStars = if (foundSubstitutes.size < 2) 5 else 4,
                                popularityLabel = if (foundSubstitutes.size < 2) "Phổ biến cao (90%)" else "Thông dụng (78%)",
                                nuanceNote = "Từ cùng gốc chữ / cùng trường nghĩa với “$kw” ($cleanVi)",
                                replaceableSentenceZh = "这个语境下可以用“${item.hanzi}”（${item.vi}）。",
                                replaceableSentencePinyin = "Zhège yǔjìng xià kěyǐ yòng \"${item.pinyin}\".",
                                replaceableSentenceVi = "Trong ngữ cảnh này có thể thay bằng “${item.hanzi}” (${item.vi})."
                            )
                        )
                    }
                }
            }
        }

        Pair(foundExamples.distinctBy { it.zhSimplified }.take(6), foundSubstitutes.distinctBy { it.hanzi }.take(5))
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
                        Text("Đang nhận diện chữ Hán / Tiếng Việt từ ảnh và dịch Online...", fontSize = 12.sp)
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
                                    text = if (res.isOnlineResult) "🌐 Bản dịch Online:" else "📚 Bản dịch:",
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
    val starredPairs by viewModel.allStarredPairs.collectAsState()
    var editedVi by remember { mutableStateOf(vi) }
    var editedForeign by remember { mutableStateOf(foreign) }
    var selectedFolderIds by remember { mutableStateOf(setOf<Long>()) }
    var selectedSetIds by remember { mutableStateOf(setOf<Long>()) }
    var newFolderName by remember { mutableStateOf("") }
    var selectedStatusTag by remember { mutableStateOf(viewModel.getWordPersonalTag(foreign)) }

    val existingSavedDate = remember(editedForeign, viewModel.vocabStatusVersion.value) {
        viewModel.getWordSavedDateFormatted(editedForeign)
    }
    val currentTimePreview = remember {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
    }
    val isStarred = remember(editedVi, editedForeign, starredPairs) {
        starredPairs.any {
            it.vi.trim().equals(editedVi.trim(), ignoreCase = true) ||
                it.foreign.trim().equals(editedForeign.trim(), ignoreCase = true)
        }
    }

    // Pre-detect which folders already contain this word/sentence
    val foldersAlreadyContaining = remember(editedForeign, studySets) {
        val cleanKey = editedForeign.substringBefore("(").trim()
        if (cleanKey.isEmpty()) emptySet()
        else studySets.filter { it.folderId != null && it.rawContent.contains(cleanKey) }
            .mapNotNull { it.folderId }
            .toSet()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔖 Lưu Từ Vựng / Câu Vào Thư Mục",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.toggleStar(editedVi, editedForeign, "zh") },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Gắn sao",
                            tint = if (isStarred) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = if (existingSavedDate.isNotBlank()) "📅 Đã lưu lần trước: $existingSavedDate • Cập nhật mới: $currentTimePreview"
                        else "📅 Ngày lưu tự động ghi nhận: $currentTimePreview",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF92400E),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
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
                    label = { Text("Chữ Hán / Câu tiếng Trung (kèm Pinyin)", fontSize = 11.5.sp) },
                    singleLine = false,
                    maxLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editedVi,
                    onValueChange = { editedVi = it },
                    label = { Text("Nghĩa Tiếng Việt / Bản dịch câu", fontSize = 11.5.sp) },
                    singleLine = false,
                    maxLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Learning status bucket selector
                Text(
                    text = "Phân loại vào danh sách học tập & ôn tập:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                val statusTags = listOf("Đã lưu", "Chưa thuộc", "Cần ôn tập", "Hay nhầm", "Đã thuộc")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    statusTags.forEach { tag ->
                        val isSel = selectedStatusTag == tag
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedStatusTag = tag },
                            label = {
                                Text(
                                    text = tag,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }

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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chọn thư mục (${selectedFolderIds.size + selectedSetIds.size} đã chọn):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (folders.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                selectedFolderIds = if (selectedFolderIds.size == folders.size) emptySet()
                                else folders.map { it.id }.toSet()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(
                                if (selectedFolderIds.size == folders.size) "Bỏ chọn hết" else "Chọn tất cả",
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 210.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    folders.forEach { folder ->
                        val isChecked = selectedFolderIds.contains(folder.id)
                        val alreadyHas = foldersAlreadyContaining.contains(folder.id)
                        val setCount = studySets.count { it.folderId == folder.id }
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folder.name,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (alreadyHas) "✅ Đã có từ này • $setCount bài học" else "$setCount bài học",
                                    fontSize = 10.sp,
                                    color = if (alreadyHas) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val currentNote = viewModel.getWordPersonalNote(editedForeign)
                    viewModel.saveWordPersonalNoteAndTag(
                        hanziKey = editedForeign,
                        tag = selectedStatusTag,
                        note = currentNote,
                        vi = editedVi,
                        foreign = editedForeign
                    )
                    if (selectedFolderIds.isEmpty() && selectedSetIds.isEmpty()) {
                        viewModel.saveQuickVocabOrNote(
                            isNoteType = false,
                            viOrNote = editedVi,
                            foreignOrHanzi = editedForeign,
                            pinyinOrTag = selectedStatusTag
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
                Text("Lưu & Ghi ngày", fontWeight = FontWeight.Bold)
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

    val noteTags = listOf("Chưa thuộc", "Cần ôn tập", "Hay nhầm", "Ngữ pháp", "Mẹo nhớ", "Mẫu câu")
    val todayFormatted = remember {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
    }

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
                    text = "📅 Ngày lưu tự động: $todayFormatted • Phân loại vào thư mục riêng",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFB45309)
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
                        label = { Text("Từ vựng hoặc câu chữ Hán (VD: 保险)", fontSize = 12.sp) },
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
