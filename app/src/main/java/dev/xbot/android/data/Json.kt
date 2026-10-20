package dev.xbot.android.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Single shared JSON codec. The server is additive: unknown fields must be ignored. */
val XbotJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

/** REST envelope used by every /api response except /api/rpc. */
@kotlinx.serialization.Serializable
data class ApiEnvelope<T>(
    val ok: Boolean = false,
    val data: T? = null,
    val error: ApiError? = null,
)

@kotlinx.serialization.Serializable
data class ApiError(
    val code: String = "",
    val message: String = "",
)

class ApiException(
    val code: String,
    override val message: String,
    val httpStatus: Int = 0,
) : RuntimeException(message)

class UnauthorizedException(message: String = "登录已失效，请重新登录") :
    RuntimeException(message)

/** Format an arbitrary JSON value for display (tool args/results). */
fun JsonElement.pretty(): String =
    runCatching { XbotJson.encodeToString(JsonElement.serializer(), this) }.getOrDefault(toString())
