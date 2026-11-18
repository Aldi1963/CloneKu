# Checklist Uji WhatsApp 2 Space — CloneKu 1.0.0

Checklist langkah-demi-langkah untuk menguji WhatsApp multi-akun di HP.
Pakai **APK release** `CloneKu_1.0.0` dari Trek C (bukan APK debug).
Siapkan **2 nomor WhatsApp berbeda** (mis. nomor utama + nomor kedua).
Nomor bisa dari 2 kartu SIM, atau 1 SIM + 1 nomor eSIM/WhatsApp kedua.

## Persiapan

- [ ] Instal APK release CloneKu 1.0.0 (izinkan "instal dari sumber tak dikenal" bila diminta).
- [ ] Buka CloneKu → berikan izin **"All Files Access"** saat dialog muncul
      (pengaturan akan terbuka — aktifkan untuk CloneKu, lalu kembali).
- [ ] Pastikan WhatsApp resmi **sudah terinstal** di HP dan login dengan nomor pertama.
- [ ] Pastikan koneksi internet HP aktif (Wi-Fi / data).

## 1. Clone WhatsApp ke space 1 dan space 2

- [ ] Di CloneKu, cari **WhatsApp** di daftar aplikasi → ketuk **Clone / +**.
- [ ] Clone pertama dibuat → beri nama mis. **"WA 1"** (space 1).
- [ ] Ulangi: clone WhatsApp sekali lagi → beri nama **"WA 2"** (space 2).
- [ ] Kedua ikon WA 1 dan WA 2 muncul di daftar clone CloneKu.

## 2. Login nomor berbeda di tiap space

- [ ] Buka **WA 1** dari CloneKu → login dengan **nomor pertama** (kode OTP via SMS/WhatsApp).
- [ ] Buka **WA 2** dari CloneKu → login dengan **nomor kedua**.
- [ ] Pastikan WA resmi di luar CloneKu **tetap login nomor pertama**
      dan tidak ikut ter-logout.

## 3. Kirim / terima pesan antar nomor

- [ ] Dari **WA 1**, kirim pesan ke nomor kedua → cek pesan **masuk di WA 2**.
- [ ] Dari **WA 2**, balas pesan → cek balasan **masuk di WA 1**.
- [ ] Kirim dari **WA resmi** (nomor pertama) ke nomor kedua → cek masuk di WA 2.
- [ ] Kirim **foto** dari WA 1 ke WA 2 → cek foto tampil dan bisa dibuka.
- [ ] Kirim **voice note** dari WA 2 ke WA 1 → cek bisa diputar.
- [ ] Cek **centang biru / status online**: WA 1 dan WA 2 tampil sebagai
      dua pengguna berbeda di HP lawan bicara (bila memungkinkan).

## 4. Notifikasi

- [ ] Tutup CloneKu dari recent-apps (swipe), tapi jangan force-stop.
- [ ] Kirim pesan dari HP lain ke **nomor kedua** → notifikasi muncul?
      Ketuk notifikasi → apakah membuka **WA 2** (bukan WA 1 / WA resmi)?
- [ ] Kirim pesan ke **nomor pertama** → notifikasi muncul?
      Ketuk notifikasi → apakah membuka **WA 1** (bukan WA resmi)?
- [ ] Cek apakah ada notifikasi sistem "CloneKu berjalan di latar belakang"
      yang tidak bisa dihilangkan — catat bila ada (kosmetik).

## 5. Riwayat chat tetap ada sesudah restart

- [ ] Di WA 1 dan WA 2, catat 1 pesan terakhir sebagai penanda.
- [ ] Tutup paksa CloneKu: Pengaturan → Aplikasi → CloneKu → **Paksa berhenti**.
- [ ] Buka lagi CloneKu → buka WA 1 dan WA 2 → pesan penanda masih ada?
- [ ] **Restart HP** → buka CloneKu → buka WA 1 dan WA 2 →
      riwayat chat masih utuh? Login masih bertahan (tidak diminta login ulang)?

## 6. Uji ketahanan (bila sempat)

- [ ] Buka WA 1, lalu tanpa menutup buka WA 2 (pindah space) → keduanya
      tetap jalan, tidak saling menutup / crash.
- [ ] Terima **panggilan suara** di WA 2 saat WA 1 sedang dibuka → panggilan masuk?
- [ ] Coba **Status** di WA 2 (lihat status kontak) → tampil normal?

## Bila gagal — yang dicatat

Untuk tiap kegagalan, catat:

1. **Langkah** yang gagal (nomor langkah di atas) dan **apa yang terlihat**
   (crash? layar hitam? loading berputar terus? pesan error?).
2. **Screenshot** layar saat gagal + screenshot daftar clone.
3. **HP**: merek/model, versi Android (Pengaturan → Tentang ponsel).
4. **Nomor**: space mana (WA 1 / WA 2 / WA resmi) dan nomor mana yang bermasalah
   (cukup "nomor pertama/kedua", jangan tulis nomor lengkap di laporan umum).
5. **Log (bila bisa)**: hubungkan HP ke laptop, jalankan
   `adb logcat | grep -i -E "blackbox|hiddenapi|cloneku"` saat mengulang
   langkah yang gagal, simpan outputnya.
6. Kirim semua catatan itu ke pengembang — jangan hanya bilang "gagal",
   karena penyebab gagal clone WA 1 dan WA 2 bisa berbeda.
