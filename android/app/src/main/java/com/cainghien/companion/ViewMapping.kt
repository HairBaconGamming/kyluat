package com.cainghien.companion

/**
 * Maps normalized pose landmarks onto the on-screen skeleton view.
 *
 * The analyzer bitmap handed to MediaPipe comes from `ImageProxy.toBitmap()`, which is already
 * upright (rotation baked in) and cropped to the viewport. The preview is drawn with
 * [androidx.camera.view.PreviewView.ScaleType.FILL_CENTER]: scaled to cover the view, overflow
 * cropped symmetrically. Because both share the viewport FOV, overlaying is just FILL_CENTER of
 * the upright bitmap into the view, plus a horizontal flip for the mirrored front preview.
 *
 * Earlier builds routed joints through `CoordinateTransform(imageProxyTransform, previewTransform)`
 * from a factory with `setUsingCropRect(false)`. That transform expects unrotated full-buffer
 * coordinates, but the bitmap is rotated and cropped, so on a real phone every joint landed
 * somewhere else — the reported "joints are wildly wrong". This object replaces it with
 * arithmetic we control and can unit-test. No rotation term: the bitmap is already upright.
 */
object ViewMapping {
    /** Where the scaled preview lands inside the view, in view pixels. */
    data class ContentRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /**
     * FILL_CENTER content rect for an upright image of [imageWidth]x[imageHeight] shown in a
     * [viewWidth]x[viewHeight] view: scale by the larger ratio, centre the overflow.
     */
    fun contentRect(imageWidth: Int, imageHeight: Int, viewWidth: Int, viewHeight: Int): ContentRect {
        if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            return ContentRect(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        }
        val scale = maxOf(
            viewWidth.toFloat() / imageWidth,
            viewHeight.toFloat() / imageHeight
        )
        val drawnWidth = imageWidth * scale
        val drawnHeight = imageHeight * scale
        val left = (viewWidth - drawnWidth) / 2f
        val top = (viewHeight - drawnHeight) / 2f
        return ContentRect(left, top, left + drawnWidth, top + drawnHeight)
    }

    /**
     * Map a normalized landmark (0..1 in upright bitmap space, y down) to view pixels.
     * [mirrored] flips horizontally to match the mirrored front-camera preview.
     */
    fun toView(x: Float, y: Float, content: ContentRect, mirrored: Boolean): Pair<Float, Float> {
        val u = if (mirrored) 1f - x else x
        return (content.left + u * content.width) to (content.top + y * content.height)
    }
}