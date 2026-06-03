package com.vladutu.pilot.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash-survivable diagnostic log. Writes append-only to `filesDir/diagnostic.log` so entries
 * persist across process death (the very thing we're trying to diagnose).
 *
 * Synchronous writes by design: a coroutine that's about to crash needs its final log line
 * flushed before the JVM exits, so we cannot defer to a background dispatcher.
 *
 * If `init` is never called (unit tests, early-startup paths) every method is a silent no-op
 * apart from mirroring to `android.util.Log`, so the rest of the code can call it freely.
 */
object DiagnosticLog {

    private const val MAX_BYTES = 256L * 1024L
    private const val ROTATE_KEEP_BYTES = 128 * 1024

    @Volatile private var file: File? = null
    private val writeLock = Any()
    private val tsFmt = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    private val sessionFmt = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }

    fun init(context: Context) {
        val f = File(context.filesDir, "diagnostic.log")
        synchronized(writeLock) {
            rotateIfNeeded(f)
            file = f
            appendRaw(f, "--- session start ${sessionFmt.get().format(Date())} ---\n")
        }
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        write('I', tag, msg, null)
    }

    fun w(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.w(tag, msg, t) else Log.w(tag, msg)
        write('W', tag, msg, t)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg)
        write('E', tag, msg, t)
    }

    fun read(): String {
        val f = file ?: return "(diagnostic log not initialised)"
        return try {
            if (f.exists()) f.readText() else "(no diagnostic log yet)"
        } catch (e: IOException) {
            "(failed to read log: ${e.message})"
        }
    }

    fun clear() {
        val f = file ?: return
        synchronized(writeLock) {
            try {
                f.writeText("--- cleared ${sessionFmt.get().format(Date())} ---\n")
            } catch (_: IOException) { /* nothing to do */ }
        }
    }

    private fun write(level: Char, tag: String, msg: String, t: Throwable?) {
        val f = file ?: return
        val line = buildString {
            append(tsFmt.get().format(Date()))
            append(' ').append(level).append('/').append(tag).append(": ").append(msg)
            if (t != null) {
                append('\n')
                append(stackTrace(t))
            }
            append('\n')
        }
        synchronized(writeLock) { appendRaw(f, line) }
    }

    private fun appendRaw(f: File, s: String) {
        try {
            f.appendText(s)
        } catch (_: IOException) { /* swallow — the diagnostic must never crash the app */ }
    }

    private fun rotateIfNeeded(f: File) {
        if (!f.exists() || f.length() <= MAX_BYTES) return
        try {
            val all = f.readBytes()
            val from = (all.size - ROTATE_KEEP_BYTES).coerceAtLeast(0)
            val tail = all.copyOfRange(from, all.size)
            f.writeBytes("--- truncated ${sessionFmt.get().format(Date())} ---\n".toByteArray())
            f.appendBytes(tail)
        } catch (_: IOException) { /* leave the file alone */ }
    }

    private fun stackTrace(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }
}
