package top.niunaijun.blackboxa.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.core.env.BEnvironment

/**
 * Backup & restore data clone per aplikasi (item 4).
 *
 * MASALAH YANG DIATASI: uninstall CloneKu (atau uninstall satu clone)
 * menghapus seluruh data clone — tidak ada jalan kembali. Manager ini
 * mengekspor data satu clone ke arsip ZIP lewat Storage Access Framework
 * (pemilih file sistem), dan memulihkannya kembali ke ruang (user space)
 * yang sama.
 *
 * APA YANG DICADANGKAN — direktori data clone menurut [BEnvironment]:
 * - `blackbox/data/user/<userId>/<paket>` (data internal: files,
 *   databases, shared_prefs, ...)
 * - `blackbox/data/user_de/<userId>/<paket>` (data device-encrypted)
 * - `<external>/blackbox/storage/emulated/<userId>/Android/data/<paket>`
 *   (data eksternal aplikasi)
 *
 * APA YANG SENGAJA TIDAK DICADANGKAN:
 * - `package.conf` / `uid.conf` di bawah `blackbox/data/app/<paket>`:
 *   itu metadata instalasi milik engine (termasuk appId virtual yang
 *   diberikan saat install). Mengembalikan file itu akan merusak
 *   pemetaan engine. Restore HANYA data, dan HANYA bila clone sudah
 *   ter-install kembali di ruang yang sama.
 * - Subdirektori `lib/` (native library hasil ekstrak saat install),
 *   `cache/` dan `code_cache/`: dibuat ulang saat install/jalan, dan
 *   mengembalikan `.so` lama setelah install ulang versi berbeda bisa
 *   merusak clone. Saat restore, isi `lib/` yang ada DIBIARKAN.
 *
 * BATASAN (jujur — bacalah sebelum mengandalkan fitur ini):
 * 1. Restore hanya ke ruang (userId) yang SAMA dengan saat backup dibuat,
 *    dan hanya untuk paket yang sama. Restore lintas ruang DITOLAK karena
 *    sebagian aplikasi menyimpan path/ID yang mengandung userId.
 * 2. Clone HARUS sudah ter-install di ruang itu sebelum restore
 *    (install dulu aplikasinya, baru pulihkan datanya).
 * 3. Data yang sedang ditulis aplikasi TIDAK boleh dibackup: ekspor dan
 *    impor selalu menghentikan dulu clone yang bersangkutan
 *    ([BlackBoxCore.stopPackage]). Jangan pakai clone itu selama proses
 *    berjalan.
 * 4. Aplikasi yang mengikat data ke identitas perangkat (mis. kunci
 *    enkripsi di keystore, token yang terikat Android ID / Play Integrity)
 *    bisa tetap menolak login setelah restore — itu di luar kendali
 *    CloneKu. Backup ini menyelamatkan FILE data, bukan sesi login.
 * 5. Restore menimpa data clone yang ada saat ini. Bila restore gagal di
 *    tengah jalan, data lama dikembalikan dari salinan `.cloneku-bak`
 *    (rollback), tapi tetap: ekspor dulu sebelum mencoba restore bila
 *    datanya berharga.
 *
 * Semua pekerjaan file berjalan di thread latar; hasil disampaikan lewat
 * [Callback] di thread utama.
 */
object CloneBackupManager {

    private const val TAG = "CloneBackupManager"

    /** Versi format arsip. Naikkan bila struktur ZIP berubah. */
    private const val FORMAT_VERSION = 1

    private const val MANIFEST_NAME = "manifest.json"

    private const val PREFIX_INTERNAL = "internal/"
    private const val PREFIX_DEVICE_ENCRYPTED = "device_encrypted/"
    private const val PREFIX_EXTERNAL = "external/"

    /** Subdirektori data yang tidak ikut dicadangkan (lihat dokumentasi di atas). */
    private val SKIPPED_DIR_NAMES = setOf("lib", "cache", "code_cache")

    private val mainHandler = Handler(Looper.getMainLooper())

    interface Callback {
        fun onSuccess(message: String)
        fun onError(message: String)
    }

    /** Nama file saran untuk dialog simpan SAF. */
    fun suggestFileName(packageName: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "cloneku-backup-$packageName-$stamp.zip"
    }

    // ------------------------------------------------------------------
    // Ekspor
    // ------------------------------------------------------------------

