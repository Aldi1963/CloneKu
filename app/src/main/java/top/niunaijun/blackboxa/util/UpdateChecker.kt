package top.niunaijun.blackboxa.util

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import top.niunaijun.blackboxa.app.App

/**
 * Pemeriksa update di dalam aplikasi (item 8).
 *
 * Endpoint JSON versi ada di repo rilis CloneKu:
 * `https://raw.githubusercontent.com/Aldi1963/CloneKu/main/update.json`
 * (file `update.json` di root repo). Isi file JSON:
 * ```json
 * {
 *   "versionCode": 2,
 *   "versionName": "1.0.1",
 *   "downloadUrl": "https://github.com/Aldi1963/CloneKu/releases/download/v1.0.1/CloneKu_1.0.1_arm64-v8a-release.apk",
 *   "changelog": "Perbaikan ..."
 * }
 * ```
 * `versionCode` WAJIB ada; field lain opsional (`downloadUrl` kosong
 * berarti dialog hanya memberi tahu tanpa tombol unduh). `downloadUrl`
 * mengarah ke aset rilis GitHub — rilis dengan tag dan nama file APK yang
 * sama harus dibuat di GitHub agar tautan hidup. Perbarui `update.json`
 * setiap kali ada rilis baru, dan naikkan versionCode di `build.gradle`
 * root — pemeriksaan membandingkan `versionCode` JSON dengan versionCode
 * terinstall yang dibaca dari PackageManager (BuildConfig tidak digenerate
 * proyek ini). Obtainium tetap didukung sebagai jalur update alternatif.
 *
 * Pemeriksaan hanya berjalan saat pengguna mengetuk "Periksa update" di
 * Pengaturan (tidak ada cek otomatis di latar belakang).
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"

    /**
     * Endpoint JSON versi di repo rilis CloneKu
     * (file `update.json` di root repo, dibaca lewat raw.githubusercontent).
     * Perbarui file itu setiap rilis — lihat dokumentasi di atas.
     */
    const val UPDATE_CHECK_URL: String =
            "https://raw.githubusercontent.com/Aldi1963/CloneKu/main/update.json"

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    private val mainHandler = Handler(Looper.getMainLooper())

    sealed class Result {
        /** Endpoint belum dikonfigurasi — bukan error jaringan. */
        object NotConfigured : Result()

        /** Versi terinstall sudah yang terbaru. */
        data class UpToDate(val versionName: String) : Result()

        /** Ada versi baru. */
        data class Available(
                val versionCode: Int,
                val versionName: String,
                val downloadUrl: String,
                val changelog: String
        ) : Result()

        /** Gagal jaringan / format JSON salah. [message] untuk log/toast. */
        data class Error(val message: String) : Result()
    }

    fun interface Callback {
        fun onResult(result: Result)
    }

    /** VersionCode CloneKu yang terinstall (dibaca dari PackageManager). */
    fun currentVersionCode(): Long {
        return try {
            val context = App.getContext()
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read own versionCode", e)
            0L
        }
    }

    /** VersionName CloneKu yang terinstall (dibaca dari PackageManager). */
    fun currentVersionName(): String {
        return try {
            val context = App.getContext()
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Could not read own versionName", e)
            ""
        }
    }

    /** Jalankan pemeriksaan di thread latar; hasil di thread utama. */
    fun check(callback: Callback) {
        Thread {
            val result = try {
                checkNow()
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                Result.Error(e.message ?: e.javaClass.simpleName)
            }
            mainHandler.post { callback.onResult(result) }
        }.start()
    }

    private fun checkNow(): Result {
        if (UPDATE_CHECK_URL.isBlank()) {
            return Result.NotConfigured
        }
        val connection = URL(UPDATE_CHECK_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty(
                    "User-Agent", "CloneKu/${currentVersionName()}"
            )
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return Result.Error("HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)
            if (!json.has("versionCode")) {
                return Result.Error("JSON tidak berisi versionCode")
            }
            val latestCode = json.getInt("versionCode")
            val latestName = json.optString("versionName", latestCode.toString())
            if (latestCode.toLong() <= currentVersionCode()) {
                return Result.UpToDate(currentVersionName())
            }
            return Result.Available(
                    versionCode = latestCode,
                    versionName = latestName,
                    downloadUrl = json.optString("downloadUrl", ""),
                    changelog = json.optString("changelog", "")
            )
        } finally {
            connection.disconnect()
        }
    }
}
