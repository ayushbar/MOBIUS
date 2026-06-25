package com.jlr.adaptive

object StreamConfig {

    // Big Buck Bunny (Mux test stream)
    // Tiers: 528 kbps@640×360, 2128 kbps@1280×720, others
    const val DEFAULT_URL =
        "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"

    // 528 kbps tier has PiP baked into frames — skip it by flooring at 1128 kbps.
    enum class Quality(
        val label:      String,
        val maxBitrate: Int,
    ) {
        Q_SD("480p",   1_300_000),     // 1128 kbps → 848×480
        Q_HD("720p",   2_500_000),     // 2128 kbps → 1280×720
        Q_FHD("1080p", Int.MAX_VALUE), // highest available tier
    }
}
