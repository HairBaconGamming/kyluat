package com.cainghien.companion

import kotlin.math.abs

class PoseOrientation {
    var targetRotation: Int? = null
        private set
    var revision = 0L
        private set

    fun update(degrees: Int): Boolean {
        val previous = targetRotation
        val next = if (degrees !in 0..359) null else {
            if (previous != null) {
                val previousDegrees = (360 - previous * 90) % 360
                val distance = abs(degrees - previousDegrees)
                if (minOf(distance, 360 - distance) < 60) return false
            }
            // OrientationEventListener is clockwise; Surface rotation is counter-clockwise.
            (4 - (degrees + 45) / 90 % 4) % 4
        }
        if (next == previous) return false
        targetRotation = next
        revision += 1
        return true
    }

    companion object {
        fun inputRotation(displaySensorRotation: Int, uprightSensorRotation: Int): Int =
            (uprightSensorRotation - displaySensorRotation + 360) % 360
    }
}
