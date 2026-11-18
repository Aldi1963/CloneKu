# Kompatibilitas Android 15 — CloneKu 1.0.0

Audit kode terhadap perubahan perilaku Android 15 (API 35) untuk aplikasi
dengan `targetSdk 28`, `compileSdk 35`, `minSdk 21`.
Dilakukan 18 Nov 2026 di branch `trek-c-rilis` (Trek C, item 6).
Audit ini **statis (baca kode + periksa artefak build)** — bukan uji HP.
Bagian terakhir mendaftar hal yang HANYA bisa dipastikan lewat uji HP.

## Ringkasan

| Area Android 15 | Status | Keterangan |
|---|---|---|
| 16 KB page size | ✅ Siap | Segmen ELF `libblackbox.so` rata `0x4000` di arm64-v8a & armeabi-v7a; `APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true`; NDK 29 |
| Batas install `targetSdk >= 24` | ✅ Aman | `targetSdk 28` |
| Edge-to-edge | ✅ Tidak berlaku | Penegakan edge-to-edge hanya untuk `targetSdk 35`; kami 28 |
| Foreground service | ✅ Sudah ditangani | `foregroundServiceType="specialUse"` + izin `FOREGROUND_SERVICE_SPECIAL_USE` + fallback `MissingForegroundServiceTypeException` |
| Hidden API / refleksi | ⚠️ Risiko utama | Bypass native via `setHiddenApiExemptions` + fallback FreeReflection; bila simbol ART berubah di Android 15, virtualisasi bisa gagal — hanya uji HP yang memastikan |
| Notifikasi | ✅ Aman | `POST_NOTIFICATIONS` dideklarasi (auto-granted untuk target < 33); channel dibuat |
| Penyimpanan | ✅ Aman | Tanpa `requestLegacyExternalStorage` / `getExternalStorageDirectory`; `MANAGE_EXTERNAL_STORAGE` diminta lewat dialog |
| PendingIntent mutability | ✅ Tidak berlaku | Kode aplikasi tidak membuat PendingIntent; syarat berlaku untuk target 31+ |
| Exact alarm | ✅ Tidak dipakai | Kode aplikasi tidak memakai AlarmManager (hanya stub proxy untuk aplikasi clone) |
| QUERY_ALL_PACKAGES | ✅ Dideklarasi | Diperlukan untuk enumerasi aplikasi yang bisa di-clone |
| Predictive back | ✅ Aktif | `android:enableOnBackInvokedCallback="true"` |
| Background activity launch | ⚠️ Uji HP | Peluncuran clone dari latar belakang bisa diblokir Android 15 |
| Private Space | ⚠️ Uji HP | Aplikasi di Private Space tidak terlihat oleh enumerasi paket |

## 1. Yang sudah dicek

### 1.1 16 KB page size — LOLOS (terverifikasi dari APK)
- `Bcore/src/main/cpp/Application.mk`: `APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true`,
  `APP_PLATFORM := android-24`, NDK `29.0.13846066`.
- `readelf -l` pada `libblackbox.so` dari APK debug rebrand
  (`CloneKu_4.1.0_arm64-v8a-debug.apk` dan `..._armeabi-v7a-debug.apk`):
  semua segmen `LOAD` punya `Align 0x4000` (16 KB) di **kedua** ABI.
- `Bcore/build.gradle`: `packagingOptions.jniLibs.useLegacyPackaging true` —
  `.so` disimpan terkompresi lalu diekstrak saat instal, jadi alignment
  offset di dalam ZIP tidak relevan; file hasil ekstrak di disk sudah
  rata 16 KB.
- AGP 8.13.2 + Gradle 8.14.5 (mendukung build 16 KB; pemeriksaan
  alignment tersedia sejak AGP 8.5.1).

### 1.2 Foreground service — LOLOS dengan catatan
- `Bcore/src/main/AndroidManifest.xml`:
  `DaemonService`, `DaemonService$DaemonInnerService`, dan
  `ProxyVpnService` mendeklarasikan `android:foregroundServiceType="specialUse"`;
  izin `FOREGROUND_SERVICE` dan `FOREGROUND_SERVICE_SPECIAL_USE` dideklarasikan.
- `ProxyVpnService.onStartCommand()` sudah bercabang API 34+:
  `startForeground(id, notif, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`.
