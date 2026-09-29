package com.jadroid.launcher.data.mojang

import android.os.Build

/**
 * Describes the "OS" used while evaluating Mojang rule sets and native classifiers.
 *
 * Android is not a name Mojang knows about, so (exactly like other mobile launchers) we map it to
 * `linux`: that selects the `natives-linux` / `natives-linux-arm64` artifacts and the Linux rule set.
 */
data class OsProfile(
    val name: String,
    val version: String,
    val arch: String
) {
    companion object {
        val ANDROID: OsProfile = OsProfile(
            name = "linux",
            version = Build.VERSION.RELEASE.orEmpty(),
            arch = normalizeArch()
        )

        private fun normalizeArch(): String = when (val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "") {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a", "armeabi" -> "arm32"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> if (abi.contains("arm64")) "arm64" else abi.ifEmpty { "unknown" }
        }
    }
}

/** Feature flags that Mojang rules can key off (`has_custom_resolution`, `is_demo_user`, ...). */
data class LaunchFeatures(
    val isDemoUser: Boolean = false,
    val hasCustomResolution: Boolean = false,
    val hasQuickPlaysSupport: Boolean = false,
    val isQuickPlaySingleplayer: Boolean = false,
    val isQuickPlayMultiplayer: Boolean = false,
    val isQuickPlayRealms: Boolean = false
) {
    fun toMap(): Map<String, Boolean> = mapOf(
        "is_demo_user" to isDemoUser,
        "has_custom_resolution" to hasCustomResolution,
        "has_quick_plays_support" to hasQuickPlaysSupport,
        "is_quick_play_singleplayer" to isQuickPlaySingleplayer,
        "is_quick_play_multiplayer" to isQuickPlayMultiplayer,
        "is_quick_play_realms" to isQuickPlayRealms
    )
}

object RuleEvaluator {

    fun isAllowed(rules: List<Rule>, profile: OsProfile, features: Map<String, Boolean>): Boolean {
        if (rules.isEmpty()) return true
        var allowed = false
        for (rule in rules) {
            if (matches(rule, profile, features)) allowed = rule.action.equals("allow", ignoreCase = true)
        }
        return allowed
    }

    private fun matches(rule: Rule, profile: OsProfile, features: Map<String, Boolean>): Boolean {
        val os = rule.os
        if (os != null) {
            if (os.name != null && !os.name.equals(profile.name, ignoreCase = true)) return false
            if (os.arch != null && !os.arch.equals(profile.arch, ignoreCase = true)) return false
            if (os.version != null && !Regex(os.version).containsMatchIn(profile.version)) return false
        }
        if (rule.features.isNotEmpty()) {
            for ((key, expected) in rule.features) {
                val actual = features[key] ?: false
                if (actual != expected) return false
            }
        }
        return true
    }
}
