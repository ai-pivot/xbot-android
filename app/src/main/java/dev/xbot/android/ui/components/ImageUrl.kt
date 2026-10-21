package dev.xbot.android.ui.components

import java.net.URI

/**
 * Resolve a Markdown image URL.
 *
 * - same-origin relative paths join the xbot server base URL
 * - absolute http(s) URLs are kept
 * - localhost/127.0.0.1 is rewritten ONLY to the explicitly configured media
 *   base URL (MinIO/public object host), never to the xbot server host
 * - javascript/file/data URLs are rejected
 */
internal fun resolveImageUrl(
    baseUrl: String,
    raw: String,
    mediaBaseUrl: String = "",
): String? {
    val value = raw.trim()
    if (value.isBlank()) return null

    val lower = value.lowercase()
    if (lower.startsWith("javascript:") ||
        lower.startsWith("file:") ||
        lower.startsWith("data:")
    ) {
        return null
    }

    val base = baseUrl.trim().trimEnd('/')

    if (lower.startsWith("http://") || lower.startsWith("https://")) {
        val uri = runCatching { URI(value) }.getOrNull() ?: return value
        val host = uri.host ?: return value

        if (host == "localhost" || host == "127.0.0.1") {
            val media = mediaBaseUrl.trim().trimEnd('/')
            if (media.isBlank()) return null
            val mediaUri = runCatching { URI(media) }.getOrNull() ?: return null
            val mediaHost = mediaUri.host ?: return null
            return runCatching {
                URI(
                    mediaUri.scheme ?: uri.scheme,
                    null,
                    mediaHost,
                    mediaUri.port,
                    uri.path,
                    uri.query,
                    uri.fragment,
                ).toString()
            }.getOrNull()
        }

        return value
    }

    if (base.isBlank()) return null
    return if (value.startsWith("/")) {
        base + value
    } else {
        "$base/$value"
    }
}
