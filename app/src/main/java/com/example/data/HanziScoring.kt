package com.example.data

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** Toàn bộ hàm ở đây làm việc trong không gian 1024x1024 của dữ liệu nét chữ. */

fun polylineLength(points: List<Offset>): Float {
    var total = 0f
    for (i in 1 until points.size) total += (points[i] - points[i - 1]).getDistance()
    return total
}

fun resample(points: List<Offset>, n: Int): List<Offset> {
    if (points.isEmpty()) return emptyList()
    val total = polylineLength(points)
    if (points.size == 1 || total < 1e-3f) return List(n) { points[0] }

    val step = total / (n - 1)
    val out = ArrayList<Offset>(n)
    out.add(points[0])
    var cur = points[0]
    var idx = 1
    var needed = step
    while (out.size < n - 1 && idx < points.size) {
        val next = points[idx]
        val seg = (next - cur).getDistance()
        if (seg >= needed && seg > 0f) {
            val t = needed / seg
            val np = Offset(cur.x + (next.x - cur.x) * t, cur.y + (next.y - cur.y) * t)
            out.add(np)
            cur = np
            needed = step
        } else {
            needed -= seg
            cur = next
            idx++
        }
    }
    while (out.size < n) out.add(points.last())
    return out
}

private fun meanDistance(a: List<Offset>, b: List<Offset>): Float {
    val n = min(a.size, b.size)
    if (n == 0) return Float.MAX_VALUE
    var sum = 0f
    for (i in 0 until n) sum += (a[i] - b[i]).getDistance()
    return sum / n
}

private fun averagePoint(points: List<Offset>): Offset {
    var x = 0f
    var y = 0f
    points.forEach { x += it.x; y += it.y }
    return Offset(x / points.size, y / points.size)
}

/** Hướng của nét: so sánh vector từ trung bình vài điểm đầu tới vài điểm cuối (chống rung tay). */
private fun directionCosine(user: List<Offset>, ref: List<Offset>): Float {
    fun vector(p: List<Offset>): Offset {
        val k = if (p.size >= 12) 3 else 1
        return averagePoint(p.takeLast(k)) - averagePoint(p.take(k))
    }
    val ru = vector(ref)
    val uu = vector(user)
    val rl = ru.getDistance()
    val ul = uu.getDistance()
    // Nét rất ngắn (chấm) hoặc người dùng chỉ chạm: bỏ qua hướng.
    if (rl < 80f || ul < 20f) return 1f
    return (ru.x * uu.x + ru.y * uu.y) / (rl * ul)
}

/** Kiểm tra một nét vẽ có khớp với nét mẫu (chế độ viết theo nét, không chuẩn hoá vị trí). */
fun strokeMatches(user: List<Offset>, ref: List<Offset>): Boolean {
    if (user.isEmpty() || ref.isEmpty()) return false
    val refLen = polylineLength(ref)
    val userLen = polylineLength(user)
    if (refLen > 150f && userLen < refLen * 0.35f) return false
    if (userLen > refLen * 4f + 400f) return false

    val u = resample(user, 24)
    val r = resample(ref, 24)
    val dist = meanDistance(u, r)
    val startD = (u.first() - r.first()).getDistance()
    val endD = (u.last() - r.last()).getDistance()
    val cos = directionCosine(u, r)
    return dist < 150f && cos > 0.3f && startD < 280f && endD < 280f
}

data class FreeResult(
    val score: Int,
    val label: String,
    /** Điểm 0..1 cho từng nét người dùng vẽ (theo thứ tự). */
    val strokeScores: List<Float>,
    val userStrokeCount: Int,
    val refStrokeCount: Int,
    val shapePercent: Int,
    val directionPercent: Int,
    val balancePercent: Int,
    val notes: List<String>
)

fun ratingLabel(score: Int): String = when {
    score >= 85 -> "Xuất sắc"
    score >= 70 -> "Tốt"
    score >= 50 -> "Khá"
    score >= 30 -> "Cần luyện thêm"
    else -> "Chưa đạt"
}

/**
 * Chấm chữ viết tự do: so sánh từng nét theo thứ tự với nét mẫu sau khi chuẩn hoá
 * kích thước/vị trí chung của cả chữ (không phạt việc viết nhỏ/lệch quá nặng — phần đó tính ở "cân đối").
 */
