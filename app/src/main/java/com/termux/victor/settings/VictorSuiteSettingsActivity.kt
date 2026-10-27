package com.termux.victor.settings

import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.example.xh_lib.utils.UUtils
import com.termux.api.TermuxApiEmbed
import com.termux.R
import com.termux.victor.victorfiles.VictorFilesPanel
import com.termux.victor.theme.FleetThemeBinding // VictorSuite add: fleet theme
import com.termux.victor.remotecontrol.android.RemoteControlSettings // VictorSuite add
import com.termux.victor.ftp.utils.UserSetManage
import com.termux.victor.url.FileUrl
import com.termux.victor.utils.FileHttpUtils.Companion.get
import com.topjohnwu.superuser.Shell
import com.zp.z_file.util.LogUtils
import java.io.File

class VictorSuiteSettingsActivity : AppCompatActivity() {

    private val ztDownloadServicesSwitch by lazy {findViewById<SwitchCompat>(R.id.zt_download_services_switch)}
    private val ztDownloadServicesLl by lazy {findViewById<LinearLayout>(R.id.zt_download_services_ll)}

    private val inputMethodTriggerCloseSwitch by lazy {findViewById<SwitchCompat>(R.id.input_method_trigger_close_switch)}
    private val inputMethodTriggerCloseLl by lazy {findViewById<LinearLayout>(R.id.input_method_trigger_close_ll)}

    private val styleTriggerOffSwitch by lazy {findViewById<SwitchCompat>(R.id.style_trigger_off_switch)}
    private val styleTriggerOffLl by lazy {findViewById<LinearLayout>(R.id.style_trigger_off_ll)}

    private val isToolShowSwitch by lazy {findViewById<SwitchCompat>(R.id.is_tool_show_switch)}
    private val isToolShowLl by lazy {findViewById<LinearLayout>(R.id.is_tool_show_ll)}

    private val forceUseNumpadSwitch by lazy {findViewById<SwitchCompat>(R.id.force_use_numpad_switch)}
    private val forceUseNumpadLl by lazy {findViewById<LinearLayout>(R.id.force_use_numpad_ll)}

    private val logOutputSwitch by lazy {findViewById<SwitchCompat>(R.id.log_output_switch)}
    private val logOutputLl by lazy {findViewById<LinearLayout>(R.id.log_output_ll)}

    private val shellTermuxSwitch by lazy {findViewById<SwitchCompat>(R.id.shell_termux_switch)}
    private val shellTermuxLl by lazy {findViewById<LinearLayout>(R.id.shell_termux_ll)}

    private val volumeFunctionSwitch by lazy {findViewById<SwitchCompat>(R.id.volume_function_switch)}
    private val volumeFunctionLl by lazy {findViewById<LinearLayout>(R.id.volume_function_ll)}

    private val foldMenuCloseSwitch by lazy {findViewById<SwitchCompat>(R.id.fold_menu_close_switch)}
    private val foldMenuCloseLl by lazy {findViewById<LinearLayout>(R.id.fold_menu_close_ll)}

    private val mainMenuConfigCloseSwitch by lazy {findViewById<SwitchCompat>(R.id.main_menu_config_close_switch)}
    private val mainMenuConfigCloseLl by lazy {findViewById<LinearLayout>(R.id.main_menu_config_close_ll)}

    private val termuxApiEmbedSwitch by lazy {findViewById<SwitchCompat>(R.id.termux_api_embed_switch)}
    private val termuxApiEmbedLl by lazy {findViewById<LinearLayout>(R.id.termux_api_embed_ll)}

    private val victorFilesPanelSwitch by lazy {findViewById<SwitchCompat>(R.id.victorfiles_panel_switch)}
    private val victorFilesPanelLl by lazy {findViewById<LinearLayout>(R.id.victorfiles_panel_ll)}
    // VictorSuite add {@ fleet remote-control envelope v1
    private val remoteControlSwitch by lazy {findViewById<SwitchCompat>(R.id.remote_control_switch)}
    private val remoteControlLl by lazy {findViewById<LinearLayout>(R.id.remote_control_ll)}
    private val remoteControlPairingsLl by lazy {findViewById<LinearLayout>(R.id.remote_control_pairings_ll)}
    // @}

    // VictorSuite add: fleet theme for this settings screen's chrome (View/XML only)
    private val fleetTheme by lazy { FleetThemeBinding(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_victor_suite_settings)
        initView()
        initStatus()
    }

    override fun onStart() {
        super.onStart()
        fleetTheme.onStart()
    }

    override fun onStop() {
        fleetTheme.onStop()
        super.onStop()
    }

    private fun initView() {
        setSwitchStatus(ztDownloadServicesSwitch, ztDownloadServicesLl)
        setSwitchStatus(inputMethodTriggerCloseSwitch, inputMethodTriggerCloseLl)
        setSwitchStatus(styleTriggerOffSwitch, styleTriggerOffLl)
        setSwitchStatus(isToolShowSwitch, isToolShowLl)
        setSwitchStatus(forceUseNumpadSwitch, forceUseNumpadLl)
        setSwitchStatus(logOutputSwitch, logOutputLl)
        setSwitchStatus(shellTermuxSwitch, shellTermuxLl)
        setSwitchStatus(volumeFunctionSwitch, volumeFunctionLl)
        setSwitchStatus(foldMenuCloseSwitch, foldMenuCloseLl)
        setSwitchStatus(mainMenuConfigCloseSwitch, mainMenuConfigCloseLl)
        setSwitchStatus(termuxApiEmbedSwitch, termuxApiEmbedLl)
        setSwitchStatus(victorFilesPanelSwitch, victorFilesPanelLl)
        setSwitchStatus(remoteControlSwitch, remoteControlLl) // VictorSuite add
        remoteControlPairingsLl.setOnClickListener { showPairingsDialog() } // VictorSuite add
    }

