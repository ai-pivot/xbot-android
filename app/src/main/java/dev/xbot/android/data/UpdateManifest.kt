package dev.xbot.android.data

import kotlinx.serialization.Serializable

/** Manifest published next to the APK on the update host. */
@Serializable
data class UpdateManifest(
    val versionCode: Int = 0,
    val versionName: String = "",
    val minSdk: Int = 26,
    val size: Long = 0L,
    val sha256: String = "",
    val url: String = "",
    val changelog: String = "",
    val mandatory: Boolean = false,
    val publishedAt: String = "",
)
