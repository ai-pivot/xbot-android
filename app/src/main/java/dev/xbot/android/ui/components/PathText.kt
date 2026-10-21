package dev.xbot.android.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** `/home/cjw/xbot-android` / `/Users/x/…` 之外的常见前缀，换成 `~/`。 */
private val HOME_PREFIX = Regex("^/(?:home|Users|root)/?[^/]*/")

/**
 * 路径尾巴最重要，所以按段左截断：`/home/cjw/xbot-android/app` → `…/xbot-android/app`。
 * 段数不多、或本来就以 `~` 开头时原样返回（去掉尾部斜杠）。
 */
fun shortenPath(raw: String, keepSegments: Int = 2): String {
    val path = raw.trim()
    if (path.isEmpty()) return ""
    val expanded = HOME_PREFIX.replace(path, "~/")
    val segments = expanded.trimEnd('/').split('/').filter { it.isNotEmpty() }
    if (segments.size <= keepSegments + 1) return expanded.trimEnd('/')
    return "…/" + segments.takeLast(keepSegments).joinToString("/")
}

/** 工作目录：小文件夹图标 + 截断后的路径。路径为空时整体不占位。 */
@Composable
fun PathText(
    path: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    style: TextStyle = MaterialTheme.typography.labelSmall,
) {
    if (path.isBlank()) return

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            modifier = Modifier.size(11.dp),
            tint = color,
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = shortenPath(path),
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
