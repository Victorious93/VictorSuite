package com.termux.victor.dialog

import android.content.Context
import android.os.Environment
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import com.termux.victor.util.LogUtils
import com.termux.victor.util.UUtils
import com.github.iielse.switchbutton.SwitchView
import com.github.iielse.switchbutton.SwitchView.OnStateChangedListener
import com.termux.R
import com.termux.victor.bean.VsEngineData
import com.termux.victor.ftp.new_ftp.Constants
import com.termux.victor.ftp.new_ftp.config.FTP_CONFIG
import com.termux.victor.ftp.new_ftp.services.FtpService
import com.termux.victor.url.FileUrl.mainFilesUrl
import kotlinx.coroutines.*

class FtpWindowsDialog : BaseDialogCentre {

    object FtpWindowsDialogConstant {
        public val TAG = "FtpWindowsDialog"
        public val ROOT_PATH_SDCARD = 10001
        public val ROOT_PATH_VS_HOME = 10002
    }
    private var mSwitchBtn: SwitchView? = null
    private var mPortEd: EditText? = null
    private var mSdcard: TextView? = null
    private var mFtpMsg: TextView? = null
    private var mTitleFtp: TextView? = null
    private var mVsRoot: TextView? = null
    private var mPassword: EditText? = null
    private var mProgressBar: ProgressBar? = null
    private var mPopupFtpWindowsSwitchBtnListener: PopupFtpWindowsSwitchBtnListener? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, themeResId: Int) : super(context, themeResId)


    override fun initViewDialog(mView: View) {
        mSwitchBtn = mView.findViewById(R.id.switch_btn)
        mPortEd = mView.findViewById(R.id.port_ed)
        mProgressBar = mView.findViewById(R.id.pro)
        mSdcard = mView.findViewById(R.id.sdcard)
        mVsRoot = mView.findViewById(R.id.vs_root)
        mFtpMsg = mView.findViewById(R.id.ftp_msg)
        mPassword = mView.findViewById(R.id.password)
        mTitleFtp = mView.findViewById(R.id.title_ftp)
        initViewClick()
    }

    private fun initViewClick() {
        initSwitchIndexConfig()
        writerConfig()
        initTitleText()
        mSdcard?.setOnClickListener {
            switchIndex(FtpWindowsDialogConstant.ROOT_PATH_SDCARD, true)
        }
        mFtpMsg?.setOnClickListener {
           // UUtils.startUrl("https://github.com/ppareit/swiftp")
        }
        mVsRoot?.setOnClickListener {
            switchIndex(FtpWindowsDialogConstant.ROOT_PATH_VS_HOME, true)
        }
        mSwitchBtn?.let {
            it.isOpened = FtpService.isFTPServiceRunning()
            it.setOnStateChangedListener(object :OnStateChangedListener{
                override fun toggleToOn(view: SwitchView?) {
                    switchOn(it)
                }
                override fun toggleToOff(view: SwitchView?) {
                    switchOff(it)
                }

             })
        }
    }



    override fun getContentView(): Int {
      return R.layout.pupu_window_ftp_start
    }

    private fun switchOn(it:SwitchView) {
        /**
         * 初始化配置文件
         */
        initDefUser()
        GlobalScope.launch {
            withContext(Dispatchers.Main) {
                FtpService.stopService()
                it.visibility = View.INVISIBLE
                mProgressBar?.visibility = View.VISIBLE
            }
            withContext(Dispatchers.IO) {
                delay(1000)
            }
            withContext(Dispatchers.Main){
                FtpService.startService(UUtils.getContext())
            }
            withContext(Dispatchers.IO) {
                delay(1500)
            }
            withContext(Dispatchers.Main){
                val stringData =
                    VsEngineData.getStringData(UUtils.getContext(), VsEngineData.FTP_CHROOT)
                LogUtils.d(FtpWindowsDialogConstant.TAG, "initDefUser stringData:$stringData")
                it.visibility = View.VISIBLE
                mProgressBar?.visibility = View.INVISIBLE
                it.isOpened = FtpService.isFTPServiceRunning()
                mPopupFtpWindowsSwitchBtnListener?.switchBtn(true)
                initTitleText()
            }
        }
    }

    private fun switchOff(it:SwitchView) {
        GlobalScope.launch {
            withContext(Dispatchers.Main) {
                FtpService.stopService()
                it.visibility = View.INVISIBLE
                mProgressBar?.visibility = View.VISIBLE
            }
            withContext(Dispatchers.IO) {
                delay(2000)
            }
            withContext(Dispatchers.Main){
                it.visibility = View.VISIBLE
                mProgressBar?.visibility = View.INVISIBLE
                it.isOpened = false
                mPopupFtpWindowsSwitchBtnListener?.switchBtn(false)
                initTitleText()
            }
        }
    }

    private fun writerConfig() {
        mPortEd?.addTextChangedListener(object :TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun afterTextChanged(p0: Editable?) {
                if (TextUtils.isEmpty(p0)) {
                    VsEngineData.putStringData(UUtils.getContext(), VsEngineData.FTP_PORT, VsEngineData.FTP_DEF_PORT)
                    return
                }
                try {
                    val parseInt = Integer.parseInt(p0!!.toString())
                    if (parseInt < VsEngineData.FTP_MIN_PORT) {
                        mPortEd?.setTextColor(UUtils.getColor(R.color.color_CC5A6B))
                    } else if(parseInt > VsEngineData.FTP_MAX_PORT) {
                        UUtils.showMsg(UUtils.getString(R.string.ftp_port_msg))
                        mPortEd?.setText("2121")
                    } else {
                        mPortEd?.setTextColor(UUtils.getColor(R.color.color_ffffff))
                        VsEngineData.putStringData(UUtils.getContext(), VsEngineData.FTP_PORT, mPortEd!!.text.toString())
                    }
                } catch (e:Exception) {
                    UUtils.showMsg(UUtils.getString(R.string.ftp_port_msg))
                    mPortEd?.setText("2121")
                }
            }
        })
        mPassword?.addTextChangedListener(object :TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun afterTextChanged(p0: Editable?) {
                if (TextUtils.isEmpty(p0)) {
                    VsEngineData.putStringData(UUtils.getContext(), VsEngineData.FTP_PASS_WORD, VsEngineData.FTP_DEF_PWD)
                    return
                }
                VsEngineData.putStringData(UUtils.getContext(), VsEngineData.FTP_PASS_WORD, mPassword?.text.toString())
            }
        })
    }

    private fun switchIndex(index: Int, isInit: Boolean) {
        mSdcard?.setBackgroundResource(R.drawable.shape_r_3dp_161823_232635)
        mVsRoot?.setBackgroundResource(R.drawable.shape_r_3dp_161823_232635)
        when (index) {
            FtpWindowsDialogConstant.ROOT_PATH_SDCARD -> {
                if (isInit) {
                    VsEngineData.putStringData(UUtils.getContext(), VsEngineData.FTP_CHROOT, VsEngineData.FTP_SDCARD_ROOT)
                }
              //  FTP_CONFIG.PATH = Environment.getExternalStorageDirectory().absolutePath
                mSdcard?.setBackgroundResource(R.drawable.shape_line_8cff5a)
            }
            FtpWindowsDialogConstant.ROOT_PATH_VS_HOME -> {
                if (isInit) {
                    VsEngineData.putStringData(UUtils.getContext(), VsEngineData.FTP_CHROOT, VsEngineData.FTP_VICTOR_SUITE_FILE)
                }
                mVsRoot?.setBackgroundResource(R.drawable.shape_line_8cff5a)
             //   FTP_CONFIG.PATH = mainFilesUrl
            }
        }
        if (isInit) {
            mSwitchBtn?.let {
                switchOn(it)
            }
        }
    }

    private fun initDefUser() {
        writerDefConfig(VsEngineData.FTP_USER_NAME, VsEngineData.FTP_DEF_USER)
        writerDefConfig(VsEngineData.FTP_PASS_WORD, VsEngineData.FTP_DEF_PWD)
        writerDefConfig(VsEngineData.FTP_PORT, VsEngineData.FTP_DEF_PORT)
        writerDefConfig(VsEngineData.FTP_CHROOT, VsEngineData.FTP_SDCARD_ROOT)
        initSwitchIndexConfig()
    }

    private fun initTitleText() {
        if (FtpService.isFTPServiceRunning()) {
            val port =
                VsEngineData.getStringData(UUtils.getContext(), VsEngineData.FTP_PORT)
            val empty = VsEngineData.isEmpty(UUtils.getContext(), VsEngineData.FTP_PORT)
            if (empty) {
                mTitleFtp?.setText(UUtils.getString(R.string.ftp_start_window) + "\nftp://" + UUtils.getHostIP() + ":2121")
            } else {
                mTitleFtp?.setText(UUtils.getString(R.string.ftp_start_window) + "\nftp://" + UUtils.getHostIP() + ":" + port)
            }
            mTitleFtp?.setTextColor(UUtils.getColor(R.color.color_48baf3))
        } else {
            mTitleFtp?.setText("VictorSuite FTP")
            mTitleFtp?.setTextColor(UUtils.getColor(R.color.color_ffffff))
        }
    }

    private fun initSwitchIndexConfig() {
        if (!VsEngineData.isEmpty(UUtils.getContext(), VsEngineData.FTP_PORT)) {
            val port =
                VsEngineData.getStringData(UUtils.getContext(), VsEngineData.FTP_PORT)
            mPortEd?.setText(port)
            try {
                Constants.PreferenceConsts.PORT_NUMBER_DEFAULT = port.toInt()
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
        if (!VsEngineData.isEmpty(UUtils.getContext(), VsEngineData.FTP_PASS_WORD)) {
            val password =
                VsEngineData.getStringData(UUtils.getContext(), VsEngineData.FTP_PASS_WORD)
            mPassword?.setText(password)
            FTP_CONFIG.PASSWORD = password
        }
        val stringData =
            VsEngineData.getStringData(UUtils.getContext(), VsEngineData.FTP_CHROOT)
        if (stringData.contains("com.termux")) {
            switchIndex(FtpWindowsDialogConstant.ROOT_PATH_VS_HOME, false)
        } else {
            switchIndex(FtpWindowsDialogConstant.ROOT_PATH_SDCARD, false)
        }

    }

    public fun setPopupFtpWindowsSwitchBtnListener(mPopupFtpWindowsSwitchBtnListener: PopupFtpWindowsSwitchBtnListener) {
        this.mPopupFtpWindowsSwitchBtnListener = mPopupFtpWindowsSwitchBtnListener
    }

    public interface PopupFtpWindowsSwitchBtnListener {
        fun switchBtn(switch: Boolean)
    }

    private fun writerDefConfig(key: String, value: String) {
        val empty = VsEngineData.isEmpty(UUtils.getContext(), key)
        if (empty) {
            VsEngineData.putStringData(UUtils.getContext(), key, value)
        }
    }


}


