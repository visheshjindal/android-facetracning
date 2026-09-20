package com.xim.facetracking.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Pure polygon sampling on the original frame's 0–255 luma values. No clipping of ROIs. */
object CheekLightMeasurement {
    fun measure(image: LumaImage, left: List<MeshPoint>, right: List<MeshPoint>): CheekLighting {
        val l = region(image, left)
        val r = region(image, right)
        return CheekLighting(l, r, if (l == null || r == null) null else
            abs(l.medianLuma - r.medianLuma) / max(max(l.medianLuma, r.medianLuma), 1f))
    }

    fun region(image: LumaImage, polygon: List<MeshPoint>): CheekLight? {
        if (polygon.size < 3 || polygon.any { !it.x.isFinite() || !it.y.isFinite() ||
                it.x < 0 || it.y < 0 || it.x > image.width || it.y > image.height }) return null
        val area = polygon.indices.sumOf { i ->
            val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]
            (a.x * b.y - b.x * a.y).toDouble()
        }
        if (abs(area) < 1e-4) return null
        val histogram = IntArray(256)
        var count = 0; var dark = 0; var bright = 0
        val left = floor(polygon.minOf { it.x }).toInt()
        val right = ceil(polygon.maxOf { it.x }).toInt().coerceAtMost(image.width)
        val top = floor(polygon.minOf { it.y }).toInt()
        val bottom = ceil(polygon.maxOf { it.y }).toInt().coerceAtMost(image.height)
        for (y in top until bottom) for (x in left until right) {
            if (!contains(polygon, x + .5f, y + .5f)) continue
            val value = image.at(x, y)
            if (!value.isFinite() || value !in 0f..255f) return null
            histogram[value.toInt()]++
            count++
            if (value < 55f) dark++
            if (value >= 245f) bright++
        }
        if (count == 0) return null
        fun quantile(index: Int): Int {
            var total = 0
            for (i in histogram.indices) { total += histogram[i]; if (total > index) return i }
            return 255
        }
        val median = (quantile((count - 1) / 2) + quantile(count / 2)) / 2f
        return CheekLight(median, dark.toFloat() / count, bright.toFloat() / count, count)
    }

    private fun contains(p: List<MeshPoint>, x: Float, y: Float): Boolean {
        var inside = false
        var previous = p.last()
        for (current in p) {
            if ((current.y > y) != (previous.y > y) &&
                x < (previous.x - current.x) * (y - current.y) / (previous.y - current.y) + current.x) inside = !inside
            previous = current
        }
        return inside
    }
}

data class FaceBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

object MeshAssociation {
    fun select(primary: FaceBounds, meshes: List<FaceBounds>): Int? = meshes.indices
        .maxByOrNull { overlap(primary, meshes[it]) }
        ?.takeIf { overlap(primary, meshes[it]) >= 0.5f }

    private fun overlap(a: FaceBounds, b: FaceBounds): Float {
        val intersection = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0f) *
            (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0f)
        val union = (a.right - a.left).coerceAtLeast(0f) * (a.bottom - a.top).coerceAtLeast(0f) +
            (b.right - b.left).coerceAtLeast(0f) * (b.bottom - b.top).coerceAtLeast(0f) - intersection
        return if (union > 0f) intersection / union else 0f
    }
}
