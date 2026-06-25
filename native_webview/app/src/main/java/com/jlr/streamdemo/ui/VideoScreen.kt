package com.jlr.streamdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.jlr.streamdemo.StreamConfig
import com.jlr.streamdemo.util.BenchmarkStore
import com.jlr.streamdemo.util.CpuMonitor
import kotlinx.coroutines.delay

enum class PlayerMode { NATIVE, WEBVIEW }

// Key for benchmark results table
data class BenchKey(
    val video:   StreamConfig.VideoSource,
    val quality: StreamConfig.Quality,
)

private val GOLD  = Color(0xFFE8B84B)
private val GREEN = Color(0xFF2ECC71)
private val AMBER = Color(0xFFF39C12)
private val RED   = Color(0xFFE74C3C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen() {
    val context = LocalContext.current

    var mode    by remember { mutableStateOf(PlayerMode.NATIVE) }
    var video   by remember { mutableStateOf(StreamConfig.VideoSource.BIPBOP) }
    var quality by remember { mutableStateOf(StreamConfig.Quality.Q270) }
    var cpuPercent    by remember { mutableFloatStateOf(0f) }
    var countdown     by remember { mutableIntStateOf(0) }
    var benchPhase    by remember { mutableStateOf("") }
    var showResults   by remember { mutableStateOf(false) }
    var isAutoRunning by remember { mutableStateOf(false) }
    var autoCurrent   by remember { mutableIntStateOf(0) }
    var autoLabel     by remember { mutableStateOf("") }

    val autoTotal = StreamConfig.VideoSource.entries.size *
                    StreamConfig.Quality.entries.size * 2   // native + webview

    // results[key] = Pair(nativeCpu, webviewCpu) — null means not yet captured
    val results = remember { mutableStateMapOf<BenchKey, Pair<Float?, Float?>>() }

    // Load persisted results once on first composition
    LaunchedEffect(Unit) {
        results.putAll(BenchmarkStore.load(context))
    }

    // ── CPU sampler ───────────────────────────────────────────────────────
    // NATIVE: OS-level /proc accounting via CpuMonitor.
    // WEBVIEW: same JS busy-loop injected into the WebView — reported via
    //          JavascriptInterface so both modes use identical measurement code.
    LaunchedEffect(mode) {
        if (mode == PlayerMode.WEBVIEW) return@LaunchedEffect
        CpuMonitor.sample() // reset baseline after mode switch
        while (true) {
            delay(1_000)
            cpuPercent = CpuMonitor.sample()
        }
    }

    // ── Full auto-run: cycles every combination unattended ────────────────
    LaunchedEffect(isAutoRunning) {
        if (!isAutoRunning) return@LaunchedEffect

        val combos = StreamConfig.VideoSource.entries.flatMap { v ->
            StreamConfig.Quality.entries.flatMap { q ->
                listOf(Triple(PlayerMode.NATIVE, v, q), Triple(PlayerMode.WEBVIEW, v, q))
            }
        }

        combos.forEachIndexed { idx, (m, v, q) ->
            autoCurrent = idx + 1
            autoLabel   = "${v.label}  ${q.label}  ${if (m == PlayerMode.NATIVE) "Native" else "WebView"}"
            mode = m; video = v; quality = q

            benchPhase = "Stabilising"
            for (i in 5 downTo 1) { countdown = i; delay(1_000) }

            val samples = mutableListOf<Float>()
            benchPhase = "Sampling"
            for (i in 5 downTo 1) { countdown = i; delay(1_000); samples.add(cpuPercent) }
            countdown = 0; benchPhase = "Recorded"

            val avg = samples.average().toFloat()
            val key = BenchKey(v, q)
            val prev = results[key] ?: (null to null)
            results[key] = when (m) {
                PlayerMode.NATIVE  -> avg to prev.second
                PlayerMode.WEBVIEW -> prev.first to avg
            }
            BenchmarkStore.save(context, results)
        }

        isAutoRunning = false
        showResults   = true   // auto-open chart when done
    }

    // ── Manual capture: 5 s stabilise → 5 s sample → store average ───────
    // Skipped while auto-run is managing captures itself.
    LaunchedEffect(mode, video, quality) {
        if (isAutoRunning) return@LaunchedEffect
        benchPhase = "Stabilising"
        for (i in 5 downTo 1) {
            countdown = i
            delay(1_000)
        }
        val samples = mutableListOf<Float>()
        benchPhase = "Sampling"
        for (i in 5 downTo 1) {
            countdown = i
            delay(1_000)
            samples.add(cpuPercent)
        }
        countdown = 0
        benchPhase = "Recorded"

        val avg = samples.average().toFloat()
        val key = BenchKey(video, quality)
        val prev = results[key] ?: (null to null)
        results[key] = when (mode) {
            PlayerMode.NATIVE  -> avg to prev.second
            PlayerMode.WEBVIEW -> prev.first to avg
        }
        // Persist immediately after every capture
        BenchmarkStore.save(context, results)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        // ── Video surface ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            key(mode, video) {
                when (mode) {
                    PlayerMode.NATIVE  -> NativePlayerView(
                        streamUrl = video.url,
                        quality   = quality,
                        modifier  = Modifier.fillMaxSize(),
                    )
                    PlayerMode.WEBVIEW -> WebViewPlayer(
                        streamUrl   = video.url,
                        quality     = quality,
                        onCpuReport = { v -> cpuPercent = v },
                        modifier    = Modifier.fillMaxSize(),
                    )
                }
            }

            CpuBadge(
                cpuPercent = cpuPercent,
                mode       = mode,
                video      = video,
                quality    = quality,
                phase      = benchPhase,
                countdown  = countdown,
                modifier   = Modifier.align(Alignment.TopEnd).padding(12.dp),
            )
        }

        // ── Controls ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ControlRow("Video:") {
                SingleChoiceSegmentedButtonRow {
                    StreamConfig.VideoSource.entries.forEachIndexed { i, v ->
                        SegmentedButton(
                            selected = video == v,
                            onClick  = { video = v },
                            shape    = SegmentedButtonDefaults.itemShape(i, StreamConfig.VideoSource.entries.size),
                            label    = { Text(v.label, fontSize = 11.sp) },
                        )
                    }
                }
            }
            ControlRow("Quality:") {
                SingleChoiceSegmentedButtonRow {
                    StreamConfig.Quality.entries.forEachIndexed { i, q ->
                        SegmentedButton(
                            selected = quality == q,
                            onClick  = { quality = q },
                            shape    = SegmentedButtonDefaults.itemShape(i, StreamConfig.Quality.entries.size),
                            label    = { Text(q.label, fontSize = 11.sp) },
                        )
                    }
                }
            }
            ControlRow("Decoder:") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    PlayerMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick  = { if (!isAutoRunning) mode = m },
                            shape    = SegmentedButtonDefaults.itemShape(i, PlayerMode.entries.size),
                            label    = {
                                Text(
                                    when (m) {
                                        PlayerMode.NATIVE  -> "Native (MediaCodec)"
                                        PlayerMode.WEBVIEW -> "WebView (hls.js)"
                                    },
                                    fontSize = 11.sp,
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick        = { showResults = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) { Text("Results", fontSize = 11.sp) }
            }

            // ── Auto-run row ────────────────────────────────────────────────
            if (isAutoRunning) {
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinearProgressIndicator(
                        progress = { autoCurrent.toFloat() / autoTotal.toFloat() },
                        modifier = Modifier.width(80.dp).height(4.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$autoCurrent/$autoTotal  ·  $autoLabel",
                        color      = GOLD,
                        fontSize   = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier   = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick        = { isAutoRunning = false },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) { Text("Stop", fontSize = 11.sp, color = RED) }
                }
            } else {
                Button(
                    onClick        = { isAutoRunning = true },
                    modifier       = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        "▶  Auto Record All  ($autoTotal combos · ~${autoTotal * 10 / 60} min)",
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }

    // ── Results bottom sheet ───────────────────────────────────────────────
    if (showResults) {
        ModalBottomSheet(
            onDismissRequest = { showResults = false },
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ResultsTable(
                results     = results,
                liveVideo   = video,
                liveQuality = quality,
                liveMode    = mode,
                liveCpu     = cpuPercent,
                benchPhase  = benchPhase,
                onExport    = {
                    val file = BenchmarkStore.exportCsv(context, results)
                    Toast.makeText(context,
                        "Saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                },
                onClear     = {
                    results.clear()
                    BenchmarkStore.clear(context)
                },
            )
        }
    }
}

// ── Small helper ──────────────────────────────────────────────────────────
@Composable
private fun ControlRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp, modifier = Modifier.width(52.dp))
        content()
    }
}

