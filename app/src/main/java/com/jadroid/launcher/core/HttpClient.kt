package com.jadroid.launcher.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

class HttpException(val code: Int, val payload: String, message: String) : IOException(message)

/**
 * Thin OkHttp wrapper. Everything is suspend + IO-dispatched so it can be used from ViewModels directly.
 * All endpoints used by Jadroid are HTTPS, so no cleartext exceptions are required.
 */
class HttpClient(userAgent: String) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", userAgent)
                    .build()
            )
        }
        .build()

    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String =
        execute(Request.Builder().url(url).headers(headers.toHeaders()).get().build()).use { it.body?.string() ?: "" }

    suspend fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()): String {
        val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        return execute(
            Request.Builder().url(url).headers(headers.toHeaders()).post(body).build()
        ).use { it.body?.string() ?: "" }
    }

    suspend fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): String {
        val body = json.toRequestBody("application/json".toMediaType())
        return execute(
            Request.Builder().url(url).headers(headers.toHeaders()).post(body).build()
        ).use { it.body?.string() ?: "" }
    }

    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray =
        execute(Request.Builder().url(url).headers(headers.toHeaders()).get().build()).use {
            it.body?.bytes() ?: ByteArray(0)
        }

    /**
     * Streams [url] into [destination] (blocking, call from IO dispatcher).
     * When the server supports it, an existing partial file is resumed with a Range request.
     */
    fun downloadBlocking(url: String, destination: File, onBytes: ((Long) -> Unit)? = null) {
        val existing = if (destination.isFile) destination.length() else 0L
        destination.parentFile?.mkdirs()
        val requestBuilder = Request.Builder().url(url).get()
        if (existing > 0) requestBuilder.header("Range", "bytes=$existing-")
        val response = client.newCall(requestBuilder.build()).execute()
        try {
            if (response.code == 416) {
                // Requested range not satisfiable: the local file is already complete.
                return
            }
            check(response.isSuccessful) { "HTTP ${response.code} for $url" }
            val append = existing > 0 && response.code == 206
            if (!append && destination.exists()) destination.delete()
            val body = response.body ?: throw IOException("Empty response body for $url")
            body.byteStream().use { input ->
                java.io.FileOutputStream(destination, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        total += read
                        onBytes?.invoke(read.toLong())
                    }
                    output.fd.sync()
                }
            }
        } finally {
            response.close()
        }
    }

    private suspend fun execute(request: Request): Response = runInterruptible(Dispatchers.IO) {
        val response = try {
            client.newCall(request).execute()
        } catch (io: InterruptedIOException) {
            throw io
        }
        if (!response.isSuccessful) {
            val payload = runCatching { response.body?.string() ?: "" }.getOrDefault("")
            response.close()
            throw HttpException(response.code, payload, "HTTP ${response.code} for ${request.url}")
        }
        response
    }

    private fun Map<String, String>.toHeaders(): okhttp3.Headers =
        okhttp3.Headers.Builder().apply { forEach { (k, v) -> add(k, v) } }.build()
}
