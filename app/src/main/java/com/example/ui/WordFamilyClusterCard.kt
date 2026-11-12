package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StudySet
import com.example.data.isCjk
import com.example.viewmodel.TranslationViewModel

data class WordClusterNode(
    val vi: String,
    val hanzi: String,
    val pinyin: String,
    val boiAm: String,
    val popularityStars: Int = 5
)

data class RootCharCluster(
    val rootChar: String,
    val rootPinyin: String,
    val sinoVi: String,
    val nodes: List<WordClusterNode>
)

object WordFamilyClusterRepository {

    private val sinoVietnameseMap = mapOf(
        "心" to ("xīn" to "tâm"),
        "发" to ("fā" to "phát"),
        "保" to ("bǎo" to "bảo"),
        "险" to ("xiǎn" to "hiểm"),
        "生" to ("shēng" to "sinh"),
        "工" to ("gōng" to "công"),
        "学" to ("xué" to "học"),
        "电" to ("diàn" to "điện"),
        "公" to ("gōng" to "công"),
        "司" to ("sī" to "ty"),
        "会" to ("huì" to "hội"),
        "理" to ("lǐ" to "lý"),
        "家" to ("jiā" to "gia"),
        "国" to ("guó" to "quốc"),
        "面" to ("miàn" to "diện"),
        "试" to ("shì" to "thí"),
        "文" to ("wén" to "văn"),
        "化" to ("huà" to "hóa"),
        "车" to ("chē" to "xa"),
        "机" to ("jī" to "cơ"),
        "水" to ("shuǐ" to "thủy"),
        "火" to ("huǒ" to "hỏa"),
        "手" to ("shǒu" to "thủ"),
        "口" to ("kǒu" to "khẩu"),
        "人" to ("rén" to "nhân"),
        "大" to ("dà" to "đại"),
        "小" to ("xiǎo" to "tiểu"),
        "上" to ("shàng" to "thượng"),
        "下" to ("xià" to "hạ"),
        "出" to ("chū" to "xuất"),
        "入" to ("rù" to "nhập"),
        "开" to ("kāi" to "khai"),
        "关" to ("guān" to "quan"),
        "合" to ("hé" to "hợp"),
        "同" to ("tóng" to "đồng"),
        "员" to ("yuán" to "viên"),
        "资" to ("zī" to "tư"),
        "金" to ("jīn" to "kim"),
        "安" to ("ān" to "an"),
        "全" to ("quán" to "toàn"),
        "管" to ("guǎn" to "quản"),
        "产" to ("chǎn" to "sản"),
        "品" to ("pǐn" to "phẩm"),
        "质" to ("zhì" to "chất"),
        "量" to ("liàng" to "lượng"),
        "時" to ("shí" to "thời"),
        "时" to ("shí" to "thời"),
        "间" to ("jiān" to "gian"),
        "体" to ("tǐ" to "thể"),
        "力" to ("lì" to "lực"),
        "行" to ("xíng" to "hành"),
        "动" to ("dòng" to "động"),
        "作" to ("zuò" to "tác"),
        "业" to ("yè" to "nghiệp"),
        "成" to ("chéng" to "thành"),
        "功" to ("gōng" to "công"),
        "平" to ("píng" to "bình"),
        "定" to ("dìng" to "định"),
        "正" to ("zhèng" to "chính"),
        "常" to ("cháng" to "thường"),
        "新" to ("xīn" to "tân"),
        "老" to ("lǎo" to "lão"),
        "好" to ("hǎo" to "hảo"),
        "多" to ("duō" to "đa"),
        "少" to ("shǎo" to "thiểu"),
        "长" to ("cháng" to "trường"),
        "短" to ("duǎn" to "đoản"),
        "高" to ("gāo" to "cao"),
        "低" to ("dī" to "đê"),
        "天" to ("tiān" to "thiên"),
        "地" to ("dì" to "địa"),
        "日" to ("rì" to "nhật"),
        "月" to ("yuè" to "nguyệt"),
        "年" to ("nián" to "niên"),
        "祝" to ("zhù" to "chúc"),
        "庆" to ("qìng" to "khánh"),
        "圣" to ("shèng" to "thánh"),
        "诞" to ("dàn" to "đản"),
        "快" to ("kuài" to "khoái"),
        "乐" to ("lè" to "lạc")
    )

