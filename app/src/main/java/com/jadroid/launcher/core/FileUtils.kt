package com.jadroid.launcher.core

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** Hex encoded SHA-1 of the whole file, used to verify downloads against Mojang's published hashes. */
fun File.sha1Hex(): String {
    val digest = MessageDigest.getInstance("SHA-1")
    inputStream().use { stream -> digest.consume(stream) }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** MD5 hex digest, used to build the deterministic offline UUID of a local account. */
fun MessageDigest.consume(input: InputStream) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        update(buffer, 0, read)
    }
}

fun String.md5Hex(): String {
    val digest = MessageDigest.getInstance("MD5").digest(toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

fun File.ensureDir(): File {
    if (!isDirectory && !mkdirs()) throw IOException("Could not create directory ${absolutePath}")
    return this
}

fun File.ensureParent(): File {
    parentFile?.ensureDir()
    return this
}

fun File.deleteQuietly(): Boolean = runCatching { deleteRecursively() }.getOrDefault(false)

fun File.child(name: String): File = File(this, name)

/** Escapes a value so it can safely be embedded in a POSIX shell script. */
fun String.shellQuote(): String = "'" + replace("'", "'\\''") + "'"
