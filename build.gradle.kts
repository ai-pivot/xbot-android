plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    // FCM spike: google-services.json is injected locally (gitignored) / via CI secret.
    id("com.google.gms.google-services") version "4.5.0" apply false
}