    private val curatedClusters: Map<String, RootCharCluster> = mapOf(
        "心" to RootCharCluster(
            rootChar = "心",
            rootPinyin = "xīn",
            sinoVi = "tâm",
            nodes = listOf(
                WordClusterNode("tâm trạng", "心情", "xīnqíng", "xin chính", 5),
                WordClusterNode("tâm lý", "心理", "xīnlǐ", "xin lỉ", 5),
                WordClusterNode("tâm nguyện", "心愿", "xīnyuàn", "xin uyền", 4),
                WordClusterNode("tâm sự", "心事", "xīnshì", "xin sử", 5),
                WordClusterNode("tâm huyết", "心血", "xīnxuè", "xin xuề", 4),
                WordClusterNode("tâm hồn", "心灵", "xīnlíng", "xin lính", 4),
                WordClusterNode("tâm ý", "心意", "xīnyì", "xin ì", 4),
                WordClusterNode("tâm thế", "心态", "xīntài", "xin thài", 5),
                WordClusterNode("trong lòng", "心中", "xīnzhōng", "xin trung", 5),
                WordClusterNode("nhịp tim", "心跳", "xīntiào", "xin thèo", 4),
                WordClusterNode("xót xa", "心疼", "xīnténg", "xin thắng", 5),
                WordClusterNode("rung động", "心动", "xīndòng", "xin tùng", 5)
            )
        ),
        "发" to RootCharCluster(
            rootChar = "发",
            rootPinyin = "fā",
            sinoVi = "phát",
            nodes = listOf(
                WordClusterNode("phát hiện", "发现", "fāxiàn", "pha xiền", 5),
                WordClusterNode("phát triển", "发展", "fāzhǎn", "pha trản", 5),
                WordClusterNode("phát âm", "发音", "fāyīn", "pha in", 5),
                WordClusterNode("phát minh", "发明", "fāmíng", "pha mính", 4),
                WordClusterNode("phát tài", "发财", "fācái", "pha xái", 5),
                WordClusterNode("phát biểu", "发表", "fābiǎo", "pha pẻo", 4),
                WordClusterNode("phát ngôn", "发言", "fāyán", "pha diến", 4),
                WordClusterNode("phát sáng", "发光", "fāguāng", "pha quang", 4),
                WordClusterNode("xảy ra", "发生", "fāshēng", "pha sâng", 5),
                WordClusterNode("bị sốt", "发烧", "fāshāo", "pha sao", 5),
                WordClusterNode("gửi đi", "发送", "fāsòng", "pha xùng", 5),
                WordClusterNode("nổi nóng", "发火", "fāhuǒ", "pha hủa", 5)
            )
        ),
        "保" to RootCharCluster(
            rootChar = "保",
            rootPinyin = "bǎo",
            sinoVi = "bảo",
            nodes = listOf(
                WordClusterNode("bảo hiểm", "保险", "bǎoxiǎn", "pảo xiển", 5),
                WordClusterNode("bảo vệ", "保护", "bǎohù", "pảo hu", 5),
                WordClusterNode("bảo đảm", "保证", "bǎozhèng", "pảo trâng", 5),
                WordClusterNode("bảo trì", "保养", "bǎoyǎng", "pảo dảng", 4),
                WordClusterNode("bảo quản", "保管", "bǎoguǎn", "pảo quản", 4),
                WordClusterNode("bảo tồn", "保存", "bǎocún", "pảo xuấn", 5),
                WordClusterNode("bảo mật", "保密", "bǎomì", "pảo mi", 4),
                WordClusterNode("bảo lưu", "保留", "bǎoliú", "pảo liếu", 4),
                WordClusterNode("bảo vệ (BV)", "保安", "bǎo'ān", "pảo an", 5),
                WordClusterNode("bảo hành", "保修", "bǎoxiū", "pảo xiêu", 5),
                WordClusterNode("giữ ấm", "保暖", "bǎonuǎn", "pảo noản", 4),
                WordClusterNode("bảo trọng", "保重", "bǎozhòng", "pảo trung", 5)
            )
        ),
        "险" to RootCharCluster(
            rootChar = "险",
            rootPinyin = "xiǎn",
            sinoVi = "hiểm",
            nodes = listOf(
                WordClusterNode("bảo hiểm", "保险", "bǎoxiǎn", "pảo xiển", 5),
                WordClusterNode("nguy hiểm", "危险", "wēixiǎn", "uây xiển", 5),
                WordClusterNode("mạo hiểm", "冒险", "màoxiǎn", "mao xiển", 5),
                WordClusterNode("rủi ro", "风险", "fēngxiǎn", "phâng xiển", 5),
                WordClusterNode("bảo hiểm XH", "社保", "shèbǎo", "sưa pảo", 5),
                WordClusterNode("bảo hiểm y tế", "医保", "yībǎo", "y pảo", 5),
                WordClusterNode("bảo hiểm xe", "车险", "chēxiǎn", "trưa xiển", 4),
                WordClusterNode("thoát hiểm", "脱险", "tuōxiǎn", "thua xiển", 4),
                WordClusterNode("cứu hộ", "抢险", "qiǎngxiǎn", "chẻng xiển", 4),
                WordClusterNode("hiểm ác", "阴险", "yīnxiǎn", "in xiển", 4)
            )
        ),
        "工" to RootCharCluster(
            rootChar = "工",
            rootPinyin = "gōng",
            sinoVi = "công",
            nodes = listOf(
                WordClusterNode("công việc", "工作", "gōngzuò", "cung chua", 5),
                WordClusterNode("công nhân", "工人", "gōngrén", "cung rấn", 5),
                WordClusterNode("tiền lương", "工资", "gōngzī", "cung chư", 5),
                WordClusterNode("nhà máy", "工厂", "gōngchǎng", "cung trảng", 5),
                WordClusterNode("công cụ", "工具", "gōngjù", "cung chuy", 5),
                WordClusterNode("công trình", "工程", "gōngchéng", "cung trấng", 4),
                WordClusterNode("công nghiệp", "工业", "gōngyè", "cung gia", 4),
                WordClusterNode("tăng ca", "加班工", "jiābāngōng", "cha pan cung", 5),
                WordClusterNode("nhân viên", "员工", "yuángōng", "duyén cung", 5),
                WordClusterNode("khởi công", "开工", "kāigōng", "khai cung", 5),
                WordClusterNode("tan ca", "收工", "shōugōng", "sâu cung", 4),
                WordClusterNode("tay nghề", "手工", "shǒugōng", "sẩu cung", 4)
            )
        ),
        "生" to RootCharCluster(
            rootChar = "生",
            rootPinyin = "shēng",
            sinoVi = "sinh",
            nodes = listOf(
                WordClusterNode("cuộc sống", "生活", "shēnghuó", "sâng huố", 5),
                WordClusterNode("sinh nhật", "生日", "shēngrì", "sâng rư", 5),
                WordClusterNode("bị ốm", "生病", "shēngbìng", "sâng ping", 5),
                WordClusterNode("tức giận", "生气", "shēngqì", "sâng chi", 5),
                WordClusterNode("sản xuất", "生产", "shēngchǎn", "sâng trản", 5),
                WordClusterNode("kinh doanh", "生意", "shēngyi", "sâng y", 5),
                WordClusterNode("sinh mệnh", "生命", "shēngmìng", "sâng minh", 4),
                WordClusterNode("học sinh", "学生", "xuésheng", "xuế sâng", 5),
                WordClusterNode("bác sĩ", "医生", "yīshēng", "y sâng", 5),
                WordClusterNode("xảy ra", "发生", "fāshēng", "pha sâng", 5),
                WordClusterNode("chào đời", "出生", "chūshēng", "tru sâng", 4),
                WordClusterNode("người lạ", "陌生", "mòshēng", "mua sâng", 4)
            )
        ),
        "学" to RootCharCluster(
            rootChar = "学",
            rootPinyin = "xué",
            sinoVi = "học",
            nodes = listOf(
                WordClusterNode("học tập", "学习", "xuéxí", "xuế xí", 5),
                WordClusterNode("học sinh", "学生", "xuésheng", "xuế sâng", 5),
                WordClusterNode("trường học", "学校", "xuéxiào", "xuế xiao", 5),
                WordClusterNode("đại học", "大学", "dàxué", "ta xuế", 5),
                WordClusterNode("bạn cùng lớp", "同学", "tóngxué", "thúng xuế", 5),
                WordClusterNode("du học", "留学", "liúxué", "liếu xuế", 5),
                WordClusterNode("học phí", "学费", "xuéfèi", "xuế phây", 4),
                WordClusterNode("học viện", "学院", "xuéyuàn", "xuế duyên", 4),
                WordClusterNode("khoa học", "科学", "kēxué", "khưa xuế", 4),
                WordClusterNode("tự học", "自学", "zìxué", "chư xuế", 5)
            )
        ),
        "电" to RootCharCluster(
            rootChar = "电",
            rootPinyin = "diàn",
            sinoVi = "điện",
            nodes = listOf(
                WordClusterNode("điện thoại", "电话", "diànhuà", "tiên hoa", 5),
                WordClusterNode("máy tính", "电脑", "diànnǎo", "tiên nảo", 5),
                WordClusterNode("ti vi", "电视", "diànshì", "tiên sư", 5),
                WordClusterNode("điện ảnh", "电影", "diànyǐng", "tiên ỉnh", 5),
                WordClusterNode("thang máy", "电梯", "diàntī", "tiên thi", 5),
                WordClusterNode("pin / ắc quy", "电池", "diànchí", "tiên trứ", 5),
                WordClusterNode("sạc điện", "充电", "chōngdiàn", "trung tiên", 5),
                WordClusterNode("mất điện", "停电", "tíngdiàn", "thính tiên", 5),
                WordClusterNode("đồ điện", "家电", "jiādiàn", "cha tiên", 4),
                WordClusterNode("thương mại ĐT", "电商", "diànshāng", "tiên sang", 4)
            )
        ),
        "祝" to RootCharCluster(
            rootChar = "祝",
            rootPinyin = "zhù",
            sinoVi = "chúc",
            nodes = listOf(
                WordClusterNode("chúc phúc / ban phước", "祝福", "zhùfú", "tru phú", 5),
                WordClusterNode("chúc mừng", "祝贺", "zhùhè", "tru hưa", 5),
                WordClusterNode("kỷ niệm / ăn mừng", "庆祝", "qìngzhù", "chinh tru", 5),
                WordClusterNode("mừng thọ", "祝寿", "zhùshòu", "tru sâu", 4),
                WordClusterNode("ước nguyện / chúc nguyện", "祝愿", "zhùyuàn", "tru duyên", 5),
                WordClusterNode("lời chúc tốt đẹp", "祝福语", "zhùfúyǔ", "tru phú ủy", 4),
                WordClusterNode("nâng cốc chúc rượu", "祝酒", "zhùjiǔ", "tru chiểu", 4),
                WordClusterNode("chúc mừng chiến thắng", "祝捷", "zhùjié", "tru chiế", 4)
            )
        ),
        "快" to RootCharCluster(
            rootChar = "快",
            rootPinyin = "kuài",
            sinoVi = "khoái",
            nodes = listOf(
                WordClusterNode("vui vẻ / hạnh phúc", "快乐", "kuàilè", "khoai lưa", 5),
                WordClusterNode("nhanh chóng", "快速", "kuàisù", "khoai xu", 5),
                WordClusterNode("chuyển phát nhanh", "快递", "kuàidì", "khoai ti", 5),
                WordClusterNode("sảng khoái / vui sướng", "愉快", "yúkuài", "duý khoai", 5),
                WordClusterNode("mau chóng / gấp", "赶快", "gǎnkuài", "cản khoai", 5),
                WordClusterNode("nhanh nhẹn", "快活", "kuàihuo", "khoai huố", 4),
                WordClusterNode("đồ ăn nhanh", "快餐", "kuàicān", "khoai xan", 5),
                WordClusterNode("tàu tốc hành", "快车", "kuàichē", "khoai trưa", 4)
            )
        )
    )

