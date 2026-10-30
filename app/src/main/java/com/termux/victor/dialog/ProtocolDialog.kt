package com.termux.victor.dialog

import com.termux.victor.util.OtherPrefs
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.TextView
import com.termux.victor.util.LogUtils
import com.termux.victor.util.UUtils
import com.termux.R
import com.termux.app.TermuxService
import com.termux.shared.termux.TermuxConstants
import com.termux.victor.ftp.utils.UserSetManage
import com.termux.victor.utils.ViewBackUtils
import com.termux.victor.view.CustomScrollView
import kotlinx.coroutines.Runnable
import java.lang.RuntimeException

class ProtocolDialog : BaseDialogCentre, View.OnFocusChangeListener {
    public val TAG = "ProtocolDialog"
    public var edit_text:TextView? = null
    public var ok:TextView? = null
    public var cancel:TextView? = null
    public var custom_scroll: CustomScrollView? = null

    public var isYD = false
    constructor(context: Context) : super(context)
    constructor(context: Context, themeResId: Int) : super(context, themeResId)

    override fun initViewDialog(mView: View?) {
        edit_text = mView?.findViewById(R.id.edit_text)
        custom_scroll = mView?.findViewById(R.id.custom_scroll)
        ok = mView?.findViewById(R.id.ok)
        cancel = mView?.findViewById(R.id.cancel)
        edit_text?.text = UUtils.getString(R.string.vs_legal_notices_victorsuite_is_a_non_profi)
        cancel?.setOnClickListener {
            val intent = Intent(mContext, TermuxService::class.java)
            intent.action = TermuxConstants.TERMUX_APP.TERMUX_SERVICE.ACTION_STOP_SERVICE
            mContext.startService(intent)
            System.exit(1)
            throw RuntimeException("Agreement declined, exiting...")
        }
        ok?.setOnClickListener {
            if (isYD){
                usageHabits()
                OtherPrefs.saveStringOther("xieyi","true")
                dismiss()
            }else{
                UUtils.showMsg(UUtils.getString(R.string.vs_please_read_the_license_agreement_and_th))
            }
        }
        custom_scroll?.setOnScrollChangeListener(object : CustomScrollView.OnScrollChangeListener {
            override fun onScrollToStart() {

            }

            override fun onScrollToEnd() {
                isYD = true
            }

        })
        focusManagement()
    }

    // 使用习惯
    private fun usageHabits() {
        var switchDialog = SwitchDialog(mContext)
        switchDialog.title?.text = UUtils.getString(R.string.vs_usage_habits_title)
        switchDialog.msg?.text = UUtils.getString(R.string.vs_usage_habits_content)
        switchDialog.ok?.text = "Termux"
        switchDialog.ok?.setOnClickListener {
            val vsUserBean = UserSetManage.get().getVsUserBean()
            vsUserBean.isToolShow = true
            vsUserBean.isResetVolume = true
            UserSetManage.get().setVsUserBean(vsUserBean)
            UUtils.showMsg(UUtils.getString(R.string.vs_usage_habits_termux))
            switchDialog.dismiss()
        }
        switchDialog.cancel?.text = "VictorSuite"
        switchDialog.cancel?.setOnClickListener {
            UUtils.showMsg(UUtils.getString(R.string.vs_usage_habits_victorsuite))
            switchDialog.dismiss()
        }
        switchDialog.other?.visibility = View.GONE
        switchDialog.show()
    }
    private fun focusManagement() {
        custom_scroll?.onFocusChangeListener = this
        ok?.onFocusChangeListener = this
        cancel?.onFocusChangeListener = this
    }

    override fun getContentView(): Int {

        return R.layout.dialog_protocol
    }

    override fun onFocusChange(p0: View?, p1: Boolean) {
        if (p0 == null) {
            LogUtils.d(TAG, "focus changed return view is empty!")
            return
        }
        ViewBackUtils.setBackLine161823_2e(custom_scroll!!)
        ok?.setTextColor(UUtils.getColor(R.color.color_ffffff))
        cancel?.setTextColor(UUtils.getColor(R.color.color_ffffff))
        when (p0.id) {
            R.id.custom_scroll -> {
                ViewBackUtils.setBackLine161823_fd(custom_scroll!!)
            }
            R.id.ok -> {
                ok?.setTextColor(UUtils.getColor(com.termux.R.color.color_FD9F3E))
            }
            R.id.cancel -> {
                cancel?.setTextColor(UUtils.getColor(com.termux.R.color.color_FD9F3E))
            }
        }
    }
}