fun scoreFreeWriting(userStrokes: List<List<Offset>>, ref: HanziChar): FreeResult {
    val strokes = userStrokes.filter { it.isNotEmpty() }
    val refCount = ref.strokeCount
    if (strokes.isEmpty()) {
        return FreeResult(0, ratingLabel(0), emptyList(), 0, refCount, 0, 0, 0, listOf("Bạn chưa viết nét nào."))
    }

    val refPts = ref.medians.flatten()
    val userPts = strokes.flatten()
    fun bounds(p: List<Offset>): List<Float> =
        listOf(p.minOf { it.x }, p.minOf { it.y }, p.maxOf { it.x }, p.maxOf { it.y })

    val rb = bounds(refPts)
    val ub = bounds(userPts)
    val refCenter = Offset((rb[0] + rb[2]) / 2f, (rb[1] + rb[3]) / 2f)
    val userCenter = Offset((ub[0] + ub[2]) / 2f, (ub[1] + ub[3]) / 2f)
    val refDim = max(rb[2] - rb[0], rb[3] - rb[1])
    val userDim = max(ub[2] - ub[0], ub[3] - ub[1])

    if (userDim < 40f) {
        return FreeResult(
            0, ratingLabel(0), strokes.map { 0f }, strokes.size, refCount, 0, 0, 0,
            listOf("Nét viết quá nhỏ, hãy viết to và rõ trong khung.")
        )
    }

    val scale = if (refDim < 100f) 1f else refDim / userDim
    fun normalize(p: List<Offset>) = p.map {
        Offset((it.x - userCenter.x) * scale + refCenter.x, (it.y - userCenter.y) * scale + refCenter.y)
    }

    val sizeRatio = if (refDim > 0f) userDim / refDim else 1f
    val sizeScore = (1f - abs(ln(sizeRatio.toDouble())).toFloat() / ln(2.2f)).coerceIn(0f, 1f)
    val posScore = (1f - (userCenter - refCenter).getDistance() / 380f).coerceIn(0f, 1f)
    val balance = 0.6f * sizeScore + 0.4f * posScore

    val total = max(strokes.size, refCount)
    val perStroke = ArrayList<Float>()
    var shapeSum = 0f
    var dirSum = 0f
    var compared = 0
    val notes = ArrayList<String>()

    for (i in 0 until strokes.size) {
        if (i >= refCount) {
            perStroke.add(0f)
            continue
        }
        val u = resample(normalize(strokes[i]), 24)
        val r = resample(ref.medians[i], 24)
        // Hình dạng tính theo cả hai chiều để nét viết ngược vẫn được nhận ra là đúng hình (chỉ sai hướng).
        val dist = min(meanDistance(u, r), meanDistance(u.reversed(), r))
        val shape = (1f - dist / 165f).coerceIn(0f, 1f)
        val cos = directionCosine(u, r)
        val dir = (cos / 0.85f).coerceIn(0f, 1f)
        val s = 0.7f * shape + 0.3f * dir
        perStroke.add(s)
        shapeSum += shape
        dirSum += dir
        compared++

        if (cos < -0.3f && shape > 0.2f) {
            notes.add("Nét ${i + 1} viết ngược hướng.")
        } else if (shape < 0.45f) {
            notes.add("Nét ${i + 1} chưa đúng hình dạng hoặc vị trí.")
        }
    }

    val raw = perStroke.sum() / total
    val score = ((0.9f * raw + 0.1f * balance) * 100f).toInt().coerceIn(0, 100)

    if (strokes.size < refCount) {
        notes.add(0, "Thiếu ${refCount - strokes.size} nét (chữ có $refCount nét, bạn viết ${strokes.size} nét).")
    } else if (strokes.size > refCount) {
        notes.add(0, "Thừa ${strokes.size - refCount} nét (chữ có $refCount nét, bạn viết ${strokes.size} nét).")
    }
    if (balance < 0.6f) {
        notes.add(if (sizeRatio < 1f) "Chữ viết hơi nhỏ hoặc lệch so với ô." else "Chữ viết hơi to hoặc lệch so với ô.")
    }
    if (notes.isEmpty()) notes.add("Rất tốt! Thứ tự, hướng và hình dạng các nét đều chuẩn.")

    return FreeResult(
        score = score,
        label = ratingLabel(score),
        strokeScores = perStroke,
        userStrokeCount = strokes.size,
        refStrokeCount = refCount,
        shapePercent = if (compared > 0) (shapeSum / compared * 100).toInt() else 0,
        directionPercent = if (compared > 0) (dirSum / compared * 100).toInt() else 0,
        balancePercent = (balance * 100).toInt(),
        notes = notes.take(5)
    )
}