    val popularRootChars = listOf(
        "心", "发", "保", "险", "工", "生", "学", "电", "公", "会", "理", "家", "国", "面", "文", "车", "机", "人", "大", "上", "出", "开"
    )

    /**
     * Converts standard Pinyin into easy-to-read Vietnamese phonetic approximation ("bồi âm")
     * matching the blue column in image_9b89f242.png & image_a9184395.png.
     */
    fun pinyinToVietnameseBoiAm(pinyin: String): String {
        if (pinyin.isBlank()) return ""
        val tokens = TwoWayTranslatorEngine.splitPinyinIntoSyllables(pinyin)
        if (tokens.isEmpty()) return pinyin.lowercase()
        return tokens.joinToString(" ") { syl ->
            when (syl) {
                "xin" -> "xin"
                "qing" -> "chính"
                "li" -> "lỉ"
                "yuan" -> "uyền"
                "shi" -> "sử"
                "xue" -> "xuề"
                "ling" -> "lính"
                "yi" -> "ì"
                "tai" -> "thài"
                "zhong" -> "trung"
                "tiao" -> "thèo"
                "teng" -> "thắng"
                "dong" -> "tùng"
                "fa" -> "pha"
                "xian" -> "xiền"
                "zhan" -> "trản"
                "yin" -> "in"
                "ming" -> "mính"
                "cai" -> "xái"
                "biao" -> "pẻo"
                "yan" -> "diến"
                "guang" -> "quang"
                "sheng" -> "sâng"
                "shao" -> "sao"
                "song" -> "xùng"
                "huo" -> "hủa"
                "bao" -> "pảo"
                "gong" -> "cung"
                "si" -> "sư"
                "hui" -> "huây"
                "jia" -> "cha"
                "guo" -> "cuố"
                "ren" -> "rấn"
                "da" -> "ta"
                "xiao" -> "xẻo"
                "shang" -> "sang"
                "xia" -> "xia"
                "chu" -> "tru"
                "ru" -> "ru"
                "kai" -> "khai"
                "guan" -> "quan"
                else -> {
                    syl.replace("zh", "tr")
                        .replace("ch", "tr")
                        .replace("sh", "s")
                        .replace("q", "ch")
                        .replace("x", "x")
                        .replace("j", "ch")
                        .replace("z", "ch")
                        .replace("c", "x")
                        .replace("b", "p")
                        .replace("p", "ph")
                        .replace("d", "t")
                        .replace("t", "th")
                        .replace("g", "c")
                        .replace("k", "kh")
                        .replace("r", "r")
                        .replace("eng", "âng")
                        .replace("ang", "ang")
                        .replace("ong", "ung")
                        .replace("ian", "iên")
                        .replace("uan", "oan")
                }
            }
        }
    }

