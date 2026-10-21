package dev.xbot.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.xbot.android.XbotApp

private val imageHeightMemory = mutableMapOf<String, Dp>()
private val IMAGE_MIN_HEIGHT = 140.dp
private val IMAGE_MAX_HEIGHT = 400.dp

/**
 * Markdown image renderer.
 *
 * Uses Coil with the app's OkHttp client so /api/files/download and
 * /api/files/viewimg requests carry the xbot session cookie. Images are
 * height-capped; tapping opens a full-screen pinch-zoom viewer.
 */
@Composable
fun MarkdownImage(
    alt: String,
    rawUrl: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as XbotApp
    val api = app.container.api
    val baseUrl = api.baseUrl()
    val mediaBase = app.container.prefs.mediaBaseUrl
    val resolved = remember(rawUrl, baseUrl, mediaBase) {
        resolveImageUrl(baseUrl, rawUrl, mediaBase)
    }

    if (resolved == null) {
        ImageFallback(alt, rawUrl, null, modifier)
        return
    }

    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .okHttpClient(api.client)
            .crossfade(true)
            .build()
    }
    val request = remember(resolved) {
        ImageRequest.Builder(context)
            .data(resolved)
            .crossfade(true)
            .build()
    }

    var failed by remember(resolved) { mutableStateOf(false) }
    var loading by remember(resolved) { mutableStateOf(true) }
    var fullscreen by remember { mutableStateOf(false) }
    var imageHeight by remember(resolved) {
        mutableStateOf(imageHeightMemory[resolved] ?: IMAGE_MIN_HEIGHT)
    }

    if (failed) {
        ImageFallback(alt, rawUrl, resolved, modifier)
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight.coerceAtMost(IMAGE_MAX_HEIGHT))
                .clip(RoundedCornerShape(10.dp))
                .clickable { fullscreen = true },
        ) {
            AsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = alt.ifBlank { "图片" },
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onLoading = { loading = true },
                onSuccess = { success ->
                    loading = false
                    val drawable = success.result.drawable
                    val width = drawable.intrinsicWidth
                    val height = drawable.intrinsicHeight
                    if (width > 0 && height > 0) {
                        val desired = (availableWidth.value * height.toFloat() / width).dp
                            .coerceIn(IMAGE_MIN_HEIGHT, IMAGE_MAX_HEIGHT)
                        imageHeight = desired
                        imageHeightMemory[resolved] = desired
                    }
                },
                onError = {
                    loading = false
                    failed = true
                },
            )
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }

    if (fullscreen) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ZoomableImageDialog(
                request = request,
                imageLoader = imageLoader,
                alt = alt,
                onClose = { fullscreen = false },
            )
        }
    }
}

@Composable
private fun ZoomableImageDialog(
    request: ImageRequest,
    imageLoader: ImageLoader,
    alt: String,
    onClose: () -> Unit,
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, 5f)
                    scale = next
                    offset = if (next <= 1f) Offset.Zero else offset + pan
                }
            },
    ) {
        AsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = alt.ifBlank { "图片" },
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "关闭",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun ImageFallback(
    alt: String,
    rawUrl: String,
    resolved: String?,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable {
                val target = resolved ?: rawUrl
                if (target.isNotBlank()) {
                    runCatching { uriHandler.openUri(target) }
                }
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🖼", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                alt.ifBlank { "图片加载失败" },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                if (resolved == null && rawUrl.contains("localhost")) {
                    "未配置媒体地址"
                } else {
                    rawUrl
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "打开",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
