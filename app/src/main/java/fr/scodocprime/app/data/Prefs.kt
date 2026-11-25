package fr.scodocprime.app.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("scodocprime", Context.MODE_PRIVATE)

    var baseUrl: String?
        get() = sp.getString("baseUrl", null)
        set(v) = sp.edit { putString("baseUrl", v) }

    var selectedSem: String?
        get() = sp.getString("selectedSem", null)
        set(v) = sp.edit { putString("selectedSem", v) }

    var notifEnabled: Boolean
        get() = sp.getBoolean("notif", true)
        set(v) = sp.edit { putBoolean("notif", v) }

    var authExpiredNotified: Boolean
        get() = sp.getBoolean("authNotified", false)
        set(v) = sp.edit { putBoolean("authNotified", v) }

    var lastCheck: Long
        get() = sp.getLong("lastCheck", 0L)
        set(v) = sp.edit { putLong("lastCheck", v) }

    fun snapshot(sem: String): Map<String, String>? {
        val s = sp.getString("snap_$sem", null) ?: return null
        val o = JSONObject(s)
        return o.keys().asSequence().associateWith { o.getString(it) }
    }

    fun saveSnapshot(sem: String, map: Map<String, String>) =
        sp.edit(commit = true) { putString("snap_$sem", JSONObject(map).toString()) }

    fun highlights(): Set<String> = sp.getStringSet("highlights", emptySet())?.toSet() ?: emptySet()
    fun setHighlights(keys: Set<String>) = sp.edit { putStringSet("highlights", keys) }
    fun addHighlights(keys: Collection<String>) = setHighlights(highlights() + keys)

    fun cachedSemestres(): String? = sp.getString("cache_sems", null)
    fun cacheSemestres(json: String) = sp.edit { putString("cache_sems", json) }
    fun cachedReleve(sem: String): String? = sp.getString("cache_rel_$sem", null)
    fun cacheReleve(sem: String, json: String) = sp.edit { putString("cache_rel_$sem", json) }
}
