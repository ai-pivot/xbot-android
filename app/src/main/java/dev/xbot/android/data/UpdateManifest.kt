package dev.xbot.android.data

import kotlinx.serialization.Serializable

/**
 * Manifest published next to the APK on the update host.
 *
 * [channel] defaults to [CHANNEL_STABLE] so manifests written before the
 * two-channel split keep validating instead of being rejected as "wrong channel".
 */
@Serializable
data class UpdateManifest(
    val schema: Int = 1,
    val channel: String = CHANNEL_STABLE,
    val versionCode: Int = 0,
    val versionName: String = "",
    val minSdk: Int = 26,
    val size: Long = 0L,
    val sha256: String = "",
    val url: String = "",
    val changelog: String = "",
    val mandatory: Boolean = false,
    val publishedAt: String = "",
    /** Commit a dev build was produced from; empty for stable releases. */
    val commit: String = "",
) {
    val isDev: Boolean get() = channel == CHANNEL_DEV

    companion object {
        const val CHANNEL_STABLE = "stable"
        const val CHANNEL_DEV = "dev"
        const val CHANNEL_CUSTOM = "custom"
    }
}
