package fr.scodocprime.app.data

import org.json.JSONArray
import org.json.JSONObject

data class Semestre(val id: String, val titre: String, val annee: String?)

data class NoteVal(val value: String, val min: String?, val max: String?, val moy: String?) {
    val numeric: Double? get() = value.replace(',', '.').trim().toDoubleOrNull()
}

data class Evaluation(
    val key: String,
    val description: String,
    val date: String?,
    val coef: String?,
    val note: NoteVal?,
)

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
                val c = e.coef?.replace(',', '.')?.toDoubleOrNull() ?: 1.0
                if (c <= 0) continue
                sum += n * c
                w += c
            }
            return if (w > 0) sum / w else null
        }
}

data class Ue(
    val code: String,
    val titre: String,
    val color: String?,
    val moyenne: NoteVal?,
    val rang: String?,
    val ects: String?,
    val bonus: String?,
    val malus: String?,
)

data class Releve(
    val nom: String?,
    val formation: String?,
    val titreSemestre: String?,
    val moyenne: NoteVal?,
    val rang: String?,
    val absInjust: String?,
    val absTotal: String?,
    val situation: String?,
    val ects: String?,
    val ues: List<Ue>,
    val ressources: List<Module>,
    val saes: List<Module>,
) {
    val modules: List<Module> get() = ressources + saes
}

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

        return Releve(
            nom = nom,
            formation = formation,
            titreSemestre = titreSem,
            moyenne = noteVal(sem?.opt("notes")),
            rang = rang,
            absInjust = abs?.s("injustifie"),
            absTotal = abs?.s("total"),
            situation = sem?.s("situation"),
            ects = fraction(sem?.optJSONObject("ECTS"), "acquis", "total"),
            ues = ues,
            ressources = modules(r.optJSONObject("ressources"), false),
            saes = modules(r.optJSONObject("saes"), true),
        )
    }

    private fun ue(code: String, o: JSONObject): Ue {
        val m = o.opt("moyenne")
        return Ue(
            code = code,
            titre = o.s("titre") ?: code,
            color = o.s("color"),
            moyenne = noteVal(m),
            rang = if (m is JSONObject) fraction(m, "rang", "total") else null,
            ects = fraction(o.optJSONObject("ECTS"), "acquis", "total"),
            bonus = o.s("bonus"),
            malus = o.s("malus"),
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
}
