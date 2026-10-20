package dev.xbot.android.update

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.xbot.android.BuildConfig
import dev.xbot.android.XbotApp
import dev.xbot.android.data.UpdateManifest
import dev.xbot.android.data.XbotJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class UpdateUiState(
    val checking: Boolean = false,
    val manifest: UpdateManifest? = null,
    val available: Boolean = false,
    val upToDate: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val verifying: Boolean = false,
    val readyPath: String? = null,
    val needInstallPermission: Boolean = false,
    val error: String? = null,
)

/**
 * LAN-friendly full-APK updater:
 * manifest (latest.json) -> download to cache -> verify size/SHA-256/package/
 * signer certificate -> hand the APK to the system installer.
 *
 * The signer certificate check is the real trust anchor: even if the HTTP
 * manifest is tampered with, an attacker cannot produce an APK signed with the
 * installed app's key.
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val context = app.applicationContext
    private val prefs = (app as XbotApp).container.prefs
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    fun autoCheck() {
        if (prefs.updateManifestUrl.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - prefs.lastUpdateCheckAt < AUTO_CHECK_INTERVAL_MS) return
        prefs.lastUpdateCheckAt = now
        check(manual = false)
    }

    fun setManifestUrl(url: String) {
        prefs.updateManifestUrl = url
        _state.value = UpdateUiState()
    }

    fun check(manual: Boolean) {
        val current = _state.value
        if (current.checking || current.downloading) return
        val manifestUrl = prefs.updateManifestUrl
        if (manifestUrl.isBlank()) {
            _state.update { it.copy(error = "请先在设置中填写更新清单地址") }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(checking = true, error = null, upToDate = false, available = false)
            }
            try {
                val manifest = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(manifestUrl)
                        .header("Cache-Control", "no-cache")
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                        val text = response.body?.string().orEmpty()
                        if (text.isBlank()) throw IOException("更新清单为空")
                        XbotJson.decodeFromString<UpdateManifest>(text)
                    }
                }
                val available = manifest.versionCode > BuildConfig.VERSION_CODE
                _state.update {
                    it.copy(
                        checking = false,
                        manifest = manifest,
                        available = available,
                        upToDate = !available,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        checking = false,
                        error = e.message ?: "检查更新失败",
                    )
                }
            }
        }
    }

    fun download() {
        val manifest = _state.value.manifest ?: return
        if (_state.value.downloading) return
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    downloading = true,
                    progress = 0f,
                    verifying = false,
                    readyPath = null,
                    error = null,
                )
            }
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "xbot-android-${manifest.versionCode}.apk")

            try {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(manifest.url).get().build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                        val body = response.body ?: throw IOException("更新包响应为空")
                        val total = if (manifest.size > 0) manifest.size else body.contentLength()
                        body.byteStream().use { input ->
                            FileOutputStream(target).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var readTotal = 0L
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    output.write(buffer, 0, n)
                                    readTotal += n
                                    if (total > 0) {
                                        _state.update {
                                            it.copy(
                                                progress = (readTotal.toFloat() / total)
                                                    .coerceIn(0f, 1f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                _state.update { it.copy(downloading = false, verifying = true, progress = 1f) }
                val error = withContext(Dispatchers.IO) { verify(target, manifest) }
                if (error != null) {
                    target.delete()
                    _state.update { it.copy(verifying = false, error = error) }
                } else {
                    _state.update {
                        it.copy(verifying = false, readyPath = target.absolutePath)
                    }
                }
            } catch (e: Exception) {
                target.delete()
                _state.update {
                    it.copy(
                        downloading = false,
                        verifying = false,
                        error = e.message ?: "下载更新失败",
                    )
                }
            }
        }
    }

    fun install() {
        val path = _state.value.readyPath ?: return
        val file = File(path)
        if (!file.exists()) {
            _state.update { it.copy(error = "安装包已不存在，请重新下载") }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            _state.update { it.copy(needInstallPermission = true) }
            openInstallPermissionSettings()
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            _state.update { it.copy(needInstallPermission = false) }
        }.onFailure { e ->
            _state.update { it.copy(error = e.message ?: "无法启动安装程序") }
        }
    }

    fun openInstallPermissionSettings() {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { e ->
            _state.update { it.copy(error = e.message ?: "无法打开安装权限设置") }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    override fun onCleared() {
        downloadJob?.cancel()
        super.onCleared()
    }

    private fun verify(file: File, manifest: UpdateManifest): String? {
        if (!file.exists()) return "安装包不存在"
        if (manifest.size > 0 && file.length() != manifest.size) {
            return "安装包大小不匹配"
        }
        if (manifest.sha256.isBlank()) return "更新清单缺少 sha256"
        if (!sha256(file).equals(manifest.sha256, ignoreCase = true)) {
            return "安装包校验失败（SHA256）"
        }

        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: return "无法读取安装包信息"
        if (archive.packageName != context.packageName) return "安装包包名不匹配"
        val archiveVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archive.versionCode.toLong()
        }
        if (archiveVersion != manifest.versionCode.toLong()) return "安装包版本号不匹配"

        val current = pm.getPackageInfo(context.packageName, flags)
        val archiveSigner = signerHash(archive) ?: return "无法读取安装包签名"
        val currentSigner = signerHash(current) ?: return "无法读取当前应用签名"
        if (!archiveSigner.equals(currentSigner, ignoreCase = true)) {
            return "安装包签名校验失败"
        }
        return null
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun signerHash(info: PackageInfo): String? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return null
        val signature = signatures.firstOrNull() ?: return null
        return MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
