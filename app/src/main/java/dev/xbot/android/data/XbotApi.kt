package dev.xbot.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * REST + RPC client for the existing xbot web channel.
 *
 * No server-side RPC changes are required: this speaks the same surface the
 * React web client uses (POST /api endpoints, POST /api/rpc, GET /api/sse).
 */
class XbotApi(private val prefs: AppPrefs) {

    private val cookieJar = PersistentCookieJar(prefs)

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    /** Long-lived SSE client: no read timeout, EventSource owns the connection. */
    val sseClient: OkHttpClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun baseUrl(): String = normalizeBaseUrl(prefs.serverUrl)

    fun hasServer(): Boolean = baseUrl().isNotBlank()

    fun clearSession() = cookieJar.clear()

    // ------------------------------------------------------------------
    // Auth
    // ------------------------------------------------------------------

    suspend fun authConfig(): AuthConfig {
        val text = execute(post("/api/auth/config", "{}"))
        return decodeEnvelope<AuthConfig>(text)
    }

    suspend fun login(username: String, password: String) {
        val body = XbotJson.encodeToString(LoginRequest(username.trim(), password))
        val text = execute(post("/api/auth/login", body))
        decodeEnvelope<AuthResponse>(text)
        prefs.secrets.put(username.trim(), password)
    }

    /** First-user bootstrap (server allows it only while web_users is empty). */
    suspend fun register(username: String, password: String) {
        val body = XbotJson.encodeToString(LoginRequest(username.trim(), password))
        execute(post("/api/auth/register", body))
        // Registration only creates the account; establish the session cookie.
        login(username, password)
    }

    suspend fun logout() {
        runCatching { execute(post("/api/auth/logout", "{}")) }
        clearSession()
        prefs.secrets.clear()
    }

    /** Try a stored credential login. Returns true when the cookie is valid again. */
    suspend fun trySilentLogin(): Boolean {
        val user = prefs.secrets.username()
        val pass = prefs.secrets.password() ?: return false
        if (user.isBlank()) return false
        return runCatching {
            login(user, pass)
            true
        }.getOrDefault(false)
    }

    // ------------------------------------------------------------------
    // Sessions & history
    // ------------------------------------------------------------------

    suspend fun sessionTree(): SessionTreeResponse =
        decodeEnvelope(execute(post("/api/session-tree", "{}")))

    suspend fun chats(): ChatsResponse =
        decodeEnvelope(execute(post("/api/chats/list", "{}")))

    suspend fun createChat(label: String): String {
        val body = buildJsonObject { put("label", label) }.toString()
        return decodeEnvelope<CreateChatResponse>(execute(post("/api/chats/create", body))).chatId
    }

    /**
     * 设置会话的工作目录。
     *
     * `force = true` 表示这是**显式用户动作**（新建会话弹窗 / 会话信息里改路径），
     * 服务端会原样采用传入路径，不会被既有 cwd 或自动推断值顶掉
     * （见服务端 `rpc_table.go` 的 set_cwd 与 web 端 `setCwd`）。
     *
     * 注意：`POST /api/chats/create` 没有 work_dir 参数，所以只能在建完会话后设置；
     * 这一步失败不代表会话创建失败。
     */
    /**
     * 列出服务器上某个目录的内容（Web 端目录树用的同一个接口）。
     * 新建会话时的工作目录选择器靠它做逐级浏览：工作目录在**服务器**上，
     * 手机侧的文件选择器（SAF）拿不到这种路径。
     */
    suspend fun listDir(path: String, showHidden: Boolean = false): List<FsEntry> {
        val body = buildJsonObject {
            put("path", path)
            put("show_hidden", showHidden)
        }.toString()
        return decodeEnvelope<FsListResponse>(execute(post("/api/fs/list", body))).entries
    }

    suspend fun setCwd(chatId: String, dir: String, channel: String = "web") {
        rpc(
            "set_cwd",
            buildJsonObject {
                put("channel", channel)
                put("chat_id", chatId)
                put("dir", dir)
                put("force", true)
            },
        )
    }

