package com.xim.facetracking.domain

import kotlin.math.abs
import kotlin.math.max

data class NormalizedRegion(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun clamp() = NormalizedRegion(left.coerceIn(0f, 1f), top.coerceIn(0f, 1f), right.coerceIn(0f, 1f), bottom.coerceIn(0f, 1f))
}

data class FacialRegions(
    val leftCheek: NormalizedRegion,
    val rightCheek: NormalizedRegion,
    val forehead: NormalizedRegion
)

object FacialRegionDeriver {
    /** Conservative approximate regions used until denser landmarks are selected. */
    fun fromFace(face: FaceSnapshot): FacialRegions? {
        if (!face.regionsVisible || face.width <= 0f || face.height <= 0f) return null
        val left = face.centerX - face.width / 2f
        val top = face.centerY - face.height / 2f
        val cheekHeight = top + face.height * 0.48f
        return FacialRegions(
            leftCheek = NormalizedRegion(left + face.width * 0.12f, top + face.height * 0.48f, left + face.width * 0.42f, cheekHeight),
            rightCheek = NormalizedRegion(left + face.width * 0.58f, top + face.height * 0.48f, left + face.width * 0.88f, cheekHeight),
            forehead = NormalizedRegion(left + face.width * 0.27f, top + face.height * 0.12f, left + face.width * 0.73f, top + face.height * 0.36f)
        )
    }
}

/** Small, dependency-free luma image used by both Android analyzers and JVM tests. */
data class LumaImage(val width: Int, val height: Int, val values: FloatArray) {
    init {
        require(width > 0 && height > 0 && values.size == width * height)
    }

    fun at(x: Int, y: Int): Float = values[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]
}

data class RegionImageMetrics(
    val medianLuma: Float,
    val highlightFraction: Float,
    val darkFraction: Float,
    val sharpness: Float
)

data class ImageQualityResult(
    val lighting: LightingMetrics,
    val sharpness: Float,
    val regionsAvailable: Boolean
)

object ImageQualityMetrics {
    fun measure(image: LumaImage, regions: FacialRegions, previous: LightingMetrics? = null): ImageQualityResult {
        val left = measureRegion(image, regions.leftCheek)
        val right = measureRegion(image, regions.rightCheek)
        val forehead = measureRegion(image, regions.forehead)
        val all = listOf(left, right, forehead)
        val medians = all.map { it.medianLuma }.sorted()
        val median = medians[medians.size / 2]
        val cheekBase = max(max(left.medianLuma, right.medianLuma), 1f)
        val imbalance = abs(left.medianLuma - right.medianLuma) / cheekBase
        val temporal = previous?.let { abs(median - it.medianLuma) / max(it.medianLuma, 1f) } ?: 0f
        return ImageQualityResult(
            lighting = LightingMetrics(
                medianLuma = median,
                highlightFraction = all.map { it.highlightFraction }.average().toFloat(),
                cheekImbalance = imbalance,
                temporalChange = temporal
            ),
            sharpness = all.map { it.sharpness }.average().toFloat(),
            regionsAvailable = all.all { it.medianLuma.isFinite() }
        )
    }

    private fun measureRegion(image: LumaImage, input: NormalizedRegion): RegionImageMetrics {
        val region = input.clamp()
        val left = (region.left * image.width).toInt().coerceIn(0, image.width - 1)
        val top = (region.top * image.height).toInt().coerceIn(0, image.height - 1)
        val right = (region.right * image.width).toInt().coerceIn(left + 1, image.width)
        val bottom = (region.bottom * image.height).toInt().coerceIn(top + 1, image.height)
        val pixels = ArrayList<Float>((right - left) * (bottom - top))
        var highlights = 0
        var dark = 0
        for (y in top until bottom) for (x in left until right) {
            val value = image.at(x, y)
            pixels += value
            if (value >= 245f) highlights++
            if (value < 55f) dark++
        }
        pixels.sort()
        val median = pixels[pixels.size / 2]
        val variance = laplacianVariance(image, left, top, right, bottom)
        return RegionImageMetrics(median, highlights.toFloat() / pixels.size, dark.toFloat() / pixels.size, variance)
    }

    private fun laplacianVariance(image: LumaImage, left: Int, top: Int, right: Int, bottom: Int): Float {
        if (right - left < 3 || bottom - top < 3) return 0f
        var sum = 0f
        var sumSquares = 0f
        var count = 0
        for (y in top + 1 until bottom - 1 step 2) for (x in left + 1 until right - 1 step 2) {
            val value = image.at(x, y) * 4f - image.at(x - 1, y) - image.at(x + 1, y) - image.at(x, y - 1) - image.at(x, y + 1)
            sum += value
            sumSquares += value * value
            count++
        }
        if (count == 0) return 0f
        val mean = sum / count
        return (sumSquares / count - mean * mean).coerceAtLeast(0f)
    }
}