- `BlackBoxCore` (dipakai saat aplikasi clone menjalankan service) sudah
  defensif: `startForegroundService()` dibungkus try/catch
  `MissingForegroundServiceTypeException` lalu fallback ke `startService()`.
- Catatan: properti manifest `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE`
  **tidak** dideklarasikan — properti itu baru wajib saat `targetSdk >= 34`.
  Bila targetSdk kelak dinaikkan, properti ini wajib ditambahkan.
- Catatan: ratusan stub service untuk aplikasi clone di `Bcore` manifest
  tidak punya `foregroundServiceType`. Bila aplikasi clone yang
  menargetkan SDK 34+ menjalankan foreground service, sistem melempar
  `MissingForegroundServiceTypeException`; kode `BlackBoxCore` menangkapnya
  dan fallback ke service biasa — fitur clone itu bisa gagal diam-diam
  (bukan crash host). Lihat uji HP.

### 1.3 Hidden API / refleksi — RISIKO UTAMA, tidak bisa dipastikan dari kode
- Inti virtualisasi BlackBox memakai hidden API Android secara masif
  (`Bcore/src/main/java/black/**` — ratusan stub refleksi atas
  `ActivityManager`, `PackageManager`, `AlarmManager`, `NotificationManager`,
  `VMRuntime`, dll.).
- `Bcore/src/main/cpp/hidden_api.cpp` (`disable_hidden_api()`): mencari simbol
  `VMRuntime.setHiddenApiExemptions` di `libart.so` (3 varian nama simbol)
  lalu membebaskan prefix `"L"` (semua). Dipanggil dari
  `BlackBoxCore.setEssentialProperties()`; bila gagal, fallback ke
  `Reflection.unseal(context)` (FreeReflection 3.2.2,
  `com.github.tiann:FreeReflection`, lihat `Bcore/build.gradle`).
- Kegagalan ditangani dengan log + lanjut jalan (tidak crash), tapi bila
  kedua jalur gagal di Android 15, virtualisasi (jalankan clone) bisa
  gagal total. Mekanisme ini sensitif terhadap perubahan internal ART
  tiap rilis Android — **tidak ada cabang kode khusus Android 15
  (API 35 / `VANILLA_ICE_CREAM`) di repo**; satu-satunya cabang versi
  baru adalah `UPSIDE_DOWN_CAKE` (API 34) di `ProxyVpnService`.
- Native hook (Dobby inline hook, `DexFileHook`, `BinderHook`,
  `RuntimeHook` di `Bcore/src/main/cpp/Hook/`) juga bergantung pada
  internal ART/libbinder — risiko yang sama. Hanya uji HP yang memastikan.

### 1.4 Notifikasi — LOLOS
- `POST_NOTIFICATIONS` dideklarasikan di `Bcore` manifest. Untuk
  `targetSdk < 33`, izin ini auto-granted di Android 13+ — tidak perlu
  runtime request.
- `DaemonService` membuat notification channel (`blackbox_daemon_channel`,
  `IMPORTANCE_LOW`, tanpa suara/vibrasi) sebelum `startForeground`.

### 1.5 Penyimpanan — LOLOS
- Tidak ada `requestLegacyExternalStorage` di manifest mana pun, tidak ada
  pemakaian `Environment.getExternalStorageDirectory()` /
  `getExternalStoragePublicDirectory()` di kode aplikasi.
- `targetSdk 28` → perilaku legacy external storage tetap berlaku di
  Android 15 (scoped storage ditegakkan mulai target 29).
- `MainActivity.checkStoragePermission()`: di API 30+ meminta
  `MANAGE_EXTERNAL_STORAGE` lewat dialog → `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`;
  di bawah API 30 meminta runtime permission `READ/WRITE_EXTERNAL_STORAGE`.
  Alur ini tetap valid di Android 15.

### 1.6 Lain-lain yang dicek
- **Install**: Android 15 memblokir instal aplikasi `targetSdk < 24` —
  kami 28, aman. (Play Store mewajibkan target 35, tapi CloneKu
  sideload-only; `targetSdk` sengaja dipertahankan 28.)
- **Edge-to-edge**: penegakan hanya untuk `targetSdk 35`. Tidak ada
  perubahan kode yang diperlukan.
- **PendingIntent**: kode aplikasi tidak membuat `PendingIntent`
  (hanya stub refleksi `black/android/app/PendingIntent*` untuk
  aplikasi clone). Syarat `FLAG_IMMUTABLE`/`FLAG_MUTABLE` berlaku
  untuk target 31+ — tidak berlaku untuk kami.
