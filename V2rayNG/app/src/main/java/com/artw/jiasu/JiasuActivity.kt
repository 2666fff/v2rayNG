package com.artw.jiasu

import android.content.res.ColorStateList
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityJiasuBinding
import com.v2ray.ang.extension.toast
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.v2ray.ang.service.V2RayServiceManager
import com.v2ray.ang.ui.JiasuRecyclerAdapter
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.drakeet.support.toast.ToastCompat
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

class JiasuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJiasuBinding
    val mainViewModel: MainViewModel by viewModels()
    private var mItemTouchHelper: ItemTouchHelper? = null

    private val adapter by lazy { JiasuRecyclerAdapter(this) }
    private val mainStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_MAIN,
            MMKV.MULTI_PROCESS_MODE
        )
    }

    private val serverAdd by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_MAIN,
            MMKV.SINGLE_PROCESS_MODE
        )
    }
    private val settingsStorage by lazy {
        MMKV.mmkvWithID(
            MmkvManager.ID_SETTING,
            MMKV.MULTI_PROCESS_MODE
        )
    }
    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                startV2Ray()
            }
        }

    fun startV2Ray() {
        if (mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER).isNullOrEmpty()) {
            return
        }
        showCircle()
//        toast(R.string.toast_services_start)
        V2RayServiceManager.startV2Ray(this)
        hideCircle()
    }


    fun hideCircle() {
        try {
            Observable.timer(300, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    if (binding.fabProgressCircle.isShown) {
                        binding.fabProgressCircle.hide()
                    }
                }
        } catch (e: Exception) {
        }
    }


    fun showCircle() {
        binding.fabProgressCircle.show()
    }

    fun importBatchConfig(server: String?, subid: String = "") {
        var count = AngConfigManager.importBatchConfig(server, subid)
        if (count <= 0) {
            count = AngConfigManager.importBatchConfig(Utils.decode(server!!), subid)
        }
        if (count > 0) {
            toast(R.string.toast_success)
            mainViewModel.reloadServerList()
        } else {
            toast(R.string.toast_failure)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJiasuBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        val callback = SimpleItemTouchHelperCallback(adapter)
        mItemTouchHelper = ItemTouchHelper(callback)
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)

        binding.fab.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                Utils.stopVService(this)
            } else if (settingsStorage?.decodeString(AppConfig.PREF_MODE) ?: "VPN" == "VPN") {
                val intent = VpnService.prepare(this)
                if (intent == null) {
                    startV2Ray()
                } else {
                    requestVpnPermission.launch(intent)
                }
            } else {
                startV2Ray()
            }
        }

        setupViewModelObserver()
        migrateLegacy()

        if (!serverAdd.getBoolean("add",false)) {
            importBatchConfig("vmess://ewoidiI6ICIyIiwKInBzIjogIuWFjei0uemmmea4rzUwME0iLAoiYWRkIjogIjkxLjI0NS4yNTUuODYiLAoicG9ydCI6ICI0NjI1MyIsCiJpZCI6ICIyNjk0NDM1ZC00YjkzLTQxYTktYjZjYi1mM2U2MWJmMzI0ZDgiLAoiYWlkIjogIjAiLAoibmV0IjogInRjcCIsCiJ0eXBlIjogIm5vbmUiLAoiaG9zdCI6ICIiLAoicGF0aCI6ICIiLAoidGxzIjogIiIKfQ==")
            serverAdd.putBoolean("add",true)
        }
    }

    private fun migrateLegacy() {
        GlobalScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.migrateLegacyConfig(this@JiasuActivity)
            if (result != null) {
                launch(Dispatchers.Main) {
                    if (result) {
                        toast(getString(R.string.migration_success))
                        mainViewModel.reloadServerList()
                    } else {
                        toast(getString(R.string.migration_fail))
                    }
                }
            }
        }
    }

    private fun setupViewModelObserver() {
        mainViewModel.updateListAction.observe(this) {
            val index = it ?: return@observe
            if (index >= 0) {
                adapter.notifyItemChanged(index)
            } else {
                adapter.notifyDataSetChanged()
            }
        }
//        mainViewModel.updateTestResultAction.observe(this) { binding.tvTestState.text = it }
        mainViewModel.isRunning.observe(this) {
            val isRunning = it ?: return@observe
            adapter.isRunning = isRunning
            if (isRunning) {
                binding.fab.setImageResource(R.drawable.ic_baseline_toggle_on_24)
                binding.fab.backgroundTintList = ColorStateList.valueOf(applicationContext.resources.getColor(R.color.colorPrimary));
//                binding.tvTestState.text = getString(R.string.connection_connected)
//                binding.layoutTest.isFocusable = true
            } else {
                binding.fab.setImageResource(R.drawable.ic_baseline_toggle_off_24)
                binding.fab.backgroundTintList = ColorStateList.valueOf(applicationContext.resources.getColor(R.color.white));
//                binding.tvTestState.text = getString(R.string.connection_not_connected)
//                binding.layoutTest.isFocusable = false
            }
            hideCircle()
        }
        mainViewModel.startListenBroadcast()
    }
}