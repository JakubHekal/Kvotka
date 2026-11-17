package com.jakubhekal.kvotka.managers

import android.app.Activity
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.util.concurrent.TimeUnit

class ShizukuManager {

    companion object {
        const val REQUEST_CODE = 1001
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
     *
     * shortcut: readLines() has no hard read timeout, so a command that never closes stdout can hang this call.
     */
    suspend fun run(command: String): List<String>? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null

        try {
            val process = newProcess(command)
            try {
                val lines = process.inputStream.bufferedReader().use { it.readLines() }
                if (process.waitFor(15, TimeUnit.SECONDS) && process.exitValue() == 0) {
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
