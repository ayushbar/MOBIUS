package com.jlr.streamdemo

object StreamConfig {

    enum class VideoSource(val label: String, val url: String) {
        BIPBOP(
            "BipBop",
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"
        ),
        BUNNY(
            "Bunny",
            "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        ),
        TEARS(
            "Tears",
            "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"
        ),
        BIPBOP16(
            "16×9",
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8"
        ),
    }

    // Quality presets — applied as an upper-bound constraint.
    // ExoPlayer and hls.js each pick the best rendition that fits within the cap.
    // hlsLevel = index in hls.js's bitrate-sorted level list (-1 = ABR auto).
    enum class Quality(
        val label: String,
        val maxWidth: Int,
        val maxHeight: Int,
        val maxBitrate: Int,
        val hlsLevel: Int,
    ) {
        Q270("270p",  480,           270,           600_000,       0),
        Q360("360p",  640,           360,         1_000_000,       1),
        Q432("432p",  768,           432,         1_400_000,       2),
        AUTO("Auto",  Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, -1),
    }
}