    private fun initStatus() {
        val ztUserBean = UserSetManage.get().getZTUserBean()
        ztDownloadServicesSwitch.isChecked = ztUserBean.isOpenDownloadFileServices
        inputMethodTriggerCloseSwitch.isChecked = ztUserBean.isInputMethodTriggerClose
        styleTriggerOffSwitch.isChecked = ztUserBean.isStyleTriggerOff
        isToolShowSwitch.isChecked = ztUserBean.isToolShow
        forceUseNumpadSwitch.isChecked = ztUserBean.isForceUseNumpad
        logOutputSwitch.isChecked = ztUserBean.isOutputLOG
        shellTermuxSwitch.isChecked = isShellTermux()
        volumeFunctionSwitch.isChecked = ztUserBean.isResetVolume
        foldMenuCloseSwitch.isChecked = ztUserBean.isCloseFoldMenu
        mainMenuConfigCloseSwitch.isChecked = ztUserBean.isDisableMainConfigMenu
        termuxApiEmbedSwitch.isChecked = TermuxApiEmbed.isEnabled(this)
        victorFilesPanelSwitch.isChecked = VictorFilesPanel.isEnabled(this)
        remoteControlSwitch.isChecked = RemoteControlSettings.isExternalControlEnabled(this) // VictorSuite add
    }

    private fun setSwitchStatus(switchCompat: SwitchCompat, linearLayout: LinearLayout) {
        linearLayout.setOnClickListener {
            switchCompat.isChecked = !(switchCompat.isChecked)
        }
        switchCompat.setOnCheckedChangeListener { buttonView, isChecked ->
            val ztUserBean = UserSetManage.get().getZTUserBean()
            when (switchCompat) {
                ztDownloadServicesSwitch -> {
                    ztUserBean.isOpenDownloadFileServices = switchCompat.isChecked
                    Thread {
                        if (isChecked) {
                            if (!get().isServicesRun()) {
                                get().bootHttp()
                            }
                        } else {
                            get().stopServer()
                        }
                    }.start()
                }
                inputMethodTriggerCloseSwitch -> {
                    ztUserBean.isInputMethodTriggerClose = switchCompat.isChecked
                }
                styleTriggerOffSwitch -> {
                    ztUserBean.isStyleTriggerOff = switchCompat.isChecked
                }
                isToolShowSwitch -> {
                    ztUserBean.isToolShow = switchCompat.isChecked
                }
                forceUseNumpadSwitch -> {
                    ztUserBean.isForceUseNumpad = switchCompat.isChecked
                }
                logOutputSwitch -> {
                    ztUserBean.isOutputLOG = switchCompat.isChecked
                    LogUtils.isShow = switchCompat.isChecked
                }
                shellTermuxSwitch -> {
                    if (shellTermuxSwitch.isChecked) {
                        writerShellTermux()
                    } else {
                        File(FileUrl.timerShellExecFile).delete()
                    }

                }
                volumeFunctionSwitch -> {
                    ztUserBean.isResetVolume = switchCompat.isChecked
                }
                foldMenuCloseSwitch -> {
                    ztUserBean.isCloseFoldMenu = switchCompat.isChecked
                    //Toast.makeText(this, UUtils.getString(R.string.zt_command_path_ok), Toast.LENGTH_SHORT).show()
                }
                mainMenuConfigCloseSwitch -> {
                    ztUserBean.isDisableMainConfigMenu = switchCompat.isChecked
                    //Toast.makeText(this, UUtils.getString(R.string.zt_command_path_ok), Toast.LENGTH_SHORT).show()
                }
                victorFilesPanelSwitch -> {
                    VictorFilesPanel.setEnabled(this, isChecked)
                }
                remoteControlSwitch -> {
                    // VictorSuite add: only act on a real change (also fires on initial state).
                    if (isChecked != RemoteControlSettings.isExternalControlEnabled(this)) {
                        RemoteControlSettings.setExternalControlEnabled(this, isChecked)
                        Toast.makeText(
                            this,
                            UUtils.getString(if (isChecked) R.string.remote_control_on_toast else R.string.remote_control_off_toast),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                termuxApiEmbedSwitch -> {
                    // Also fires while the screen sets its initial state; only act on a real change.
                    if (isChecked != TermuxApiEmbed.isEnabled(this)) {
                        TermuxApiEmbed.setEnabled(this, isChecked)
                        Toast.makeText(
                            this,
                            UUtils.getString(if (isChecked) R.string.termux_api_embed_on_toast else R.string.termux_api_embed_off_toast),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            UserSetManage.get().setZTUserBean(ztUserBean)
        }
    }

    // VictorSuite add {@
    private fun showPairingsDialog() {
        val input = EditText(this)
        input.setText(RemoteControlSettings.formatPairingsText(RemoteControlSettings.pairings(this)))
        input.hint = "ai.droidcommand.app=controller"
        AlertDialog.Builder(this)
            .setTitle(R.string.remote_control_pairings_title)
            .setMessage(R.string.remote_control_pairings_summary)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                RemoteControlSettings.setPairings(this, RemoteControlSettings.parsePairingsText(input.text.toString()))
                Toast.makeText(this, R.string.remote_control_pairings_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    // @}

    private fun writerShellTermux() {
        val file = File(FileUrl.timerShellExecFile)
        if (!file.exists()) {
            UUtils.writerFile("runcommand/execTermuxEnv.sh", file)
            Shell.cmd("shell_chmod").exec()
        }
    }

    private fun isShellTermux(): Boolean {
        val file = File(FileUrl.timerShellExecFile)
        return file.exists()
    }

}
