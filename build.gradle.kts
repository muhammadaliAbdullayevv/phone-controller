plugins {
    // AGP 9 compiles Kotlin itself now -- no separate kotlin-android
    // plugin needed (or allowed) alongside it. The Compose compiler is
    // still its own plugin, though.
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
}
