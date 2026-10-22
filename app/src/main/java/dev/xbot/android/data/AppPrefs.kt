package dev.xbot.android.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Tiny encrypted-at-rest store for the login credentials.
 *
 * The session cookie alone is not enough: xbot keeps web sessions in memory, so a
 * server restart invalidates every cookie. Keeping the username/password in
 * Android Keystore-backed AES-GCM lets the app silently re-login.
 */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    fun put(username: String, password: String) {
        runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
            val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
            prefs.edit().putString(KEY_USER, username).putString(KEY_PASSWORD, payload).apply()
        }
    }

    fun username(): String = prefs.getString(KEY_USER, "").orEmpty()

    fun password(): String? {
        val payload = prefs.getString(KEY_PASSWORD, null) ?: return null
        return runCatching {
            val (ivPart, dataPart) = payload.split(":", limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(128, Base64.decode(ivPart, Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(dataPart, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY_USER).remove(KEY_PASSWORD).apply()
    }

    private companion object {
        const val PREFS = "xbot_secrets"
        const val KEY_USER = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_ALIAS = "xbot_credentials"
    }
}

/** App settings + persisted cookies. All values are app-private. */
class AppPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("xbot_prefs", Context.MODE_PRIVATE)
    val secrets = SecretStore(context)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SERVER, value.trim().trimEnd('/')).apply()

    var activeChatId: String
        get() = prefs.getString(KEY_CHAT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_CHAT, value).apply()

    var activeChannel: String
        get() = prefs.getString(KEY_CHANNEL, "web").orEmpty().ifBlank { "web" }
        set(value) = prefs.edit().putString(KEY_CHANNEL, value).apply()

    /** FCM registration token (shown in Settings for diagnostics). */
    var fcmToken: String
        get() = prefs.getString(KEY_FCM_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_FCM_TOKEN, value).apply()

    /** Whether the current token was accepted by the server. */
    var pushRegistered: Boolean
        get() = prefs.getBoolean(KEY_PUSH_REGISTERED, false)
        set(value) = prefs.edit().putBoolean(KEY_PUSH_REGISTERED, value).apply()

    var updateManifestUrl: String
        get() = prefs.getString(KEY_UPDATE_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_UPDATE_URL, value.trim()).apply()

    /**
     * Public origin used to rewrite localhost/127.0.0.1 image URLs
     * (e.g. https://minio.cjiw.site). Empty disables rewriting.
     */
    var mediaBaseUrl: String
        get() = prefs.getString(KEY_MEDIA_URL, DEFAULT_MEDIA_BASE_URL).orEmpty()
        set(value) = prefs.edit().putString(KEY_MEDIA_URL, value.trim()).apply()

    var lastUpdateCheckAt: Long
        get() = prefs.getLong(KEY_UPDATE_CHECK_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_UPDATE_CHECK_AT, value).apply()

    /**
     * 最近用过的工作目录（新建会话弹窗的快捷选项），最新在前，最多 [MAX_RECENT_WORK_DIRS] 个。
     * 纯本地状态：服务端没有「最近目录」接口，Web 端也是存 localStorage。
     */
    var recentWorkDirs: List<String>
        get() {
            val raw = prefs.getString(KEY_RECENT_WORK_DIRS, "").orEmpty()
            if (raw.isBlank()) return emptyList()
            return runCatching {
                XbotJson.decodeFromString<List<String>>(raw)
            }.getOrDefault(emptyList()).filter { it.isNotBlank() }
        }
        set(value) {
            prefs.edit()
                .putString(KEY_RECENT_WORK_DIRS, XbotJson.encodeToString(value.take(MAX_RECENT_WORK_DIRS)))
                .apply()
        }

    /** 记住一个工作目录（去重、置顶、截断），返回更新后的列表。 */
    fun rememberWorkDir(dir: String): List<String> {
        val path = dir.trim()
        if (path.isEmpty()) return recentWorkDirs
        val next = (listOf(path) + recentWorkDirs.filterNot { it == path }).take(MAX_RECENT_WORK_DIRS)
        recentWorkDirs = next
        return next
    }

    fun lastSeq(chatId: String): Long =
        if (chatId.isBlank()) 0L else prefs.getLong("$KEY_SEQ_PREFIX$chatId", 0L)

    fun setLastSeq(chatId: String, seq: Long) {
        if (chatId.isBlank()) return
        prefs.edit().putLong("$KEY_SEQ_PREFIX$chatId", seq).apply()
    }

    var cookies: Set<String>
        get() = prefs.getStringSet(KEY_COOKIES, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_COOKIES, value).apply()

    fun clearSession() {
        prefs.edit().remove(KEY_COOKIES).remove(KEY_CHAT).apply()
        secrets.clear()
    }

    private companion object {
        const val KEY_SERVER = "server_url"
        const val KEY_CHAT = "active_chat_id"
        const val KEY_CHANNEL = "active_channel"
        const val KEY_FCM_TOKEN = "fcm_token"
        const val KEY_PUSH_REGISTERED = "push_registered"
        const val KEY_SEQ_PREFIX = "last_seq_"
        const val KEY_COOKIES = "cookies"
        const val KEY_UPDATE_URL = "update_manifest_url"
        const val KEY_UPDATE_CHECK_AT = "update_last_check_at"
        const val KEY_MEDIA_URL = "media_base_url"
        const val KEY_RECENT_WORK_DIRS = "recent_work_dirs"
        const val MAX_RECENT_WORK_DIRS = 5
    }
}

const val DEFAULT_MEDIA_BASE_URL = "https://minio.cjiw.site"
