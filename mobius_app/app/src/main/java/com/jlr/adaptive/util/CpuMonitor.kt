package com.jlr.adaptive.util

import android.os.Process
import android.os.SystemClock
import java.io.File

object CpuMonitor {
    private const val HZ = 100L

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
        return ticks * 1000L / HZ
    }

    private fun statTicks(pid: Int): Long = try {
        val stat      = File("/proc/$pid/stat").readText()
        val afterComm = stat.indexOf(')') + 2
        val fields    = stat.substring(afterComm).trim().split(" ")
        fields[11].toLong() + fields[12].toLong()
    } catch (_: Exception) { 0L }
}
