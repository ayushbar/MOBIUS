package com.jlr.adaptive.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlr.adaptive.StreamConfig
import com.jlr.adaptive.middleware.AdaptiveMiddleware
import com.jlr.adaptive.middleware.ThrottleState

private val GOLD  = Color(0xFFE8B84B)
private val GREEN = Color(0xFF2ECC71)
private val RED   = Color(0xFFE74C3C)
private val AMBER = Color(0xFFF39C12)
private val DIM   = Color(0xFF2A2A3E)

data class LogEntry(val timeLabel: String, val message: String, val color: Color)

@Composable
fun StatusDashboard(
    ecuCpu:           Float,
    onEcuCpuChange:   (Float) -> Unit,
    remoteIp:         String,
    onRemoteIpChange: (String) -> Unit,
    remoteOk:         Boolean,
    quality:          StreamConfig.Quality,
    actualWidth:      Int,
    actualHeight:     Int,
    middleware:       AdaptiveMiddleware,
    log:              List<LogEntry>,
    modifier:         Modifier = Modifier,
) {
    val state    = middleware.state
    val nowMs    = System.currentTimeMillis()
    val sustain  = middleware.sustainProgress(nowMs)
    val cooldown = middleware.cooldownProgress(nowMs)

    val cpuColor = when {
        ecuCpu >= middleware.config.cpuHighThreshold -> RED
        ecuCpu >= middleware.config.cpuLowThreshold  -> AMBER
        else                                         -> GREEN
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── ECU CPU Load ──────────────────────────────────────────────────
        val isRemote = remoteIp.isNotBlank()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ECU CPU Load", color = Color.White.copy(alpha = 0.55f),
                fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f))
            if (isRemote) {
                Text(
                    if (remoteOk) "⚡ REMOTE" else "⚡ offline",
                    color      = if (remoteOk) GREEN else RED,
                    fontSize   = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        // Numeric readout
        Text(
            "%.0f%%".format(ecuCpu),
            color      = cpuColor,
            fontSize   = 32.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.align(Alignment.CenterHorizontally),
        )

        // Bar with threshold markers
        Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
            val r = CornerRadius(4.dp.toPx())
            drawRoundRect(color = cpuColor.copy(alpha = 0.12f), cornerRadius = r)
            val fill = (ecuCpu / 100f).coerceIn(0f, 1f)
            if (fill > 0f) drawRoundRect(
                color = cpuColor, cornerRadius = r,
                size  = Size(size.width * fill, size.height),
            )
            val highX = size.width * (middleware.config.cpuHighThreshold / 100f)
            val lowX  = size.width * (middleware.config.cpuLowThreshold  / 100f)
            drawLine(RED,   Offset(highX, 0f), Offset(highX, size.height), strokeWidth = 3f)
            drawLine(GREEN, Offset(lowX,  0f), Offset(lowX,  size.height), strokeWidth = 3f)
        }

        // Local slider — hidden when remote is active
        if (!isRemote) {
            Slider(
                value         = ecuCpu,
                onValueChange = onEcuCpuChange,
                valueRange    = 0f..100f,
                modifier      = Modifier.fillMaxWidth().height(32.dp),
                colors        = SliderDefaults.colors(
                    thumbColor       = cpuColor,
                    activeTrackColor = cpuColor,
                ),
            )
        } else {
            Text(
                "Controlled from PC — ${remoteIp}:5000",
                color      = Color.White.copy(alpha = 0.35f),
                fontSize   = 8.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        // ── Remote ECU IP input ───────────────────────────────────────────
        val focusMgr = LocalFocusManager.current
        var ipDraft by remember { mutableStateOf(remoteIp) }
        OutlinedTextField(
            value         = ipDraft,
            onValueChange = { ipDraft = it },
            label         = { Text("PC IP (leave blank = local)", fontSize = 8.sp,
                                   fontFamily = FontFamily.Monospace) },
            placeholder   = { Text("192.168.1.100", fontSize = 9.sp,
                                   fontFamily = FontFamily.Monospace,
                                   color = Color.White.copy(alpha = 0.2f)) },
            singleLine    = true,
            textStyle     = LocalTextStyle.current.copy(
                fontSize   = 10.sp,
                fontFamily = FontFamily.Monospace,
                color      = Color.White,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction    = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                onRemoteIpChange(ipDraft.trim())
                focusMgr.clearFocus()
            }),
            modifier = Modifier.fillMaxWidth(),
            colors   = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = GOLD,
                unfocusedBorderColor = DIM,
                focusedLabelColor    = GOLD,
                unfocusedLabelColor  = Color.White.copy(alpha = 0.3f),
            ),
        )

        // ── State + Quality chips ─────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatChip("State",  state.name,    stateColor(state), Modifier.weight(1f))
            StatChip("Target", quality.label, GOLD,              Modifier.weight(1f))
        }

        // ── Actual resolution from ExoPlayer ──────────────────────────────
        if (actualHeight > 0) {
            val resColor = when {
                actualHeight <= 144  -> RED
                actualHeight <= 270  -> Color(0xFFE74C3C).copy(alpha = 0.8f)
                actualHeight <= 360  -> AMBER
                actualHeight <= 540  -> GOLD
                actualHeight <= 720  -> GREEN
                else                 -> Color(0xFF00AAFF)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF12121F), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Playing", color = Color.White.copy(alpha = 0.4f),
                    fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f))
                Text("${actualWidth}×${actualHeight}",
                    color = resColor, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }

        // ── Progress bars ─────────────────────────────────────────────────
        if (state == ThrottleState.OVERTHROTTLED || state == ThrottleState.RECOVERING) {
            ProgressRow(
                label    = if (state == ThrottleState.OVERTHROTTLED) "Sustain ▼" else "Sustain ▲",
                progress = sustain,
                color    = if (state == ThrottleState.OVERTHROTTLED) RED else GREEN,
            )
        }
        if (state == ThrottleState.COOLDOWN) {
            ProgressRow(label = "Cooldown", progress = cooldown, color = AMBER)
        }

        HorizontalDivider(color = DIM)

        // ── Event Log ─────────────────────────────────────────────────────
        Text("Event Log", color = Color.White.copy(alpha = 0.55f),
            fontSize = 10.sp, fontFamily = FontFamily.Monospace)

        val listState = rememberLazyListState()
        LaunchedEffect(log.size) {
            if (log.isNotEmpty()) listState.animateScrollToItem(0)
        }

        LazyColumn(
            state               = listState,
            modifier            = Modifier.weight(1f).fillMaxWidth(),
            reverseLayout       = true,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            items(log.asReversed()) { entry ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(entry.timeLabel,
                        color = Color.White.copy(alpha = 0.3f), fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(44.dp))
                    Text(entry.message,
                        color = entry.color, fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFF12121F), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(label, color = Color.White.copy(alpha = 0.4f),
            fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = color, fontSize = 13.sp,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ProgressRow(label: String, progress: Float, color: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, color = color.copy(alpha = 0.8f),
                fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f))
            Text("${(progress * 100).toInt()}%", color = color,
                fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 2.dp),
            color    = color,
        )
    }
}

private fun stateColor(s: ThrottleState) = when (s) {
    ThrottleState.NORMAL        -> GREEN
    ThrottleState.OVERTHROTTLED -> RED
    ThrottleState.RECOVERING    -> Color(0xFF44AAFF)
    ThrottleState.COOLDOWN      -> AMBER
}
