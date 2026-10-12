package com.example.data

import android.content.Context
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Nét chữ Hán (nguồn: hanzi-writer-data, không gian toạ độ 1024x1024, gốc ở góc trên-trái).
 * - [strokes]: đường viền (outline) của từng nét theo đúng thứ tự viết.
 * - [medians]: đường xương sống của từng nét, dùng để chạy animation và chấm điểm.
 */
class HanziChar(
    val char: String,
    val strokes: List<Path>,
    val medians: List<List<Offset>>
) {
    val strokeCount: Int get() = strokes.size
}

/** Một mục từ vựng dùng cho danh sách luyện viết. */
data class StudyWord(
    val hanzi: String,
    val pinyin: String,
    val meaning: String,
    /** Nguyên văn hai vế trong bộ đề, dùng để khớp trạng thái gắn sao. */
    val rawVi: String = meaning,
    val rawForeign: String = hanzi
)

fun isCjk(c: Char): Boolean = c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF

fun containsCjk(text: String): Boolean = text.any { isCjk(it) }

/** Lấy các cặp "Việt = Hán (pinyin)" từ nội dung bộ đề và giữ lại những mục có chữ Hán. */
fun parseStudyWords(rawContent: String): List<StudyWord> {
    return rawContent.split("\n").mapNotNull { line ->
        if (!line.contains("=") && !line.contains("#")) return@mapNotNull null
        val parts = line.split("=", "#").map { it.trim() }
        if (parts.size < 2) return@mapNotNull null
        val (a, b) = parts[0] to parts[1]
        val hanSide = when {
            containsCjk(a) -> a
            containsCjk(b) -> b
            else -> return@mapNotNull null
        }
        val viSide = if (hanSide == a) b else a

        val parenIdx = hanSide.indexOfAny(charArrayOf('(', '（', '[', '【'))
        val hanzi = (if (parenIdx != -1) hanSide.substring(0, parenIdx) else hanSide).trim()
        val pinyin = if (parenIdx != -1) {
            hanSide.substring(parenIdx).trim('(', ')', '（', '）', '[', ']', '【', '】', ' ')
        } else ""
        if (!containsCjk(hanzi)) return@mapNotNull null
        StudyWord(hanzi = hanzi, pinyin = pinyin, meaning = viSide, rawVi = viSide, rawForeign = hanSide)
    }.distinctBy { it.hanzi }
}

object HanziRepository {
    private const val TAG = "HanziRepository"
    private val memory = ConcurrentHashMap<String, HanziChar>()
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val mirrors = listOf(
        "https://cdn.jsdelivr.net/npm/hanzi-writer-data@2.0.1/",
        "https://unpkg.com/hanzi-writer-data@2.0.1/",
        "https://fastly.jsdelivr.net/npm/hanzi-writer-data@2.0.1/"
    )

    /** Dữ liệu gốc có trục y hướng lên (gốc 900): lật thành y' = 900 - y để khớp toạ độ màn hình. */
    private fun buildFlippedPath(svg: String): Path {
        val path = Path()
        fun fy(y: Float) = 900f - y
        for (node in PathParser().parsePathString(svg).toNodes()) {
            when (node) {
                is PathNode.MoveTo -> path.moveTo(node.x, fy(node.y))
                is PathNode.LineTo -> path.lineTo(node.x, fy(node.y))
                is PathNode.QuadTo -> path.quadraticTo(node.x1, fy(node.y1), node.x2, fy(node.y2))
                is PathNode.CurveTo -> path.cubicTo(node.x1, fy(node.y1), node.x2, fy(node.y2), node.x3, fy(node.y3))
                is PathNode.Close -> path.close()
                else -> {}
            }
        }
        return path
    }

    suspend fun load(context: Context, ch: String): Result<HanziChar> = withContext(Dispatchers.IO) {
        memory[ch]?.let { return@withContext Result.success(it) }
        try {
            val dir = File(context.applicationContext.filesDir, "hanzi").apply { mkdirs() }
            val cache = File(dir, "${ch.codePointAt(0).toString(16)}.json")

            var json: String? = if (cache.exists()) runCatching { cache.readText() }.getOrNull() else null
            if (json.isNullOrBlank()) {
                json = download(ch)
                if (json != null) runCatching { cache.writeText(json) }
            }
            if (json.isNullOrBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Không tải được dữ liệu nét chữ \"$ch\". Hãy kiểm tra kết nối mạng (chỉ cần lần đầu).")
                )
            }
            val parsed = parse(ch, json)
                ?: return@withContext Result.failure(IllegalStateException("Dữ liệu chữ \"$ch\" không hợp lệ."))
            memory[ch] = parsed
            Result.success(parsed)
        } catch (e: Exception) {
            Log.e(TAG, "load($ch) failed", e)
            Result.failure(e)
        }
    }

    private fun download(ch: String): String? {
        val encoded = URLEncoder.encode(ch, "UTF-8")
        for (base in mirrors) {
            try {
                val request = Request.Builder().url("$base$encoded.json").build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank() && body.trimStart().startsWith("{")) return body
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "mirror $base failed for $ch: ${e.message}")
            }
        }
        return null
    }

    private fun parse(ch: String, json: String): HanziChar? {
        return try {
            val obj = JSONObject(json)
            val strokesArr = obj.getJSONArray("strokes")
            val mediansArr = obj.getJSONArray("medians")
            val strokes = ArrayList<Path>(strokesArr.length())
            val medians = ArrayList<List<Offset>>(mediansArr.length())
            for (i in 0 until strokesArr.length()) {
                strokes.add(buildFlippedPath(strokesArr.getString(i)))
            }
            for (i in 0 until mediansArr.length()) {
                val line = mediansArr.getJSONArray(i)
                val pts = ArrayList<Offset>(line.length())
                for (j in 0 until line.length()) {
                    val pt = line.getJSONArray(j)
                    pts.add(Offset(pt.getDouble(0).toFloat(), 900f - pt.getDouble(1).toFloat()))
                }
                medians.add(pts)
            }
            if (strokes.isEmpty() || strokes.size != medians.size) null else HanziChar(ch, strokes, medians)
        } catch (e: Exception) {
            Log.e(TAG, "parse($ch) failed", e)
            null
        }
    }
}
