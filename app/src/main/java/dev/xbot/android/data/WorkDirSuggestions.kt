package dev.xbot.android.data

/**
 * 新建会话时的工作目录候选。
 *
 * 服务端没有「浏览目录」接口（工作目录在服务器上，手机侧看不到），所以只能给候选 + 手输：
 * 先放最近用过的（`AppPrefs.recentWorkDirs`，最新在前），再补现有会话里出现过的 `work_dir`，
 * 去重后截断。纯函数，便于单测。
 */
fun workDirSuggestions(
    recent: List<String>,
    sessions: List<UserChat>,
    limit: Int = 5,
): List<String> {
    if (limit <= 0) return emptyList()
    val out = LinkedHashSet<String>()
    recent.forEach { raw ->
        val path = raw.trim()
        if (path.isNotEmpty()) out += path
    }
    sessions.forEach { session ->
        val path = session.workDir.trim()
        if (path.isNotEmpty()) out += path
    }
    return out.take(limit)
}
