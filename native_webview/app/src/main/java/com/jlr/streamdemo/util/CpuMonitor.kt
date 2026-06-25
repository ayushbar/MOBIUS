package com.jlr.streamdemo.util

import android.os.Process
import android.os.SystemClock
import java.io.File

/**
 * Measures CPU load as a percentage of one core.
 *
 * Main process  → android.os.Process.getElapsedCpuTime()  (official Android API)
 * Child processes (WebView sandboxed renderer, etc.)
 *               → /proc/<pid>/stat, filtered by PPid == myPid
 *                 (no public API exists for another process's CPU time)
 *
 * CPU% = Δcpu_ms / Δwall_ms × 100
 *
 * Can exceed 100% on multi-core devices if multiple threads are saturated,
 * which is fine — we only use it for relative Native-vs-WebView comparison.
 */
object CpuMonitor {
    private const val HZ = 100L  // Linux/Android kernel tick rate (jiffies/s)

    private var lastCpuMs  = totalCpuMs()
    private var lastWallMs = SystemClock.elapsedRealtime()

    fun sample(): Float {
        val nowCpu  = totalCpuMs()
        val nowWall = SystemClock.elapsedRealtime()

        val cpuDelta  = (nowCpu  - lastCpuMs).coerceAtLeast(0)
        val wallDelta = (nowWall - lastWallMs).coerceAtLeast(1)

        lastCpuMs  = nowCpu
        lastWallMs = nowWall

        return cpuDelta.toFloat() / wallDelta.toFloat() * 100f
    }

    // Main process via official API + children via /proc (no API alternative exists)
    private fun totalCpuMs(): Long = Process.getElapsedCpuTime() + childCpuMs()

    private fun childCpuMs(): Long {
        val myPid = Process.myPid()
        var ticks  = 0L
        try {
            File("/proc").listFiles()?.forEach { dir ->
                val pid = dir.name.toIntOrNull() ?: return@forEach
                if (pid == myPid) return@forEach
                try {
                    val status = File("/proc/$pid/status").readText()
                    val ppid   = Regex("PPid:\\s*(\\d+)").find(status)
                                     ?.groupValues?.get(1)?.toIntOrNull()
                    if (ppid == myPid) ticks += statTicks(pid)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return ticks * 1000L / HZ  // jiffies → ms
    }

    private fun statTicks(pid: Int): Long = try {
        val stat      = File("/proc/$pid/stat").readText()
        val afterComm = stat.indexOf(')') + 2
        val fields    = stat.substring(afterComm).trim().split(" ")
        fields[11].toLong() + fields[12].toLong()  // utime + stime
    } catch (_: Exception) { 0L }
}
