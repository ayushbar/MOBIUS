package com.jlr.adaptive.middleware

import com.jlr.adaptive.StreamConfig

/**
 * Adaptive bitrate middleware.
 *
 * Call [onCpuSample] every second with the latest CPU reading and the current
 * quality index. Returns a new quality index if the middleware wants to change
 * bitrate, or null if no change is needed.
 *
 * All thresholds and timing are read from [config] live — changing a slider
 * in the UI takes effect on the very next sample.
 */
class AdaptiveMiddleware(val config: MiddlewareConfig) {

    var state: ThrottleState = ThrottleState.NORMAL
        private set

    // Timestamp (ms) when the current high/low streak started.
    private var streakStartMs: Long = 0L
    // Timestamp of the last quality change (for cooldown enforcement).
    private var lastChangeMs:  Long = 0L

    data class Decision(
        val newQualityIndex: Int,
        val reason: String,
    )

    /**
     * Feed a new CPU sample. Returns a [Decision] if quality should change,
     * or null if the current quality should be kept.
     */
    fun onCpuSample(
        cpuPercent:    Float,
        currentIndex:  Int,
        nowMs:         Long = System.currentTimeMillis(),
    ): Decision? {
        val qualities  = StreamConfig.Quality.entries
        val maxIndex   = qualities.lastIndex
        val inCooldown = (nowMs - lastChangeMs) < config.cooldownMs

        return when {
            // ── Overthrottle path ──────────────────────────────────────────
            cpuPercent >= config.cpuHighThreshold -> {
                if (state != ThrottleState.OVERTHROTTLED &&
                    state != ThrottleState.COOLDOWN) {
                    state        = ThrottleState.OVERTHROTTLED
                    streakStartMs = nowMs
                }
                val sustained = (nowMs - streakStartMs) >= config.sustainWindowMs
                if (sustained && !inCooldown && currentIndex > 0) {
                    val newIndex  = (currentIndex - config.stepDown).coerceAtLeast(0)
                    lastChangeMs  = nowMs
                    state         = ThrottleState.COOLDOWN
                    Decision(
                        newIndex,
                        "CPU ${cpuPercent.toInt()}% > ${config.cpuHighThreshold.toInt()}% " +
                        "for ${config.sustainWindowMs / 1000}s → " +
                        "↓ ${qualities[currentIndex].label} → ${qualities[newIndex].label}"
                    )
                } else null
            }

            // ── Recovery path ──────────────────────────────────────────────
            cpuPercent <= config.cpuLowThreshold -> {
                if (state != ThrottleState.RECOVERING &&
                    state != ThrottleState.COOLDOWN) {
                    state         = ThrottleState.RECOVERING
                    streakStartMs = nowMs
                }
                val sustained = (nowMs - streakStartMs) >= config.sustainWindowMs
                if (sustained && !inCooldown && currentIndex < maxIndex) {
                    val newIndex  = (currentIndex + config.stepUp).coerceAtMost(maxIndex)
                    lastChangeMs  = nowMs
                    state         = ThrottleState.COOLDOWN
                    Decision(
                        newIndex,
                        "CPU ${cpuPercent.toInt()}% < ${config.cpuLowThreshold.toInt()}% " +
                        "for ${config.sustainWindowMs / 1000}s → " +
                        "↑ ${qualities[currentIndex].label} → ${qualities[newIndex].label}"
                    )
                } else null
            }

            // ── Normal range ───────────────────────────────────────────────
            else -> {
                // Reset streak if CPU is in the normal band
                if (state == ThrottleState.OVERTHROTTLED ||
                    state == ThrottleState.RECOVERING) {
                    state         = ThrottleState.NORMAL
                    streakStartMs = 0L
                }
                // Clear cooldown once it expires
                if (state == ThrottleState.COOLDOWN && !inCooldown) {
                    state = ThrottleState.NORMAL
                }
                null
            }
        }
    }

    /** How far through the current sustain window we are (0f–1f). */
    fun sustainProgress(nowMs: Long = System.currentTimeMillis()): Float {
        if (streakStartMs == 0L) return 0f
        return ((nowMs - streakStartMs).toFloat() / config.sustainWindowMs.toFloat())
            .coerceIn(0f, 1f)
    }

    /** How far through the current cooldown we are (0f–1f). */
    fun cooldownProgress(nowMs: Long = System.currentTimeMillis()): Float {
        if (lastChangeMs == 0L) return 1f
        return ((nowMs - lastChangeMs).toFloat() / config.cooldownMs.toFloat())
            .coerceIn(0f, 1f)
    }
}
