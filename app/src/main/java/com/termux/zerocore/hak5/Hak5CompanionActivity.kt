package com.termux.zerocore.hak5

import android.app.AlertDialog
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

    private fun formatTime(epochMs: Long): String {
        if (epochMs <= 0L) return "—"
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(epochMs))
    }
}
