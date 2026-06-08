package com.vladutu.pilot.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager

/**
 * Snapshots how Android currently sees this process — its OOM/importance bucket and whether
 * background network is restricted. Used to diagnose why share-path network calls fail their first
 * attempt (the work runs on a background coroutine after ShareReceiverActivity already finished, so
 * the process may no longer be "foreground" by the time the publish fires).
 *
 * A foreground tap should report IMPORTANCE_FOREGROUND; a failing share is expected to report a
 * lower bucket (CACHED / SERVICE) and/or restricted background data.
 */
object ProcessState {

    fun describe(context: Context): String {
        val importance = try {
            val info = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(info)
            importanceName(info.importance)
        } catch (e: Exception) {
            "importance=err(${e.javaClass.simpleName})"
        }

        val restrict = try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            restrictName(cm.restrictBackgroundStatus)
        } catch (e: Exception) {
            "restrictBg=err(${e.javaClass.simpleName})"
        }

        return "$importance $restrict"
    }

    private fun importanceName(value: Int): String {
        val name = when (value) {
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "FOREGROUND"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "FOREGROUND_SERVICE"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "VISIBLE"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "PERCEPTIBLE"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "SERVICE"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "CACHED"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE -> "GONE"
            else -> "OTHER"
        }
        return "importance=$name($value)"
    }

    private fun restrictName(value: Int): String {
        val name = when (value) {
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> "DISABLED"
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "WHITELISTED"
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "ENABLED(restricted)"
            else -> "UNKNOWN"
        }
        return "restrictBg=$name($value)"
    }
}
