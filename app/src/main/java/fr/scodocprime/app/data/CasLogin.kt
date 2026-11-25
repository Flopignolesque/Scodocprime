package fr.scodocprime.app.data

import java.net.URL

data class LoginForm(val action: String, val fields: Map<String, String>, val userField: String, val passField: String)

/** Lecture du formulaire de connexion CAS pour la reconnexion automatique. */
object CasLogin {
    private val formRe = Regex("""<form\b([^>]*)>(.*?)</form>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val inputRe = Regex("""<(input|button)\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val attrRe = Regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))""")

    fun findForm(html: String, pageUrl: String): LoginForm? {
        if (!html.contains("password", ignoreCase = true)) return null
        for (f in formRe.findAll(html)) {
            val inputs = inputRe.findAll(f.groupValues[2])
                .map { it.groupValues[1].lowercase() to attrs(it.groupValues[2]) }
                .toList()
            val pass = inputs.firstOrNull { it.second["type"]?.lowercase() == "password" }?.second?.get("name") ?: continue
            val user = inputs.firstOrNull { (tag, a) ->
                val t = a["type"]?.lowercase() ?: "text"
                val n = a["name"]
                tag == "input" && n != null && n != pass && (t == "text" || t == "email")
            }?.second?.get("name") ?: continue
            val fields = LinkedHashMap<String, String>()
            for ((tag, a) in inputs) {
                val n = a["name"] ?: continue
                val t = a["type"]?.lowercase() ?: if (tag == "button") "submit" else "text"
                if (t == "hidden") fields[n] = a["value"] ?: ""
                else if (t == "submit" && !fields.containsKey(n)) fields[n] = a["value"] ?: ""
            }
            val actionRaw = attrs(f.groupValues[1])["action"]?.takeIf { it.isNotBlank() } ?: pageUrl
            return LoginForm(URL(URL(pageUrl), actionRaw).toString(), fields, user, pass)
        }
        return null
    }

    private fun attrs(s: String): Map<String, String> = attrRe.findAll(s).associate { m ->
        val v = m.groupValues[3].ifEmpty { m.groupValues[4].ifEmpty { m.groupValues[5] } }
        m.groupValues[1].lowercase() to decode(v)
    }

    private fun decode(s: String): String {
        if (!s.contains('&')) return s
        return Regex("""&(#x[0-9a-fA-F]+|#\d+|amp|quot|apos|lt|gt|#39);""").replace(s) { m ->
            when (val e = m.groupValues[1]) {
                "amp" -> "&"
                "quot" -> "\""
                "apos", "#39" -> "'"
                "lt" -> "<"
                "gt" -> ">"
                else -> if (e.startsWith("#x")) e.substring(2).toInt(16).toChar().toString()
                else e.substring(1).toInt().toChar().toString()
            }
        }
    }
}
