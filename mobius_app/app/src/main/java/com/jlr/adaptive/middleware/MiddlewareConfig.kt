package com.jlr.adaptive.middleware

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue

/**
 * All calibratable parameters for the adaptive middleware.
 * Each field is Compose State so the UI sliders update the middleware live.
 */
class MiddlewareConfig {
    // CPU level above which the middleware considers the system overthrottled.
    var cpuHighThreshold  by mutableFloatStateOf(70f)   // %

    // CPU level below which the middleware considers recovery possible.
    var cpuLowThreshold   by mutableFloatStateOf(35f)   // %

    // How long (ms) CPU must continuously exceed the threshold before action is taken.
    // Prevents reacting to brief spikes.
    var sustainWindowMs   by mutableLongStateOf(2_000L) // ms

    // Minimum time (ms) between consecutive quality changes.
    // Prevents thrashing up and down.
    var cooldownMs        by mutableLongStateOf(2_000L) // ms between steps

    // Number of quality steps to drop when overthrottle is confirmed.
    var stepDown          by mutableIntStateOf(1)

    // Number of quality steps to recover when CPU is low for sustainWindowMs.
    var stepUp            by mutableIntStateOf(1)
}
