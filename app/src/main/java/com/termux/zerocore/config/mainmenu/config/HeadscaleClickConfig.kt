package com.termux.zerocore.config.mainmenu.config

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.Drawable
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.example.xh_lib.utils.SaveData
import com.example.xh_lib.utils.UUtils
import com.termux.R
import com.termux.zerocore.code.CodeString
import com.termux.zerocore.url.FileUrl
import com.termux.zerocore.utils.SingletonCommunicationUtils
import java.io.File

/**
 * Headscale / WireGuard tailnet client.
 *
 * Installs the official static Tailscale client binaries (no root required,
 * userspace-networking mode) into the app's home directory and connects the
 * device to a user-supplied headscale coordination server. Traffic for
 * device-to-device access is carried over the WireGuard-based tailnet and
 * exposed locally via a SOCKS5/HTTP proxy on 127.0.0.1:1055, following
 * Tailscale's documented rootless setup.
 */
class HeadscaleClickConfig : BaseMenuClickConfig() {
    companion object {
        private const val KEY_LOGIN_SERVER = "headscale_login_server"
    }

    override fun getIcon(context: Context?): Drawable? {
        return context?.getDrawable(R.mipmap.link_ico)
    }

    override fun getString(context: Context?): String? {
        return "Headscale / Tailnet"
    }

    override fun onClick(view: View?, context: Context?) {
        val ctx = context ?: return
        showConnectDialog(ctx)
    }

