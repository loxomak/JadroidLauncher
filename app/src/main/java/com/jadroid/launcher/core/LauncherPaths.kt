package com.jadroid.launcher.core

import android.content.Context
import java.io.File

/**
 * On-device layout, modelled after a normal `.minecraft` directory so instances stay portable:
 *
 * ```
 * filesDir/minecraft/
 *   libraries/                     shared maven repository cache
 *   assets/{indexes,objects}/      official Mojang asset store
 *   instances/<instance>/
 *     instance.json
 *     .minecraft/                  game directory (mods, saves, logs, options.txt ...)
 *       versions/<versionId>/<versionId>.jar   client jar
 *       natives/                   extracted native libraries
 * ```
 */
class LauncherPaths(context: Context) {

    val root: File = File(context.filesDir, "minecraft")
    val libraries: File = File(root, "libraries")
    val assets: File = File(root, "assets")
    val assetIndexes: File = File(assets, "indexes")
    val assetObjects: File = File(assets, "objects")
    val instances: File = File(root, "instances")
    val runtime: File = File(context.filesDir, "runtime")
    val logs: File = File(context.filesDir, "logs")

    fun ensureBaseDirs() {
        listOf(root, libraries, assets, assetIndexes, assetObjects, instances, runtime, logs).forEach { it.ensureDir() }
    }

    fun instanceDir(instanceId: String): File = File(instances, instanceId)
    fun gameDir(instanceId: String): File = File(instanceDir(instanceId), ".minecraft")
    fun modsDir(instanceId: String): File = File(gameDir(instanceId), "mods")
    fun nativesDir(instanceId: String): File = File(gameDir(instanceId), "natives")
    fun versionDir(instanceId: String, versionId: String): File = File(gameDir(instanceId), "versions/$versionId")
    fun clientJar(instanceId: String, versionId: String): File = File(versionDir(instanceId, versionId), "$versionId.jar")
    fun libraryFile(path: String): File = File(libraries, path)
    fun assetObjectFile(hash: String): File = File(assetObjects, "${hash.take(2)}/$hash")
    fun assetIndexFile(id: String): File = File(assetIndexes, "$id.json")
    fun instanceLogFile(instanceId: String): File = File(logs, "$instanceId.log")
}