    suspend fun switchChat(chatId: String, channel: String = "web") {
        val url = urlBuilder("/api/chats/${encodePath(chatId)}/switch")
            .addQueryParameter("channel", channel)
            .build()
        execute(Request.Builder().url(url).post("{}".toRequestBody(JSON)).build())
    }

    suspend fun deleteChat(chatId: String, channel: String = "web") {
        val body = buildJsonObject { put("channel", channel) }.toString()
        execute(post("/api/chats/${encodePath(chatId)}/delete", body))
    }

    suspend fun renameChat(chatId: String, label: String, channel: String = "web") {
        val url = urlBuilder("/api/chats/${encodePath(chatId)}/rename")
            .addQueryParameter("channel", channel)
            .build()
        val body = buildJsonObject { put("label", label) }.toString()
        execute(Request.Builder().url(url).post(body.toRequestBody(JSON)).build())
    }

    suspend fun history(
        chatId: String,
        channel: String = "web",
        limit: Int = 40,
        beforeId: Long = 0,
    ): HistoryResponse {
        val body = buildJsonObject {
            put("chat_id", chatId)
            put("channel", channel)
            put("limit", limit)
            put("before_id", beforeId)
        }.toString()
        return decodeEnvelope(execute(post("/api/history", body)))
    }

    // ------------------------------------------------------------------
    // Sending / control
    // ------------------------------------------------------------------

    suspend fun sendMessage(
        content: String,
        chatId: String,
        channel: String = "web",
        uploadKeys: List<String> = emptyList(),
        fileNames: List<String> = emptyList(),
        fileSizes: List<Long> = emptyList(),
        fileMimes: List<String> = emptyList(),
        requestId: String = "",
        interrupt: Boolean = false,
    ): SendMessageResponse {
        val req = SendMessageRequest(
            content = content,
            chatId = chatId,
            channel = channel,
            uploadKeys = uploadKeys,
            fileNames = fileNames,
            fileSizes = fileSizes,
            fileMimes = fileMimes,
            id = requestId.ifBlank { null },
            interrupt = interrupt,
        )
        val body = XbotJson.encodeToString(req)
        return decodeEnvelope(execute(post("/api/message", body)))
    }

    suspend fun cancel(chatId: String, channel: String = "web") {
        val body = buildJsonObject {
            put("chat_id", chatId)
            put("channel", channel)
        }.toString()
        execute(post("/api/cancel", body))
    }

    suspend fun respondAskUser(
        chatId: String,
        answers: Map<String, String>,
        cancelled: Boolean = false,
        channel: String = "web",
    ) {
        val body = XbotJson.encodeToString(
            AskUserRequest(
                channel = channel,
                chatId = chatId,
                answers = answers,
                cancelled = cancelled,
            ),
        )
        execute(post("/api/ask_user/respond", body))
    }

