package com.jadroid.launcher.data.mojang

/** Parsed maven coordinate, e.g. `org.lwjgl:lwjgl:3.3.3:natives-linux`. */
data class MavenName(
    val group: String,
    val artifact: String,
    val version: String,
    val classifier: String? = null,
    val extension: String = "jar"
) {
    /** Relative repository path, i.e. `org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar`. */
    fun path(): String = buildString {
        append(group.replace('.', '/')).append('/')
        append(artifact).append('/').append(version).append('/')
        append(artifact).append('-').append(version)
        if (!classifier.isNullOrEmpty()) append('-').append(classifier)
        append('.').append(extension)
    }

    fun withClassifier(newClassifier: String?): MavenName = copy(classifier = newClassifier)

    override fun toString(): String = listOfNotNull(group, artifact, version, classifier).joinToString(":")

    companion object {
        /** Accepts `group:artifact:version`, `group:artifact:version:classifier` and `group:artifact:version@ext`. */
        fun parse(raw: String): MavenName? {
            var value = raw.trim()
            var extension = "jar"
            val at = value.indexOf('@')
            if (at >= 0) {
                extension = value.substring(at + 1)
                value = value.substring(0, at)
            }
            val parts = value.split(':').filter { it.isNotEmpty() }
            if (parts.size < 3) return null
            return MavenName(
                group = parts[0],
                artifact = parts[1],
                version = parts[2],
                classifier = parts.getOrNull(3),
                extension = extension
            )
        }
    }
}