- **Exact alarm**: kode aplikasi tidak memakai `AlarmManager` /
  `setExact`; hanya stub proxy `IAlarmManagerProxy` untuk clone.
  Tidak butuh `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`.
- **WorkManager**: aplikasi tidak memakai WorkManager langsung
  (hanya `WorkManagerProxy` untuk clone). Dependensi
  `androidx.work:work-runtime:2.7.1` tetap ada.
- **Paket**: `QUERY_ALL_PACKAGES` dideklarasikan di manifest aplikasi
  (dibutuhkan untuk daftar aplikasi yang bisa di-clone).
- **Predictive back**: `android:enableOnBackInvokedCallback="true"`
  sudah di-set di manifest aplikasi.
- **Toolchain**: `compileSdk 35`, AGP 8.13.2, Gradle 8.14.5, NDK 29,
  Kotlin JVM target 21 — modern, tidak ada pemakaian API yang
  dihapus di API 35 pada kode aplikasi.
- **Cleartext**: `Bcore` memakai `android:usesCleartextTraffic="true"`;
  manifest aplikasi memakai `networkSecurityConfig` sendiri.

## 2. Perbaikan kode yang diterapkan

**Tidak ada perubahan perilaku yang diterapkan.** Alasannya:

- Semua temuan di atas sudah ditangani oleh kode yang ada (FGS type,
  fallback exception, channel notifikasi, alur izin storage), atau
- merupakan risiko inheren virtualisasi (hidden API / ART hook) yang
  tidak bisa diperbaiki dengan aman tanpa mengubah perilaku inti — dan
  setiap "perbaikan" spekulatif di jalur itu berisiko merusak perangkat
  Android lama yang sudah berjalan.

Satu-satunya perubahan terkait rilis di trek ini adalah penomoran versi
dan signing (lihat `build.gradle`, `app/build.gradle`, dan commit
`trek-c-rilis`); tidak ada perubahan logika runtime.

## 3. Yang HANYA bisa dipastikan lewat uji HP (tugas user)

Dilakukan di HP Android 15 (dan bila ada, Android 14 sebagai pembanding),
dengan APK release `CloneKu_1.0.0` dari trek ini:

1. **Instal + buka pertama**: APK terinstal tanpa blokir; dialog
   "All Files Access" muncul dan setelah diberikan, daftar aplikasi muncul.
2. **Clone WhatsApp** (lihat `docs/UJI_WA.md`): clone dibuat, dibuka,
   login, kirim/terima pesan.
3. **Daemon tetap hidup**: setelah CloneKu ditutup dari recent-apps,
   clone WhatsApp masih menerima pesan (daemon + FGS berjalan);
   periksa apakah muncul notifikasi sistem "CloneKu berjalan di
   latar belakang" (trik sembunyi-notifikasi `DaemonInnerService`
   bisa tidak lagi bekerja di Android 13+ — kosmetik, catat saja).
4. **Restart HP**: setelah reboot, buka CloneKu → clone WhatsApp
   masih ada dan riwayat chat utuh.
5. **Notifikasi clone**: pesan WA masuk di space 1 & 2 memunculkan
   notifikasi dengan nama/ikon yang benar; ketuk notifikasi membuka
   space yang benar.
6. **Mode hemat baterai / background restriction**: dengan "Battery
   optimization" aktif, clone tetap menerima pesan (atau catat bila
   harus di-unrestricted).
7. **Private Space (bila aktif)**: aplikasi di dalam Private Space
   tidak muncul di daftar clone — catat sebagai keterbatasan.
8. **Aplikasi clone targetSdk 34+ yang memakai FGS** (mis. pemutar
   musik / perekam): catat bila fiturnya mati diam-diam saat
   berjalan di dalam clone.
9. **Log bila gagal**: `adb logcat` saat reproduksi; cari
   `HiddenAPI:`, `Reflection.unseal failed`, `Failed to install
   system hooks`, `MissingForegroundServiceTypeException`.

Bila poin 2/3/4 gagal total, kemungkinan besar penyebabnya adalah
bypass hidden API (bagian 1.3) — laporkan beserta log di atas; perbaikan
saat itu memerlukan penyesuaian native/ART, bukan perbaikan kecil.