    suspend fun uploadFile(
        bytes: ByteArray,
        fileName: String,
        mime: String,
    ): UploadResponse {
        val part = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                bytes.toRequestBody(mime.toMediaType()),
            )
            .build()
        val text = execute(
            Request.Builder()
                .url(urlBuilder("/api/files/upload").build())
                .post(part)
                .build(),
        )
        return decodeEnvelope(text)
    }

    // ------------------------------------------------------------------
    // RPC (existing /api/rpc dispatch table — unchanged)
    // ------------------------------------------------------------------

    suspend fun rpc(method: String, params: JsonObject = JsonObject(emptyMap())): JsonElement {
        val body = buildJsonObject {
            put("method", method)
            put("params", params)
        }.toString()
        return unwrapRpcEnvelope(
            XbotJson.parseToJsonElement(execute(post("/api/rpc", body))),
        )
    }

    suspend fun getActiveProgress(chatId: String, channel: String = "web"): ProgressEvent? {
        val result = rpc(
            "get_active_progress",
            buildJsonObject {
                put("channel", channel)
                put("chat_id", chatId)
                put("from_iteration", 0)
            },
        )
        return if (result is JsonNull) null else runCatching {
            XbotJson.decodeFromJsonElement(ProgressEvent.serializer(), result)
        }.getOrNull()
    }

    suspend fun getPendingAskUser(chatId: String, channel: String = "web"): ProgressEvent? {
        val result = rpc(
            "get_pending_ask_user",
            buildJsonObject {
                put("channel", channel)
                put("chat_id", chatId)
            },
        )
        return if (result is JsonNull) null else runCatching {
            XbotJson.decodeFromJsonElement(ProgressEvent.serializer(), result)
        }.getOrNull()
    }

    /**
     * 轻量会话状态：只返回 token 用量与 cwd。
     * 上下文预算指示器就靠它的 `token_usage.max_context_tokens / usage_percent`。
     */
    suspend fun sessionStatus(
        chatId: String,
        channel: String = "web",
    ): SessionStatusResponse {
        val body = buildJsonObject {
            put("chat_id", chatId)
            put("channel", channel)
        }.toString()
        return decodeEnvelope(execute(post("/api/session/status", body)))
    }

    // ------------------------------------------------------------------
    // Models & user settings
    // ------------------------------------------------------------------

    suspend fun listModelEntries(): List<ModelEntry> =
        decodeRpc("list_all_model_entries")

    suspend fun listSubscriptions(): List<Subscription> =
        decodeRpc("list_subscriptions")

    suspend fun setSubscriptionEnabled(subId: String, enabled: Boolean) {
        rpc(
            "set_subscription_enabled",
            buildJsonObject {
                put("sub_id", subId)
                put("enabled", enabled)
            },
        )
    }

    suspend fun setModelEnabled(subId: String, model: String, enabled: Boolean) {
        rpc(
            "set_model_enabled",
            buildJsonObject {
                put("sub_id", subId)
                put("model", model)
                put("enabled", enabled)
            },
        )
    }

    suspend fun getSessionModel(chatId: String, channel: String = "web"): SessionModel =
        decodeRpc(
            "get_session_subscription",
            buildJsonObject {
                put("chat_id", chatId)
                put("channel", channel)
            },
        )

    suspend fun selectModel(chatId: String, channel: String, subId: String, model: String) {
        rpc(
            "select_model",
            buildJsonObject {
                put("chat_id", chatId)
                put("channel", channel)
                put("sub_id", subId)
                put("model", model)
            },
        )
    }

    suspend fun getDefaultModel(): DefaultModel = decodeRpc("get_default_model")

    suspend fun setDefaultModel(subId: String, model: String) {
        rpc(
            "set_default_model",
            buildJsonObject {
                put("sub_id", subId)
                put("model", model)
            },
        )
    }

    suspend fun getThinkingMode(): String = decodeRpc("get_user_thinking_mode")

    suspend fun setThinkingMode(mode: String) {
        rpc("set_user_thinking_mode", buildJsonObject { put("mode", mode) })
    }

    suspend fun getContextMode(): String = decodeRpc("get_context_mode")

    suspend fun setContextMode(mode: String) {
        rpc("set_context_mode", buildJsonObject { put("mode", mode) })
    }

    suspend fun getMaxOutputTokens(): Int = decodeRpc("get_user_max_output_tokens")

    suspend fun setMaxOutputTokens(value: Int) {
        rpc("set_user_max_output_tokens", buildJsonObject { put("max_tokens", value) })
    }

    suspend fun getMaxContextTokens(): Int = decodeRpc("get_user_max_context")

    suspend fun setMaxContextTokens(value: Int) {
        rpc("set_user_max_context", buildJsonObject { put("max_context", value) })
    }

    suspend fun getCompressionThreshold(): String {
        val obj = rpc(
            "get_settings",
            buildJsonObject { put("namespace", "agent") },
        ).jsonObject
        return obj["compression_threshold"]?.jsonPrimitive?.content.orEmpty()
    }

    suspend fun setCompressionThreshold(value: Double) {
        rpc("set_compression_threshold", buildJsonObject { put("threshold", value) })
    }

    suspend fun getToolSettings(): List<ToolSetting> =
        decodeRpc<ToolSettingsResponse>("get_tools_settings").tools

    suspend fun setToolEnabled(name: String, enabled: Boolean) {
        rpc(
            "set_tool_enabled",
            buildJsonObject {
                put("name", name)
                put("enabled", enabled)
            },
        )
    }

    suspend fun getLLMConcurrency(): Int = decodeRpc("get_llm_concurrency")

    suspend fun setLLMConcurrency(value: Int) {
        rpc("set_llm_concurrency", buildJsonObject { put("personal", value) })
    }

    // ------------------------------------------------------------------
    // Session actions: fork / rewind
    // ------------------------------------------------------------------

    suspend fun forkChat(
        sourceChatId: String,
        sourceChannel: String = "web",
        label: String,
    ): ForkResponse {
        val body = buildJsonObject {
            put("source_chat_id", sourceChatId)
            put("source_channel", sourceChannel)
            put("label", label)
        }.toString()
        return decodeEnvelope(execute(post("/api/chats/fork", body)))
    }

    suspend fun rewindHistory(
        chatId: String,
        channel: String = "web",
        historyId: Long,
    ): RewindResponse {
        val body = buildJsonObject {
            put("chat_id", chatId)
            put("channel", channel)
            put("history_id", historyId)
        }.toString()
        return decodeEnvelope(execute(post("/api/history/rewind", body)))
    }

    private suspend inline fun <reified T> decodeRpc(
        method: String,
        params: JsonObject = JsonObject(emptyMap()),
    ): T = XbotJson.decodeFromJsonElement(rpc(method, params))

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    suspend fun ensureAuth(): Boolean {
        if (!hasServer()) return false
        // A cheap authenticated call: if the cookie is still valid this succeeds.
        return runCatching { sessionTree(); true }.getOrElse { e ->
            if (e is UnauthorizedException) trySilentLogin() else false
        }
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val error = runCatching {
                    XbotJson.decodeFromString<ApiEnvelope<JsonElement>>(text).error
                }.getOrNull()
                if (response.code == 401) {
                    throw UnauthorizedException(error?.message ?: "登录已失效，请重新登录")
                }
                throw ApiException(
                    code = error?.code ?: "http_${response.code}",
                    message = error?.message ?: "HTTP ${response.code}",
                    httpStatus = response.code,
                )
            }
            text
        }
    }

    private inline fun <reified T> decodeEnvelope(text: String): T {
        val env = XbotJson.decodeFromString<ApiEnvelope<T>>(text)
        if (!env.ok) {
            val message = env.error?.message ?: "请求失败"
            if (env.error?.code == "unauthorized") throw UnauthorizedException(message)
            throw ApiException(env.error?.code ?: "error", message)
        }
        return env.data ?: throw ApiException("empty_response", "服务端返回空数据")
    }

    private fun get(path: String): Request =
        Request.Builder().url(urlBuilder(path).build()).get().build()

    private fun post(path: String, body: String): Request =
        Request.Builder()
            .url(urlBuilder(path).build())
            .post(body.toRequestBody(JSON))
            .build()

    private fun urlBuilder(path: String): okhttp3.HttpUrl.Builder =
        (
            (normalizeBaseUrl(prefs.serverUrl) + path).toHttpUrlOrNull()
                ?: throw ApiException("bad_url", "服务器地址无效：${prefs.serverUrl}")
            ).newBuilder()

    private fun encodePath(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun normalizeBaseUrl(raw: String): String {
            var value = raw.trim().trimEnd('/')
            if (value.isBlank()) return ""
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                value = "http://$value"
            }
            return value
        }
    }
}

@kotlinx.serialization.Serializable
data class CreateChatResponse(
    @kotlinx.serialization.SerialName("chat_id") val chatId: String = "",
)

/**
 * The REST bridge wraps every successful `/api/rpc` result in the standard
 * `{ok:true,data:<result>,error:null}` envelope (writeJSON in web_auth.go).
 * Raw arrays such as `list_all_model_entries` therefore arrive as
 * `{ok:true,data:[...]}`; decoding the envelope itself as a List produces
 * "Expected JsonArray, but had JsonObject". Unwrap only when the shape is the
 * known envelope so plain object results stay untouched.
 */
internal fun unwrapRpcEnvelope(element: JsonElement): JsonElement {
    if (element is JsonObject &&
        element["ok"]?.jsonPrimitive?.content == "true" &&
        element.containsKey("data")
    ) {
        return element["data"] ?: JsonNull
    }
    return element
}
