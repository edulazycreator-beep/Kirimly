package com.kirimly.app.util

import android.content.Context
import android.os.Build
import com.kirimly.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes uncaught exceptions to private storage (files/crash/) so a tester can share them from
 * Settings. Nothing leaves the device unless the user shares a log.
 */
object CrashLogger {

    private const val DIR = "crash"
    private const val MAX_LOGS = 10

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                write(appContext, thread, error)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun directory(context: Context): File = File(context.filesDir, DIR)

    fun logs(context: Context): List<File> =
        directory(context).listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun clear(context: Context) {
        directory(context).listFiles()?.forEach { it.delete() }
    }

    /** Joins all logs into one shareable file (newest first). */
    fun bundle(context: Context): File? {
        val files = logs(context)
        if (files.isEmpty()) return null
        val out = File(directory(context), "kirimly-crash-report.txt")
        out.writeText(files.filter { it.name != out.name }.joinToString("\n\n" + "=".repeat(60) + "\n\n") { it.readText() })
        return out
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val dir = directory(context)
        if (!dir.exists()) dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val report = buildString {
            appendLine("Kirimly ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Waktu: $stamp")
            appendLine("Perangkat: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(trace)
        }
        File(dir, "crash-$stamp.txt").writeText(report)
        logs(context).filter { it.name.startsWith("crash-") }.drop(MAX_LOGS).forEach { it.delete() }
    }
}
