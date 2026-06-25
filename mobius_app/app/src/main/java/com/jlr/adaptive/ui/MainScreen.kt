package com.jlr.adaptive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.jlr.adaptive.middleware.MiddlewareConfig
import com.jlr.adaptive.middleware.ThrottleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GREEN = Color(0xFF2ECC71)
private val RED   = Color(0xFFE74C3C)
private val AMBER = Color(0xFFF39C12)
private val GOLD  = Color(0xFFE8B84B)

@Composable
fun MainScreen() {
    val config     = remember { MiddlewareConfig() }
    val middleware = remember { AdaptiveMiddleware(config) }

    val qualities    = StreamConfig.Quality.entries
    var qualityIndex by remember { mutableIntStateOf(qualities.lastIndex) }
    var ecuCpu       by remember { mutableFloatStateOf(20f) }
    var actualWidth  by remember { mutableIntStateOf(0) }
    var actualHeight by remember { mutableIntStateOf(0) }

    var remoteIp by remember { mutableStateOf("") }
    var remoteOk by remember { mutableStateOf(false) }

    val quality = qualities[qualityIndex]
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val log     = remember { mutableStateListOf<LogEntry>() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            if (remoteIp.isNotBlank()) {
                val fetched = try {
                    withContext(Dispatchers.IO) {
                        val text = URL("http://$remoteIp:8765/cpu")
                            .openConnection()
                            .also { it.connectTimeout = 800; it.readTimeout = 800 }
                            .getInputStream()
                            .bufferedReader().readText()
                        JSONObject(text).getDouble("value").toFloat()
                    }
                } catch (_: Exception) { null }
                if (fetched != null) { ecuCpu = fetched; remoteOk = true }
                else remoteOk = false
            }

            val prevIndex = qualityIndex
            val decision  = middleware.onCpuSample(ecuCpu, qualityIndex)
            if (decision != null) {
                qualityIndex = decision.newQualityIndex
                log.add(LogEntry(
                    timeLabel = timeFmt.format(Date()),
                    message   = decision.reason,
                    color     = if (decision.newQualityIndex < prevIndex) RED else GREEN,
                ))
                while (log.size > 50) log.removeAt(0)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding(),
    ) {
        // ── Full-screen video ─────────────────────────────────────────────
        PlayerSection(
            streamUrl           = StreamConfig.DEFAULT_URL,
            quality             = quality,
            onResolutionChanged = { w, h -> actualWidth = w; actualHeight = h },
            modifier            = Modifier.fillMaxSize(),
        )

        // ── Top overlay bar ───────────────────────────────────────────────
        TopBar(
            actualWidth      = actualWidth,
            actualHeight     = actualHeight,
            quality          = quality,
            state            = middleware.state,
            ecuCpu           = ecuCpu,
            onEcuCpuChange   = { ecuCpu = it },
            remoteIp         = remoteIp,
            remoteOk         = remoteOk,
            onRemoteIpChange = { remoteIp = it },
            modifier         = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun TopBar(
    actualWidth:      Int,
    actualHeight:     Int,
    quality:          StreamConfig.Quality,
    state:            ThrottleState,
    ecuCpu:           Float,
    onEcuCpuChange:   (Float) -> Unit,
    remoteIp:         String,
    remoteOk:         Boolean,
    onRemoteIpChange: (String) -> Unit,
    modifier:         Modifier = Modifier,
) {
    val focusMgr  = LocalFocusManager.current
    var ipDraft   by remember { mutableStateOf(remoteIp) }
    val isRemote  = remoteIp.isNotBlank()

    val resLabel  = if (actualHeight > 0) "${actualWidth}×${actualHeight}" else "—"
    val resColor  = when {
        actualHeight <= 0   -> Color.White.copy(alpha = 0.4f)
        actualHeight <= 360 -> RED
        actualHeight <= 480 -> AMBER
        else                -> GREEN
    }
    val qualityColor = when (quality) {
        StreamConfig.Quality.Q_SD  -> AMBER
        StreamConfig.Quality.Q_HD  -> GREEN
        StreamConfig.Quality.Q_FHD -> Color(0xFF00AAFF)
    }
    val cpuColor  = when {
        ecuCpu >= 70f -> RED
        ecuCpu >= 35f -> AMBER
        else          -> GREEN
    }

    Row(
        modifier              = modifier,
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Video quality (left) ──────────────────────────────────────────
        Column(modifier = Modifier.wrapContentWidth()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text       = quality.label,
                    color      = qualityColor,
                    fontSize   = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text       = "  $resLabel",
                    color      = resColor.copy(alpha = 0.6f),
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier   = Modifier.padding(bottom = 3.dp),
                )
            }
            Text(
                text       = state.name,
                color      = stateColor(state),
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }

        HorizontalDivider(
            modifier  = Modifier.width(1.dp).height(36.dp),
            color     = Color.White.copy(alpha = 0.2f),
            thickness = 1.dp,
        )

        // ── ECU CPU (centre, expands) ─────────────────────────────────────
        Text(
            text       = "ECU ${ecuCpu.toInt()}%",
            color      = cpuColor,
            fontSize   = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.wrapContentWidth(),
        )

        if (isRemote) {
            Text(
                text       = if (remoteOk) "⚡ ${remoteIp}" else "offline",
                color      = if (remoteOk) GREEN else RED,
                fontSize   = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier   = Modifier.weight(1f),
            )
        } else {
            Slider(
                value         = ecuCpu,
                onValueChange = onEcuCpuChange,
                valueRange    = 0f..100f,
                modifier      = Modifier.weight(1f).height(28.dp),
                colors        = SliderDefaults.colors(
                    thumbColor       = cpuColor,
                    activeTrackColor = cpuColor,
                ),
            )
        }

        HorizontalDivider(
            modifier  = Modifier.width(1.dp).height(36.dp),
            color     = Color.White.copy(alpha = 0.2f),
            thickness = 1.dp,
        )

        // ── PC IP input (right) ───────────────────────────────────────────
        OutlinedTextField(
            value         = ipDraft,
            onValueChange = { ipDraft = it },
            label         = { Text("PC IP", fontSize = 8.sp, fontFamily = FontFamily.Monospace) },
            placeholder   = { Text("192.168.x.x", fontSize = 9.sp,
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
            modifier = Modifier.width(160.dp),
            colors   = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = GOLD,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedLabelColor    = GOLD,
                unfocusedLabelColor  = Color.White.copy(alpha = 0.3f),
            ),
        )
    }
}

private fun stateColor(s: ThrottleState) = when (s) {
    ThrottleState.NORMAL        -> GREEN
    ThrottleState.OVERTHROTTLED -> RED
    ThrottleState.RECOVERING    -> Color(0xFF44AAFF)
    ThrottleState.COOLDOWN      -> AMBER
}
