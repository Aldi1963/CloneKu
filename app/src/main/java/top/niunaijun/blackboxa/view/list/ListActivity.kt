package top.niunaijun.blackboxa.view.list

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import cbfg.rvadapter.RVAdapter
import com.afollestad.materialdialogs.MaterialDialog
import com.ferfalk.simplesearchview.SimpleSearchView
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.InstalledAppBean
import top.niunaijun.blackboxa.databinding.ActivityListBinding
import top.niunaijun.blackboxa.util.CloneRisk
import top.niunaijun.blackboxa.util.InjectionUtil
import top.niunaijun.blackboxa.util.inflate
import top.niunaijun.blackboxa.view.base.BaseActivity

class ListActivity : BaseActivity() {

    private val viewBinding: ActivityListBinding by inflate()

    private lateinit var mAdapter: RVAdapter<InstalledAppBean>

    private lateinit var viewModel: ListViewModel

    private var appList: List<InstalledAppBean> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        initToolbar(viewBinding.toolbarLayout.toolbar, R.string.installed_app, true)

        mAdapter =
                RVAdapter<InstalledAppBean>(this, ListAdapter())
                        .bind(viewBinding.recyclerView)
                        .setItemClickListener { _, item, _ -> onAppChosen(item) }

        viewBinding.recyclerView.layoutManager = LinearLayoutManager(this)

        initSearchView()
        initViewModel()
    }

    private fun initSearchView() {
        viewBinding.searchView.setOnQueryTextListener(
                object : SimpleSearchView.OnQueryTextListener {
                    override fun onQueryTextChange(newText: String): Boolean {
                        filterApp(newText)
                        return true
                    }

                    override fun onQueryTextCleared(): Boolean {
                        return true
                    }

                    override fun onQueryTextSubmit(query: String): Boolean {
                        return true
                    }
                }
        )
    }

    private fun initViewModel() {
        viewModel =
                ViewModelProvider(this, InjectionUtil.getListFactory())
                        .get(ListViewModel::class.java)
        val userID = intent.getIntExtra("userID", 0)
        viewModel.getInstallAppList(userID)
        viewBinding.toolbarLayout.toolbar.setTitle(R.string.installed_app)

        viewModel.loadingLiveData.observe(this) {
            if (it) {
                viewBinding.stateView.showLoading()
            } else {
                viewBinding.stateView.showContent()
            }
        }

        viewModel.appsLiveData.observe(this) {
            if (it != null) {
                this.appList = it
                viewBinding.searchView.setQuery("", false)
                filterApp("")
                if (it.isNotEmpty()) {
                    viewBinding.stateView.showContent()
                    viewModel.previewInstalledList()
                } else {
                    viewBinding.stateView.showEmpty()
                }
            }
        }
    }

    private fun filterApp(newText: String) {
        val newList =
                this.appList.filter {
                    it.name.contains(newText, true) or it.packageName.contains(newText, true)
                }
        mAdapter.setItems(newList)
    }

    private val openDocumentedResult =
            registerForActivityResult(ActivityResultContracts.GetContent()) {
                it?.run { finishWithResult(it.toString()) }
            }

    /**
     * Dipanggil saat pengguna memilih aplikasi untuk di-clone.
     * Bila aplikasi membawa sinyal risiko (item 3: banking/pembayaran,
     * item 7: WhatsApp/pesan), tampilkan dulu dialog peringatan dan
     * lanjutkan clone hanya bila pengguna menekan "Tetap clone".
     */
    private fun onAppChosen(item: InstalledAppBean) {
        if (item.risk == CloneRisk.Risk.NONE) {
            finishWithResult(item.packageName)
            return
        }

        val titleRes = when (item.risk) {
            CloneRisk.Risk.PAYMENT -> R.string.clone_risk_payment_title
            CloneRisk.Risk.MESSAGING ->
                if (CloneRisk.isWhatsApp(item.packageName)) {
                    R.string.clone_risk_whatsapp_title
                } else {
                    R.string.clone_risk_messaging_title
                }
            CloneRisk.Risk.BOTH -> R.string.clone_risk_both_title
            CloneRisk.Risk.NONE -> return
        }

        val parts = mutableListOf<String>()
        if (item.risk == CloneRisk.Risk.PAYMENT || item.risk == CloneRisk.Risk.BOTH) {
            parts.add(getString(R.string.clone_risk_payment_message, item.name))
        }
        if (item.risk == CloneRisk.Risk.MESSAGING || item.risk == CloneRisk.Risk.BOTH) {
            parts.add(
                    if (CloneRisk.isWhatsApp(item.packageName)) {
                        getString(R.string.clone_risk_whatsapp_message, item.name)
                    } else {
                        getString(R.string.clone_risk_messaging_message, item.name)
                    }
            )
        }

        MaterialDialog(this).show {
            title(titleRes)
            message(text = parts.joinToString("\n\n"))
            positiveButton(R.string.clone_risk_continue) {
                finishWithResult(item.packageName)
            }
            negativeButton(R.string.cancel)
        }
    }

    private fun finishWithResult(source: String) {
        intent.putExtra("source", source)
        setResult(Activity.RESULT_OK, intent)
        val imm: InputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        window.peekDecorView()?.run { imm.hideSoftInputFromWindow(windowToken, 0) }
        finish()
    }

    override fun onBackPressed() {
        if (viewBinding.searchView.isSearchOpen) {
            viewBinding.searchView.closeSearch()
        } else {
            super.onBackPressed()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.list_choose) {
            openDocumentedResult.launch("application/vnd.android.package-archive")
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_list, menu)
        val item = menu!!.findItem(R.id.list_search)
        viewBinding.searchView.setMenuItem(item)

        return true
    }

    override fun onStop() {
        super.onStop()
        viewModel.loadingLiveData.postValue(true)
        viewModel.loadingLiveData.removeObservers(this)
        viewModel.appsLiveData.postValue(null)
        viewModel.appsLiveData.removeObservers(this)
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, ListActivity::class.java)
            context.startActivity(intent)
        }
    }
}
