package top.niunaijun.blackboxa.view.main

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.DrawableCompat
import androidx.viewpager2.widget.ViewPager2
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.app.App
import top.niunaijun.blackboxa.app.AppManager
import top.niunaijun.blackboxa.databinding.ActivityMainBinding
import top.niunaijun.blackboxa.util.Resolution
import top.niunaijun.blackboxa.util.WhatsNewDialog
import top.niunaijun.blackboxa.util.inflate
import top.niunaijun.blackboxa.view.apps.AppsFragment
import top.niunaijun.blackboxa.view.base.LoadingActivity
import top.niunaijun.blackboxa.view.fake.FakeManagerActivity
import top.niunaijun.blackboxa.view.list.ListActivity
import top.niunaijun.blackboxa.view.setting.SettingActivity

class MainActivity : LoadingActivity() {

    private val viewBinding: ActivityMainBinding by inflate()

    private lateinit var mViewPagerAdapter: ViewPagerAdapter

    private val fragmentList = mutableListOf<AppsFragment>()

    private var currentUser = 0

    companion object {
        private const val TAG = "MainActivity"
        private const val STORAGE_PERMISSION_REQUEST_CODE = 1001
        private const val VPN_PERMISSION_REQUEST_CODE = 1002

        /** Paket aplikasi cloner lama (MaxCloner). */
        private const val OLD_CLONER_PACKAGE = "com.maxcloner.app"

        /** Flag sekali-tampil di SharedPreferences "UserRemark". */
        private const val PREF_MIGRATION_WARNING_SHOWN = "MigrationWarningShown"
        private const val PREF_WHATS_NEW_SHOWN_101 = "WhatsNewShown101"

        /**
         * Built-in icons a clone space can use, as (drawable, label) pairs.
         * The chosen index is stored in the UserRemark SharedPreferences
         * under "SpaceIcon<userId>". Framework drawables are used so no
         * extra assets are needed.
         */
        val SPACE_ICONS = listOf(
                android.R.drawable.ic_menu_view to R.string.space_icon_general,
                android.R.drawable.ic_menu_agenda to R.string.space_icon_work,
                android.R.drawable.ic_menu_myplaces to R.string.space_icon_personal,
                android.R.drawable.ic_menu_slideshow to R.string.space_icon_game,
                android.R.drawable.ic_menu_share to R.string.space_icon_social,
                android.R.drawable.ic_menu_gallery to R.string.space_icon_media
        )

        fun start(context: Context) {
            val intent = Intent(context, MainActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)

            try {
                BlackBoxCore.get().onBeforeMainActivityOnCreate(this)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onBeforeMainActivityOnCreate: ${e.message}")
            }

            setContentView(viewBinding.root)
            initToolbar(viewBinding.toolbarLayout.toolbar, R.string.app_name)
            initViewPager()
            initFab()
            initToolbarSubTitle()
            postFirstRunNotices()

            
            checkStoragePermission()

            
            checkVpnPermission()

            try {
                BlackBoxCore.get().onAfterMainActivityOnCreate(this)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onAfterMainActivityOnCreate: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in onCreate: ${e.message}")
            
            showErrorDialog(getString(R.string.init_failed, e.message ?: ""))
        }
    }

    private fun checkStoragePermission() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                
                if (!android.os.Environment.isExternalStorageManager()) {
                    Log.w(TAG, "MANAGE_EXTERNAL_STORAGE permission not granted")
                    showStoragePermissionDialog()
                }
            } else {
                
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                                this,
                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED ||
                                androidx.core.content.ContextCompat.checkSelfPermission(
                                        this,
                                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w(
                            TAG,
                            "Storage permissions not granted on Android ${android.os.Build.VERSION.SDK_INT}"
                    )
                    requestLegacyStoragePermission()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking storage permission: ${e.message}")
        }
    }

