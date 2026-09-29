package com.jadroid.launcher.data.minecraft

import com.jadroid.launcher.data.instance.Instance
import com.jadroid.launcher.data.instance.LoaderInfo
import com.jadroid.launcher.data.instance.LoaderType
import com.jadroid.launcher.data.mojang.FabricRepository
import com.jadroid.launcher.data.mojang.MojangRepository
import com.jadroid.launcher.data.mojang.VersionJson
import com.jadroid.launcher.data.mojang.VersionMerger

/** A version json ready to be launched, fully merged (vanilla + mod loader). */
data class ResolvedVersion(
    val id: String,
    val json: VersionJson,
    val vanilla: VersionJson,
    val loader: LoaderInfo
)

class VersionResolver(
    private val mojang: MojangRepository,
    private val fabric: FabricRepository
) {

    /**
     * Takes the vanilla version from the official Mojang repository and, when the instance uses a
     * mod loader, merges the loader profile (inheriting version json) on top of it.
     */
    suspend fun resolve(instance: Instance): ResolvedVersion {
        val vanilla = mojang.versionJson(instance.gameVersion, instance.versionUrl)
        val loader = instance.loader
        return when {
            loader.isVanilla -> ResolvedVersion(vanilla.id, vanilla, vanilla, loader)
            loader.type == LoaderType.FABRIC -> {
                val profile = fabric.profile(instance.gameVersion, loader.version)
                val id = profile.id.ifBlank { "${instance.gameVersion}-fabric-${loader.version}" }
                ResolvedVersion(id, VersionMerger.merge(vanilla, profile, id), vanilla, loader)
            }
            else -> ResolvedVersion(vanilla.id, vanilla, vanilla, loader)
        }
    }
}