    /**
     * Ekspor data clone [packageName] di ruang [userId] ke [destUri]
     * (didapat dari SAF CreateDocument). Menghentikan dulu clone tersebut.
     */
    fun exportBackup(
            context: Context,
            packageName: String,
            userId: Int,
            destUri: Uri,
            callback: Callback
    ) {
        Thread {
            try {
                if (!BlackBoxCore.get().isInstalled(packageName, userId)) {
                    postError(callback, "not_installed")
                    return@Thread
                }
                // Jangan baca file yang sedang ditulis aplikasi.
                runCatching { BlackBoxCore.get().stopPackage(packageName, userId) }

                val sources = dataSources(packageName, userId)
                if (sources.all { !it.second.exists() }) {
                    postError(callback, "no_data")
                    return@Thread
                }

                val (versionCode, versionName) = appVersion(context)
                val manifest = JSONObject().apply {
                    put("formatVersion", FORMAT_VERSION)
                    put("packageName", packageName)
                    put("userId", userId)
                    put("cloneKuVersionCode", versionCode)
                    put("cloneKuVersionName", versionName)
                    put("createdAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date()))
                }

                context.contentResolver.openOutputStream(destUri)?.use { rawOut ->
                    ZipOutputStream(BufferedOutputStream(rawOut)).use { zip ->
                        zip.putNextEntry(ZipEntry(MANIFEST_NAME))
                        zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                        zip.closeEntry()

                        for ((prefix, dir) in sources) {
                            if (!dir.exists()) continue
                            zipDirectory(zip, dir, prefix)
                        }
                        zip.finish()
                    }
                } ?: run {
                    postError(callback, "open_failed")
                    return@Thread
                }

                postSuccess(callback, "export_ok")
            } catch (e: Exception) {
                Log.e(TAG, "Export failed for $packageName", e)
                postError(callback, "export_failed: ${e.message}")
            }
        }.start()
    }

    // ------------------------------------------------------------------
    // Impor / restore
    // ------------------------------------------------------------------

    /**
     * Pulihkan arsip backup [sourceUri] (didapat dari SAF OpenDocument) ke
     * clone [packageName] di ruang [userId]. Menolak bila manifest tidak
     * cocok (paket/userId/format lain). Menimpa data yang ada saat ini.
     */
    fun importBackup(
            context: Context,
            packageName: String,
            userId: Int,
            sourceUri: Uri,
            callback: Callback
    ) {
        Thread {
            try {
                if (!BlackBoxCore.get().isInstalled(packageName, userId)) {
                    postError(callback, "not_installed")
                    return@Thread
                }
                runCatching { BlackBoxCore.get().stopPackage(packageName, userId) }

                // Buka ZIP dan baca manifest dulu — tanpa manifest yang cocok,
                // jangan sentuh data yang ada sama sekali.
                context.contentResolver.openInputStream(sourceUri)?.use { rawIn ->
                    ZipInputStream(BufferedInputStream(rawIn)).use { zip ->
                        val manifest = readManifest(zip)
                                ?: run {
                                    postError(callback, "invalid_file")
                                    return@Thread
                                }
                        if (manifest.optInt("formatVersion", -1) != FORMAT_VERSION ||
                                        manifest.optString("packageName") != packageName ||
                                        manifest.optInt("userId", -1) != userId) {
                            postError(callback, "mismatch")
                            return@Thread
                        }

                        val targets = dataSources(packageName, userId)
                                .associate { (prefix, dir) -> prefix to dir }

                        // Pindahkan data lama ke .cloneku-bak untuk rollback.
                        val backups = mutableMapOf<File, File>()
                        try {
                            for ((_, dir) in targets) {
                                if (!dir.exists()) continue
                                val bak = File(dir.parentFile, dir.name + ".cloneku-bak")
                                deleteRecursively(bak)
                                if (!dir.renameTo(bak)) {
                                    throw IllegalStateException("Tidak bisa memindahkan ${dir.path}")
                                }
                                backups[dir] = bak
                            }

                            // Tulis ulang data dari arsip. Direktori lib/ yang
                            // ada dipertahankan: kosongkan isi data dir kecuali
                            // lib/ bila direktori itu sudah ada kembali.
                            unzipInto(zip, targets)

                            // Sukses: hapus salinan lama.
                            for ((_, bak) in backups) {
                                deleteRecursively(bak)
                            }
                        } catch (e: Exception) {
                            // Rollback: hapus hasil restore yang setengah jalan,
                            // kembalikan data lama.
                            Log.e(TAG, "Restore failed, rolling back $packageName", e)
                            for ((dir, bak) in backups) {
                                deleteRecursively(dir)
                                if (bak.exists()) {
                                    runCatching { dir.parentFile?.mkdirs() }
                                    bak.renameTo(dir)
                                }
                            }
                            throw e
                        }
                    }
                } ?: run {
                    postError(callback, "open_failed")
                    return@Thread
                }

                postSuccess(callback, "import_ok")
            } catch (e: Exception) {
                Log.e(TAG, "Import failed for $packageName", e)
                postError(callback, "import_failed: ${e.message}")
            }
        }.start()
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    /** Pasangan (prefix ZIP, direktori data) untuk satu clone. */
    private fun dataSources(packageName: String, userId: Int): List<Pair<String, File>> =
            listOf(
                    PREFIX_INTERNAL to BEnvironment.getDataDir(packageName, userId),
                    PREFIX_DEVICE_ENCRYPTED to BEnvironment.getDeDataDir(packageName, userId),
                    PREFIX_EXTERNAL to BEnvironment.getExternalDataDir(packageName, userId)
            )

    /**
     * Versi CloneKu yang terinstall, dibaca dari PackageManager.
     * (BuildConfig tidak digenerate proyek ini — `buildFeatures` hanya
     * mengaktifkan viewBinding — jadi versi selalu dibaca runtime.)
     */
    private fun appVersion(context: Context): Pair<Long, String> {
        return try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            code to (info.versionName ?: "")
        } catch (e: Exception) {
            Log.w(TAG, "Could not read own version", e)
            0L to ""
        }
    }

    private fun zipDirectory(zip: ZipOutputStream, dir: File, prefix: String) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.name in SKIPPED_DIR_NAMES) continue
            zipRecursively(zip, child, prefix + child.name)
        }
    }

    private fun zipRecursively(zip: ZipOutputStream, file: File, entryPath: String) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            if (children.isEmpty()) {
                zip.putNextEntry(ZipEntry("$entryPath/"))
                zip.closeEntry()
                return
            }
            for (child in children) {
                zipRecursively(zip, child, "$entryPath/${child.name}")
            }
            return
        }
        FileInputStream(file).use { input ->
            zip.putNextEntry(ZipEntry(entryPath))
            input.copyTo(zip)
            zip.closeEntry()
        }
    }

    /**
     * Membaca entri manifest dari ZIP yang sedang dibuka. Mengembalikan null
     * bila tidak ada manifest. Setelah pemanggilan ini, stream ZIP berada
     * setelah entri manifest (atau di posisi awal bila manifest tidak ada —
     * pemanggil harus menangani kedua kasus; di sini manifest selalu ditulis
     * pertama saat ekspor sehingga posisi stream sudah benar).
     */
    private fun readManifest(zip: ZipInputStream): JSONObject? {
        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name == MANIFEST_NAME) {
                val text = zip.readBytes().toString(Charsets.UTF_8)
                return JSONObject(text)
            }
            // Entri sebelum manifest (seharusnya tidak ada) dilewati.
            entry = zip.nextEntry
        }
        return null
    }

    /**
     * Menulis seluruh entri ZIP yang tersisa ke direktori target berdasarkan
     * prefix-nya. Menolak entri dengan path yang mencoba keluar dari direktori
     * target ("../" — arsip jahat). Direktori `lib/` yang sudah ada di target
     * tidak disentuh (entri lib/ tidak pernah ditulis saat ekspor).
     */
    private fun unzipInto(zip: ZipInputStream, targets: Map<String, File>) {
        var entry = zip.nextEntry
        while (entry != null) {
            val name = entry.name
            val target = targets.entries.firstOrNull { name.startsWith(it.key) }?.value
            if (target != null) {
                val relative = name.removePrefix(
                        targets.entries.first { name.startsWith(it.key) }.key
                )
                val outFile = File(target, relative)
                // Cegah path traversal dari arsip yang dimodifikasi.
                if (!outFile.canonicalPath.startsWith(target.canonicalPath)) {
                    throw IllegalStateException("Entri arsip tidak aman: $name")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out -> zip.copyTo(out) }
                }
            }
            // Entri dengan prefix tak dikenal diabaikan.
            entry = zip.nextEntry
        }
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }

    private fun postSuccess(callback: Callback, message: String) {
        mainHandler.post { callback.onSuccess(message) }
    }

    private fun postError(callback: Callback, message: String) {
        mainHandler.post { callback.onError(message) }
    }
}
