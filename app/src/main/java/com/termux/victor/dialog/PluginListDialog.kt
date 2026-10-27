package com.termux.victor.dialog

import android.app.AlertDialog
import android.content.Context
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.ub.util.custom.dialog.BaseDialogDown
import com.blockchain.ub.utils.httputils.BaseHttpUtils
import com.blockchain.ub.utils.httputils.HttpResponseListenerBase
import com.example.xh_lib.utils.UUtils
import com.github.iielse.switchbutton.SwitchView
import com.google.gson.Gson
import com.lzy.okgo.model.Response
import com.termux.R
import com.termux.victor.config.VsLinks
import com.termux.victor.plugin.PluginManifest
import com.termux.victor.plugin.PluginProcessLauncher
import com.termux.victor.plugin.PluginRecord
import com.termux.victor.plugin.PluginRegistry
import com.termux.victor.plugin.PluginState
import com.termux.victor.url.FileUrl
import java.io.File

/**
 * Lists registered plugins and lets the user acknowledge authorized use, enable/disable,
 * configure, and launch them. Fetches remote/plugins/main.json to pick up new manifest
 * entries, but always shows whatever is already registered locally even if that fetch
 * fails, since enable/launch state lives in [PluginRegistry] independent of the network.
 */
class PluginListDialog : BaseDialogDown {
    private var recycler_view: RecyclerView? = null
    private var loading_data: View? = null
    private var show_data: RelativeLayout? = null
    private var service_name: TextView? = null
    private var plugin_empty: TextView? = null
    private var mUrl: String = VsLinks.FEED_PLUGINS
    private val LOG_TAG = "Termux--Apk:PluginListDialog"

    constructor(context: Context) : super(context)
    constructor(context: Context, themeResId: Int) : super(context, themeResId)

    override fun initViewDialog(mView: View) {
        recycler_view = mView.findViewById(R.id.recycler_view)
        loading_data = mView.findViewById(R.id.loading_data)
        show_data = mView.findViewById(R.id.show_data)
        service_name = mView.findViewById(R.id.service_name)
        plugin_empty = mView.findViewById(R.id.plugin_empty)
        service_name?.text = UUtils.getString(R.string.plugin_manager_title)

        recycler_view?.layoutManager = LinearLayoutManager(this@PluginListDialog.mContext)
        renderList()
        show_data?.visibility = View.VISIBLE
        loading_data?.visibility = View.GONE

        fetchManifest()
    }

    override fun getContentView(): Int {
        return R.layout.dialog_plugin_list
    }

    private fun renderList() {
        val records = PluginRegistry.list(mContext)
        plugin_empty?.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        recycler_view?.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE
        recycler_view?.adapter = PluginListAdapter(records) { renderList() }
    }

    private fun fetchManifest() {
        BaseHttpUtils().getUrl(mUrl, object : HttpResponseListenerBase {
            override fun onSuccessful(msg: Message, mWhat: Int) {
                try {
                    val manifest = Gson().fromJson(msg.obj as String, PluginManifest::class.java)
                    manifest.data.forEach { entry -> PluginRegistry.register(mContext, entry) }
                    UUtils.runOnUIThread { renderList() }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Keep showing whatever was already registered locally.
                }
            }

            override fun onFailure(response: Response<String>?, msg: String, mWhat: Int) {
                // Manifest fetch is best-effort; already-registered plugins still show.
            }
        }, HashMap(), 559)
    }

    inner class PluginListAdapter(
        private val records: List<PluginRecord>,
        private val onChanged: () -> Unit
    ) : RecyclerView.Adapter<PluginViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PluginViewHolder {
            return PluginViewHolder(UUtils.getViewLayViewGroup(R.layout.item_plugin, parent))
        }

        override fun onBindViewHolder(holder: PluginViewHolder, position: Int) {
            val record = records[position]
            val entry = record.manifestEntry
            holder.name?.text = entry.name
            holder.versionCategory?.text = "${entry.version} · ${entry.category}"
            holder.note?.text = entry.description
            holder.stateLabel?.text = if (record.state == PluginState.ENABLED)
                UUtils.getString(R.string.plugin_state_enabled)
            else
                UUtils.getString(R.string.plugin_state_disabled)

            holder.switchBtn?.setOnStateChangedListener(null)
            holder.switchBtn?.isOpened = record.state == PluginState.ENABLED
            holder.switchBtn?.setOnStateChangedListener(object : SwitchView.OnStateChangedListener {
                override fun toggleToOn(view: SwitchView?) {
                    enablePlugin(record)
                }

                override fun toggleToOff(view: SwitchView?) {
                    PluginRegistry.disable(mContext, record.id)
                    onChanged()
                }
            })

            holder.configureBtn?.setOnClickListener {
                PluginConfigDialog(mContext, record).show()
            }

            holder.launchBtn?.setOnClickListener {
                launchPlugin(record)
            }
        }

        override fun getItemCount(): Int = records.size

        private fun enablePlugin(record: PluginRecord) {
            if (record.acknowledgedAuthorizedUseAt != null) {
                doEnable(record)
                return
            }
            AlertDialog.Builder(mContext)
                .setTitle(UUtils.getString(R.string.plugin_authorized_use_title))
                .setMessage(UUtils.getString(R.string.plugin_authorized_use_message).format(record.manifestEntry.name))
                .setPositiveButton(UUtils.getString(R.string.plugin_authorized_use_confirm)) { _, _ ->
                    PluginRegistry.acknowledgeAuthorizedUse(mContext, record.id)
                    doEnable(record)
                }
                .setNegativeButton(UUtils.getString(R.string.取消)) { _, _ ->
                    onChanged()
                }
                .setCancelable(false)
                .show()
        }

        private fun doEnable(record: PluginRecord) {
            val result = PluginRegistry.enable(mContext, record.id)
            if (result.isFailure) {
                UUtils.showMsg(
                    UUtils.getString(R.string.plugin_enable_failed_msg).format(result.exceptionOrNull()?.message)
                )
            }
            onChanged()
        }

        private fun launchPlugin(record: PluginRecord) {
            val installDir = File(FileUrl.victorSuitePlugins, record.id)
            try {
                val command = PluginProcessLauncher.resolveCommand(mContext, installDir, record.id)
                com.termux.victor.utils.SingletonCommunicationUtils.getInstance()
                    .getmSingletonCommunicationListener().sendTextToTerminal("$command\n")
                UUtils.showMsg(UUtils.getString(R.string.plugin_launch_sent_msg))
                dismiss()
            } catch (e: PluginProcessLauncher.PluginNotEnabledException) {
                UUtils.showMsg(UUtils.getString(R.string.plugin_not_enabled_msg))
            } catch (e: PluginProcessLauncher.EntryPointMissingException) {
                UUtils.showMsg(UUtils.getString(R.string.plugin_entry_point_missing_msg).format(installDir.absolutePath))
            } catch (e: Exception) {
                e.printStackTrace()
                UUtils.showMsg(e.message ?: "")
            }
        }
    }

    class PluginViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var name: TextView? = itemView.findViewById(R.id.name)
        var versionCategory: TextView? = itemView.findViewById(R.id.version_category)
        var note: TextView? = itemView.findViewById(R.id.note)
        var stateLabel: TextView? = itemView.findViewById(R.id.state_label)
        var switchBtn: SwitchView? = itemView.findViewById(R.id.switch_btn)
        var configureBtn: TextView? = itemView.findViewById(R.id.configure_btn)
        var launchBtn: TextView? = itemView.findViewById(R.id.launch_btn)
    }
}
