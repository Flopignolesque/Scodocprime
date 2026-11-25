package fr.scodocprime.app.data

import org.json.JSONArray
import org.json.JSONObject

const val DEFAULT_URL = "https://scodocetudiant.iut-blagnac.fr"

data class Semestre(val id: String, val titre: String, val annee: String?)

data class NoteVal(val value: String, val min: String?, val max: String?, val moy: String?) {
    val numeric: Double? get() = value.toD()
}

data class Evaluation(
    val key: String,
    val description: String,
    val date: String?,
    val coef: String?,
    val note: NoteVal?,
) {
    val hasNote: Boolean get() = note?.value?.let { Tracker.isReal(it) } == true
}

data class Module(
    val code: String,
    val titre: String,
    val evaluations: List<Evaluation>,
    val sae: Boolean,
) {
    /** Moyenne indicative pondérée par les coefs des évaluations notées. */
    val moyenneIndicative: Double?
        get() {
            var sum = 0.0
            var w = 0.0
            for (e in evaluations) {
                val n = e.note?.numeric ?: continue
                val c = e.coef.toD() ?: 1.0
                if (c <= 0) continue
                sum += n * c
                w += c
            }
            return if (w > 0) sum / w else null
        }

    val graded: Int get() = evaluations.count { it.hasNote }

    /** Date de la note la plus récente (ISO, pour trier). */
    val lastDate: String? get() = evaluations.filter { it.hasNote }.mapNotNull { it.date }.maxOrNull()
}

/** Contribution d'une ressource/SAÉ à une UE (coef + moyenne du module dans cette UE). */
data class UeModule(val code: String, val coef: Double?, val moyenne: Double?)

data class Ue(
    val code: String,
    val titre: String,
    val color: String?,
    val moyenne: NoteVal?,
    val rang: String?,
    val ects: String?,
    val bonus: String?,
    val malus: String?,
    val modules: List<UeModule>,
)

data class Absence(
    val date: String?,
    val debut: String?,
    val fin: String?,
    val matiere: String?,
    val statut: String?,
    val justifie: Boolean,
    val enseignant: String?,
    val sortKey: String,
) {
    val isRetard: Boolean get() = statut?.contains("retard", true) == true
}

data class Releve(
    val nom: String?,
    val formation: String?,
    val titreSemestre: String?,
    val moyenne: NoteVal?,
    val rang: String?,
    val absInjust: String?,
    val absTotal: String?,
    val absMetrique: String?,
    val situation: String?,
    val ects: String?,
    val ues: List<Ue>,
    val ressources: List<Module>,
    val saes: List<Module>,
    val absences: List<Absence>,
) {
    val modules: List<Module> get() = ressources + saes
    fun module(code: String): Module? = modules.firstOrNull { it.code == code }
    fun ue(code: String): Ue? = ues.firstOrNull { it.code == code }
    /** UE auxquelles un module contribue, avec son coef. */
    fun uesOf(code: String): List<Pair<Ue, UeModule>> =
        ues.mapNotNull { ue -> ue.modules.firstOrNull { it.code == code }?.let { ue to it } }
}

fun String?.toD(): Double? = this?.replace(',', '.')?.trim()?.toDoubleOrNull()

internal fun Any?.str(): String? = when (this) {
    null, JSONObject.NULL -> null
    is JSONObject -> opt("value").str()
    is JSONArray -> null
    else -> toString().trim().takeIf { it.isNotEmpty() && it != "null" }
}

internal fun JSONObject.s(key: String): String? = opt(key).str()

object Parser {

