package fr.scodocprime.app.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("scodocprime", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = sp.getString("baseUrl", null) ?: DEFAULT_URL
        set(v) = sp.edit { putString("baseUrl", v) }

    var selectedSem: String?
        get() = sp.getString("selectedSem", null)
        set(v) = sp.edit { putString("selectedSem", v) }

    // Notifications
    var notifEnabled: Boolean
        get() = sp.getBoolean("notif", true)
        set(v) = sp.edit { putBoolean("notif", v) }
    var freqMinutes: Int
        get() = sp.getInt("freq", 30)
        set(v) = sp.edit { putInt("freq", v) }
    var quietEnabled: Boolean
        get() = sp.getBoolean("quiet", false)
        set(v) = sp.edit { putBoolean("quiet", v) }
    var quietStart: Int
        get() = sp.getInt("quietStart", 22)
        set(v) = sp.edit { putInt("quietStart", v) }
    var quietEnd: Int
        get() = sp.getInt("quietEnd", 7)
        set(v) = sp.edit { putInt("quietEnd", v) }

    fun isQuietNow(hour: Int): Boolean {
        if (!quietEnabled) return false
        val s = quietStart
        val e = quietEnd
        return if (s == e) false else if (s < e) hour in s until e else hour >= s || hour < e
    }

    var authExpiredNotified: Boolean
        get() = sp.getBoolean("authNotified", false)
        set(v) = sp.edit { putBoolean("authNotified", v) }
    var lastCheck: Long
        get() = sp.getLong("lastCheck", 0L)
        set(v) = sp.edit { putLong("lastCheck", v) }

    // Sécurité / apparence
    var autoLogin: Boolean
        get() = sp.getBoolean("autoLogin", true)
        set(v) = sp.edit { putBoolean("autoLogin", v) }
    var lockEnabled: Boolean
        get() = sp.getBoolean("lock", false)
        set(v) = sp.edit { putBoolean("lock", v) }
    var lockDelaySec: Int
        get() = sp.getInt("lockDelay", 0)
        set(v) = sp.edit { putInt("lockDelay", v) }
    /** 0 = système, 1 = clair, 2 = sombre */
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) = sp.edit { putInt("theme", v) }

    // Identifiants chiffrés (Android Keystore)
    var credUser: String?
        get() = sp.getString("credUser", null)
        set(v) = sp.edit { putString("credUser", v) }
    var credPass: String?
        get() = sp.getString("credPass", null)
        set(v) = sp.edit { putString("credPass", v) }

    // Instantanés et historique
    fun snapshot(sem: String): Map<String, String>? {
        val s = sp.getString("snap_$sem", null) ?: return null
        val o = JSONObject(s)
        return o.keys().asSequence().associateWith { o.getString(it) }
    }

    fun saveSnapshot(sem: String, map: Map<String, String>) =
        sp.edit(commit = true) { putString("snap_$sem", JSONObject(map).toString()) }

    fun events(): List<NoteEvent> = NoteEvent.listFromJson(sp.getString("events", null))
    fun saveEvents(l: List<NoteEvent>) = sp.edit(commit = true) { putString("events", NoteEvent.listToJson(l)) }

    fun cachedSemestres(): String? = sp.getString("cache_sems", null)
    fun cacheSemestres(json: String) = sp.edit { putString("cache_sems", json) }
    fun cachedReleve(sem: String): String? = sp.getString("cache_rel_$sem", null)
    fun cacheReleve(sem: String, json: String) = sp.edit { putString("cache_rel_$sem", json) }
}
