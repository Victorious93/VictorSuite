package com.termux.zerocore.hak5

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.termux.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Phase 1 Hak5 companion screen: authorized-use gate, Tetra reachability / firmware
 * detection over Wi-Fi/LAN, status, and Plunder Bug → RootForge handoff tile.
 * No USB code. No offensive actions.
 */
class Hak5CompanionActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var gatePanel: LinearLayout
    private lateinit var gateStatus: TextView
    private lateinit var gateAckButton: Button
    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var saveButton: Button
    private lateinit var checkButton: Button
    private lateinit var statusReachable: TextView
    private lateinit var statusFirmware: TextView
    private lateinit var statusDetail: TextView
    private lateinit var progress: ProgressBar
    private lateinit var plunderTile: LinearLayout
    private lateinit var revokeButton: Button

    private var lastCapabilities: CapabilityMap? = null

    private lateinit var statusDevice: TextView
    private lateinit var connectButton: Button
    private lateinit var commandLogButton: Button
    private lateinit var modulesEmpty: TextView
    private lateinit var modulesContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hak5_companion)
        bindViews()
        loadSettingsIntoForm()
        refreshGateUi()
        refreshCachedStatus()
        wireClicks()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    private fun bindViews() {
        gatePanel = findViewById(R.id.hak5_gate_panel)
        gateStatus = findViewById(R.id.hak5_gate_status)
        gateAckButton = findViewById(R.id.hak5_gate_ack_button)
        hostInput = findViewById(R.id.hak5_host_input)
        portInput = findViewById(R.id.hak5_port_input)
        tokenInput = findViewById(R.id.hak5_token_input)
        saveButton = findViewById(R.id.hak5_save_button)
        checkButton = findViewById(R.id.hak5_check_button)
        statusReachable = findViewById(R.id.hak5_status_reachable)
        statusFirmware = findViewById(R.id.hak5_status_firmware)
        statusDetail = findViewById(R.id.hak5_status_detail)
        progress = findViewById(R.id.hak5_progress)
        plunderTile = findViewById(R.id.hak5_plunder_tile)
        revokeButton = findViewById(R.id.hak5_revoke_button)
        statusDevice = findViewById(R.id.hak5_status_device)
        connectButton = findViewById(R.id.hak5_connect_button)
        commandLogButton = findViewById(R.id.hak5_command_log_button)
        modulesEmpty = findViewById(R.id.hak5_modules_empty)
        modulesContainer = findViewById(R.id.hak5_modules_container)
    }

    private fun loadSettingsIntoForm() {
        hostInput.setText(Hak5ConfigStore.getHost(this))
        portInput.setText(Hak5ConfigStore.getPort(this).toString())
        val token = Hak5ConfigStore.getApiToken(this)
        if (!token.isNullOrBlank()) {
            tokenInput.hint = getString(R.string.hak5_token_saved_hint)
        }
    }

    private fun refreshGateUi() {
        val ack = Hak5AuthGate.isAcknowledged(this)
        gatePanel.visibility = if (ack) View.GONE else View.VISIBLE
        val featuresEnabled = ack
        hostInput.isEnabled = featuresEnabled
        portInput.isEnabled = featuresEnabled
        tokenInput.isEnabled = featuresEnabled
        saveButton.isEnabled = featuresEnabled
        checkButton.isEnabled = featuresEnabled
        connectButton.isEnabled = featuresEnabled
        revokeButton.isEnabled = ack
        if (ack) {
            val at = Hak5AuthGate.acknowledgedAt(this) ?: 0L
            gateStatus.text = getString(R.string.hak5_gate_status_ok, formatTime(at))
        } else {
            gateStatus.text = getString(R.string.hak5_gate_status_locked)
        }
    }

    private fun refreshCachedStatus() {
        val reachable = Hak5ConfigStore.getLastReachable(this)
        val checkedAt = Hak5ConfigStore.getLastCheckedAt(this)
        val firmware = Hak5ConfigStore.getLastFirmware(this)
        if (checkedAt <= 0L) {
            statusReachable.text = getString(R.string.hak5_status_unknown)
            statusFirmware.text = ""
            statusDetail.text = getString(R.string.hak5_status_never_checked)
            return
        }
        statusReachable.text = if (reachable) {
            getString(R.string.hak5_status_online)
        } else {
            getString(R.string.hak5_status_offline)
        }
        statusFirmware.text = when {
            firmware.isNullOrBlank() -> getString(R.string.hak5_firmware_unknown_limited)
            firmware == "unknown" -> getString(R.string.hak5_firmware_unknown_limited)
            else -> getString(R.string.hak5_firmware_detected, firmware)
        }
        statusDetail.text = getString(R.string.hak5_status_last_checked, formatTime(checkedAt))
    }

    private fun wireClicks() {
        gateAckButton.setOnClickListener { showAckDialog() }
        saveButton.setOnClickListener { saveSettings() }
        checkButton.setOnClickListener { runConnectionCheck() }
        plunderTile.setOnClickListener { openPlunderBugHandoff() }
        revokeButton.setOnClickListener { confirmRevoke() }
        connectButton.setOnClickListener { runLogin() }
        commandLogButton.setOnClickListener {
            startActivity(Intent(this, Hak5CommandLogActivity::class.java))
        }
    }

    private fun showAckDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.hak5_gate_dialog_title)
            .setMessage(R.string.hak5_gate_dialog_message)
            .setPositiveButton(R.string.hak5_gate_ack_button) { _, _ ->
                val ok = Hak5AuthGate.acknowledge(this, Hak5AuthGate.ACK_STATEMENT)
                if (ok) {
                    Toast.makeText(this, R.string.hak5_gate_ack_done, Toast.LENGTH_SHORT).show()
                    refreshGateUi()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmRevoke() {
        AlertDialog.Builder(this)
            .setTitle(R.string.hak5_revoke_gate)
            .setMessage(R.string.hak5_revoke_gate_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                Hak5AuthGate.revoke(this)
                Hak5ConfigStore.clearSecrets(this)
                tokenInput.setText("")
                refreshGateUi()
                Toast.makeText(this, R.string.hak5_revoke_done, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveSettings() {
        if (!Hak5AuthGate.isAcknowledged(this)) {
            Toast.makeText(this, R.string.hak5_gate_status_locked, Toast.LENGTH_SHORT).show()
            return
        }
        val host = hostInput.text?.toString()?.trim().orEmpty()
        if (host.isEmpty()) {
            Toast.makeText(this, R.string.hak5_host_required, Toast.LENGTH_SHORT).show()
            return
        }
        val port = portInput.text?.toString()?.toIntOrNull() ?: Hak5ConfigStore.DEFAULT_PORT
        Hak5ConfigStore.setHost(this, host)
        Hak5ConfigStore.setPort(this, port)
        val tokenText = tokenInput.text?.toString()
        // Only overwrite token when the user typed something new.
        if (!tokenText.isNullOrBlank()) {
            Hak5ConfigStore.setApiToken(this, tokenText)
            tokenInput.setText("")
            tokenInput.hint = getString(R.string.hak5_token_saved_hint)
        }
        Hak5AuditLog.log(this, event = "SETTINGS_SAVED", detail = "host=$host port=$port")
        Toast.makeText(this, R.string.hak5_settings_saved, Toast.LENGTH_SHORT).show()
    }

    private fun runConnectionCheck() {
        if (!Hak5AuthGate.isAcknowledged(this)) {
            Toast.makeText(this, R.string.hak5_gate_status_locked, Toast.LENGTH_SHORT).show()
            return
        }
        saveSettings()
        val baseUrl = Hak5ConfigStore.baseUrl(this)
        val token = Hak5ConfigStore.getApiToken(this)
        progress.visibility = View.VISIBLE
        checkButton.isEnabled = false
        statusDetail.text = getString(R.string.hak5_checking)

        executor.execute {
            val result = try {
                FirmwareProbe().connect(baseUrl, token)
            } catch (e: Exception) {
                ConnectResult(
                    discovery = DiscoveryResult(
                        reachable = false,
                        baseUrl = baseUrl,
                        errorMessage = e.message
                    ),
                    capabilities = CapabilityMap.unknown(false),
                    usedToken = !token.isNullOrBlank()
                )
            }
            runOnUiThread { applyConnectResult(result) }
        }
    }

    private fun applyConnectResult(result: ConnectResult) {
        progress.visibility = View.GONE
        checkButton.isEnabled = Hak5AuthGate.isAcknowledged(this)
        lastCapabilities = result.capabilities
        val now = System.currentTimeMillis()
        Hak5ConfigStore.setLastCheckedAt(this, now)
        Hak5ConfigStore.setLastReachable(this, result.discovery.reachable)
        Hak5ConfigStore.setLastFirmware(this, result.capabilities.firmwareLabel)
        Hak5ConfigStore.setLastCapabilitiesJson(this, result.capabilities.toSummaryJson())

        Hak5AuditLog.log(
            this,
            event = "CONNECT",
            detail = "reachable=${result.discovery.reachable} " +
                "status=${result.discovery.httpStatus} " +
                "hint=${result.discovery.identityHint} " +
                "err=${result.discovery.errorMessage} " +
                "caps=${result.capabilities.confirmedActions.size}",
            deviceId = result.discovery.baseUrl,
            firmware = result.capabilities.firmwareLabel,
            success = result.discovery.reachable
        )

        if (result.discovery.reachable) {
            statusReachable.text = getString(R.string.hak5_status_online)
        } else {
            statusReachable.text = getString(R.string.hak5_status_offline)
        }

        statusFirmware.text = when {
            !result.discovery.reachable -> getString(R.string.hak5_firmware_n_a)
            result.capabilities.limitedMode -> getString(R.string.hak5_firmware_unknown_limited)
            else -> getString(R.string.hak5_firmware_detected, result.capabilities.firmwareLabel)
        }

        val parts = mutableListOf<String>()
        parts.add(getString(R.string.hak5_status_last_checked, formatTime(now)))
        result.discovery.identityHint?.let { parts.add(it) }
        result.discovery.errorMessage?.let { parts.add(it) }
        if (result.capabilities.apiShapeConfirmed) {
            parts.add(
                getString(
                    R.string.hak5_caps_confirmed,
                    result.capabilities.confirmedActions.size
                )
            )
        } else if (result.discovery.reachable) {
            parts.add(getString(R.string.hak5_api_shape_unconfirmed))
        }
        statusDetail.text = parts.joinToString("\n")
    }

    private fun openPlunderBugHandoff() {
        // Handoff tile does not require Pineapple gate for navigation, but we still
        // log the attempt. No USB code here.
        Hak5AuditLog.log(this, event = "PLUNDER_BUG_HANDOFF_TAP", detail = null)
        val resolved = RootForgeHandoff.resolve(this)
        if (resolved.installed && resolved.intent != null) {
            try {
                startActivity(resolved.intent)
                return
            } catch (e: Exception) {
                Toast.makeText(this, e.message ?: "Launch failed", Toast.LENGTH_LONG).show()
            }
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.hak5_plunder_missing_title)
            .setMessage(R.string.hak5_plunder_missing_message)
            .setPositiveButton(R.string.hak5_plunder_sideload) { _, _ ->
                startActivity(RootForgeHandoff.sideloadIntent())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------------------
    // Phase 2: authenticated session, runtime module listing, gated action exec.
    // ---------------------------------------------------------------------------

    private fun runLogin() {
        if (!Hak5AuthGate.isAcknowledged(this)) {
            Toast.makeText(this, R.string.hak5_gate_status_locked, Toast.LENGTH_SHORT).show()
            return
        }
        saveSettings()
        val token = Hak5ConfigStore.getApiToken(this)
        if (token.isNullOrBlank()) {
            Toast.makeText(this, R.string.hak5_login_need_token, Toast.LENGTH_LONG).show()
            return
        }
        val baseUrl = Hak5ConfigStore.baseUrl(this)
        progress.visibility = View.VISIBLE
        connectButton.isEnabled = false
        statusDetail.text = getString(R.string.hak5_connecting)

        executor.execute {
            val result = try {
                PineappleSession().login(baseUrl, token)
            } catch (e: Exception) {
                PineappleSession.LoginResult(
                    authenticated = false,
                    baseUrl = baseUrl,
                    capabilities = CapabilityMap.unknown(false),
                    status = PineappleSession.DeviceStatus(
                        reachable = false, apiShapeConfirmed = false, limitedMode = true
                    ),
                    errorMessage = e.message
                )
            }
            runOnUiThread { applyLoginResult(result) }
        }
    }

    private fun applyLoginResult(result: PineappleSession.LoginResult) {
        progress.visibility = View.GONE
        connectButton.isEnabled = Hak5AuthGate.isAcknowledged(this)
        lastCapabilities = result.capabilities
        val now = System.currentTimeMillis()
        Hak5ConfigStore.setLastCheckedAt(this, now)
        Hak5ConfigStore.setLastReachable(this, result.status.reachable)
        Hak5ConfigStore.setLastFirmware(this, result.capabilities.firmwareLabel)
        Hak5ConfigStore.setLastCapabilitiesJson(this, result.capabilities.toSummaryJson())

        Hak5AuditLog.log(
            this,
            event = "LOGIN",
            detail = "authenticated=${result.authenticated} " +
                "tokenRejected=${result.tokenRejected} " +
                "status=${result.httpStatus} " +
                "caps=${result.capabilities.confirmedActions.size} " +
                "err=${result.errorMessage}",
            deviceId = result.baseUrl,
            firmware = result.capabilities.firmwareLabel,
            success = result.authenticated
        )

        statusReachable.text = if (result.status.reachable) {
            getString(R.string.hak5_status_online)
        } else {
            getString(R.string.hak5_status_offline)
        }
        statusFirmware.text = when {
            !result.status.reachable -> getString(R.string.hak5_firmware_n_a)
            result.capabilities.limitedMode -> getString(R.string.hak5_firmware_unknown_limited)
            else -> getString(R.string.hak5_firmware_detected, result.capabilities.firmwareLabel)
        }
        statusDevice.text = getString(
            R.string.hak5_status_device,
            result.status.model ?: "—",
            result.status.version ?: "—"
        )

        if (result.authenticated) {
            PineappleSessionManager.start(result)
            statusDetail.text = getString(R.string.hak5_login_ok, result.capabilities.firmwareLabel)
            loadModules()
        } else {
            PineappleSessionManager.logout()
            val msg = if (result.tokenRejected) {
                getString(R.string.hak5_token_rejected)
            } else {
                getString(R.string.hak5_login_failed, result.errorMessage ?: "unknown")
            }
            statusDetail.text = msg
            renderModules(ModuleCatalog.limited(result.capabilities.firmwareLabel))
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun loadModules() {
        val caps = lastCapabilities ?: return
        val baseUrl = Hak5ConfigStore.baseUrl(this)
        val token = Hak5ConfigStore.getApiToken(this)
        progress.visibility = View.VISIBLE
        executor.execute {
            val catalog = try {
                PineappleModuleLister().list(baseUrl, token, caps)
            } catch (e: Exception) {
                ModuleCatalog.limited(caps.firmwareLabel)
            }
            runOnUiThread {
                progress.visibility = View.GONE
                PineappleSessionManager.updateCatalog(catalog)
                renderModules(catalog)
            }
        }
    }

    private fun renderModules(catalog: ModuleCatalog) {
        modulesContainer.removeAllViews()
        if (catalog.isEmpty) {
            modulesEmpty.visibility = View.VISIBLE
            modulesEmpty.text = if (catalog.limited) {
                getString(R.string.hak5_modules_limited)
            } else {
                getString(R.string.hak5_modules_none)
            }
            return
        }
        modulesEmpty.visibility = View.GONE
        for (module in catalog.modules) {
            val header = TextView(this).apply {
                text = module.displayName
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 16, 0, 4)
            }
            modulesContainer.addView(header)
            if (module.actions.isEmpty()) {
                val none = TextView(this).apply {
                    text = getString(R.string.hak5_module_no_actions)
                    textSize = 12f
                }
                modulesContainer.addView(none)
                continue
            }
            for (action in module.actions) {
                val btn = Button(this).apply {
                    text = "${action.action}  [${severityLabel(action.severity)}]"
                    setOnClickListener { onActionTapped(module, action) }
                }
                modulesContainer.addView(btn)
            }
        }
    }

    private fun severityLabel(severity: Hak5ActionSeverity): String = when (severity) {
        Hak5ActionSeverity.READ_ONLY -> getString(R.string.hak5_severity_read)
        Hak5ActionSeverity.CONFIG -> getString(R.string.hak5_severity_config)
        Hak5ActionSeverity.POTENTIALLY_INVASIVE -> getString(R.string.hak5_severity_invasive)
    }

    private fun onActionTapped(module: PineappleModule, action: ModuleActionInfo) {
        val pending = action.toPendingAction(module.name)
        if (action.severity == Hak5ActionSeverity.READ_ONLY) {
            // Read-only still requires the authorized-use gate, but not an extra confirm.
            executeAction(module, action, pending, userConfirmed = true)
        } else {
            showActionConfirm(module, action, pending)
        }
    }

    private fun showActionConfirm(
        module: PineappleModule,
        action: ModuleActionInfo,
        pending: Hak5PendingAction
    ) {
        val base = getString(
            R.string.hak5_confirm_action_message,
            pending.title,
            severityLabel(action.severity)
        )
        val message = if (action.severity == Hak5ActionSeverity.POTENTIALLY_INVASIVE) {
            base + System.lineSeparator() + System.lineSeparator() + getString(R.string.hak5_confirm_invasive_warning)
        } else {
            base
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.hak5_confirm_action_title)
            .setMessage(message)
            .setPositiveButton(R.string.hak5_confirm_run) { _, _ ->
                executeAction(module, action, pending, userConfirmed = true)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun executeAction(
        module: PineappleModule,
        action: ModuleActionInfo,
        pending: Hak5PendingAction,
        userConfirmed: Boolean
    ) {
        val caps = lastCapabilities
        val baseUrl = Hak5ConfigStore.baseUrl(this)
        val token = Hak5ConfigStore.getApiToken(this)
        val authorized = Hak5AuthGate.isAcknowledged(this)
        val request = PineappleApiRequest(
            module = module.name,
            action = action.action,
            apiToken = token
        )
        progress.visibility = View.VISIBLE
        statusDetail.text = getString(R.string.hak5_running)
        executor.execute {
            val runner = PineappleActionRunner(PineappleClient()) { entry ->
                Hak5CommandLog.append(this, entry)
            }
            val outcome = try {
                runner.run(baseUrl, token, pending, request, authorized, caps, userConfirmed)
            } catch (e: Exception) {
                PineappleActionRunner.ActionOutcome(
                    executed = false, blocked = false, errorMessage = e.message
                )
            }
            runOnUiThread {
                progress.visibility = View.GONE
                val msg = when {
                    outcome.blocked -> getString(R.string.hak5_action_blocked, outcome.blockReason ?: "")
                    outcome.executed -> getString(R.string.hak5_action_executed, outcome.httpStatus?.toString() ?: "?")
                    else -> getString(R.string.hak5_action_failed, outcome.errorMessage ?: "unknown")
                }
                statusDetail.text = msg
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun formatTime(epochMs: Long): String {
        if (epochMs <= 0L) return "—"
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(epochMs))
    }
}
