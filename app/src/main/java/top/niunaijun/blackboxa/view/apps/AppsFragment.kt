package top.niunaijun.blackboxa.view.apps

import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import cbfg.rvadapter.RVAdapter
import com.afollestad.materialdialogs.MaterialDialog
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.AppInfo
import top.niunaijun.blackboxa.databinding.FragmentAppsBinding
import top.niunaijun.blackboxa.util.CloneBackupManager
import top.niunaijun.blackboxa.util.InjectionUtil
import top.niunaijun.blackboxa.util.ShortcutUtil
import top.niunaijun.blackboxa.util.inflate
import top.niunaijun.blackboxa.util.MemoryManager
import top.niunaijun.blackboxa.util.toast
import top.niunaijun.blackboxa.view.base.LoadingActivity
import top.niunaijun.blackboxa.view.main.MainActivity
import java.util.*
import kotlin.math.abs



class AppsFragment : Fragment() {

    var userID: Int = 0

    private lateinit var viewModel: AppsViewModel

    private lateinit var mAdapter: RVAdapter<AppInfo>

    private val viewBinding: FragmentAppsBinding by inflate()

    private var popupMenu: PopupMenu? = null

    /** Clone yang sedang menunggu dipilihnya file backup/restore (SAF). */
    private var pendingBackupApp: AppInfo? = null

