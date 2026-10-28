package dev.xbot.android.logs

import android.content.Context
import android.os.Build
import dev.xbot.android.data.XbotApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 把「内存日志 + 落盘日志 + 设备信息」打成一个 zip，用于导出或上传。
 *
 * 上传走现有的 `POST /api/files/upload`（和附件同一条通道，10MB 限制对几百 KB 的
 * 日志包完全够用），返回的 key 可以在同源 `/api/files/download?key=` 打开。
 */
object LogBundle {

    data class Packaged(val file: File, val entries: Int)

    data class Uploaded(val uploadKey: String, val url: String, val size: Long)

    /** 打成 zip（默认落在 `cacheDir/logs/`）。[extra] 可以塞一条「用户备注」。 */
    fun packageZip(context: Context, extra: String? = null): Packaged {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        val target = File(dir, "xbot-log-$stamp.zip")
        var entries = 0
        ZipOutputStream(FileOutputStream(target).buffered()).use { zip ->
            fun put(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
                entries += 1
            }

            put("device.txt", deviceInfo(context, extra))
            put("session.txt", AppLog.snapshot())
            AppLog.logDir()?.listFiles()
                ?.filter { it.isFile }
                ?.sortedBy { it.name }
                ?.forEach { file ->
                    runCatching {
                        zip.putNextEntry(ZipEntry("files/${file.name}"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        entries += 1
                    }
                }
        }
        return Packaged(target, entries)
    }

    suspend fun upload(context: Context, api: XbotApi): Uploaded = withContext(Dispatchers.IO) {
        val packaged = packageZip(context)
        val bytes = packaged.file.readBytes()
        AppLog.i("log", "upload bundle ${packaged.file.name} (${bytes.size}B, ${packaged.entries} entries)")
        val response = api.uploadFileBytes(bytes, packaged.file.name, "application/zip")
        val key = response.uploadKey
        AppLog.i("log", "bundle uploaded key=$key")
        Uploaded(uploadKey = key, url = api.downloadUrl(key), size = bytes.size.toLong())
    }

    /** 设备/构建信息：排查问题需要，但不含任何凭据。 */
    fun deviceInfo(context: Context, extra: String? = null): String {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val prefs = (context.applicationContext as? dev.xbot.android.XbotApp)?.container?.prefs
        return buildString {
            appendLine("time(UTC): $now")
            appendLine("app: ${BuildInfo.versionName} (${BuildInfo.versionCode})")
            appendLine("package: ${context.packageName}")
            appendLine("android: ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("abi: ${Build.SUPPORTED_ABIS.joinToString(",")}")
            appendLine("locale: ${Locale.getDefault()}")
            appendLine("server: ${prefs?.serverUrl?.ifBlank { "（未配置）" } ?: "（未知）"}")
            appendLine("channel: ${prefs?.activeChannel ?: "（未知）"}")
            appendLine("chat: ${prefs?.activeChatId?.ifBlank { "（无）" } ?: "（未知）"}")
            appendLine("log lines: ${AppLog.snapshotLines().size}")
            if (!extra.isNullOrBlank()) {
                appendLine()
                appendLine("note:")
                appendLine(extra)
            }
        }
    }
}
