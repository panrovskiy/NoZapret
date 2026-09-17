package com.example.nozapret.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing

object MotionConstants {
    // Durations
    const val DurationFast = 150
    const val DurationNormal = 250
    const val DurationEmphasis = 400
    const val DurationLong = 600

    // Easing Curves (Material 3 standard)
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0f, 1.0f)
    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0f, 1.0f)
    val DecelerateEasing: Easing = LinearOutSlowInEasing
    val AccelerateEasing: Easing = FastOutSlowInEasing
}
