package com.brainunplug.app.model

import com.brainunplug.app.R

enum class BrainState(
    val minCount: Int,
    val maxCount: Int,
    val label: String,
    val colorRes: Int,
    val drawableRes: Int
) {
    HEALTHY(0, 25, "Healthy & Happy", R.color.accent_green, R.drawable.brain_healthy),
    TIRED(26, 50, "Slightly Tired", R.color.accent_amber, R.drawable.brain_tired),
    EXHAUSTED(51, 100, "Exhausted", R.color.accent_pink, R.drawable.brain_exhausted),
    SICK(101, 150, "Unhealthy & Sick", R.color.accent_purple, R.drawable.brain_exhausted),
    ROTTING(151, Int.MAX_VALUE, "Rotting & Dizzy", R.color.accent_red, R.drawable.brain_exhausted);

    companion object {
        fun fromCount(count: Int): BrainState {
            return entries.firstOrNull { count in it.minCount..it.maxCount } ?: ROTTING
        }
    }
}
