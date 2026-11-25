package fr.scodocprime.app.data

data class NewNote(val module: Module, val eval: Evaluation)

object Tracker {
    fun isReal(v: String): Boolean {
        val t = v.trim()
        return t.isNotEmpty() && t != "~" && t != "-" && !t.equals("NA", true) && !t.equals("ATT", true)
    }

    private fun noteMap(r: Releve): Map<String, String> =
        r.modules.flatMap { m ->
            m.evaluations.mapNotNull { e -> e.note?.value?.takeIf { isReal(it) }?.let { e.key to it } }
        }.toMap()

    /** Met à jour l'instantané et renvoie les notes nouvelles/modifiées (null si premier passage). */
    fun update(prefs: Prefs, semId: String, r: Releve): List<NewNote>? {
        val current = noteMap(r)
        val old = prefs.snapshot(semId)
        prefs.saveSnapshot(semId, current)
        if (old == null) return null
        val changed = current.filter { (k, v) -> old[k] != v }.keys
        if (changed.isEmpty()) return emptyList()
        return r.modules.flatMap { m -> m.evaluations.filter { it.key in changed }.map { NewNote(m, it) } }
    }
}
