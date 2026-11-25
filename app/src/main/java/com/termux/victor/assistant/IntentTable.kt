package com.termux.victor.assistant

import org.json.JSONArray
import org.json.JSONObject

/** Versioned JSON intent table plus overlays (overlays add or override by id only). */
class IntentTable private constructor(val version: Int, val intents: List<Intent>) {

    /** Overlay intents replace same-id entries and append new ones. Nothing else can change. */
    fun withOverlay(overlayJson: String): IntentTable {
        val extra = try {
            parseIntents(JSONObject(overlayJson).optJSONArray("intents"))
        } catch (_: Exception) {
            return this // malformed overlays are ignored
        }
        val byId = LinkedHashMap<String, Intent>()
        intents.forEach { byId[it.id] = it }
        extra.forEach { byId[it.id] = it }
        return IntentTable(version, byId.values.toList())
    }

    companion object {
        fun parse(json: String): IntentTable {
            val o = JSONObject(json)
            return IntentTable(o.optInt("version", 1), parseIntents(o.optJSONArray("intents")))
        }

        private fun strings(a: JSONArray?): List<String> =
            if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

        private fun parseIntents(arr: JSONArray?): List<Intent> {
            if (arr == null) return emptyList()
            val out = ArrayList<Intent>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id", "")
                if (id.isEmpty()) continue
                val user = o.optString("command_user", "").ifBlank { null }
                val root = o.optString("command_root", "").ifBlank { null }
                if (user == null && root == null) continue
                // Unknown fields (including any "endpoint"/"url"/"destructive") are ignored on purpose.
                out += Intent(
                    id = id,
                    title = o.optString("title", id),
                    keywords = strings(o.optJSONArray("keywords")),
                    aliases = strings(o.optJSONArray("aliases")),
                    commandUser = user,
                    commandRoot = root,
                    explain = o.optString("explain", ""),
                    androidNote = o.optString("android_note", ""),
                )
            }
            return out
        }
    }
}