    /**
     * SAF: pilih lokasi simpan arsip backup (item 4). MIME application/zip;
     * beberapa file manager melaporkan ZIP sebagai octet-stream saat dibuka
     * kembali, jadi impor menerima keduanya.
     */
    private val exportBackupLauncher =
            registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
                val app = pendingBackupApp
                pendingBackupApp = null
                if (uri != null && app != null) {
                    runBackupExport(app, uri)
                }
            }

    private val importBackupLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                val app = pendingBackupApp
                pendingBackupApp = null
                if (uri != null && app != null) {
                    runBackupImport(app, uri)
                }
            }

    companion object {
        private const val TAG = "AppsFragment"
        
        fun newInstance(userID:Int): AppsFragment {
            val fragment = AppsFragment()
            val bundle = bundleOf("userID" to userID)
            fragment.arguments = bundle
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            viewModel =
                ViewModelProvider(this, InjectionUtil.getAppsFactory()).get(AppsViewModel::class.java)
            userID = requireArguments().getInt("userID", 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        try {
            // Layar kosong kustom (fix 1.0.1): petunjuk migrasi dari
            // MaxCloner + cara clone pertama, bukan sekadar "No data!".
            // (StateView ditulis di Kotlin: pakai sintaks properti.)
            viewBinding.stateView.emptyResource = R.layout.state_empty_cloneku
            viewBinding.stateView.showEmpty()

            mAdapter =
                RVAdapter<AppInfo>(requireContext(), AppsAdapter()).bind(viewBinding.recyclerView)

            viewBinding.recyclerView.adapter = mAdapter
            
            
            val layoutManager = GridLayoutManager(requireContext(), 4)
            layoutManager.isItemPrefetchEnabled = true
            layoutManager.initialPrefetchItemCount = 8
            viewBinding.recyclerView.layoutManager = layoutManager
            
            
            viewBinding.recyclerView.setItemViewCacheSize(20)
            viewBinding.recyclerView.setHasFixedSize(true)
            
            
            viewBinding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    try {
                        super.onScrollStateChanged(recyclerView, newState)
                        when (newState) {
                            RecyclerView.SCROLL_STATE_IDLE -> {
                                
                                MemoryManager.optimizeMemoryForRecyclerView()
                            }
                            RecyclerView.SCROLL_STATE_DRAGGING -> {
                                
                                
                            }
                            RecyclerView.SCROLL_STATE_SETTLING -> {
                                
                                
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in scroll state change: ${e.message}")
                    }
                }
                
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    try {
                        super.onScrolled(recyclerView, dx, dy)
                        
                        if (Math.abs(dy) > 100) {
                            
                            
                            
                            if (MemoryManager.isMemoryCritical()) {
                                Log.w(TAG, "Memory critical during fast scrolling, forcing GC")
                                MemoryManager.forceGarbageCollectionIfNeeded()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in scroll: ${e.message}")
                    }
                }
            })

            val touchCallBack = AppsTouchCallBack { from, to ->
                try {
                    onItemMove(from, to)
                    viewModel.updateSortLiveData.postValue(true)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in touch callback: ${e.message}")
                }
            }

            val itemTouchHelper = ItemTouchHelper(touchCallBack)
            itemTouchHelper.attachToRecyclerView(viewBinding.recyclerView)

            mAdapter.setItemClickListener { _, data, _ ->
                try {
                    showLoading()
                    viewModel.launchApk(data.packageName, userID)
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching app: ${e.message}")
                    hideLoading()
                }
            }

            interceptTouch()
            setOnLongClick()
            return viewBinding.root
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreateView: ${e.message}")
            
            return View(requireContext())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        try {
            super.onViewCreated(view, savedInstanceState)
            initData()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated: ${e.message}")
        }
    }

    override fun onStart() {
        try {
            super.onStart()
            
            
            try {
                BlackBoxCore.get().addServiceAvailableCallback {
                    Log.d(TAG, "Services became available, refreshing app list")
                    
                    viewModel.getInstalledAppsWithRetry(userID)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering service available callback: ${e.message}")
            }
            
            viewModel.getInstalledAppsWithRetry(userID)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStart: ${e.message}")
        }
    }

    
    private fun interceptTouch() {
        try {
            val point = Point()
            var isScrolling = false
            var scrollStartTime = 0L
            
            viewBinding.recyclerView.setOnTouchListener { _, e ->
                try {
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> {
                            
                            isScrolling = false
                            scrollStartTime = System.currentTimeMillis()
                            point.set(0, 0)
                        }
                        
                        MotionEvent.ACTION_UP -> {
                            val scrollDuration = System.currentTimeMillis() - scrollStartTime
                            
                            
                            if (!isScrolling && !isMove(point, e) && scrollDuration < 500) {
                                try {
                                    popupMenu?.show()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error showing popup menu: ${e.message}")
                                }
                            }
                            
                            popupMenu = null
                            point.set(0, 0)
                            isScrolling = false
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (point.x == 0 && point.y == 0) {
                                point.x = e.rawX.toInt()
                                point.y = e.rawY.toInt()
                            }
                            
                            
                            if (isMove(point, e)) {
                                isScrolling = true
                                popupMenu?.dismiss()
                            }
                            
                            
                            isDownAndUp(point, e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in touch listener: ${e.message}")
                }
                return@setOnTouchListener false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in interceptTouch: ${e.message}")
        }
    }

    private fun isMove(point: Point, e: MotionEvent): Boolean {
        return try {
            val max = 40

            val x = point.x
            val y = point.y

            val xU = abs(x - e.rawX)
            val yU = abs(y - e.rawY)
            xU > max || yU > max
        } catch (e: Exception) {
            Log.e(TAG, "Error in isMove: ${e.message}")
            false
        }
    }

    private fun isDownAndUp(point: Point, e: MotionEvent) {
        try {
            val min = 10
            val y = point.y
            val yU = y - e.rawY

            if (abs(yU) > min) {
                try {
                    (requireActivity() as? MainActivity)?.showFloatButton(yU < 0)
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing/hiding float button: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in isDownAndUp: ${e.message}")
        }
    }

    private fun onItemMove(fromPosition: Int, toPosition: Int) {
        try {
            
            val items = mAdapter.getItems()
            if (fromPosition < 0 || toPosition < 0 || 
                fromPosition >= items.size || toPosition >= items.size) {
                Log.w(TAG, "Invalid positions for move: from=$fromPosition, to=$toPosition, size=${items.size}")
                return
            }
            
            if (fromPosition < toPosition) {
                for (i in fromPosition until toPosition) {
                    try {
                        Collections.swap(items, i, i + 1)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error swapping items at position $i: ${e.message}")
                        return
                    }
                }
            } else {
                for (i in fromPosition downTo toPosition + 1) {
                    try {
                        Collections.swap(items, i, i - 1)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error swapping items at position $i: ${e.message}")
                        return
                    }
                }
            }
            
            try {
                mAdapter.notifyItemMoved(fromPosition, toPosition)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying item moved: ${e.message}")
                
                mAdapter.notifyDataSetChanged()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onItemMove: ${e.message}")
        }
    }

    private fun setOnLongClick() {
        try {
            mAdapter.setItemLongClickListener { view, data, _ ->
                try {
                    popupMenu = PopupMenu(requireContext(),view).also {
                        it.inflate(R.menu.app_menu)
                        it.setOnMenuItemClickListener { item ->
                            try {
                                when (item.itemId) {
                                    R.id.app_remove -> {
                                        if (data.isXpModule) {
                                            toast(R.string.uninstall_module_toast)
                                        } else {
                                            unInstallApk(data)
                                        }
                                    }

                                    R.id.app_clear -> {
                                        clearApk(data)
                                    }

                                    R.id.app_backup -> {
                                        startBackupExport(data)
                                    }

                                    R.id.app_restore -> {
                                        startBackupImport(data)
                                    }

                                    R.id.app_stop -> {
                                        stopApk(data)
                                    }

                                    R.id.app_shortcut -> {
                                        ShortcutUtil.createShortcut(requireContext(), userID, data)
                                    }
                                }
                                return@setOnMenuItemClickListener true
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in menu item click: ${e.message}")
                                return@setOnMenuItemClickListener false
                            }
                        }
                        it.show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in long click: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in setOnLongClick: ${e.message}")
        }
    }
    
    private fun initData() {
        try {
            viewBinding.stateView.showLoading()
            viewModel.getInstalledApps(userID)
            viewModel.appsLiveData.observe(viewLifecycleOwner) {
                try {
                    if (it != null) {
                        mAdapter.setItems(it)
                        if (it.isEmpty()) {
                            viewBinding.stateView.showEmpty()
                        } else {
                            viewBinding.stateView.showContent()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error observing apps data: ${e.message}")
                }
            }

            viewModel.resultLiveData.observe(viewLifecycleOwner) {
                try {
                    if (!TextUtils.isEmpty(it)) {
                        hideLoading()
                        requireContext().toast(it)
                        viewModel.getInstalledApps(userID)
                        scanUser()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error observing result data: ${e.message}")
                }
            }

            viewModel.launchLiveData.observe(viewLifecycleOwner) {
                try {
                    it?.run {
                        hideLoading()
                        if (!it) {
                            toast(R.string.start_fail)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error observing launch data: ${e.message}")
                }
            }

            viewModel.updateSortLiveData.observe(viewLifecycleOwner) {
                try {
                    if (this::mAdapter.isInitialized) {
                        viewModel.updateApkOrder(userID, mAdapter.getItems())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error observing sort data: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in initData: ${e.message}")
        }
    }

    override fun onStop() {
        try {
            super.onStop()
            viewModel.resultLiveData.value = null
            viewModel.launchLiveData.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStop: ${e.message}")
        }
    }

    private fun unInstallApk(info: AppInfo) {
        try {
            MaterialDialog(requireContext()).show {
                title(R.string.uninstall_app)
                // Ingatkan soal backup: uninstall menghapus data clone (item 4).
                message(
                        text = getString(R.string.uninstall_app_hint, info.name) +
                                "\n\n" + getString(R.string.backup_uninstall_reminder)
                )
                positiveButton(R.string.done) {
                    try {
                        showLoading()
                        viewModel.unInstall(info.packageName, userID)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error uninstalling app: ${e.message}")
                        hideLoading()
                    }
                }
                negativeButton(R.string.cancel)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing uninstall dialog: ${e.message}")
        }
    }

    
    private fun stopApk(info: AppInfo) {
        try {
            MaterialDialog(requireContext()).show {
                title(R.string.app_stop)
                message(text = getString(R.string.app_stop_hint,info.name))
                positiveButton(R.string.done) {
                    try {
                        BlackBoxCore.get().stopPackage(info.packageName, userID)
                        toast(getString(R.string.is_stop,info.name))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping app: ${e.message}")
                    }
                }
                negativeButton(R.string.cancel)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing stop dialog: ${e.message}")
        }
    }

    
    private fun clearApk(info: AppInfo) {
        try {
            MaterialDialog(requireContext()).show {
                title(R.string.app_clear)
                message(text = getString(R.string.app_clear_hint,info.name))
                positiveButton(R.string.done) {
                    try {
                        showLoading()
                        viewModel.clearApkData(info.packageName, userID)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error clearing app data: ${e.message}")
                        hideLoading()
                    }
                }
                negativeButton(R.string.cancel)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing clear dialog: ${e.message}")
        }
    }

    fun installApk(source: String) {
        try {
            showLoading()
            viewModel.install(source, userID)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK: ${e.message}")
            hideLoading()
        }
    }

    // ------------------------------------------------------------------
    // Backup & restore data clone (item 4)
    // ------------------------------------------------------------------

    private fun startBackupExport(info: AppInfo) {
        try {
            pendingBackupApp = info
            exportBackupLauncher.launch(CloneBackupManager.suggestFileName(info.packageName))
        } catch (e: Exception) {
            Log.e(TAG, "Error starting backup export: ${e.message}")
            pendingBackupApp = null
        }
    }

    private fun startBackupImport(info: AppInfo) {
        try {
            pendingBackupApp = info
            importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
        } catch (e: Exception) {
            Log.e(TAG, "Error starting backup import: ${e.message}")
            pendingBackupApp = null
        }
    }

    private fun runBackupExport(info: AppInfo, uri: Uri) {
        try {
            showLoading()
            toast(R.string.backup_exporting)
            CloneBackupManager.exportBackup(
                    requireContext(),
                    info.packageName,
                    userID,
                    uri,
                    object : CloneBackupManager.Callback {
                        override fun onSuccess(message: String) {
                            hideLoading()
                            toast(R.string.backup_export_success)
                        }

                        override fun onError(message: String) {
                            hideLoading()
                            toast(backupErrorText(message, isExport = true))
                        }
                    }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting backup: ${e.message}")
            hideLoading()
        }
    }

    private fun runBackupImport(info: AppInfo, uri: Uri) {
        try {
            showLoading()
            toast(R.string.backup_importing)
            CloneBackupManager.importBackup(
                    requireContext(),
                    info.packageName,
                    userID,
                    uri,
                    object : CloneBackupManager.Callback {
                        override fun onSuccess(message: String) {
                            hideLoading()
                            toast(getString(R.string.backup_restore_success, info.name))
                        }

                        override fun onError(message: String) {
                            hideLoading()
                            toast(backupErrorText(message, isExport = false))
                        }
                    }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error importing backup: ${e.message}")
            hideLoading()
        }
    }

    /**
     * Terjemahkan kode error dari [CloneBackupManager] ke pesan untuk pengguna.
     * Kode berbentuk "kode" atau "kode: detail" (detail hanya untuk log, dan
     * untuk pesan gagal umum yang menyertakan pesan exception).
     */
    private fun backupErrorText(message: String, isExport: Boolean): String {
        val parts = message.split(":", limit = 2)
        return when (parts[0]) {
            "not_installed" -> getString(R.string.backup_not_installed)
            "no_data" -> getString(R.string.backup_no_data)
            "invalid_file" -> getString(R.string.backup_invalid_file)
            "mismatch" -> getString(R.string.backup_mismatch)
            "open_failed" -> getString(R.string.backup_open_failed)
            "export_failed" -> getString(
                    R.string.backup_export_failed,
                    parts.getOrNull(1)?.trim().orEmpty()
            )
            "import_failed" -> getString(
                    R.string.backup_restore_failed,
                    parts.getOrNull(1)?.trim().orEmpty()
            )
            else -> if (isExport) {
                getString(R.string.backup_export_failed, message)
            } else {
                getString(R.string.backup_restore_failed, message)
            }
        }
    }

    private fun scanUser() {
        try {
            (requireActivity() as? MainActivity)?.scanUser()
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning user: ${e.message}")
        }
    }

    private fun showLoading() {
        try {
            if(requireActivity() is LoadingActivity){
                (requireActivity() as LoadingActivity).showLoading()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing loading: ${e.message}")
        }
    }

    private fun hideLoading() {
        try {
            if(requireActivity() is LoadingActivity){
                (requireActivity() as LoadingActivity).hideLoading()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding loading: ${e.message}")
        }
    }
}
