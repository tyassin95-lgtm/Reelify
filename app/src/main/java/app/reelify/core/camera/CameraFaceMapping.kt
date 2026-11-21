package app.reelify.core.camera

import app.reelify.core.media.engine.FaceUniform

/** A normalized point/box in ML Kit's upright (rotation-corrected) image space, origin top-left. */
data class UprightBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Maps ML Kit face detections into the camera OES texture space used by the live beauty shader.
 *
 * Why: the live shader samples the camera texture in SENSOR (buffer) orientation, while ML Kit
 * reports faces in UPRIGHT (display) orientation. Reelify 1.x passed display-space coordinates
 * straight to the shader, so on phones (sensor rotated 90°/270°) the face mask landed in the wrong
 * place and face-localized smoothing ran at ~35 % strength — one root cause of "beauty does nothing".
 *
 * Both ImageAnalysis and Preview buffers come from the same sensor at 16:9, so normalized buffer
 * coordinates line up (approximation: tiny crop differences between streams are ignored).
 * Texture space: s = bufferU, t = 1 - bufferV (SurfaceTexture's matrix flips Y); radii are
 * aspect-corrected (x scaled by the texture aspect) to match the shader's A() helper.
 */
object CameraFaceMapping {

    /** Inverse of the analysis rotation: upright normalized → buffer normalized (both top-left origin). */
    fun uprightToBuffer(x: Float, y: Float, rotationDegrees: Int): Pair<Float, Float> = when (((rotationDegrees % 360) + 360) % 360) {
        90 -> y to (1f - x)
        180 -> (1f - x) to (1f - y)
        270 -> (1f - y) to x
        else -> x to y
    }

    /** Direction of "up on the face" in texture space for a given analysis rotation. */
    fun upVector(rotationDegrees: Int): Pair<Float, Float> = when (((rotationDegrees % 360) + 360) % 360) {
        90 -> -1f to 0f
        180 -> 0f to -1f
        270 -> 1f to 0f
        else -> 0f to 1f
    }

    fun bufferToTexture(u: Float, v: Float): Pair<Float, Float> = u to (1f - v)

    fun toTexture(x: Float, y: Float, rotationDegrees: Int): Pair<Float, Float> {
        val (u, v) = uprightToBuffer(x, y, rotationDegrees)
        return bufferToTexture(u, v)
    }

    /**
     * @param box face box in upright normalized coordinates.
     * @param leftEye/rightEye upright normalized eye landmarks or null.
     * @param textureAspect buffer width / height in sensor orientation (e.g. 1920/1080).
     */
    fun faceUniform(box: UprightBox, leftEye: Pair<Float, Float>?, rightEye: Pair<Float, Float>?, rotationDegrees: Int, textureAspect: Float): FaceUniform {
        val corners = listOf(box.left to box.top, box.right to box.top, box.left to box.bottom, box.right to box.bottom)
            .map { (x, y) -> toTexture(x, y, rotationDegrees) }
        val minS = corners.minOf { it.first }; val maxS = corners.maxOf { it.first }
        val minT = corners.minOf { it.second }; val maxT = corners.maxOf { it.second }
        val cs = (minS + maxS) / 2f; val ct = (minT + maxT) / 2f
        val rx = ((maxS - minS) / 2f * textureAspect).coerceAtLeast(0.02f)
        val ry = ((maxT - minT) / 2f).coerceAtLeast(0.02f)
        val (lx, ly) = leftEye?.let { toTexture(it.first, it.second, rotationDegrees) } ?: (-1f to -1f)
        val (rx2, ry2) = rightEye?.let { toTexture(it.first, it.second, rotationDegrees) } ?: (-1f to -1f)
        return FaceUniform(cs, ct, rx, ry, lx, ly, rx2, ry2)
    }
}
