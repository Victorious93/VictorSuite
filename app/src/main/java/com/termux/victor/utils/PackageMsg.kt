package com.termux.victor.utils

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Message
import android.view.View
import android.view.View.OnClickListener
import com.termux.victor.util.LogUtils
import com.termux.victor.util.UUtils
import com.termux.victor.bean.MessageBean
import com.termux.victor.bean.PackageBean
import com.termux.victor.dialog.SwitchDialog

object PackageMsg {
    final val TAG = "PackageMsg"
    final val VICTOR_SUITE_TERMUX_API = "com.termux.api"
    final val VICTOR_SUITE_TERMUX_BOOT = "com.termux.boot"
    final val VICTOR_SUITE_TERMUX_STYLING = "com.termux.styling"
    final val VICTOR_SUITE_TERMUX_TASKER = "com.termux.tasker"
    final val VICTOR_SUITE_TERMUX_X11 = "com.termux.x11"
    final val VICTOR_SUITE_TERMUX_FILE_MSG = "me.zhanghai.android.files"
    final val VICTOR_SUITE_TERMUX_WEB_START = "org.join.web.serv"
    final val VICTOR_SUITE_TERMUX_FLOAT = "com.termux.window"
    final val VICTOR_SUITE_EG = "com.xinhao.zerocoremanage"
    final val VICTOR_SUITE_VNC = "com.iiordanov.bVNC"
    final val REQUEST_CODE = 3000
    private var index: Int = 0

    private val mHandlerIns1: Handler = object : Handler(){
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.obj == null) {
                LogUtils.d(TAG, "handleMessage: activity is null, return.")
                return
            }
            var mMessageBean: MessageBean = msg.obj as MessageBean
            if (index == mMessageBean.mList.size) {
                mMessageBean.mList = null
                mMessageBean.mActivity = null
                LogUtils.d(TAG, "handleMessage: index => size, return.")
                return
            }
            val packageName = mMessageBean.mList[index].packageName
            val dialog = getDialog(mMessageBean.mActivity)
            unInstallApk(dialog, mMessageBean.mActivity,
                mMessageBean.mList[index].packageName,
                mMessageBean.mList[index].showName
            ) {
                LogUtils.d(TAG, "unInstallApk UninstallApk: $packageName")
                val packageURI: Uri = Uri.parse("package:$packageName")
                val uninstallIntent = Intent(Intent.ACTION_DELETE, packageURI)
                mMessageBean.mActivity.startActivityForResult(uninstallIntent, REQUEST_CODE)
                index++
                var mMessage = Message()
                mMessage.obj = mMessageBean
                mHandlerIns2.sendMessage(mMessage)
                dialog.dismiss()
            }
        }
    }

    private val mHandlerIns2: Handler = object : Handler(){
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.obj == null) {
                LogUtils.d(TAG, "handleMessage: activity is null, return.")
                return
            }
            var mMessageBean: MessageBean = msg.obj as MessageBean
            if (index == mMessageBean.mList.size) {
                LogUtils.d(TAG, "handleMessage: index => size, return.")
                return
            }
            val packageName = mMessageBean.mList[index].packageName
            val dialog = getDialog(mMessageBean.mActivity)
            unInstallApk(dialog, mMessageBean.mActivity,
                mMessageBean.mList[index].packageName,
                mMessageBean.mList[index].showName
            ) {
                LogUtils.d(TAG, "unInstallApk UninstallApk: $packageName")
                val packageURI: Uri = Uri.parse("package:$packageName")
                val uninstallIntent = Intent(Intent.ACTION_DELETE, packageURI)
                mMessageBean.mActivity.startActivityForResult(uninstallIntent, REQUEST_CODE)
                index++
                var mMessage = Message()
                mMessage.obj = mMessageBean
                mHandlerIns1.sendMessage(mMessage)
                dialog.dismiss()
            }
        }
    }


    public fun unInstallALLApk(activity: Activity) {
        val arrayList = ArrayList<PackageBean>()
        val packageAPI = PackageBean()
        packageAPI.packageName = VICTOR_SUITE_TERMUX_API
        packageAPI.showName = "API(1/10)"
        arrayList.add(packageAPI)

        val packageBoot = PackageBean()
        packageBoot.packageName = VICTOR_SUITE_TERMUX_BOOT
        packageBoot.showName = "BOOT(2/10)"
        arrayList.add(packageBoot)

        val packageStyling = PackageBean()
        packageStyling.packageName = VICTOR_SUITE_TERMUX_STYLING
        packageStyling.showName = "STYLING(3/10)"
        arrayList.add(packageStyling)

        val packageTasker = PackageBean()
        packageTasker.packageName = VICTOR_SUITE_TERMUX_TASKER
        packageTasker.showName = "TASKER(4/10)"
        arrayList.add(packageTasker)

        val packageX11 = PackageBean()
        packageX11.packageName = VICTOR_SUITE_TERMUX_X11
        packageX11.showName = "X11(5/10)"
        arrayList.add(packageX11)

        val packageFileMsg = PackageBean()
        packageFileMsg.packageName = VICTOR_SUITE_TERMUX_FILE_MSG
        packageFileMsg.showName = "FILE_MSG(6/10)"
        arrayList.add(packageFileMsg)

        val packageWebStart = PackageBean()
        packageWebStart.packageName = VICTOR_SUITE_TERMUX_WEB_START
        packageWebStart.showName = "WEB_START(7/10)"
        arrayList.add(packageWebStart)

        val packageFloat = PackageBean()
        packageFloat.packageName = VICTOR_SUITE_TERMUX_FLOAT
        packageFloat.showName = "FLOAT(8/10)"
        arrayList.add(packageFloat)

        val packageEg = PackageBean()
        packageEg.packageName = VICTOR_SUITE_EG
        packageEg.showName = "EG(9/10)"
        arrayList.add(packageEg)

        val packageVnc = PackageBean()
        packageVnc.packageName = VICTOR_SUITE_VNC
        packageVnc.showName = "VNC(10/10)"
        arrayList.add(packageVnc)

        var mMessageBean = MessageBean()
        mMessageBean.mActivity = activity
        mMessageBean.mList = arrayList
        unInstallHandler(activity, mMessageBean)

    }

    public fun unInstallHandler(mActivity: Activity, mMessageBean:  MessageBean) {
        index = 0
        var mMessage = Message()
        mMessage.obj = mMessageBean
        mHandlerIns1.sendMessage(mMessage)
    }

    private fun getDialog(mActivity: Activity):  SwitchDialog{
        return SwitchDialog(mActivity)
    }

    public fun unInstallApk(mSwitchDialog: SwitchDialog,activity: Activity, packageName: String, name: String, mOnClickListener: OnClickListener): SwitchDialog {
        mSwitchDialog.show()
        mSwitchDialog.createSwitchDialog("unInstall $name?")
        mSwitchDialog.ok?.setOnClickListener(mOnClickListener)
        return mSwitchDialog
    }


}