    fun getClusterForCharacter(
        targetChar: String,
        fallbackPinyin: String,
        allStudySets: List<StudySet>
    ): RootCharCluster {
        val cleanChar = targetChar.trim().take(1)
        curatedClusters[cleanChar]?.let { return it }

        val meta = sinoVietnameseMap[cleanChar]
        val rootPy = meta?.first ?: fallbackPinyin.trim().split("\\s+".toRegex()).firstOrNull().orEmpty().ifBlank { cleanChar }
        val sino = meta?.second ?: "hán tự"

        val dynamicNodes = ArrayList<WordClusterNode>(12)
        val seenHanzi = HashSet<String>()

        for (set in allStudySets) {
            if (dynamicNodes.size >= 12) break
            if (!set.rawContent.contains(cleanChar)) continue
            for (line in set.rawContent.lineSequence()) {
                if (dynamicNodes.size >= 12) break
                if (!line.contains(cleanChar)) continue
                val item = parseEditableVocabItems(line).firstOrNull() ?: continue
                val hz = item.hanzi.trim()
                if (hz.length in 2..4 && hz.contains(cleanChar) && seenHanzi.add(hz)) {
                    val shortVi = item.vi.split(",", ";", "/").first().trim().lowercase()
                    dynamicNodes.add(
                        WordClusterNode(
                            vi = shortVi,
                            hanzi = hz,
                            pinyin = item.pinyin.lowercase(),
                            boiAm = pinyinToVietnameseBoiAm(item.pinyin),
                            popularityStars = if (dynamicNodes.size < 5) 5 else 4
                        )
                    )
                }
            }
        }

        if (dynamicNodes.isEmpty()) {
            return curatedClusters["心"]!!
        }

        return RootCharCluster(
            rootChar = cleanChar,
            rootPinyin = rootPy,
            sinoVi = sino,
            nodes = dynamicNodes
        )
    }