// ── CPU badge ─────────────────────────────────────────────────────────────
@Composable
private fun CpuBadge(
    cpuPercent: Float,
    mode:       PlayerMode,
    video:      StreamConfig.VideoSource,
    quality:    StreamConfig.Quality,
    phase:      String,
    countdown:  Int,
    modifier:   Modifier = Modifier,
) {
    val valueColor = when {
        cpuPercent < 10f -> GREEN
        cpuPercent < 30f -> AMBER
        else             -> RED
    }
    val phaseText = when {
        countdown > 0 -> "$phase ${countdown}s"
        phase == "Recorded" -> "✓ Recorded"
        else -> ""
    }
    Surface(
        color    = Color.Black.copy(alpha = 0.75f),
        shape    = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("App CPU", color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("%.1f%%".format(cpuPercent), color = valueColor,
                fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(
                when (mode) {
                    PlayerMode.NATIVE  -> "MediaCodec"
                    PlayerMode.WEBVIEW -> "hls.js / SW"
                },
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 9.sp, fontFamily = FontFamily.Monospace,
            )
            Text("${video.label} · ${quality.label}",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            if (phaseText.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(phaseText,
                    color = if (phase == "Recorded") GREEN else GOLD,
                    fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ── Results sheet — Chart + Table tabs ────────────────────────────────────
@Composable
private fun ResultsTable(
    results:     Map<BenchKey, Pair<Float?, Float?>>,
    liveVideo:   StreamConfig.VideoSource,
    liveQuality: StreamConfig.Quality,
    liveMode:    PlayerMode,
    liveCpu:     Float,
    benchPhase:  String,
    onExport:    () -> Unit,
    onClear:     () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Header bar ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("CPU Benchmark",
                style = MaterialTheme.typography.titleMedium, color = GOLD,
                modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = onExport,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) { Text("Export CSV", fontSize = 11.sp) }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(
                onClick = onClear,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) { Text("Clear", fontSize = 11.sp, color = RED) }
        }

        Text(
            "5-sample avg · 5 s stabilise · includes WebView renderer process · auto-saved",
            color      = Color.White.copy(alpha = 0.3f),
            fontSize   = 8.sp,
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp),
        )

        // ── Tabs ────────────────────────────────────────────────────────────
        TabRow(
            selectedTabIndex = tab,
            containerColor   = Color.Transparent,
            contentColor     = GOLD,
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0 },
                text = { Text("Chart", fontFamily = FontFamily.Monospace, fontSize = 12.sp) })
            Tab(selected = tab == 1, onClick = { tab = 1 },
                text = { Text("Table", fontFamily = FontFamily.Monospace, fontSize = 12.sp) })
        }

        // ── Tab content ─────────────────────────────────────────────────────
        if (tab == 0) {
            BenchmarkChart(results = results, modifier = Modifier.fillMaxWidth())
        } else {
            ResultsTableContent(results, liveVideo, liveQuality, liveMode, liveCpu)
        }
    }
}

@Composable
private fun ResultsTableContent(
    results:     Map<BenchKey, Pair<Float?, Float?>>,
    liveVideo:   StreamConfig.VideoSource,
    liveQuality: StreamConfig.Quality,
    liveMode:    PlayerMode,
    liveCpu:     Float,
) {
    val videos    = StreamConfig.VideoSource.entries
    val qualities = StreamConfig.Quality.entries

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Header row
        Row(modifier = Modifier.fillMaxWidth()) {
            TableCell("", modifier = Modifier.width(60.dp), header = true)
            qualities.forEach { q ->
                TableCell(q.label, modifier = Modifier.weight(1f), header = true)
            }
        }
        HorizontalDivider(color = Color(0xFF2A2A3E), thickness = 1.dp)

        videos.forEach { v ->
            Spacer(Modifier.height(6.dp))
            Text(v.label, color = GOLD, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 2.dp))

            ResultRow(
                rowLabel  = "Native",
                rowColor  = GREEN,
                qualities = qualities,
                values    = qualities.map { q ->
                    val stored = results[BenchKey(v, q)]?.first
                    val isLive = v == liveVideo && q == liveQuality && liveMode == PlayerMode.NATIVE
                    Triple(stored, isLive && stored == null, liveCpu)
                },
            )
            ResultRow(
                rowLabel  = "WebView",
                rowColor  = RED,
                qualities = qualities,
                values    = qualities.map { q ->
                    val stored = results[BenchKey(v, q)]?.second
                    val isLive = v == liveVideo && q == liveQuality && liveMode == PlayerMode.WEBVIEW
                    Triple(stored, isLive && stored == null, liveCpu)
                },
            )
            RatioRow(qualities = qualities, results = results, video = v)
            HorizontalDivider(color = Color(0xFF2A2A3E), thickness = 1.dp,
                modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

// values: Triple(storedAvg, isCurrentlyLive, liveCpuReading)
@Composable
private fun ResultRow(
    rowLabel:  String,
    rowColor:  Color,
    qualities: List<StreamConfig.Quality>,
    values:    List<Triple<Float?, Boolean, Float>>,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TableCell(rowLabel, modifier = Modifier.width(60.dp), textColor = rowColor)
        values.forEach { (stored, isLive, live) ->
            val text  = when {
                stored != null -> "%.1f%%".format(stored)
                isLive         -> "~%.0f%%".format(live)   // live reading while waiting
                else           -> "—"
            }
            val color = when {
                stored != null -> rowColor
                isLive         -> rowColor.copy(alpha = 0.5f)
                else           -> Color.White.copy(alpha = 0.25f)
            }
            TableCell(text, modifier = Modifier.weight(1f), textColor = color)
        }
    }
}

@Composable
private fun RatioRow(
    qualities: List<StreamConfig.Quality>,
    results:   Map<BenchKey, Pair<Float?, Float?>>,
    video:     StreamConfig.VideoSource,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TableCell("Ratio", modifier = Modifier.width(60.dp), textColor = Color.White.copy(alpha = 0.5f))
        qualities.forEach { q ->
            val pair    = results[BenchKey(video, q)]
            val native  = pair?.first
            val webview = pair?.second
            val ratio   = if (native != null && webview != null && native > 0f)
                webview / native else null
            TableCell(
                text      = if (ratio != null) "%.1f×".format(ratio) else "—",
                modifier  = Modifier.weight(1f),
                textColor = when {
                    ratio == null -> Color.White.copy(alpha = 0.3f)
                    ratio >= 2f   -> RED
                    ratio >= 1.5f -> AMBER
                    else          -> Color.White
                },
            )
        }
    }
}

@Composable
private fun TableCell(
    text:      String,
    modifier:  Modifier = Modifier,
    header:    Boolean = false,
    textColor: Color = Color.White,
) {
    Text(
        text       = text,
        color      = if (header) Color.White.copy(alpha = 0.6f) else textColor,
        fontSize   = if (header) 9.sp else 11.sp,
        fontWeight = if (header) FontWeight.Normal else FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        textAlign  = TextAlign.Center,
        modifier   = modifier.padding(vertical = 2.dp),
    )
}
