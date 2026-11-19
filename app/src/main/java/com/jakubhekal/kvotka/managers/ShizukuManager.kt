package com.jakubhekal.kvotka.managers

import android.app.Activity
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.util.concurrent.TimeUnit

class ShizukuManager {

    companion object {
        const val REQUEST_CODE = 1001
        private const val READ_TIMEOUT_SECONDS = 15L
    }

    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun hasPermission(): Boolean = isAvailable() && try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun requestPermission(activity: Activity) {
        if (!isAvailable() || hasPermission()) return
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (_: Throwable) {
        }
    }

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        if (!isAvailable()) return
        try {
            Shizuku.addRequestPermissionResultListener(listener)
        } catch (_: Throwable) {
        }
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.removeRequestPermissionResultListener(listener)
        } catch (_: Throwable) {
        }
    }

    /**
     * API 13 removed Shizuku.newProcess. The service still exposes it, so call the binder and
     * rebuild the remote process through its package-private constructor.
     */
    private fun newProcess(command: String): Process {
        val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
        val remote = service.newProcess(arrayOf("sh", "-c", command), null, null)
        val constructor = ShizukuRemoteProcess::class.java
            .getDeclaredConstructor(IRemoteProcess::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(remote)
    }

    /**
     * Runs a shell command through Shizuku and returns stdout, or null when unavailable or it fails.
     */
    suspend fun run(command: String): List<String>? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null

        try {
            val process = newProcess(command)
            try {
                // Read in a child coroutine so the timeout can resume here and destroy the process,
                // which unblocks a stdout read that never reaches EOF.
                val lines = withTimeoutOrNull(READ_TIMEOUT_SECONDS * 1000) {
                    withContext(Dispatchers.IO) {
                        process.inputStream.bufferedReader().use { it.readLines() }
                    }
                }
                if (lines == null) {
                    process.destroy()
                    return@withContext null
                }
                if (process.waitFor(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    lines
                } else {
                    process.destroy()
                    null
                }
            } catch (e: Exception) {
                process.destroy()
                null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
