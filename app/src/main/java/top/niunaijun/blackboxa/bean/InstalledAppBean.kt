package top.niunaijun.blackboxa.bean

import android.graphics.drawable.Drawable
import top.niunaijun.blackboxa.util.CloneRisk


data class InstalledAppBean(
        val name: String,
        val icon: Drawable?,
        val packageName: String,
        val sourceDir: String,
        val isInstall: Boolean,
        /** Hasil heuristik [CloneRisk] (NONE bila tidak ada sinyal risiko). */
        val risk: CloneRisk.Risk = CloneRisk.Risk.NONE
)
