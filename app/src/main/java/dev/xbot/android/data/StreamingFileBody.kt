package dev.xbot.android.data

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.InputStream

/**
 * 流式上传的请求体：每次 `writeTo` 打开一次 [open]，把字节直接泵进 socket。
 *
 * 相比 `bytes.toRequestBody(...)`（先在内存里攒完整个文件），多附件时峰值内存从
 * 「所有文件之和」降到单次缓冲。两个硬约束：
 *
 * - **`contentLength` 必须准确**：服务端 `MaxBytesReader` 依赖 Content-Length，
 *   长度不对会被截断或直接拒绝；OkHttp 在写入字节数与声明值不一致时会抛
 *   `ProtocolException("unexpected end of stream")`。
 * - **`contentType` 不能抛异常**：`MediaType.parse` 对畸形字符串（如某些 provider
 *   返回的空串/带参数的怪值）会抛 `IllegalArgumentException`，而 `writeTo` 是在
 *   OkHttp 的 IO 线程上跑的 —— 抛在那里就成了「上传失败」且看不到原因。
 */
internal class StreamingFileBody(
    private val mime: String,
    private val length: Long,
    private val open: () -> InputStream,
) : RequestBody() {

    override fun contentType(): MediaType =
        runCatching { mime.toMediaType() }.getOrNull() ?: FALLBACK_MIME

    override fun contentLength(): Long = length

    override fun writeTo(sink: BufferedSink) {
        open().use { input -> sink.writeAll(input.source()) }
    }

    private companion object {
        val FALLBACK_MIME = "application/octet-stream".toMediaType()
    }
}
