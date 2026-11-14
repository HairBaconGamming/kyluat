package com.cainghien.companion

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

    fun toView(
        x: Float, y: Float, content: ContentRect, mirrored: Boolean, inputRotation: Int = 0
    ): Pair<Float, Float> {
        // Undo only the extra gravity rotation before applying the preview mirror.
        val (sourceX, sourceY) = when (inputRotation) {
            90 -> y to (1f - x)
            180 -> (1f - x) to (1f - y)
            270 -> (1f - y) to x
            else -> x to y
        }
        val u = if (mirrored) 1f - sourceX else sourceX
        return (content.left + u * content.width) to (content.top + sourceY * content.height)
    }

    fun toUpright(x: Float, y: Float, content: ContentRect, inputRotation: Int): Pair<Float, Float> =
        if (inputRotation == 90 || inputRotation == 270) {
            (x * content.height) to (y * content.width)
        } else {
            (x * content.width) to (y * content.height)
        }
}