    private fun showConnectDialog(context: Context) {
        val density = context.resources.displayMetrics.density
        val padding = (16 * density).toInt()

        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(padding, padding / 2, padding, padding / 2)

        val serverInput = EditText(context)
        serverInput.hint = "Login server URL, e.g. https://headscale.example.com"
        serverInput.setText(SaveData.getStringOther(KEY_LOGIN_SERVER) ?: "")
        layout.addView(serverInput)

        val authKeyInput = EditText(context)
        authKeyInput.hint = "Pre-auth key (leave blank to log in interactively)"
        authKeyInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        layout.addView(authKeyInput)

        AlertDialog.Builder(context)
            .setTitle("Headscale / WireGuard Tailnet")
            .setMessage(
                "Connects this device to your own headscale server over a WireGuard-based " +
                    "tailnet (no root required). If you leave the pre-auth key blank, the " +
                    "terminal will print a login URL to open in a browser instead."
            )
            .setView(layout)
            .setPositiveButton("Connect") { _, _ ->
                val server = serverInput.text?.toString()?.trim().orEmpty()
                val authKey = authKeyInput.text?.toString()?.trim().orEmpty()
                if (server.isEmpty()) {
                    Toast.makeText(context, "Login server URL is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                SaveData.saveStringOther(KEY_LOGIN_SERVER, server)
                connect(server, authKey)
            }
            .setNeutralButton("Status") { _, _ -> status() }
            .setNegativeButton("Disconnect") { _, _ -> disconnect() }
            .show()
    }

    private fun connect(server: String, authKey: String) {
        File(FileUrl.headscaleDir).mkdirs()

        val authKeyFlag = if (authKey.isEmpty()) "" else "--authkey=${shellQuote(authKey)}"

        val script = """
            #!/data/data/com.termux/files/usr/bin/bash
            set -e

            TS_DIR="${'$'}HOME/.headscale"
            TS_BIN="${'$'}TS_DIR/bin"
            mkdir -p "${'$'}TS_BIN"

            ARCH_RAW="${'$'}(uname -m)"
            case "${'$'}ARCH_RAW" in
                aarch64|arm64) TS_ARCH=arm64 ;;
                armv7l|armv8l|arm) TS_ARCH=arm ;;
                x86_64|amd64) TS_ARCH=amd64 ;;
                i686|i386) TS_ARCH=386 ;;
                *) echo "Unsupported architecture: ${'$'}ARCH_RAW"; exit 1 ;;
            esac

            if [ ! -x "${'$'}TS_BIN/tailscale" ] || [ ! -x "${'$'}TS_BIN/tailscaled" ]; then
                echo "Fetching latest Tailscale static build for ${'$'}TS_ARCH ..."
                pkg install -y curl >/dev/null 2>&1 || true
                LATEST_FILE="${'$'}(curl -fsSL https://pkgs.tailscale.com/stable/ | grep -oE "tailscale_[0-9.]+_${'$'}{TS_ARCH}\.tgz" | sort -V | tail -n1)"
                if [ -z "${'$'}LATEST_FILE" ]; then
                    echo "Could not determine latest Tailscale release for ${'$'}TS_ARCH; aborting."
                    exit 1
                fi
                curl -fsSL "https://pkgs.tailscale.com/stable/${'$'}LATEST_FILE" -o "${'$'}TS_DIR/tailscale.tgz"
                tar -xzf "${'$'}TS_DIR/tailscale.tgz" -C "${'$'}TS_DIR"
                EXTRACTED_DIR="${'$'}(find "${'$'}TS_DIR" -maxdepth 1 -type d -name 'tailscale_*' | head -n1)"
                cp "${'$'}EXTRACTED_DIR/tailscale" "${'$'}TS_BIN/tailscale"
                cp "${'$'}EXTRACTED_DIR/tailscaled" "${'$'}TS_BIN/tailscaled"
                chmod +x "${'$'}TS_BIN/tailscale" "${'$'}TS_BIN/tailscaled"
                rm -rf "${'$'}EXTRACTED_DIR" "${'$'}TS_DIR/tailscale.tgz"
            fi

            SOCK_FILE="${'$'}TS_DIR/tailscaled.sock"

            if ! pgrep -f "tailscaled --tun=userspace-networking --statedir=${'$'}TS_DIR" >/dev/null 2>&1; then
                echo "Starting tailscaled (userspace networking, no root required)..."
                nohup "${'$'}TS_BIN/tailscaled" \
                    --tun=userspace-networking \
                    --statedir="${'$'}TS_DIR" \
                    --socket="${'$'}SOCK_FILE" \
                    --socks5-server=localhost:1055 \
                    --outbound-http-proxy=localhost:1055 \
                    > "${'$'}TS_DIR/tailscaled.log" 2>&1 &
                sleep 2
            fi

            echo "Connecting to headscale server: ${shellQuote(server)}"
            "${'$'}TS_BIN/tailscale" --socket="${'$'}SOCK_FILE" up \
                --login-server=${shellQuote(server)} \
                $authKeyFlag \
                --accept-dns=false

            echo ""
            echo "Local SOCKS5/HTTP proxy for device-to-device access: 127.0.0.1:1055"
            "${'$'}TS_BIN/tailscale" --socket="${'$'}SOCK_FILE" status
        """.trimIndent() + "\n"

        val scriptFile = File(FileUrl.headscaleConnectScript)
        UUtils.setFileString(scriptFile, script)
        try {
            android.system.Os.chmod(FileUrl.headscaleConnectScript, 448) // 0700, keeps auth key private
        } catch (e: Exception) {
            e.printStackTrace()
        }

        writeAuxiliaryScripts()

        SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener()
            .sendTextToTerminal(CodeString.runHeadscaleConnect)
    }

    private fun disconnect() {
        writeAuxiliaryScripts()
        SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener()
            .sendTextToTerminal(CodeString.runHeadscaleDisconnect)
    }

    private fun status() {
        writeAuxiliaryScripts()
        SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener()
            .sendTextToTerminal(CodeString.runHeadscaleStatus)
    }

    private fun writeAuxiliaryScripts() {
        File(FileUrl.headscaleDir).mkdirs()

        val disconnectScript = """
            #!/data/data/com.termux/files/usr/bin/bash
            TS_DIR="${'$'}HOME/.headscale"
            "${'$'}TS_DIR/bin/tailscale" --socket="${'$'}TS_DIR/tailscaled.sock" down
        """.trimIndent() + "\n"
        UUtils.setFileString(File(FileUrl.headscaleDisconnectScript), disconnectScript)

        val statusScript = """
            #!/data/data/com.termux/files/usr/bin/bash
            TS_DIR="${'$'}HOME/.headscale"
            "${'$'}TS_DIR/bin/tailscale" --socket="${'$'}TS_DIR/tailscaled.sock" status
        """.trimIndent() + "\n"
        UUtils.setFileString(File(FileUrl.headscaleStatusScript), statusScript)
    }

    /** Wraps a value in single quotes for safe interpolation into the generated shell script. */
    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }
}
