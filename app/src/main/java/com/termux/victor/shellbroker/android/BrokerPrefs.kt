package com.termux.victor.shellbroker.android

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.termux.victor.shellbroker.BrokerSettings
import com.termux.victor.shellbroker.Scope
import com.termux.victor.shellbroker.TrustStore
import com.termux.victor.shellbroker.TrustedCaller

/** Owner switches. Both OFF by default; root is its own switch. */
class PrefsBrokerSettings(context: Context) : BrokerSettings {
    private val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    override fun enabled() = p.getBoolean("enabled", false)
    override fun rootAllowed() = p.getBoolean("root_allowed", false)
    fun setEnabled(v: Boolean) { p.edit().putBoolean("enabled", v).apply() }
    fun setRootAllowed(v: Boolean) { p.edit().putBoolean("root_allowed", v).apply() }
    companion object { const val PREFS = "victorsuite_shell_broker" }
}

/** Trust list persisted in app-private storage (allowBackup is false, so it is never exported by Android backup). */
class PrefsTrustStore(context: Context) : TrustStore {
    private val p = context.applicationContext.getSharedPreferences(PrefsBrokerSettings.PREFS, Context.MODE_PRIVATE)
    private val listeners = mutableListOf<() -> Unit>()

    @Synchronized override fun all(): List<TrustedCaller> {
        val raw = p.getString("trusted", null) ?: return emptyList()
        return try {
            JsonParser.parseString(raw).asJsonArray.mapNotNull {
                val o = it.asJsonObject
                val scopes = o.getAsJsonArray("scopes").mapNotNull { s -> runCatching { Scope.valueOf(s.asString) }.getOrNull() }.toSet()
                TrustedCaller(o["package"].asString, o["cert"].asString, scopes, o.get("label")?.asString ?: o["package"].asString)
            }
        } catch (e: Exception) { emptyList() }                // unreadable list = trust nobody
    }

    override fun find(packageName: String) = all().firstOrNull { it.packageName == packageName }

    override fun upsert(c: TrustedCaller) { save(all().filter { it.packageName != c.packageName } + c); fire() }
    override fun remove(packageName: String) { save(all().filter { it.packageName != packageName }); fire() }
    @Synchronized override fun addListener(l: () -> Unit) { listeners += l }

    private fun save(rows: List<TrustedCaller>) {
        val a = JsonArray()
        rows.forEach { r -> a.add(JsonObject().apply {
            addProperty("package", r.packageName); addProperty("cert", r.certSha256); addProperty("label", r.label)
            add("scopes", JsonArray().also { arr -> r.scopes.forEach { arr.add(it.name) } })
        }) }
        p.edit().putString("trusted", a.toString()).commit()
    }

    private fun fire() { val ls = synchronized(this) { listeners.toList() }; ls.forEach { it() } }
}
