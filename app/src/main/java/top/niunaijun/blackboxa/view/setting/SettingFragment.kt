package top.niunaijun.blackboxa.view.setting

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.afollestad.materialdialogs.MaterialDialog
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.app.AppManager
import top.niunaijun.blackboxa.util.UpdateChecker
import top.niunaijun.blackboxa.util.toast
import top.niunaijun.blackboxa.view.gms.GmsManagerActivity

class SettingFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.setting, rootKey)

        initGms()

        initCheckUpdate()

        invalidHideState {
            val rootHidePreference: Preference = (findPreference("root_hide")!!)
            val hideRoot = AppManager.mBlackBoxLoader.hideRoot()
            rootHidePreference.setDefaultValue(hideRoot)
            rootHidePreference
        }

        invalidHideState {
            val daemonPreference: Preference = (findPreference("daemon_enable")!!)
            val mDaemonEnable = AppManager.mBlackBoxLoader.daemonEnable()
            daemonPreference.setDefaultValue(mDaemonEnable)
            daemonPreference
        }

        invalidHideState {
            val vpnPreference: Preference = (findPreference("use_vpn_network")!!)
            val mUseVpnNetwork = AppManager.mBlackBoxLoader.useVpnNetwork()
            vpnPreference.setDefaultValue(mUseVpnNetwork)
            vpnPreference
        }

        invalidHideState {
            val disableFlagSecurePreference: Preference = (findPreference("disable_flag_secure")!!)
            val mDisableFlagSecure = AppManager.mBlackBoxLoader.disableFlagSecure()
            disableFlagSecurePreference.setDefaultValue(mDisableFlagSecure)
            disableFlagSecurePreference
        }

        initSendLogs()
    }

    /**
     * Pemeriksa update di dalam aplikasi (item 8). Selama
     * [UpdateChecker.UPDATE_CHECK_URL] masih kosong (belum ada repo rilis),
     * pengguna mendapat pesan jujur "belum dikonfigurasi", bukan error.
     */
    private fun initCheckUpdate() {
        val checkUpdatePreference: Preference? = findPreference("check_update")
        checkUpdatePreference?.setOnPreferenceClickListener {
            toast(R.string.update_checking)
            UpdateChecker.check { result ->
                val ctx = context ?: return@check
                when (result) {
                    is UpdateChecker.Result.NotConfigured -> {
                        MaterialDialog(ctx).show {
                            title(R.string.check_update)
                            message(R.string.update_not_configured)
                            positiveButton(R.string.done)
                        }
                    }
                    is UpdateChecker.Result.UpToDate -> {
                        toast(getString(R.string.update_up_to_date, result.versionName))
                    }
                    is UpdateChecker.Result.Available -> {
                        val message =
                                getString(
                                        R.string.update_available_message,
                                        result.versionName,
                                        UpdateChecker.currentVersionName()
                                ) + if (result.changelog.isNotBlank()) {
                                    "\n\n${result.changelog}"
                                } else {
                                    ""
                                }
                        MaterialDialog(ctx).show {
                            title(R.string.update_available_title)
                            message(text = message)
                            if (result.downloadUrl.isNotBlank()) {
                                positiveButton(R.string.update_download) {
                                    runCatching {
                                        startActivity(
                                                Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse(result.downloadUrl)
                                                )
                                        )
                                    }
                                }
                                negativeButton(R.string.cancel)
                            } else {
                                positiveButton(R.string.done)
                            }
                        }
                    }
                    is UpdateChecker.Result.Error -> {
                        toast(getString(R.string.update_check_failed, result.message))
                    }
                }
            }
            true
        }
    }

    private fun initGms() {
        val gmsManagerPreference: Preference = (findPreference("gms_manager")!!)

        if (BlackBoxCore.get().isSupportGms) {

            gmsManagerPreference.setOnPreferenceClickListener {
                GmsManagerActivity.start(requireContext())
                true
            }
        } else {
            gmsManagerPreference.summary = getString(R.string.no_gms)
            gmsManagerPreference.isEnabled = false
        }
    }

    private fun invalidHideState(block: () -> Preference) {
        val pref = block()
        pref.setOnPreferenceChangeListener { preference, newValue ->
            val tmpHide = (newValue == true)
            when (preference.key) {
                "root_hide" -> {

                    AppManager.mBlackBoxLoader.invalidHideRoot(tmpHide)
                }
                "daemon_enable" -> {
                    AppManager.mBlackBoxLoader.invalidDaemonEnable(tmpHide)
                }
                "use_vpn_network" -> {
                    AppManager.mBlackBoxLoader.invalidUseVpnNetwork(tmpHide)
                }
                "disable_flag_secure" -> {
                    AppManager.mBlackBoxLoader.invalidDisableFlagSecure(tmpHide)
                }
            }

            toast(R.string.restart_module)
            return@setOnPreferenceChangeListener true
        }
    }
    private fun initSendLogs() {
        val sendLogsPreference: Preference? = findPreference("send_logs")
        sendLogsPreference?.setOnPreferenceClickListener {
            it.isEnabled = false
            BlackBoxCore.get()
                    .sendLogs(
                            "Manual Log Upload from Settings",
                            true,
                            object : BlackBoxCore.LogSendListener {
                                override fun onSuccess() {
                                    activity?.runOnUiThread { sendLogsPreference.isEnabled = true }
                                }

                                override fun onFailure(error: String?) {
                                    activity?.runOnUiThread { sendLogsPreference.isEnabled = true }
                                }
                            }
                    )
            toast("Sending logs... (Check notifications for status)")
            true
        }
    }
}
