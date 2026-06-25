package com.jlr.streamdemo.ui

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.jlr.streamdemo.StreamConfig

// Bridge so JS busy-loop can push CPU readings back to Kotlin on the main thread.
private class JsCpuBridge(private val onReport: (Float) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    @JavascriptInterface
    fun report(cpu: Double) = main.post { onReport(cpu.toFloat()) }
}

@Composable
fun WebViewPlayer(
    streamUrl:   String,
    quality:     StreamConfig.Quality,
    onCpuReport: (Float) -> Unit = {},
    modifier:    Modifier = Modifier,
) {

    val html = remember {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
            <style>
                @keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.3} }
                @keyframes slide { from{transform:translateX(-200%)} to{transform:translateX(500%)} }
                * { margin:0; padding:0; box-sizing:border-box; font-family:monospace; }
                html, body { width:100%; height:100%; background:#0d0d0d; color:#eee; overflow:hidden; }
                #left {
                    position:fixed; top:12px; left:12px; bottom:12px; width:36%;
                    display:flex; flex-direction:column; justify-content:center;
                    gap:12px; padding-right:12px; border-right:1px solid #2a2a3e;
                }
                #badge h1 { color:#e8b84b; font-size:1.3em; line-height:1.3; }
                #badge p  { color:#aaa; font-size:.85em; margin-top:8px; }
                #activity { width:100%; height:12px; background:#1a1a2e; border-radius:6px; overflow:hidden; position:relative; }
                #slider   { position:absolute; top:0; height:100%; width:20%;
                            background:linear-gradient(90deg,transparent,#e8b84b,#00aaff,transparent);
                            animation:slide 1.5s linear infinite; }
                #stats {
                    position:fixed; top:12px; left:38%; right:12px; bottom:12px;
                    display:grid; grid-template-columns:auto 1fr;
                    gap:0 14px; align-content:center; row-gap:8px; padding-left:16px;
                }
                .sk { color:#888; font-size:1em; white-space:nowrap; }
                .sv { color:#fff; font-size:1em; font-weight:bold; }
                #dot { display:inline-block; width:8px; height:8px; background:#44cc88;
                       border-radius:50%; animation:pulse 1s ease-in-out infinite; margin-right:5px; }
            </style>
        </head>
        <body>
            <video id="v" autoplay muted playsinline
                   style="position:absolute;left:-9999px;width:1px;height:1px;"></video>

            <div id="left">
                <div id="badge">
                    <h1>WebView<br>+ hls.js</h1>
                    <p><span id="dot"></span><span id="st">Initialising…</span></p>
                </div>
                <div id="activity"><div id="slider"></div></div>
            </div>

            <div id="stats">
                <span class="sk">Level</span>    <span class="sv" id="lv">-</span>
                <span class="sk">Bitrate</span>  <span class="sv" id="br">-</span>
                <span class="sk">Segments</span> <span class="sv" id="sg">0</span>
                <span class="sk">Buffer</span>   <span class="sv" id="bu">-</span>
                <span class="sk">Uptime</span>   <span class="sv" id="up">0 s</span>
                <span class="sk">Decoder</span>  <span class="sv">JS / MSE</span>
            </div>

            <script src="https://cdn.jsdelivr.net/npm/hls.js@1/dist/hls.min.js"></script>
            <script>
            (function() {
                var video = document.getElementById('v');
                var t0    = Date.now();
                var segs  = 0;
                var hls   = null;

                function $$(id) { return document.getElementById(id); }

                // Called from Kotlin via evaluateJavascript("changeLevel(N)")
                // level >= 0 → lock to that hls.js index; -1 → ABR auto
                window.changeLevel = function(level) {
                    window._desiredLevel = level;
                    if (!hls) return;
                    applyLevel(level);
                };
                window._desiredLevel = 0;

                function applyLevel(level) {
                    if (level < 0) {
                        hls.autoLevelEnabled = true;
                    } else {
                        hls.autoLevelEnabled = false;
                        hls.currentLevel = level;
                        var lv = hls.levels[level];
                        if (lv) {
                            $$('lv').textContent = lv.width+'x'+lv.height;
                            $$('br').textContent = (lv.bitrate/1000).toFixed(0)+' kbps';
                        }
                    }
                }

                setInterval(function() {
                    $$('up').textContent = ((Date.now()-t0)/1000).toFixed(0) + ' s';
                    if (video.buffered.length > 0) {
                        var b = (video.buffered.end(video.buffered.length-1)
                                 - video.currentTime).toFixed(1);
                        $$('bu').textContent = b + ' s';
                    }
                }, 500);

                var src = '$streamUrl';
                if (typeof Hls !== 'undefined' && Hls.isSupported()) {
                    hls = new Hls({ enableWorker:true, maxBufferLength:12,
                                    startLevel:0, autoLevelEnabled:false });

                    hls.on(Hls.Events.MANIFEST_PARSED, function(e, d) {
                        applyLevel(window._desiredLevel);
                        $$('st').textContent = 'Streaming ▶';
                        video.play();
                    });
                    hls.on(Hls.Events.LEVEL_SWITCHED, function(e, d) {
                        var lv = hls.levels[d.level];
                        if (lv) {
                            $$('lv').textContent = lv.width+'x'+lv.height;
                            $$('br').textContent = (lv.bitrate/1000).toFixed(0)+' kbps';
                        }
                    });
                    hls.on(Hls.Events.FRAG_LOADED, function() {
                        segs++; $$('sg').textContent = segs;
                        $$('st').textContent = 'Streaming ▶';
                    });
                    hls.on(Hls.Events.ERROR, function(e, d) {
                        if (d.fatal) $$('st').textContent = 'Error: '+d.type;
                    });
                    hls.loadSource(src);
                    hls.attachMedia(video);
                } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                    $$('st').textContent = 'Native HLS';
                    video.src = src; video.play();
                } else {
                    $$('st').textContent = 'HLS not supported';
                }
            })();

            // ── CPU probe: same busy-loop as browser-demo.html ─────────────
            // Reports combined main-thread + worker load to Android via
            // JavascriptInterface (window.AndroidCpu.report) every 500 ms.
            // This makes the WebView CPU reading directly comparable to the
            // browser demo since both use identical measurement code.
            (function() {
                var PROBE_MS = 8;
                var mainBase = 0;
                var workerLoad = 0;
                var mainLoad   = 0;

                function probe() {
                    var end = performance.now() + PROBE_MS;
                    var n = 0; while (performance.now() < end) n++;
                    return n;
                }
                function calibrate() {
                    var best = 0;
                    for (var i = 0; i < 5; i++) best = Math.max(best, probe());
                    if (best > mainBase) mainBase = best;
                }

                // Worker thread probe (hls.js demuxer runs here)
                var blob = new Blob([
                    'var B=8,b=0;function p(){var e=performance.now()+B,n=0;while(performance.now()<e)n++;return n;}' +
                    'var x=0;for(var i=0;i<5;i++)x=Math.max(x,p());b=x;' +
                    'setInterval(function(){var bb=0;for(var i=0;i<5;i++)bb=Math.max(bb,p());if(bb>b)b=bb;},30000);' +
                    'setInterval(function(){if(!b)return;var n=p();self.postMessage(Math.max(0,(1-n/b)*100));},500);'
                ], {type:'application/javascript'});
                var w = new Worker(URL.createObjectURL(blob));
                w.onmessage = function(e) { workerLoad = e.data; };

                setTimeout(function() {
                    calibrate();
                    setInterval(function() {
                        if (!mainBase) return;
                        var n   = probe();
                        var raw = Math.max(0, (1 - n / mainBase) * 100);
                        mainLoad = mainLoad * 0.5 + raw * 0.5;
                        var combined = Math.max(mainLoad, workerLoad);
                        if (window.AndroidCpu) window.AndroidCpu.report(combined);
                    }, 500);
                    setInterval(calibrate, 30000);
                }, 800);
            })();
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    val webViewRef = remember { WebViewRef() }

    // Live quality switch — tells hls.js to lock to a different level index.
    LaunchedEffect(quality) {
        webViewRef.view?.evaluateJavascript("changeLevel(${quality.hlsLevel})", null)
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef.view?.apply {
                loadUrl("about:blank")
                destroy()
            }
            webViewRef.view = null
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).also { wv ->
                webViewRef.view = wv
                wv.settings.apply {
                    javaScriptEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    domStorageEnabled = true
                    allowFileAccess  = false
                }
                wv.addJavascriptInterface(JsCpuBridge(onCpuReport), "AndroidCpu")
                wv.webChromeClient = WebChromeClient()
                wv.webViewClient   = WebViewClient()
                wv.loadDataWithBaseURL(
                    "https://jlr.demo/",
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        modifier = modifier,
    )
}

private class WebViewRef { var view: WebView? = null }
