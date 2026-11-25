package fr.scodocprime.app.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AuthRequiredException : Exception("Connexion requise")

/**
 * Client de la passerelle "ScoDoc étudiant" (services/data.php).
 * Les cookies sont partagés avec la WebView de connexion (CAS), ce qui permet
 * de rester connecté et de vérifier les notes en arrière-plan.
 */
class ScodocClient(context: Context, base: String) {
    private val base = base.trimEnd('/')
    private val cookies: CookieManager = CookieManager.getInstance().apply { setAcceptCookie(true) }
    private val userAgent: String = try {
        WebSettings.getDefaultUserAgent(context)
    } catch (e: Exception) {
        "Mozilla/5.0 (Linux; Android) ScodocPrime"
    }

    suspend fun semestres(): Pair<List<Semestre>, String> = withContext(Dispatchers.IO) {
        val raw = getData("semestresEtudiant")
        Parser.semestres(raw) to raw.toString()
    }

    suspend fun releve(semId: String): JSONObject = withContext(Dispatchers.IO) {
        getData("relevéEtudiant", "&semestre=" + enc(semId)) as? JSONObject
            ?: throw IOException("Réponse inattendue du serveur")
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun getData(q: String, extra: String = ""): Any {
        val url = "$base/services/data.php?q=${enc(q)}$extra"
        var redirects = 0
        while (true) {
            val (code, body) = fetch(url)
            val t = body.trim().removePrefix("﻿")
            val parsed: Any? = try {
                when {
                    t.startsWith("{") -> JSONObject(t)
                    t.startsWith("[") -> JSONArray(t)
                    else -> null
                }
            } catch (e: JSONException) {
                null
            }
            if (parsed is JSONObject && parsed.has("redirect")) {
                // Session passerelle expirée : on tente de la renouveler via le ticket CAS (cookie TGC).
                if (redirects++ >= 1) throw AuthRequiredException()
                fetch(URL(URL("$base/"), parsed.getString("redirect")).toString())
                continue
            }
            if (parsed is JSONObject && (parsed.has("erreur") || parsed.has("error"))) {
                val msg = parsed.optString("erreur", parsed.optString("error", "Erreur"))
                if (msg.contains("auth", true) || msg.contains("connect", true)) throw AuthRequiredException()
                throw IOException(msg)
            }
            if (parsed != null) return parsed
            if (code == 401 || code == 403 || t.startsWith("<")) throw AuthRequiredException()
            throw IOException("Réponse inattendue du serveur ($code)")
        }
    }

    /** GET avec suivi manuel des redirections (multi-domaines, cookies gérés à chaque saut). */
    private fun fetch(startUrl: String): Pair<Int, String> {
        var url = startUrl
        repeat(15) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 15_000
                c.readTimeout = 25_000
                c.setRequestProperty("User-Agent", userAgent)
                c.setRequestProperty("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
                cookies.getCookie(url)?.let { c.setRequestProperty("Cookie", it) }
                val code = c.responseCode
                c.headerFields.forEach { (k, v) ->
                    if (k != null && k.equals("Set-Cookie", ignoreCase = true)) v.forEach { cookies.setCookie(url, it) }
                }
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: return code to ""
                    url = URL(URL(url), loc).toString()
                    return@repeat
                }
                val stream = if (code >= 400) c.errorStream else c.inputStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                cookies.flush()
                return code to body
            } finally {
                c.disconnect()
            }
        }
        throw IOException("Trop de redirections")
    }
}
