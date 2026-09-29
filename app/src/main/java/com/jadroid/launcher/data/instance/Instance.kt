package com.jadroid.launcher.data.instance

import kotlinx.serialization.Serializable

@Serializable
enum class LoaderType {
    VANILLA,
    FABRIC;

    val id: String get() = name.lowercase()

    val displayName: String
        get() = when (this) {
            VANILLA -> "Vanilla"
            FABRIC -> "Fabric"
        }

    companion object {
        fun fromId(id: String?): LoaderType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: VANILLA
    }
}

@Serializable
data class LoaderInfo(
    val type: LoaderType = LoaderType.VANILLA,
    val version: String = ""
) {
    val isVanilla: Boolean get() = type == LoaderType.VANILLA
}

/**
 * A launcher profile ("instance"). Everything needed to rebuild the launch command is stored here,
 * so instances survive app restarts and are shareable.
 */
@Serializable
data class Instance(
    val id: String,
    val name: String,
    val gameVersion: String,
    val loader: LoaderInfo = LoaderInfo(),
    /** Manifest url of the vanilla version, kept so older versions still resolve offline-ish. */
    val versionUrl: String? = null,
    val created: Long = System.currentTimeMillis(),
    val lastPlayed: Long = 0L,
    val installed: Boolean = false,
    val resolvedVersionId: String? = null,
    val allocatedRamMb: Int = 2048,
    val jvmArgs: String = "",
    val customResolution: Boolean = false,
    val resolutionWidth: Int = 854,
    val resolutionHeight: Int = 480
) {
    val loaderLabel: String
        get() = if (loader.isVanilla) "Vanilla ${gameVersion}" else "${loader.type.displayName} ${loader.version}"
}