    /**
     * Estimates vocabulary popularity rating (1..5 stars + frequency %)
     */
    fun evaluateWordPopularity(hanzi: String, allStudySets: List<StudySet>): Triple<Int, Int, String> {
        val clean = hanzi.trim()
        var setHits = 0
        for (set in allStudySets) {
            if (set.rawContent.contains(clean)) {
                setHits++
            }
        }
        return when {
            setHits >= 4 || clean.length <= 2 -> Triple(5, 96, "Rất phổ biến • Giao tiếp & HSK cốt lõi")
            setHits >= 2 -> Triple(5, 89, "Phổ biến cao • Thường gặp trong đời sống")
            clean.length <= 3 -> Triple(4, 82, "Thông dụng • Từ vựng ứng dụng thực tế")
            else -> Triple(4, 75, "Chuyên ngành / Ngữ cảnh mở rộng")
        }
    }
}

@Composable
fun WordFamilyClusterSectionCard(
    currentWordHanzi: String,
    currentWordPinyin: String,
    allStudySets: List<StudySet>,
    viewModel: TranslationViewModel,
    onSelectClusterWord: (WordClusterNode) -> Unit,
    onRequestMultiFolderSave: (vi: String, foreign: String) -> Unit
) {
    val starredPairs by viewModel.allStarredPairs.collectAsState()
    val wordChars = remember(currentWordHanzi) {
        currentWordHanzi.filter { isCjk(it) }.map { it.toString() }.distinct()
    }
    var selectedRootChar by remember(currentWordHanzi) {
        mutableStateOf(wordChars.firstOrNull() ?: "心")
    }

    val cluster = remember(selectedRootChar, currentWordPinyin, allStudySets) {
        WordFamilyClusterRepository.getClusterForCharacter(selectedRootChar, currentWordPinyin, allStudySets)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE7E5E4))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Section Title & Description
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🌿 Học Theo Cụm Nhiều Từ (Sơ Đồ Gốc Chữ Hán)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1C1917)
                    )
                    Text(
                        text = "Bẻ khóa Tiếng Trung: Từ 1 chữ gốc mở rộng ra cả cụm từ vựng kèm Pinyin & bồi âm",
                        fontSize = 11.sp,
                        color = Color(0xFF78716C)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B))
                ) {
                    Text(
                        text = "${cluster.nodes.size} cụm từ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF92400E),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Character Switcher Pills (Characters in current word + Popular Root Characters like 心, 发, 保, 工...)
            val chipChars = remember(wordChars) {
                (wordChars + WordFamilyClusterRepository.popularRootChars).distinct()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                chipChars.forEach { ch ->
                    val isSelected = ch == selectedRootChar
                    val isFromCurrentWord = wordChars.contains(ch)
                    Surface(
                        modifier = Modifier.clickable { selectedRootChar = ch },
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            isSelected -> Color(0xFF9A3412)
                            isFromCurrentWord -> Color(0xFFFEF3C7)
                            else -> Color(0xFFF5F5F4)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF9A3412)
                            else if (isFromCurrentWord) Color(0xFFF59E0B)
                            else Color(0xFFE7E5E4)
                        )
                    ) {
                        Text(
                            text = if (isFromCurrentWord) "★ Gốc $ch" else "Gốc $ch",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else Color(0xFF292524),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Notebook Grid Mind-Map Diagram (Matches image_9b89f242.png & image_a9184395.png)
            val rowHeightDp = 42.dp
            val totalDiagramHeight = (cluster.nodes.size * 42).coerceAtLeast(220).dp
            val tableScrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFBF8EE)) // Warm notebook paper
                    .drawBehind {
                        val gridStep = 22.dp.toPx()
                        val lineColor = Color(0xFFE8E2D0)
                        var x = 0f
                        while (x < size.width) {
                            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                            x += gridStep
                        }
                        var y = 0f
                        while (y < size.height) {
                            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                            y += gridStep
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(totalDiagramHeight),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Root Character Column (e.g. 心 / xīn / tâm or 祝 / zhù / chúc)
                        Column(
                            modifier = Modifier
                                .width(64.dp)
                                .clickable { viewModel.speakText(cluster.rootChar, "zh") },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = cluster.rootChar,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = cluster.rootPinyin,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626), // Red pinyin
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFDE68A) // Yellow highlighter tag matching screenshot
                            ) {
                                Text(
                                    text = cluster.sinoVi,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF1E293B),
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Fan of Connecting Lines Canvas
                        Canvas(
                            modifier = Modifier
                                .width(32.dp)
                                .fillMaxHeight()
                        ) {
                            val count = cluster.nodes.size.coerceAtLeast(1)
                            val startPt = Offset(2f, size.height / 2f)
                            val slotHeight = size.height / count
                            for (i in 0 until count) {
                                val targetY = slotHeight * i + slotHeight / 2f
                                drawLine(
                                    color = Color(0xFF1E293B),
                                    start = startPt,
                                    end = Offset(size.width - 2f, targetY),
                                    strokeWidth = 2.4f
                                )
                            }
                        }

                        // Right Compound Word Rows - Horizontally Scrollable so user can drag & inspect full meanings
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .horizontalScroll(tableScrollState)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxHeight(),
                                verticalArrangement = Arrangement.SpaceEvenly
                            ) {
                                cluster.nodes.forEach { node ->
                                    val fullForeign = "${node.hanzi} (${node.pinyin})"
                                    val isNodeStarred = starredPairs.any {
                                        it.foreign.contains(node.hanzi)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .height(rowHeightDp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.speakText(node.hanzi, "zh")
                                                onSelectClusterWord(node)
                                            }
                                            .padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 1. Nghĩa Tiếng Việt (Full readable width, up to 2 lines, no truncation)
                                        Text(
                                            text = node.vi,
                                            fontSize = 12.5.sp,
                                            lineHeight = 14.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1E293B),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .widthIn(min = 122.dp, max = 190.dp)
                                                .padding(end = 8.dp)
                                        )
                                        // 2. Chữ Hán
                                        Text(
                                            text = node.hanzi,
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF0F172A),
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier
                                                .widthIn(min = 58.dp)
                                                .padding(end = 8.dp)
                                        )
                                        // 3. Pinyin có dấu (Red)
                                        Text(
                                            text = node.pinyin,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFDC2626),
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier
                                                .widthIn(min = 82.dp)
                                                .padding(end = 8.dp)
                                        )
                                        // 4. Bồi âm tiếng Việt (Blue)
                                        Text(
                                            text = node.boiAm,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1D4ED8),
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier
                                                .widthIn(min = 84.dp)
                                                .padding(end = 6.dp)
                                        )
                                        // 5. Star & Save quick actions
                                        IconButton(
                                            onClick = { viewModel.toggleStar(node.vi, fullForeign, "zh") },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                if (isNodeStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = "Đánh sao",
                                                tint = if (isNodeStarred) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onRequestMultiFolderSave(node.vi, fullForeign) },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.BookmarkAdd,
                                                contentDescription = "Lưu thư mục",
                                                tint = Color(0xFF0284C7),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💡 Kéo ngang bảng để xem hết nghĩa • Chạm vào từ để phát âm & tra sâu",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Text(
                            text = "Bẻ Khóa Tiếng Trung",
                            fontSize = 11.5.sp,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2563EB),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SubstitutableWordsAndSentencesCard(
    currentWordHanzi: String,
    currentWordVi: String,
    substitutes: List<SubstitutableWordItem>,
    viewModel: TranslationViewModel,
    onSelectSubstitute: (SubstitutableWordItem) -> Unit,
    onRequestMultiFolderSave: (vi: String, foreign: String) -> Unit
) {
    val starredPairs by viewModel.allStarredPairs.collectAsState()
    if (substitutes.isEmpty()) return

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🔄 Từ Vựng & Mẫu Câu Có Thể Thay Thế Cho “$currentWordHanzi”",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1C1917)
                    )
                    Text(
                        text = "So sánh mức độ phổ biến và câu thay thế tương đương trong giao tiếp thực tế",
                        fontSize = 11.sp,
                        color = Color(0xFF78716C)
                    )
                }
            }

            substitutes.forEach { sub ->
                val fullForeign = if (sub.pinyin.isNotBlank()) "${sub.hanzi} (${sub.pinyin})" else sub.hanzi
                val isStarred = starredPairs.any { it.foreign.contains(sub.hanzi) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSubstitute(sub) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF8F5)),
                    border = BorderStroke(1.dp, Color(0xFFE7E5E4))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = sub.hanzi,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF1C1917)
                                )
                                if (sub.pinyin.isNotBlank()) {
                                    Text(
                                        text = "[${sub.pinyin}]",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF9A3412)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFEF3C7),
                                    border = BorderStroke(1.dp, Color(0xFFF59E0B))
                                ) {
                                    Text(
                                        text = "⭐".repeat(sub.popularityStars.coerceIn(1, 5)) + " " + sub.popularityLabel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF92400E),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = { viewModel.speakText(sub.hanzi, "zh") },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.VolumeUp,
                                        contentDescription = "Nghe",
                                        tint = Color(0xFF9A3412),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.toggleStar(sub.vi, fullForeign, "zh") },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Gắn sao",
                                        tint = if (isStarred) Color(0xFFF59E0B) else Color(0xFF78716C),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onRequestMultiFolderSave(sub.vi, fullForeign) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.BookmarkAdd,
                                        contentDescription = "Lưu vào thư mục",
                                        tint = Color(0xFF0284C7),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Nghĩa: ${sub.vi}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF292524)
                        )

                        if (sub.nuanceNote.isNotBlank()) {
                            Text(
                                text = "💡 ${sub.nuanceNote}",
                                fontSize = 11.5.sp,
                                color = Color(0xFF57534E)
                            )
                        }

                        if (sub.replaceableSentenceZh.isNotBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE7E5E4))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = { viewModel.speakText(sub.replaceableSentenceZh, "zh") },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.VolumeUp,
                                            contentDescription = "Đọc câu thay thế",
                                            tint = Color(0xFF0284C7),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Câu thay thế: ${sub.replaceableSentenceZh}",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                        if (sub.replaceableSentenceVi.isNotBlank()) {
                                            Text(
                                                text = sub.replaceableSentenceVi,
                                                fontSize = 11.5.sp,
                                                color = Color(0xFF475569)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            onRequestMultiFolderSave(
                                                sub.replaceableSentenceVi.ifBlank { sub.vi },
                                                sub.replaceableSentenceZh
                                            )
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.BookmarkAdd,
                                            contentDescription = "Lưu câu thay thế",
                                            tint = Color(0xFFD97706),
                                            modifier = Modifier.size(16.dp)
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
