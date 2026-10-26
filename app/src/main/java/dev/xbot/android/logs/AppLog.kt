package dev.xbot.android.logs

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 端侧日志：内存环形缓冲 + 落盘 + 崩溃捕获。
 *
 * 定位方向：**永远开启、全局可用**，不要在排查某个具体问题时临时加探针
 * （之前为「列表自己跳到底部」「附件上传失败」各加过一个一次性诊断，用完就废，
 * 下次再出问题又得重新加）。所有排查都从「设置 → 日志」里取。
 *
 * 三层：
 * 1. 内存环形缓冲（[DEFAULT_CAPACITY] 条）—— UI 实时查看、复制；
 * 2. 落盘 `filesDir/logs/app.log`（超过 [MAX_FILE_BYTES] 滚动，保留 [MAX_FILE_BACKUPS] 份）——
 *    杀掉进程、崩溃之后还能查；
 * 3. logcat（tag 前缀 `xbot-`）—— 接 adb 时直接看。
 *
 * 敏感信息（cookie / token / password / signature…）在写入前统一脱敏，见 [redactSecrets]。
 */
object AppLog {

    const val DEFAULT_CAPACITY = 1500
    private const val MAX_FILE_BYTES = 512L * 1024L
    private const val MAX_FILE_BACKUPS = 2
    private const val LOGCAT_PREFIX = "xbot-"

    private val ring = LogRing(DEFAULT_CAPACITY)
    private val revisionState = MutableStateFlow(0L)
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "app-log").apply { isDaemon = true }
    }

    @Volatile
    private var logDir: File? = null

    /** 每写一条日志自增，UI 据此刷新（只在「日志」页收集）。 */
    val revision: StateFlow<Long> = revisionState

    fun install(context: Context) {
        if (logDir != null) return
        logDir = File(context.filesDir, "logs").apply { runCatching { mkdirs() } }
        installCrashHandler()
        i(
            "app",
            "==== start ${BuildInfo.versionName} (${BuildInfo.versionCode}) " +
                "android=${BuildInfo.androidRelease} sdk=${BuildInfo.sdkInt} ====",
        )
    }

    fun d(tag: String, message: String) = write("D", tag, message, null)

    fun i(tag: String, message: String) = write("I", tag, message, null)

    fun w(tag: String, message: String, error: Throwable? = null) = write("W", tag, message, error)

    fun e(tag: String, message: String, error: Throwable? = null) = write("E", tag, message, error)

    /** 内存里保留的日志（含未落盘的最新几条）。 */
    fun snapshotLines(): List<String> = ring.snapshot()

    fun snapshot(): String = snapshotLines().joinToString("\n")

    fun clear() {
        ring.clear()
        bumpRevision()
        logDir?.let { dir -> writer.execute { runCatching { dir.listFiles()?.forEach { it.delete() } } } }
    }

    /** 日志目录（落盘文件在这），没装好时是 null。 */
    fun logDir(): File? = logDir

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        val now = System.currentTimeMillis()
        val line = formatLogLine(now, level, tag, message, error)
        ring.add(line)
        bumpRevision()
        Log.println(level.toPriority(), LOGCAT_PREFIX + tag, message + errorSuffix(error))
        val dir = logDir ?: return
        writer.execute { appendToFile(dir, line) }
    }

    private fun bumpRevision() {
        revisionState.value = revisionState.value + 1
    }

    private fun appendToFile(dir: File, line: String) {
        runCatching {
            val file = File(dir, "app.log")
            if (file.length() > MAX_FILE_BYTES) {
                File(dir, "app.$MAX_FILE_BACKUPS.log").delete()
                for (index in MAX_FILE_BACKUPS - 1 downTo 1) {
                    File(dir, "app.$index.log").renameTo(File(dir, "app.${index + 1}.log"))
                }
                file.renameTo(File(dir, "app.1.log"))
            }
            File(dir, "app.log").appendText(line + "\n")
        }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // 崩溃时同步落盘：executor 可能等不到执行。
            runCatching {
                val line = formatLogLine(
                    System.currentTimeMillis(),
                    "E",
                    "crash",
                    "uncaught in ${thread.name}",
                    error,
                )
                ring.add(line)
                logDir?.let { appendToFile(it, line) }
            }
            previous?.uncaughtException(thread, error)
        }
    }
}

/** 只在日志模块内部用的构建信息，避免 logs 包直接依赖 BuildConfig。 */
object BuildInfo {
    var versionName: String = ""
    var versionCode: Long = 0L
    var androidRelease: String = ""
    var sdkInt: Int = 0
}

internal fun errorSuffix(error: Throwable?): String {
    if (error == null) return ""
    val buffer = StringWriter()
    error.printStackTrace(PrintWriter(buffer))
    return "\n" + buffer.toString().trimEnd()
}

internal fun String.toPriority(): Int = when (this) {
    "D" -> Log.DEBUG
    "W" -> Log.WARN
    "E" -> Log.ERROR
    else -> Log.INFO
}

private val TIME_FORMAT = ThreadLocal.withInitial {
    SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
}

/** 一条日志：[10-26 02:00:00.123] I/tag: message（多行错误堆栈原样附在后面）。 */
internal fun formatLogLine(
    timeMs: Long,
    level: String,
    tag: String,
    message: String,
    error: Throwable? = null,
): String {
    val time = TIME_FORMAT.get()!!.format(Date(timeMs))
    val head = "[$time] $level/$tag: ${redactSecrets(message)}"
    val tail = errorSuffix(error)
    return if (tail.isBlank()) head else head + "\n" + redactSecrets(tail.trimEnd())
}

/**
 * 日志里绝不能出现明文凭据：cookie、token、password、signature…
 *
 * 只按 `key=value` / `key: value` 形态替换值，不会把整行吞掉。
 */
private val SECRET_PATTERN = Regex(
    "(?i)\\b(cookie|set-cookie|authorization|password|passwd|secret|token|access_token|" +
        "accesstoken|refresh_token|refreshToken|api_key|apikey|signature)\\b\\s*[:=]\\s*(\"[^\"]*\"|[^\\s,;&}]+)",
)

internal fun redactSecrets(input: String): String =
    SECRET_PATTERN.replace(input) { match -> "${match.groupValues[1]}=***" }

/** 定长环形缓冲：超容量丢最旧的，读写都要线程安全（HTTP/IO 线程都会写）。 */
internal class LogRing(private val capacity: Int = AppLog.DEFAULT_CAPACITY) {

    private val lines = ArrayDeque<String>(capacity)

    @Synchronized
    fun add(line: String) {
        if (capacity <= 0) return
        if (lines.size >= capacity) lines.removeFirst()
        lines.addLast(line)
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun clear() = lines.clear()

    @Synchronized
    fun size(): Int = lines.size
}
