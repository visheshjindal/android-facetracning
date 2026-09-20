package com.xim.facetracking.domain

/** Pixel coordinates for algorithms; normalized preview coordinates only in FaceMeshGeometry. */
data class MeshPoint(val x: Float, val y: Float)
data class MeshTriangle(val a: Int, val b: Int, val c: Int)
data class MeshEdge(val a: Int, val b: Int)
data class FaceMeshGeometry(val points: List<MeshPoint>, val edges: List<MeshEdge>)
data class CheekLight(val medianLuma: Float, val darkFraction: Float, val highlightFraction: Float, val pixelCount: Int)
data class CheekLighting(val left: CheekLight?, val right: CheekLight?, val imbalance: Float?)

/** Fixed ML Kit / MediaPipe 468-point topology. Anatomical sides, before preview mirroring.
 * Region checked against google-ai-edge/mediapipe canonical_face_model.obj.
 * The display includes the nose; sampling polygons include only inset cheeks.
 */
object MidFaceRegions {
    val rightCheekIndices = listOf(117, 118, 119, 100, 203, 205, 187, 123)
    val leftCheekIndices = listOf(346, 347, 348, 329, 423, 425, 411, 352)
    private val midFaceIndices = setOf(1, 2, 3, 4, 5, 6, 19, 20, 31, 36, 44, 45, 47, 48, 49, 50, 51, 59, 60, 64, 75, 79, 94, 98, 99, 100, 101, 102, 111, 114, 115, 117, 118, 119, 120, 121, 122, 123, 125, 126, 128, 129, 131, 134, 141, 142, 166, 174, 187, 188, 195, 196, 197, 198, 203, 205, 209, 217, 218, 219, 220, 228, 229, 230, 231, 232, 233, 235, 236, 237, 238, 239, 240, 241, 242, 248, 250, 261, 266, 274, 275, 277, 278, 279, 280, 281, 289, 290, 294, 305, 309, 327, 328, 329, 330, 331, 340, 343, 344, 346, 347, 348, 349, 350, 351, 352, 354, 355, 357, 358, 360, 363, 370, 371, 392, 399, 411, 412, 419, 420, 423, 425, 429, 437, 438, 439, 440, 448, 449, 450, 451, 452, 453, 455, 456, 457, 458, 459, 460, 461, 462)

    fun edges(triangles: List<MeshTriangle>): List<MeshEdge> = triangles
        .filter { it.a in midFaceIndices && it.b in midFaceIndices && it.c in midFaceIndices }
        .flatMap { listOf(edge(it.a, it.b), edge(it.b, it.c), edge(it.c, it.a)) }.distinct()

    private fun edge(a: Int, b: Int) = MeshEdge(minOf(a, b), maxOf(a, b))

    fun cheek(points: List<MeshPoint>, left: Boolean): List<MeshPoint> {
        val ids = if (left) leftCheekIndices else rightCheekIndices
        if (ids.any { it !in points.indices }) return emptyList()
        val polygon = ids.map { points[it] }
        val cx = polygon.map { it.x }.average().toFloat()
        val cy = polygon.map { it.y }.average().toFloat()
        return polygon.map { MeshPoint(cx + (it.x - cx) * 0.88f, cy + (it.y - cy) * 0.88f) }
    }
}