    private fun requestLegacyStoragePermission() {
        try {
            androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    STORAGE_PERMISSION_REQUEST_CODE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting storage permission: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() &&
                            grantResults.all {
                                it == android.content.pm.PackageManager.PERMISSION_GRANTED
                            }
            ) {
                Log.d(TAG, "Storage permissions granted")
            } else {
                Log.w(TAG, "Storage permissions denied")
            }
        }
    }

    private fun showStoragePermissionDialog() {
        try {
            MaterialDialog(this).show {
                title(res = R.string.storage_permission_title)
                message(text = getString(R.string.storage_permission_message))
                positiveButton(res = R.string.grant_permission) { openAllFilesAccessSettings() }
                negativeButton(res = R.string.later) { Log.w(TAG, "User postponed storage permission") }
                cancelable(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing storage permission dialog: ${e.message}")
        }
    }

    private fun openAllFilesAccessSettings() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val intent =
                        Intent(
                                android.provider.Settings
                                        .ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                        )
                intent.data = Uri.parse("package:$packageName")
                storagePermissionResult.launch(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening storage settings: ${e.message}")
            
            try {
                val intent =
                        Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                storagePermissionResult.launch(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Error opening fallback storage settings: ${e2.message}")
            }
        }
    }

    private val storagePermissionResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        if (android.os.Environment.isExternalStorageManager()) {
                            Log.d(TAG, "Storage permission granted!")
                        } else {
                            Log.w(TAG, "Storage permission still not granted")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling storage permission result: ${e.message}")
                }
            }

    
    private fun checkVpnPermission() {
        try {
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                
                Log.d(TAG, "VPN permission not granted, requesting...")
                vpnPermissionResult.launch(vpnIntent)
            } else {
                
                Log.d(TAG, "VPN permission already granted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking VPN permission: ${e.message}")
        }
    }

    private val vpnPermissionResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                try {
                    if (result.resultCode == RESULT_OK) {
                        Log.d(TAG, "VPN permission granted!")
                        
                    } else {
                        Log.w(TAG, "VPN permission denied by user")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling VPN permission result: ${e.message}")
                }
            }

    private fun showErrorDialog(message: String) {
        try {
            MaterialDialog(this).show {
                title(res = R.string.error_title)
                message(text = message)
                positiveButton(res = R.string.ok) { finish() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing error dialog: ${e.message}")
            finish()
        }
    }

    private fun initToolbarSubTitle() {
        try {
            updateUserRemark(0)

            viewBinding.toolbarLayout.toolbar.getChildAt(1)?.setOnClickListener {
                showEditSpaceDialog()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in initToolbarSubTitle: ${e.message}")
        }
    }

    /**
     * Pemberitahuan jalan-pertama (fix 1.0.1): peringatan migrasi bila
     * MaxCloner masih terinstal, lalu tur "Yang Baru di 1.0.0" sekali
     * sesudah update. Ditunda sebentar supaya tidak menumpuk dengan
     * dialog izin penyimpanan/VPN di onCreate.
     */
    private fun postFirstRunNotices() {
        try {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    if (!isFinishing) {
                        maybeShowMigrationWarning()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing first-run notices: ${e.message}")
                }
            }, 800)
        } catch (e: Exception) {
            Log.e(TAG, "Error posting first-run notices: ${e.message}")
        }
    }

    /** true bila paket terinstal (QUERY_ALL_PACKAGES sudah dideklarasikan). */
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            Log.w(TAG, "Could not check package $packageName: ${e.message}")
            false
        }
    }

    /**
     * Peringatan migrasi (fix 1.0.1): bila MaxCloner masih terinstal,
     * beri tahu sekali bahwa clone lamanya tidak ikut pindah — migrasi
     * otomatis tidak mungkin tanpa root (UID berbeda, /data/data paket
     * lain tidak bisa dibaca) dan MaxCloner tidak punya fitur backup.
     * Sesudah dialog ditutup, lanjut ke tur Yang Baru bila belum tampil.
     */
    private fun maybeShowMigrationWarning() {
        try {
            val prefs = AppManager.mRemarkSharedPreferences
            if (prefs.getBoolean(PREF_MIGRATION_WARNING_SHOWN, false)) {
                maybeShowWhatsNew()
                return
            }
            if (!isPackageInstalled(OLD_CLONER_PACKAGE)) {
                maybeShowWhatsNew()
                return
            }
            MaterialDialog(this).show {
                title(R.string.migration_title)
                message(R.string.migration_message)
                positiveButton(R.string.done) {
                    try {
                        prefs.edit { putBoolean(PREF_MIGRATION_WARNING_SHOWN, true) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving migration flag: ${e.message}")
                    }
                    maybeShowWhatsNew()
                }
                cancelable(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing migration warning: ${e.message}")
            maybeShowWhatsNew()
        }
    }

    /** Tur "Yang Baru di 1.0.0" — sekali sesudah update ke 1.0.1. */
    private fun maybeShowWhatsNew() {
        try {
            val prefs = AppManager.mRemarkSharedPreferences
            if (prefs.getBoolean(PREF_WHATS_NEW_SHOWN_101, false)) {
                return
            }
            prefs.edit { putBoolean(PREF_WHATS_NEW_SHOWN_101, true) }
            WhatsNewDialog.show(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing whats-new tour: ${e.message}")
        }
    }

    /**
     * Edit the current clone space: its name and its icon. Stored in the same
     * UserRemark SharedPreferences the space name already used, under
     * "Remark<userId>" (name) and "SpaceIcon<userId>" (icon index into
     * SPACE_ICONS). An empty name falls back to the default "Space N".
     */
    private fun showEditSpaceDialog() {
        try {
            var selectedIcon = getSpaceIcon(currentUser)
            MaterialDialog(this).show {
                title(res = R.string.space_name)
                customView(R.layout.dialog_space_edit)
                val customView = getCustomView()
                val nameInput = customView.findViewById<EditText>(R.id.space_name_input)
                nameInput.setText(getSpaceName(currentUser))
                nameInput.setSelection(nameInput.text.length)
                val iconGrid = customView.findViewById<GridLayout>(R.id.space_icon_grid)
                fun refreshSelection() {
                    for (i in 0 until iconGrid.childCount) {
                        val item = iconGrid.getChildAt(i)
                        item.isSelected =
                                (item.tag as? String)?.toIntOrNull() == selectedIcon
                    }
                }
                for (i in 0 until iconGrid.childCount) {
                    val item = iconGrid.getChildAt(i)
                    item.setOnClickListener {
                        selectedIcon = (item.tag as? String)?.toIntOrNull() ?: 0
                        refreshSelection()
                    }
                }
                refreshSelection()
                positiveButton(res = R.string.done) {
                    try {
                        val name = nameInput.text.toString().trim()
                        AppManager.mRemarkSharedPreferences.edit {
                            putString(
                                    "Remark$currentUser",
                                    name.ifEmpty {
                                        getString(
                                                R.string.space_name_default,
                                                (currentUser + 1).toString()
                                        )
                                    }
                            )
                            putInt("SpaceIcon$currentUser", selectedIcon)
                        }
                        updateUserRemark(currentUser)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving space name: ${e.message}")
                    }
                }
                negativeButton(res = R.string.cancel)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing space edit dialog: ${e.message}")
        }
    }

    /** Display name of a clone space; defaults to "Space 1", "Space 2", ... */
    private fun getSpaceName(userId: Int): String {
        val defaultName = getString(R.string.space_name_default, (userId + 1).toString())
        val remark = AppManager.mRemarkSharedPreferences.getString("Remark$userId", defaultName)
        return if (remark.isNullOrEmpty()) defaultName else remark
    }

    /** Index into SPACE_ICONS for a clone space; 0 (General) when unset. */
    private fun getSpaceIcon(userId: Int): Int {
        val index = AppManager.mRemarkSharedPreferences.getInt("SpaceIcon$userId", 0)
        return index.coerceIn(SPACE_ICONS.indices)
    }

    private fun initViewPager() {
        try {
            val userList = BlackBoxCore.get().users
            userList.forEach { fragmentList.add(AppsFragment.newInstance(it.id)) }

            currentUser = userList.firstOrNull()?.id ?: 0
            fragmentList.add(AppsFragment.newInstance(userList.size))

            mViewPagerAdapter = ViewPagerAdapter(this)
            mViewPagerAdapter.replaceData(fragmentList)
            viewBinding.viewPager.adapter = mViewPagerAdapter
            viewBinding.dotsIndicator.setViewPager2(viewBinding.viewPager)
            viewBinding.viewPager.registerOnPageChangeCallback(
                    object : ViewPager2.OnPageChangeCallback() {
                        override fun onPageSelected(position: Int) {
                            try {
                                super.onPageSelected(position)
                                currentUser = fragmentList[position].userID
                                updateUserRemark(currentUser)
                                showFloatButton(true)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in onPageSelected: ${e.message}")
                            }
                        }
                    }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in initViewPager: ${e.message}")
        }
    }

    private fun initFab() {
        try {
            viewBinding.fab.setOnClickListener {
                try {
                    val userId = viewBinding.viewPager.currentItem
                    val intent = Intent(this, ListActivity::class.java)
                    intent.putExtra("userID", userId)
                    apkPathResult.launch(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching ListActivity: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in initFab: ${e.message}")
        }
    }

    fun showFloatButton(show: Boolean) {
        try {
            val tranY: Float = Resolution.convertDpToPixel(120F, App.getContext())
            val time = 200L
            if (show) {
                viewBinding.fab.animate().translationY(0f).alpha(1f).setDuration(time).start()
            } else {
                viewBinding.fab.animate().translationY(tranY).alpha(0f).setDuration(time).start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in showFloatButton: ${e.message}")
        }
    }

    fun scanUser() {
        try {
            val userList = BlackBoxCore.get().users

            if (fragmentList.size == userList.size) {
                fragmentList.add(AppsFragment.newInstance(fragmentList.size))
            } else if (fragmentList.size > userList.size + 1) {
                fragmentList.removeLast()
            }

            mViewPagerAdapter.notifyDataSetChanged()
        } catch (e: Exception) {
            Log.e(TAG, "Error in scanUser: ${e.message}")
        }
    }

    private fun updateUserRemark(userId: Int) {
        try {
            val toolbar = viewBinding.toolbarLayout.toolbar
            toolbar.subtitle = getSpaceName(userId)

            // Show the space icon at the start of the subtitle.
            val subtitleView = toolbar.getChildAt(1) as? TextView
            val icon =
                    ContextCompat.getDrawable(this, SPACE_ICONS[getSpaceIcon(userId)].first)
            icon?.let {
                DrawableCompat.setTint(
                        it,
                        ContextCompat.getColor(this, R.color.cloneku_accent)
                )
            }
            subtitleView?.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
            subtitleView?.compoundDrawablePadding =
                    Resolution.convertDpToPixel(6F, App.getContext()).toInt()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user remark: ${e.message}")
            viewBinding.toolbarLayout.toolbar.subtitle = getSpaceName(userId)
        }
    }

    private val apkPathResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                try {
                    if (it.resultCode == RESULT_OK) {
                        it.data?.let { data ->
                            val userId = data.getIntExtra("userID", 0)
                            val source = data.getStringExtra("source")
                            if (source != null) {
                                fragmentList[userId].installApk(source)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling APK path result: ${e.message}")
                }
            }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        try {
            menuInflater.inflate(R.menu.menu_main, menu)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating options menu: ${e.message}")
            return false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        try {
            when (item.itemId) {
                R.id.edit_space -> {
                    showEditSpaceDialog()
                }
                R.id.main_git -> {
                    val intent =
                            Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/ALEX5402/NewBlackbox")
                            )
                    startActivity(intent)
                }
                R.id.main_setting -> {
                    SettingActivity.start(this)
                }
                R.id.main_tg -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/newblackboxa"))
                    startActivity(intent)
                }
                R.id.fake_location -> {
                    
                    val intent = Intent(this, FakeManagerActivity::class.java)
                    intent.putExtra("userID", 0)
                    startActivity(intent)
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error handling menu item selection: ${e.message}")
            return false
        }
    }
}
