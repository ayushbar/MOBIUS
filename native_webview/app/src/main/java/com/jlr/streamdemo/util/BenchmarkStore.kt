package com.jlr.streamdemo.util

import android.content.Context
import com.jlr.streamdemo.StreamConfig
import com.jlr.streamdemo.ui.BenchKey
import java.io.File

object BenchmarkStore {
    private const val PREFS = "benchmark_results"

    // ── Persistence ────────────────────────────────────────────────────────

    fun save(context: Context, results: Map<BenchKey, Pair<Float?, Float?>>) {
        val ed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        results.forEach { (key, pair) ->
            ed.putString(prefKey(key), "${pair.first ?: ""}|${pair.second ?: ""}")
        }
        ed.apply()
    }

    fun load(context: Context): Map<BenchKey, Pair<Float?, Float?>> {
        val prefs  = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val result = mutableMapOf<BenchKey, Pair<Float?, Float?>>()
        StreamConfig.VideoSource.entries.forEach { v ->
            StreamConfig.Quality.entries.forEach { q ->
                val raw = prefs.getString(prefKey(BenchKey(v, q)), null) ?: return@forEach
                val (a, b) = raw.split("|")
                result[BenchKey(v, q)] = a.toFloatOrNull() to b.toFloatOrNull()
            }
        }
        return result
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // ── CSV export ─────────────────────────────────────────────────────────

    fun exportCsv(context: Context, results: Map<BenchKey, Pair<Float?, Float?>>): File {
        val csv = buildString {
            appendLine("Video,Quality,Native CPU%,WebView CPU%,Ratio (W/N)")
            StreamConfig.VideoSource.entries.forEach { v ->
                StreamConfig.Quality.entries.forEach { q ->
                    val (n, w) = results[BenchKey(v, q)] ?: (null to null)
                    val ratio  = if (n != null && w != null && n > 0f)
                        "%.2f".format(w / n) else ""
                    appendLine(
                        "${v.label},${q.label}," +
                        "${n?.let { "%.1f".format(it) } ?: ""}," +
                        "${w?.let { "%.1f".format(it) } ?: ""}," +
                        ratio
                    )
                }
            }
        }
        val dir  = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "jlr_benchmark.csv")
        file.writeText(csv)
        return file
    }

    private fun prefKey(key: BenchKey) = "${key.video.name}_${key.quality.name}"
}
