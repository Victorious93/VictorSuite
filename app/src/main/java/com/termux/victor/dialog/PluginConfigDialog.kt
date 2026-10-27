package com.termux.victor.dialog

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.example.xh_lib.utils.UUtils
import com.termux.R
import com.termux.victor.plugin.PluginConfigFieldType
import com.termux.victor.plugin.PluginConfigStore
import com.termux.victor.plugin.PluginLogger
import com.termux.victor.plugin.PluginRecord

/**
 * Settings form for a plugin's configSchema, backed by the encrypted
 * [PluginConfigStore]. Fields are built programmatically since the schema is dynamic
 * (an arbitrary number/type of fields per plugin) rather than a fixed layout.
 */
class PluginConfigDialog(private val context: Context, private val record: PluginRecord) {

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_plugin_config, null as ViewGroup?)
        val title = view.findViewById<TextView>(R.id.config_title)
        val container = view.findViewById<LinearLayout>(R.id.config_fields)
        val cancelBtn = view.findViewById<TextView>(R.id.config_cancel)
        val saveBtn = view.findViewById<TextView>(R.id.config_save)

        title.text = "${record.manifestEntry.name} - ${UUtils.getString(R.string.plugin_configure)}"

        val editTexts = LinkedHashMap<String, EditText>()
        val checkBoxes = LinkedHashMap<String, CheckBox>()

        if (record.manifestEntry.configSchema.isEmpty()) {
            val empty = TextView(context)
            empty.text = UUtils.getString(R.string.plugin_none_registered)
            empty.setTextColor(Color.WHITE)
            empty.textSize = 12f
            container.addView(empty)
        }

        record.manifestEntry.configSchema.forEach { field ->
            val label = TextView(context)
            label.text = field.label
            label.setTextColor(Color.WHITE)
            label.textSize = 12f
            val labelParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            labelParams.topMargin = dp(10)
            label.layoutParams = labelParams
            container.addView(label)

            if (field.type == PluginConfigFieldType.BOOL) {
                val checkBox = CheckBox(context)
                checkBox.isChecked = PluginConfigStore.get(context, record.id, field.key, field.default) == "true"
                container.addView(checkBox)
                checkBoxes[field.key] = checkBox
            } else {
                val editText = EditText(context)
                editText.setText(PluginConfigStore.get(context, record.id, field.key, field.default))
                editText.setTextColor(Color.WHITE)
                when (field.type) {
                    PluginConfigFieldType.SECRET ->
                        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    PluginConfigFieldType.NUMBER ->
                        editText.inputType = InputType.TYPE_CLASS_NUMBER
                    else -> editText.inputType = InputType.TYPE_CLASS_TEXT
                }
                container.addView(editText)
                editTexts[field.key] = editText
            }
        }

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(true)
            .create()

        cancelBtn.setOnClickListener { dialog.dismiss() }
        saveBtn.setOnClickListener {
            editTexts.forEach { (key, editText) -> PluginConfigStore.set(context, record.id, key, editText.text.toString()) }
            checkBoxes.forEach { (key, checkBox) -> PluginConfigStore.set(context, record.id, key, checkBox.isChecked.toString()) }
            PluginLogger.log(context, record.id, "CONFIG_UPDATED")
            UUtils.showMsg(UUtils.getString(R.string.plugin_config_saved_msg))
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun dp(value: Int): Int {
        val density = context.resources.displayMetrics.density
        return (value * density).toInt()
    }
}
