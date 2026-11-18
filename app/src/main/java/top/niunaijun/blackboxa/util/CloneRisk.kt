package top.niunaijun.blackboxa.util

/**
 * Heuristik risiko clone untuk aplikasi yang kemungkinan besar gagal atau
 * bermasalah saat dijalankan di dalam CloneKu.
 *
 * PENTING — ini HANYA heuristik, bukan kepastian:
 * - Daftar paket di bawah ini dikurasi manual dan tidak lengkap; aplikasi
 *   perbankan/pembayaran lain yang tidak ada di daftar tetap bisa ditolak.
 * - Aplikasi yang ADA di daftar pun belum tentu selalu gagal — perilaku
 *   tiap aplikasi bergantung pada versi aplikasi, perangkat, dan cara
 *   vendor memeriksa lingkungan (Play Integrity, SafetyNet, deteksi root,
 *   deteksi emulator/virtualisasi).
 * - Sinyal kedua adalah kata kunci di nama paket (mis. "bank", "wallet").
 *   CATATAN: rencana awal memakai kategori Play Store dari
 *   ApplicationInfo.category, tapi API Android TIDAK punya kategori
 *   Finance di sana (hanya Game, Audio, Video, Image, Social, News,
 *   Maps, Productivity, Accessibility, Undefined) — jadi sinyal kategori
 *   tidak bisa dipakai dan diganti kata kunci nama paket. Aplikasi bank
 *   yang tidak cocok dengan keduanya tetap bisa lolos tanpa label; itu
 *   keterbatasan yang diterima, bukan bug.
 *
 * Semua teks yang ditampilkan ke pengguna harus memakai kata
 * "kemungkinan"/"mungkin" — jangan pernah mengklaim kepastian untuk
 * aplikasi yang belum diuji.
 */
object CloneRisk {

    enum class Risk {
        /** Tidak ada sinyal risiko yang diketahui. */
        NONE,

        /** Aplikasi perbankan/pembayaran: kemungkinan ditolak Play Integrity. */
        PAYMENT,

        /** Aplikasi pesan: akun hasil clone berisiko ditangguhkan vendor. */
        MESSAGING,

        /** Kedua sinyal di atas (jarang; mis. aplikasi pesan + pembayaran). */
        BOTH
    }

    /**
     * Paket aplikasi perbankan/pembayaran Indonesia yang umum.
     * Daftar kurasi — bukan daftar resmi dan bukan jaminan. Tambahkan paket
     * baru di sini bila ada laporan pengguna yang terkonfirmasi.
     */
    private val BANKING_PAYMENT_PACKAGES = setOf(
            // BCA
            "com.bca", // BCA mobile
            "com.bca.mybca.omni.android", // myBCA
            // BRI
            "com.bri.brimo", // BRImo
            // Bank Mandiri
            "com.bankmandiri.mandiri.e.cash", // Livin' by Mandiri
            // DANA
            "id.dana", // DANA
            // OVO
            "ovo.id", // OVO
            // Gojek — GoPay tinggal di dalam aplikasi Gojek
            "com.gojek.app",
            // LinkAja
            "com.telkomsel.tcash",
            // CIMB Niaga — OCTO by CIMB Niaga
            "id.co.cimbniaga.mobile.android",
            // BNI — wondr by BNI (pengganti BNI Mobile Banking)
            "id.bni.wondr",
            // Jenius (Bank SMBC Indonesia)
            "com.btpn.dc",
            // ShopeePay tinggal di dalam aplikasi Shopee
            "com.shopee.id",
            // Beberapa dompet/pembayaran global yang juga umum dipakai
            "com.paypal.android.p2pmobile", // PayPal
            "com.google.android.apps.walletnfcrel" // Google Wallet
    )

    /**
     * Paket aplikasi pesan. WhatsApp mendapat peringatan paling tegas
     * (lihat [WHATSAPP_PACKAGES]) karena penangguhan akun clone WhatsApp
     * paling sering dilaporkan.
     */
    private val MESSAGING_PACKAGES = setOf(
            "com.whatsapp", // WhatsApp
            "com.whatsapp.w4b", // WhatsApp Business
            "org.telegram.messenger", // Telegram
            "org.telegram.messenger.web", // Telegram (varian web)
            "com.facebook.orca", // Messenger
            "com.tencent.mm", // WeChat
            "jp.naver.line.android", // LINE
            "com.viber.voip", // Viber
            "com.skype.raider", // Skype
            "org.thoughtcrime.securesms", // Signal
            "com.kakao.talk" // KakaoTalk
    )

    /** WhatsApp dan WhatsApp Business — risiko suspend paling tinggi. */
    val WHATSAPP_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b"
    )

    fun isWhatsApp(packageName: String): Boolean = packageName in WHATSAPP_PACKAGES

    fun isMessagingApp(packageName: String): Boolean = packageName in MESSAGING_PACKAGES

    /**
     * Kata kunci nama paket sebagai sinyal kedua (lihat catatan di atas —
     * ApplicationInfo.category tidak punya kategori Finance). Sengaja
     * pendek dan spesifik supaya jarang salah label; daftar kurasi di atas
     * tetap sinyal utamanya.
     */
    private val PAYMENT_PACKAGE_KEYWORDS = listOf("bank", "mbanking", "wallet", "qris")

    /**
     * True bila paket ada di daftar kurasi ATAU nama paketnya mengandung
     * kata kunci pembayaran. False negative mungkin terjadi (aplikasi bank
     * dengan nama paket yang tidak mengandung kata kunci dan tidak ada di
     * daftar) — keterbatasan heuristik ini didokumentasikan di atas.
     */
    fun isPaymentRisk(packageName: String): Boolean {
        if (packageName in BANKING_PAYMENT_PACKAGES) return true
        val lower = packageName.lowercase()
        return PAYMENT_PACKAGE_KEYWORDS.any { lower.contains(it) }
    }

    /** Gabungkan kedua sinyal menjadi satu [Risk]. */
    fun assess(packageName: String): Risk {
        val payment = isPaymentRisk(packageName)
        val messaging = isMessagingApp(packageName)
        return when {
            payment && messaging -> Risk.BOTH
            payment -> Risk.PAYMENT
            messaging -> Risk.MESSAGING
            else -> Risk.NONE
        }
    }
}
