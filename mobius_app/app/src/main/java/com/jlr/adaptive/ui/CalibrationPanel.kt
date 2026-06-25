package com.jlr.adaptive.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlr.adaptive.middleware.MiddlewareConfig
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val GOLD  = Color(0xFFE8B84B)
private val PANEL = Color(0xFF12121F)

@Composable
fun CalibrationPanel(
    config:   MiddlewareConfig,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Middleware Calibration",
            color      = GOLD,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            "All parameters take effect on the next CPU sample (1 s).",
            color      = Color.White.copy(alpha = 0.35f),
            fontSize   = 9.sp,
            fontFamily = FontFamily.Monospace,
        )

        HorizontalDivider(color = Color(0xFF2A2A3E))

        // ── CPU thresholds ────────────────────────────────────────────────
        SectionLabel("CPU Thresholds")

        CalibSlider(
            label      = "High threshold",
            unit       = "%",
            value      = config.cpuHighThreshold,
            range      = 20f..95f,
            steps      = 74,
            onChanged  = { config.cpuHighThreshold = it.roundToInt().toFloat() },
            hint       = "Overthrottle triggers above this",
        )
        CalibSlider(
            label      = "Low threshold",
            unit       = "%",
            value      = config.cpuLowThreshold,
            range      = 5f..80f,
            steps      = 74,
            onChanged  = { config.cpuLowThreshold = it.roundToInt().toFloat() },
            hint       = "Recovery triggers below this",
        )

        HorizontalDivider(color = Color(0xFF2A2A3E))

        // ── Timing ────────────────────────────────────────────────────────
        SectionLabel("Timing")

        CalibSlider(
            label      = "Sustain window",
            unit       = "s",
            value      = config.sustainWindowMs / 1_000f,
            range      = 1f..15f,
            steps      = 13,
            onChanged  = { config.sustainWindowMs = (it.roundToInt() * 1_000L) },
            hint       = "CPU must stay above/below threshold for this long",
        )
        CalibSlider(
            label      = "Cooldown",
            unit       = "s",
            value      = config.cooldownMs / 1_000f,
            range      = 0f..30f,
            steps      = 29,
            onChanged  = { config.cooldownMs = (it.roundToInt() * 1_000L) },
            hint       = "Gap between quality changes (0 = instant)",
        )

        HorizontalDivider(color = Color(0xFF2A2A3E))

        // ── Step sizes ────────────────────────────────────────────────────
        SectionLabel("Step Sizes")

        CalibSlider(
            label      = "Step down",
            unit       = " levels",
            value      = config.stepDown.toFloat(),
            range      = 1f..3f,
            steps      = 1,
            onChanged  = { config.stepDown = it.roundToInt() },
            hint       = "Quality levels to drop on overthrottle",
        )
        CalibSlider(
            label      = "Step up",
            unit       = " levels",
            value      = config.stepUp.toFloat(),
            range      = 1f..3f,
            steps      = 1,
            onChanged  = { config.stepUp = it.roundToInt() },
            hint       = "Quality levels to restore on recovery",
        )

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color      = Color.White.copy(alpha = 0.55f),
        fontSize   = 10.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun CalibSlider(
    label:    String,
    unit:     String,
    value:    Float,
    range:    ClosedFloatingPointRange<Float>,
    steps:    Int,
    onChanged: (Float) -> Unit,
    hint:     String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                color      = Color.White.copy(alpha = 0.8f),
                fontSize   = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier   = Modifier.weight(1f),
            )
            Text(
                "${value.roundToInt()}$unit",
                color      = GOLD,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Slider(
            value         = value,
            onValueChange = onChanged,
            valueRange    = range,
            steps         = steps,
            modifier      = Modifier.fillMaxWidth().height(28.dp),
            colors        = SliderDefaults.colors(
                thumbColor       = GOLD,
                activeTrackColor = GOLD,
            ),
        )
        Text(
            hint,
            color      = Color.White.copy(alpha = 0.3f),
            fontSize   = 8.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
