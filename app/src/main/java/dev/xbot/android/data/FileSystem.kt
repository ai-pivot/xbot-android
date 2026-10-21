package dev.xbot.android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `POST /api/fs/list` 的一条目录项（服务端 `fsListEntry`）。 */
@Serializable
data class FsEntry(
    val name: String = "",
    @SerialName("isDir") val isDir: Boolean = false,
    val size: Long = 0L,
    val mode: String = "",
    @SerialName("modTime") val modTime: String = "",
)

@Serializable
data class FsListResponse(
    val entries: List<FsEntry> = emptyList(),
)

/** 拼子路径：保留根的 `/`，不产生 `//`。 */
fun joinPath(parent: String, name: String): String {
    val base = parent.trim()
    if (name.isEmpty()) return base.ifEmpty { "/" }
    if (base.isEmpty()) return "/$name"
    return if (base.endsWith("/")) "$base$name" else "$base/$name"
}

/** 上一级：`/a/b` → `/a`，`/a` → `/`，`/` → `/`。 */
fun parentPath(path: String): String {
    val trimmed = path.trim().trimEnd('/')
    if (trimmed.isEmpty()) return "/"
    val cut = trimmed.lastIndexOf('/')
    return if (cut <= 0) "/" else trimmed.substring(0, cut)
}

/**
 * 目录浏览只关心目录，但要保留错误信息：服务端返回空列表说明这个目录是空的，
 * 而不是「加载失败」，UI 文案不同。
 */
fun FsListResponse.directories(): List<FsEntry> =
    entries.filter { it.isDir && it.name.isNotEmpty() }.sortedBy { it.name.lowercase() }
