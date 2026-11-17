package com.termux.victor.pc

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.termux.R
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/**
 * "My PC": lets a trusted PC mirror and control THIS phone with scrcpy over wireless ADB on
 * Wi-Fi or Tailscale. The PC is the scrcpy client; this phone is the device being mirrored.
 *
 * This is the opposite direction of [com.termux.victor.scrcpy.MainActivity] (the "ADB Remote
 * Connection" menu item, where this phone views a remote device). Nothing here uses that
 * client's classes or ports, and nothing here opens a listener: the phone side is Android's own
 * Wireless debugging, switched on by the user in Developer options after reading the warning.
 */
class MyPcActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var gateStatus: TextView
    private lateinit var ackButton: Button
    private lateinit var relockButton: Button
    private lateinit var statusWireless: TextView
    private lateinit var statusUsb: TextView
    private lateinit var statusLegacy: TextView
    private lateinit var addressesView: TextView
    private lateinit var openDevOptions: Button
    private lateinit var stopDevOptions: Button
    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText
    private lateinit var pairingPortInput: EditText
    private lateinit var buildButton: Button
    private lateinit var commandsOutput: TextView
    private lateinit var copyButton: Button

    private var addresses: List<LocalAddress> = emptyList()
    private var lastScript: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_pc)
        bindViews()
        wireClicks()
    }

    override fun onResume() {
        super.onResume()
        refreshGate()
        refreshStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    private fun bindViews() {
        gateStatus = findViewById(R.id.my_pc_gate_status)
        ackButton = findViewById(R.id.my_pc_ack_button)
        relockButton = findViewById(R.id.my_pc_relock_button)
        statusWireless = findViewById(R.id.my_pc_status_wireless)
        statusUsb = findViewById(R.id.my_pc_status_usb)
        statusLegacy = findViewById(R.id.my_pc_status_legacy)
        addressesView = findViewById(R.id.my_pc_addresses)
        openDevOptions = findViewById(R.id.my_pc_open_dev_options)
        stopDevOptions = findViewById(R.id.my_pc_stop_dev_options)
        hostInput = findViewById(R.id.my_pc_host_input)
        portInput = findViewById(R.id.my_pc_port_input)
        pairingPortInput = findViewById(R.id.my_pc_pairing_port_input)
        buildButton = findViewById(R.id.my_pc_build_button)
        commandsOutput = findViewById(R.id.my_pc_commands_output)
        copyButton = findViewById(R.id.my_pc_copy_button)
    }

    private fun wireClicks() {
        ackButton.setOnClickListener { showAckDialog() }
        relockButton.setOnClickListener {
            MyPcAckGate.revoke(this)
            refreshGate()
        }
        openDevOptions.setOnClickListener { openDeveloperOptions() }
        // Stopping is never gated: the user must always be able to switch Wireless debugging off.
        stopDevOptions.setOnClickListener { openDeveloperOptions() }
        buildButton.setOnClickListener { buildCommands() }
        copyButton.setOnClickListener { copyCommands() }
    }

    private fun refreshGate() {
        val ack = MyPcAckGate.isAcknowledged(this)
        val at = MyPcAckGate.acknowledgedAt(this)
        gateStatus.text = if (ack && at != null) {
            getString(R.string.my_pc_unlocked, DateFormat.getDateTimeInstance().format(Date(at)))
        } else {
            getString(R.string.my_pc_locked)
        }
        ackButton.visibility = if (ack) View.GONE else View.VISIBLE
        relockButton.visibility = if (ack) View.VISIBLE else View.GONE
        openDevOptions.isEnabled = ack
        hostInput.isEnabled = ack
        portInput.isEnabled = ack
        pairingPortInput.isEnabled = ack
        buildButton.isEnabled = ack
        copyButton.isEnabled = ack && lastScript != null
    }

    private fun showAckDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.my_pc_ack_dialog_title)
            .setMessage(R.string.my_pc_warning_body)
            .setPositiveButton(R.string.my_pc_ack_dialog_positive) { _, _ ->
                MyPcAckGate.acknowledge(this)
                refreshGate()
            }
            .setNegativeButton(R.string.my_pc_ack_dialog_negative, null)
            .show()
    }

    private fun refreshStatus() {
        executor.execute {
            val exposure = readExposure()
            val found = readAddresses()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                showExposure(exposure)
                showAddresses(found)
            }
        }
    }

    private fun readExposure(): PcAdbExposure {
        val resolver = contentResolver
        val wireless = runCatching { Settings.Global.getInt(resolver, "adb_wifi_enabled", -1) }.getOrNull()
        val usb = runCatching { Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, -1) }.getOrNull()
        val legacy = PcAdbExposure.legacyTcpPort(
            getProp("service.adb.tcp.port"),
            getProp("persist.adb.tcp.port"),
        )
        return PcAdbExposure(PcAdbExposure.flag(wireless), PcAdbExposure.flag(usb), legacy)
    }

    private fun getProp(name: String): String? {
        var process: Process? = null
        return try {
            process = ProcessBuilder("getprop", name).redirectErrorStream(true).start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            text
        } catch (e: Exception) {
            null
        } finally {
            process?.destroy()
        }
    }

    private fun readAddresses(): List<LocalAddress> {
        val result = ArrayList<LocalAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return result
            for (ni in interfaces) {
                if (!ni.isUp || ni.isLoopback) continue
                for (addr in ni.inetAddresses) {
                    val ip = (addr as? Inet4Address)?.hostAddress ?: continue
                    result.add(PcMirrorNet.describe(ni.name, ip))
                }
            }
        } catch (e: Exception) {
            // No address list; the screen shows the "none found" text.
        }
        return result
    }

    private fun showExposure(exposure: PcAdbExposure) {
        statusWireless.text = getString(
            when (exposure.wirelessDebuggingOn) {
                true -> R.string.my_pc_status_wireless_on
                false -> R.string.my_pc_status_wireless_off
                null -> R.string.my_pc_status_wireless_unknown
            }
        )
        statusUsb.text = getString(
            when (exposure.usbDebuggingOn) {
                true -> R.string.my_pc_status_usb_on
                false -> R.string.my_pc_status_usb_off
                null -> R.string.my_pc_status_usb_unknown
            }
        )
        val legacyPort = exposure.legacyTcpPort
        if (legacyPort != null) {
            statusLegacy.text = getString(R.string.my_pc_status_legacy_open, legacyPort)
            statusLegacy.setTextColor(getColor(R.color.vs_neon_magenta))
        } else {
            statusLegacy.text = getString(R.string.my_pc_status_legacy_closed)
            statusLegacy.setTextColor(getColor(R.color.vs_neon_text))
        }
    }

    private fun showAddresses(found: List<LocalAddress>) {
        addresses = found
        if (found.isEmpty()) {
            addressesView.text = getString(R.string.my_pc_addresses_none)
            return
        }
        addressesView.text = found.joinToString("\n") { a ->
            val label = when {
                a.mobileData -> R.string.my_pc_address_mobile
                a.kind == AddrKind.LAN -> R.string.my_pc_address_lan
                a.kind == AddrKind.TAILSCALE -> R.string.my_pc_address_tailscale
                else -> R.string.my_pc_address_other
            }
            getString(label, a.iface, a.ip)
        }
        if (hostInput.text.isNullOrBlank()) {
            PcMirrorNet.usableHosts(found).firstOrNull()?.let { hostInput.setText(it.ip) }
        }
    }

    private fun openDeveloperOptions() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(this, R.string.my_pc_open_dev_options_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun buildCommands() {
        if (!MyPcAckGate.isAcknowledged(this)) {
            refreshGate()
            return
        }
        val pairingText = pairingPortInput.text?.toString()
        val pairingPort = PcMirrorCommands.parsePort(pairingText)
        if (!pairingText.isNullOrBlank() && pairingPort == null) {
            showRejection(MirrorRejection.BAD_PAIRING_PORT)
            return
        }
        val plan = PcMirrorCommands.plan(
            host = hostInput.text?.toString().orEmpty(),
            adbPort = PcMirrorCommands.parsePort(portInput.text?.toString()),
            pairingPort = pairingPort,
        )
        when (plan) {
            is MirrorPlan.Ready -> {
                lastScript = plan.asScript()
                commandsOutput.text = lastScript
                commandsOutput.setTextColor(getColor(R.color.vs_neon_cyan))
                copyButton.isEnabled = true
            }
            is MirrorPlan.Rejected -> showRejection(plan.reason)
        }
    }

    private fun showRejection(reason: MirrorRejection) {
        lastScript = null
        copyButton.isEnabled = false
        commandsOutput.setTextColor(getColor(R.color.vs_neon_magenta))
        commandsOutput.text = getString(
            when (reason) {
                MirrorRejection.BAD_HOST -> R.string.my_pc_reject_bad_host
                MirrorRejection.NOT_TRUSTED_NETWORK -> R.string.my_pc_reject_untrusted
                MirrorRejection.BAD_ADB_PORT -> R.string.my_pc_reject_bad_port
                MirrorRejection.BAD_PAIRING_PORT -> R.string.my_pc_reject_bad_pairing_port
            }
        )
    }

    private fun copyCommands() {
        val script = lastScript ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("scrcpy commands", script))
        Toast.makeText(this, R.string.my_pc_copied, Toast.LENGTH_SHORT).show()
    }
}
