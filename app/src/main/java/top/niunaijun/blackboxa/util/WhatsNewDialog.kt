package top.niunaijun.blackboxa.util

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import top.niunaijun.blackboxa.R

/**
 * Tur "Yang Baru di 1.0.0" (fix 1.0.1): dialog sederhana yang menunjuk
 * ke tiga fitur yang paling sering tidak ketemu pengguna — tahan-lama
 * pada clone (backup/restore data), ketuk nama space di toolbar (ubah
 * nama & ikon), dan label risiko di layar pilih aplikasi. Dipakai dari
 * MainActivity (sekali sesudah update ke 1.0.1) dan dari Pengaturan
 * (buka lagi kapan saja).
 */
object WhatsNewDialog {

    fun show(context: Context) {
        MaterialDialog(context).show {
            title(R.string.whats_new_title)
            message(R.string.whats_new_message)
            positiveButton(R.string.done)
        }
    }
}
