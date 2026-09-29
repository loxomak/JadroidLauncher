package com.jadroid.launcher.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Very small in-memory + logcat logger. The [lines] flow is rendered by the in-app log viewer.
 */
object AppLog {
    private const val TAG = "Jadroid"
    private const val MAX_LINES = 800

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    fun i(message: String) = append("INFO", message).also { Log.i(TAG, message) }

    fun w(message: String, t: Throwable? = null) =
        append("WARN", message + (t?.message?.let { " ($it)" } ?: "")).also { Log.w(TAG, message, t) }

    fun e(message: String, t: Throwable? = null) =
        append("ERROR", message + (t?.message?.let { ": $it" } ?: "")).also { Log.e(TAG, message, t) }

    fun clear() {
        _lines.value = emptyList()
    }

    private fun append(level: String, message: String) {
        val line = "${timeFormat.format(Date())} $level $message"
        _lines.value = (_lines.value + line).takeLast(MAX_LINES)
    }
}
