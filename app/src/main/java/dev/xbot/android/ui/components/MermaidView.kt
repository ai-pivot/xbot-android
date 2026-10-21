package dev.xbot.android.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Renders a Mermaid fenced block with a local bundled mermaid.min.js.
 *
 * Height is measured from the rendered <svg> rect through
 * `evaluateJavascript` (not body.scrollHeight, which is viewport-sized in some
 * WebView configurations and caused persistent truncation). Tap the diagram to
 * open a full-screen, pinch-zoomable view.
 */
@Composable
fun MermaidView(
    code: String,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    var fullscreen by remember(code, dark) { mutableStateOf(false) }
    var heightDp by remember(code, dark) { mutableStateOf(180.dp) }

    Box(modifier = modifier.fillMaxWidth()) {
        MermaidCanvas(
            code = code,
            dark = dark,
            interactive = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp),
            onHeight = { px ->
                heightDp = px.coerceIn(80, 1600).dp
            },
        )

        // WebView consumes touches; this transparent layer makes the whole
        // diagram tappable for the full-screen preview.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { fullscreen = true },
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                    RoundedCornerShape(6.dp),
                )
                .padding(4.dp),
        ) {
            Icon(
                Icons.Filled.Fullscreen,
                contentDescription = "放大",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }

    if (fullscreen) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(Modifier.fillMaxSize()) {
                    MermaidCanvas(
                        code = code,
                        dark = dark,
                        interactive = true,
                        modifier = Modifier.fillMaxSize(),
                        onHeight = null,
                    )
                    IconButton(
                        onClick = { fullscreen = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MermaidCanvas(
    code: String,
    dark: Boolean,
    interactive: Boolean,
    modifier: Modifier,
    onHeight: ((Int) -> Unit)?,
) {
    key(code, dark, interactive) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = true
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = false
                    settings.blockNetworkLoads = true
                    settings.domStorageEnabled = false
                    settings.loadsImagesAutomatically = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = !interactive
                    settings.setSupportZoom(interactive)
                    settings.builtInZoomControls = interactive
                    settings.displayZoomControls = false
                    isVerticalScrollBarEnabled = interactive
                    isHorizontalScrollBarEnabled = interactive

                    if (onHeight != null) {
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                longArrayOf(200L, 500L, 1000L, 1800L).forEach { delay ->
                                    view.postDelayed({
                                        view.evaluateJavascript(MEASURE_JS) { raw ->
                                            val px = raw
                                                ?.trim()
                                                ?.removeSurrounding("\"")
                                                ?.toDoubleOrNull()
                                                ?.toInt()
                                            if (px != null && px > 0) onHeight(px)
                                        }
                                    }, delay)
                                }
                            }
                        }
                    }

                    loadDataWithBaseURL(
                        "file:///android_asset/mermaid/",
                        buildMermaidHtml(code, dark),
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            },
            modifier = modifier,
            onRelease = { it.destroy() },
        )
    }
}

private const val MEASURE_JS = """
    (function () {
      var svg = document.querySelector('svg');
      if (svg) {
        var rect = svg.getBoundingClientRect();
        return Math.ceil(rect.height + 16);
      }
      return Math.ceil(document.body.scrollHeight);
    })()
"""

private fun buildMermaidHtml(code: String, dark: Boolean): String {
    val escaped = code
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    val theme = if (dark) "dark" else "default"
    return """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <style>
            html, body { margin: 0; padding: 0; background: transparent; overflow: hidden; }
            body { font-family: sans-serif; }
            pre.mermaid { margin: 0; padding: 8px; white-space: pre-wrap; }
            svg { max-width: 100%; height: auto; display: block; margin: 0 auto; }
          </style>
          <script src="mermaid.min.js"></script>
        </head>
        <body>
          <pre class="mermaid">$escaped</pre>
          <script>
            mermaid.initialize({ startOnLoad: false, securityLevel: 'strict', theme: '$theme' });
            mermaid.run({ querySelector: '.mermaid' }).catch(function (e) {
              document.body.textContent = 'Mermaid error: ' + e;
            });
          </script>
        </body>
        </html>
    """.trimIndent()
}
