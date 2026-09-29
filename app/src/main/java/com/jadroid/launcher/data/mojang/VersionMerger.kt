package com.jadroid.launcher.data.mojang

/**
 * Merges an inheriting version json (mod loaders such as Fabric) with its vanilla parent,
 * mirroring the inheritance rules used by the official launcher.
 */
object VersionMerger {

    fun merge(parent: VersionJson, child: VersionJson, id: String): VersionJson {
        val jvm: List<ArgumentValue> = child.arguments?.jvm.orEmpty() + parent.arguments?.jvm.orEmpty()
        val game: List<ArgumentValue> = parent.arguments?.game.orEmpty() + child.arguments?.game.orEmpty()

        return VersionJson(
            id = id,
            type = child.type ?: parent.type,
            mainClass = child.mainClass ?: parent.mainClass,
            assets = parent.assets,
            assetIndex = parent.assetIndex,
            downloads = parent.downloads,
            libraries = child.libraries + parent.libraries,
            arguments = if (parent.arguments == null && child.arguments == null) {
                null
            } else {
                Arguments(game = dedupe(game), jvm = dedupe(jvm))
            },
            legacyMinecraftArguments = child.legacyMinecraftArguments ?: parent.legacyMinecraftArguments,
            javaVersion = child.javaVersion ?: parent.javaVersion,
            inheritsFrom = null,
            releaseTime = child.releaseTime ?: parent.releaseTime,
            complianceLevel = parent.complianceLevel,
            logging = parent.logging
        )
    }

    private fun dedupe(values: List<ArgumentValue>): List<ArgumentValue> =
        values.distinctBy { it.values to it.rules }
}
