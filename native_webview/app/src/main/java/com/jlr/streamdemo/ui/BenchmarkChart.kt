package com.jlr.streamdemo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlr.streamdemo.StreamConfig

private val CHART_GOLD  = Color(0xFFE8B84B)
private val CHART_GREEN = Color(0xFF2ECC71)
private val CHART_RED   = Color(0xFFE74C3C)

private data class ChartEntry(
    val video:   StreamConfig.VideoSource,
    val quality: StreamConfig.Quality,
    val native:  Float?,
    val webview: Float?,
)

@Composable
fun BenchmarkChart(
    results:  Map<BenchKey, Pair<Float?, Float?>>,
    modifier: Modifier = Modifier,
) {
    val entries = StreamConfig.VideoSource.entries.flatMap { v ->
        StreamConfig.Quality.entries.mapNotNull { q ->
            val pair = results[BenchKey(v, q)]
            if (pair?.first != null || pair?.second != null)
                ChartEntry(v, q, pair!!.first, pair.second)
            else null
        }
    }

    if (entries.isEmpty()) {
        Box(
            modifier          = modifier.fillMaxWidth().padding(40.dp),
            contentAlignment  = Alignment.Center,
        ) {
            Text(
                "No data yet\n\nSelect video + quality + decoder\nthen wait ~10 s for a capture",
                color      = Color.White.copy(alpha = 0.3f),
                fontSize   = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign  = TextAlign.Center,
                lineHeight  = 20.sp,
            )
        }
        return
    }

    // Scale to max observed CPU, minimum 80% so bars don't look distorted on low-CPU runs
    val maxCpu = entries.flatMap { listOfNotNull(it.native, it.webview) }
        .maxOrNull()?.coerceAtLeast(80f) ?: 80f

    Column(
        modifier             = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement  = Arrangement.spacedBy(12.dp),
    ) {
        // Scale legend
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(50.dp))
            // 0%, 25%, 50%, 75%, 100% of maxCpu
            Row(modifier = Modifier.weight(1f)) {
                for (i in 0..4) {
                    val label = "%.0f".format(maxCpu * i / 4) + "%"
                    Text(
                        label,
                        color      = Color.White.copy(alpha = 0.25f),
                        fontSize   = 7.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign  = if (i == 4) TextAlign.End else TextAlign.Start,
                        modifier   = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.width(36.dp))
        }

        entries.forEach { entry ->
            ChartGroup(entry, maxCpu)
        }

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendDot(CHART_GREEN); Spacer(Modifier.width(4.dp))
            Text("Native (ExoPlayer / MediaCodec)", color = Color.White.copy(alpha = 0.5f),
                fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(16.dp))
            LegendDot(CHART_RED); Spacer(Modifier.width(4.dp))
            Text("WebView (hls.js / MSE)", color = Color.White.copy(alpha = 0.5f),
                fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ChartGroup(entry: ChartEntry, maxCpu: Float) {
    val n = entry.native
    val w = entry.webview
    val ratio = if (n != null && w != null && n > 0f) w / n else null

    Column(modifier = Modifier.fillMaxWidth()) {
        // Label row
        Row(
            modifier          = Modifier.fillMaxWidth().padding(bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${entry.video.label}  ·  ${entry.quality.label}",
                color      = CHART_GOLD,
                fontSize   = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                modifier   = Modifier.weight(1f),
            )
            if (ratio != null) {
                val col = when {
                    ratio >= 3f -> CHART_RED
                    ratio >= 2f -> Color(0xFFFF9944)
                    else        -> Color.White.copy(alpha = 0.55f)
                }
                Text(
                    "%.1f× overhead".format(ratio),
                    color      = col,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        if (n != null) Bar("Native",  n, maxCpu, CHART_GREEN)
        if (w != null) Bar("WebView", w, maxCpu, CHART_RED)
    }
}

@Composable
private fun Bar(label: String, value: Float, maxCpu: Float, color: Color) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color      = color.copy(alpha = 0.75f),
            fontSize   = 8.sp,
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.width(50.dp),
        )
        Canvas(modifier = Modifier.weight(1f).height(18.dp)) {
            val r        = CornerRadius(3.dp.toPx())
            val fraction = (value / maxCpu).coerceIn(0f, 1f)
            // track
            drawRoundRect(color = color.copy(alpha = 0.12f), cornerRadius = r)
            // fill
            if (fraction > 0f) drawRoundRect(
                color        = color,
                size         = Size(size.width * fraction, size.height),
                cornerRadius = r,
            )
        }
        Text(
            "%.0f%%".format(value),
            color      = color,
            fontSize   = 9.sp,
            fontFamily = FontFamily.Monospace,
            textAlign  = TextAlign.End,
            modifier   = Modifier.width(36.dp),
        )
    }
}

@Composable
private fun LegendDot(color: Color) {
    Canvas(modifier = Modifier.size(8.dp)) {
        drawCircle(color = color)
    }
}