    fun semestres(json: Any): List<Semestre> {
        val arr = when (json) {
            is JSONArray -> json
            is JSONObject -> json.optJSONArray("semestres") ?: json.optJSONArray("data") ?: JSONArray()
            else -> JSONArray()
        }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i)
            if (o == null) {
                arr.opt(i).str()?.let { Semestre(it, "Semestre $it", null) }
            } else {
                val id = o.s("formsemestre_id") ?: o.s("id")
                if (id == null) null else Semestre(
                    id = id,
                    titre = o.s("titre_num") ?: o.s("titre") ?: o.s("titre_formation")
                        ?: ("Semestre " + (o.s("semestre_id") ?: id)),
                    annee = o.s("annee_scolaire") ?: o.s("anneescolaire") ?: o.s("annee_universitaire"),
                )
            }
        }
    }

    /** Semestre courant : celui avec le plus grand identifiant (les ids ScoDoc augmentent dans le temps). */
    fun current(sems: List<Semestre>): Semestre? =
        sems.maxByOrNull { it.id.toLongOrNull() ?: Long.MIN_VALUE } ?: sems.firstOrNull()

    private fun fraction(o: JSONObject?, a: String, b: String): String? {
        val v = o?.s(a) ?: return null
        val t = o.s(b)
        return if (t != null) "$v / $t" else v
    }

    private fun noteVal(any: Any?): NoteVal? = when (any) {
        is JSONObject -> any.s("value")?.let { NoteVal(it, any.s("min"), any.s("max"), any.s("moy")) }
        else -> any.str()?.let { NoteVal(it, null, null, null) }
    }

    fun releve(root: JSONObject): Releve {
        val r = root.optJSONObject("relevé") ?: root.optJSONObject("releve") ?: root
        val etu = r.optJSONObject("etudiant")
        val nom = etu?.let {
            it.s("nomprenom") ?: listOfNotNull(it.s("prenom"), it.s("nom")).joinToString(" ").ifBlank { null }
        }
        val formation = r.optJSONObject("formation")?.let { it.s("titre_officiel") ?: it.s("titre") }
        val sem = r.optJSONObject("semestre")
        val rangAny = sem?.opt("rang")
        val rang = if (rangAny is JSONObject) fraction(rangAny, "value", "total") else rangAny.str()
        val abs = sem?.optJSONObject("absences")
        val titreSem = sem?.s("numero")?.let { n ->
            val annee = sem.s("annee_universitaire")
            if (annee != null) "Semestre $n · $annee" else "Semestre $n"
        }
        val ues = r.optJSONObject("ues")?.let { obj ->
            obj.keys().asSequence().mapNotNull { k -> obj.optJSONObject(k)?.let { ue(k, it) } }.toList()
        } ?: emptyList()

        val absList = sequenceOf(
            root.opt("absences"), r.opt("absences"), root.opt("assiduites"), r.opt("assiduites"),
            root.opt("absencesDetail"),
        ).map { parseAbs(it) }.firstOrNull { it.isNotEmpty() } ?: emptyList()

        return Releve(
            nom = nom,
            formation = formation,
            titreSemestre = titreSem,
            moyenne = noteVal(sem?.opt("notes")),
            rang = rang,
            absInjust = abs?.s("injustifie"),
            absTotal = abs?.s("total"),
            absMetrique = abs?.s("metrique"),
            situation = sem?.s("situation"),
            ects = fraction(sem?.optJSONObject("ECTS"), "acquis", "total"),
            ues = ues,
            ressources = modules(r.optJSONObject("ressources"), false),
            saes = modules(r.optJSONObject("saes"), true),
            absences = absList.sortedByDescending { it.sortKey },
        )
    }

    private fun ue(code: String, o: JSONObject): Ue {
        val m = o.opt("moyenne")
        val mods = mutableListOf<UeModule>()
        for (key in listOf("ressources", "saes")) {
            val mo = o.optJSONObject(key) ?: continue
            for (c in mo.keys()) {
                val x = mo.optJSONObject(c)
                mods += UeModule(c, x?.s("coef").toD(), x?.opt("moyenne").str().toD())
            }
        }
        return Ue(
            code = code,
            titre = o.s("titre") ?: code,
            color = o.s("color"),
            moyenne = noteVal(m),
            rang = if (m is JSONObject) fraction(m, "rang", "total") else null,
            ects = fraction(o.optJSONObject("ECTS"), "acquis", "total"),
            bonus = o.s("bonus"),
            malus = o.s("malus"),
            modules = mods,
        )
    }

    private fun modules(o: JSONObject?, sae: Boolean): List<Module> {
        if (o == null) return emptyList()
        return o.keys().asSequence().mapNotNull { code ->
            val m = o.optJSONObject(code) ?: return@mapNotNull null
            val evs = m.optJSONArray("evaluations") ?: JSONArray()
            val list = (0 until evs.length()).mapNotNull { i ->
                val e = evs.optJSONObject(i) ?: return@mapNotNull null
                val id = e.s("id") ?: "$i"
                Evaluation(
                    key = "$code|$id",
                    description = e.s("description") ?: "Évaluation ${i + 1}",
                    date = e.s("date"),
                    coef = e.s("coef"),
                    note = noteVal(e.opt("note")),
                )
            }
            Module(code, m.s("titre") ?: code, list, sae)
        }.toList()
    }

    // ---------- Absences (format variable selon les versions de la passerelle) ----------

    private fun parseAbs(src: Any?): List<Absence> = when (src) {
        is JSONArray -> (0 until src.length()).mapNotNull { i -> src.optJSONObject(i)?.let { absence(it, null) } }
        is JSONObject -> src.keys().asSequence().flatMap { k ->
            when (val v = src.opt(k)) {
                is JSONArray -> (0 until v.length()).mapNotNull { i -> v.optJSONObject(i)?.let { absence(it, k) } }.asSequence()
                is JSONObject -> listOfNotNull(absence(v, k)).asSequence()
                else -> emptySequence()
            }
        }.toList()
        else -> emptyList()
    }.filterNot { it.statut?.contains("present", true) == true || it.statut?.contains("présent", true) == true }

    private fun absence(o: JSONObject, keyDate: String?): Absence? {
        val dateRaw = o.s("date") ?: o.s("date_debut") ?: o.s("dateDebut") ?: o.s("jour")
            ?: keyDate?.takeIf { Regex("""^\d{4}-\d{2}-\d{2}""").containsMatchIn(it) }
        val statut = o.s("statut") ?: o.s("etat") ?: o.s("type")
        if (dateRaw == null && statut == null) return null
        val debut = o.s("debut") ?: o.s("heure_debut") ?: isoTime(o.s("date_debut"))
        val fin = o.s("fin") ?: o.s("heure_fin") ?: isoTime(o.s("date_fin"))
        val justif = when (val j = o.opt("justifie") ?: o.opt("est_just") ?: o.opt("justifiee") ?: o.opt("justif")) {
            is Boolean -> j
            is Number -> j.toInt() != 0
            is String -> j.equals("true", true) || j == "1" || j.equals("oui", true)
            else -> false
        }
        val matiere = o.s("matiereComplet") ?: o.s("matiere") ?: o.s("module") ?: o.s("titre_module")
            ?: o.s("ressource") ?: o.s("desc") ?: o.s("description")
        return Absence(
            date = dateRaw?.take(10),
            debut = fmtHour(debut),
            fin = fmtHour(fin),
            matiere = matiere,
            statut = statut,
            justifie = justif,
            enseignant = o.s("enseignant"),
            sortKey = (dateRaw ?: "") + " " + (debut ?: ""),
        )
    }

    private fun isoTime(v: String?): String? =
        v?.let { Regex("""T(\d{2}):(\d{2})""").find(it) }?.let { "${it.groupValues[1]}:${it.groupValues[2]}" }

    /** 8.5 -> 8h30 ; "08:30" -> 8h30 */
    fun fmtHour(v: String?): String? {
        if (v == null) return null
        v.toD()?.let { d ->
            val h = d.toInt()
            val m = Math.round((d - h) * 60).toInt()
            return if (m == 0) "${h}h" else "${h}h" + m.toString().padStart(2, '0')
        }
        Regex("""^(\d{1,2}):(\d{2})""").find(v)?.let {
            val h = it.groupValues[1].toInt()
            val m = it.groupValues[2]
            return if (m == "00") "${h}h" else "${h}h$m"
        }
        return v
    }
}
