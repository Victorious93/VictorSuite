package com.termux.victor.back

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.termux.victor.util.LogUtils
import com.termux.victor.util.UUtils
import com.termux.R
import com.termux.app.TermuxActivity
import com.termux.victor.back.adapter.RestoreAdapter
import com.termux.victor.back.bean.DataBean
import com.termux.victor.back.listener.BackupStoreDialogCloseListener
import com.termux.victor.back.listener.CreateConversationListener
import com.termux.victor.back.listener.RestoreFileDataListener
import com.termux.victor.back.listener.RestoreRefreshFileListener
import com.termux.victor.data.CommendShellData
import com.termux.victor.data.RestoreShell
import com.termux.victor.dialog.LoadingDialog
import com.termux.victor.dialog.YesNoDialog
import com.termux.victor.utils.FileIOUtils
import com.termux.victor.utils.QZUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.whileSelect

class RestoreViewModel {
    private val TAG = "RestoreViewModel"
    private var mRestoreList: RecyclerView? = null
    private var mNoData: TextView? = null
    private var mContext: Context? = null
    private var  mBackupStoreDialogCloseListener: BackupStoreDialogCloseListener? = null
    private var  mCreateConversationListener: CreateConversationListener? = null
    public fun initView(
        mLayout: RelativeLayout?,
        mContext: Context,
        mCreateConversationListener: CreateConversationListener?,
        mBackupStoreDialogCloseListener: BackupStoreDialogCloseListener) {
        mRestoreList = mLayout?.findViewById(R.id.restore_list)
        mNoData = mLayout?.findViewById(R.id.not_data)
        this.mContext = mContext
        this.mCreateConversationListener = mCreateConversationListener
        this.mBackupStoreDialogCloseListener = mBackupStoreDialogCloseListener
        initAdapter()
    }

    private fun initAdapter() {
        val xinHaoDataPathFile = FileIOUtils.getXinHaoDataPathFile()
        val listFiles = xinHaoDataPathFile.listFiles()
        if (listFiles != null && listFiles.isNotEmpty()) {
            isShowData(false)
            var mArrayList: ArrayList<DataBean> = ArrayList()
            listFiles.forEach {
                var mDataBean = DataBean()
                mDataBean.mFile = it
                mArrayList.add(mDataBean)
            }
            val restoreAdapter = RestoreAdapter(mArrayList, mContext!!)
            mRestoreList?.let {
                it.layoutManager = LinearLayoutManager(mContext)
                it.adapter = restoreAdapter
            }
            restoreAdapter.setRestoreRefreshFileListener(object : RestoreRefreshFileListener{
                override fun refresh() {
                    val refreshXinHaoDataPathFile = FileIOUtils.getXinHaoDataPathFile()
                    val refreshListFiles = refreshXinHaoDataPathFile.listFiles()
                    if (refreshListFiles != null && refreshListFiles.isNotEmpty()) {
                        isShowData(false)
                        var mRefreshArrayList: ArrayList<DataBean> = ArrayList()
                        refreshListFiles.forEach {
                            var mDataBean = DataBean()
                            mDataBean.mFile = it
                            mRefreshArrayList.add(mDataBean)
                        }
                        restoreAdapter.setList(mRefreshArrayList)
                        restoreAdapter.notifyDataSetChanged()
                    } else {
                        isShowData(true)
                    }
                }

            })
            restoreAdapter.setRestoreFileDataListener(object: RestoreFileDataListener {
                override fun file(mDataBean: DataBean) {
                    val storagePath = FileIOUtils.isStoragePath(mContext!!)
                    if (!storagePath) {
                        UUtils.showMsg(UUtils.getString(R.string.storage_error))
                        return
                    }
                    // VictorSuite: validate format and name BEFORE any container dir is created.
                    val restoreFlag = RestoreShell.restoreFlagFor(mDataBean.mFile!!.name)
                    if (restoreFlag == null) {
                        UUtils.showMsg(UUtils.getString(R.string.unrecognized_compression_format))
                        return
                    }
                    if (!RestoreShell.isSafeFileName(mDataBean.mFile!!.name)) {
                        UUtils.showMsg(UUtils.getString(R.string.backup_file_name_unsafe))
                        return
                    }
                    val mSwitchDialog = YesNoDialog(mContext!!)
                    mSwitchDialog.createEditDialog(UUtils.getString(R.string.system_create_container))
                    mSwitchDialog.show()
                    mSwitchDialog.yesTv.setOnClickListener {
                        val text = mSwitchDialog.inputSystemName.text
                        if (TextUtils.isEmpty(text)) {
                            UUtils.showMsg(UUtils.getString(R.string.system_create_container_empty))
                            return@setOnClickListener
                        }
                        mSwitchDialog.dismiss()
                        val createSystem =
                            FileIOUtils.createSystem(UUtils.getContext(), text.toString())
                        if (createSystem == null || !(createSystem.exists())) {
                            LogUtils.d(TAG, "setRestoreFileDataListener -> file createSystem is fail return")
                            return@setOnClickListener
                        }
                        mBackupStoreDialogCloseListener?.backupStoreDismiss()
                        var mLoadingDialog: LoadingDialog? = null
                        MainScope().launch {
                            withContext(Dispatchers.Main) {
                                mLoadingDialog = LoadingDialog(mContext!!)
                                mLoadingDialog?.msg?.text = UUtils.getString(R.string.vs_loading_please_wait)
                                mLoadingDialog?.show()
                                mLoadingDialog?.setCancelable(false)
                               // mCreateConversationListener?.create()
                            }
                            withContext(Dispatchers.IO) {
                                delay(1000)
                            }
                            withContext(Dispatchers.Main) {
                                sendTextToTerminal(CommendShellData.SHELL_WELCOME_MESSAGE)
                            }
                            withContext(Dispatchers.IO) {
                                delay(1000)
                            }
                            withContext(Dispatchers.Main) {
                                mLoadingDialog?.dismiss()
                                mDataBean.mFile?.let {
                                    LogUtils.d(TAG, "setRestoreFileDataListener -> file file name is: ${it.name}")
                                    val command = try {
                                        CommendShellData.getShellRestore(restoreFlag, it, createSystem)
                                    } catch (e: IllegalArgumentException) {
                                        null
                                    }
                                    if (command != null) {
                                        sendTextToTerminal(command)
                                    } else {
                                        // Do not leave an empty filesN container behind.
                                        createSystem.deleteRecursively()
                                        UUtils.showMsg(UUtils.getString(R.string.backup_file_name_unsafe))
                                        LogUtils.d(TAG, "setRestoreFileDataListener -> refused unsafe restore for: ${it.name}")
                                    }
                                }
                            }

                        }
                    }
                }

            })
        } else {
            isShowData(true)
        }
    }

    private fun sendTextToTerminal(text: String) {
        LogUtils.d(TAG, "sendTextToTerminal text to:$text")
        com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal(text)
    }

    private fun isShowData(isShow: Boolean) {
        if (isShow) {
            mNoData?.visibility = View.VISIBLE
            mRestoreList?.visibility = View.INVISIBLE
        } else {
            mNoData?.visibility = View.INVISIBLE
            mRestoreList?.visibility = View.VISIBLE
        }

    }
}
