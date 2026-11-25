package fr.scodocprime.app.data

import org.json.JSONArray
import org.json.JSONObject

data class NewNote(val module: Module, val eval: Evaluation)

/** Une note détectée (historique de l'onglet Nouveautés). */
data class NoteEvent(
    val key: String,
    val semId: String,
    val moduleCode: String,
    val moduleTitre: String,
    val description: String,
    val value: String,
    val moy: String?,
    val ts: Long,
    val read: Boolean,
    val notified: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("key", key).put("sem", semId).put("code", moduleCode).put("titre", moduleTitre)
        .put("desc", description).put("value", value).put("moy", moy ?: JSONObject.NULL)
        .put("ts", ts).put("read", read).put("notified", notified)

    companion object {
        fun fromJson(o: JSONObject) = NoteEvent(
            key = o.optString("key"),
            semId = o.optString("sem"),
            moduleCode = o.optString("code"),
            moduleTitre = o.optString("titre"),
            description = o.optString("desc"),
            value = o.optString("value"),
            moy = o.s("moy"),
            ts = o.optLong("ts"),
            read = o.optBoolean("read"),
            notified = o.optBoolean("notified"),
        )

        fun listFromJson(s: String?): List<NoteEvent> {
            if (s.isNullOrBlank()) return emptyList()
            return try {
                val a = JSONArray(s)
                (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::fromJson) }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun listToJson(l: List<NoteEvent>): String = JSONArray().apply { l.forEach { put(it.toJson()) } }.toString()
    }
}

object Tracker {
    fun isReal(v: String): Boolean {
        val t = v.trim()
        return t.isNotEmpty() && t != "~" && t != "-" && !t.equals("NA", true) && !t.equals("ATT", true)
    }

    private fun noteMap(r: Releve): Map<String, String> =
        r.modules.flatMap { m ->
            m.evaluations.mapNotNull { e -> e.note?.value?.takeIf { isReal(it) }?.let { e.key to it } }
        }.toMap()

    /**
     * Compare le relevé à l'instantané précédent, enregistre les nouvelles notes dans l'historique
     * et renvoie la liste (vide au tout premier passage sur un semestre).
     */
    @Synchronized
    fun update(prefs: Prefs, semId: String, r: Releve, seenByUser: Boolean): List<NewNote> {
        val current = noteMap(r)
        val old = prefs.snapshot(semId)
        prefs.saveSnapshot(semId, current)
        if (old == null) return emptyList()
        val changed = current.filter { (k, v) -> old[k] != v }.keys
        if (changed.isEmpty()) return emptyList()
        val news = r.modules.flatMap { m -> m.evaluations.filter { it.key in changed }.map { NewNote(m, it) } }
        val now = System.currentTimeMillis()
        val events = news.map {
            NoteEvent(
                key = it.eval.key, semId = semId, moduleCode = it.module.code, moduleTitre = it.module.titre,
                description = it.eval.description, value = it.eval.note?.value ?: "", moy = it.eval.note?.moy,
                ts = now, read = false, notified = seenByUser,
            )
        }
        val kept = prefs.events().filterNot { e -> e.semId == semId && e.key in changed }
        prefs.saveEvents((events + kept).take(300))
        return news
    }
}
