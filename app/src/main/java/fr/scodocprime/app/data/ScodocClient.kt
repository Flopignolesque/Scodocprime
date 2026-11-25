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

open class AuthRequiredException(msg: String = "Connexion requise") : Exception(msg)
class BadCredentialsException : AuthRequiredException("Identifiants refusés par le CAS")

/**
 * Client de la passerelle "ScoDoc étudiant" (services/data.php).
 * Les cookies sont partagés avec la WebView de connexion (CAS). Si des identifiants sont fournis,
 * le client se reconnecte tout seul au CAS quand la session expire.
 */
class ScodocClient(context: Context, base: String, private val creds: Credentials? = null) {
    private val base = base.trimEnd('/')
    private val cookies: CookieManager = CookieManager.getInstance().apply { setAcceptCookie(true) }
    private val userAgent: String = try {
        WebSettings.getDefaultUserAgent(context)
    } catch (e: Exception) {
        "Mozilla/5.0 (Linux; Android) ScodocPrime"
    }

    private data class Resp(val code: Int, val body: String, val url: String)

    suspend fun semestres(): Pair<List<Semestre>, String> = withContext(Dispatchers.IO) {
        val raw = getData("semestresEtudiant")
        Parser.semestres(raw) to raw.toString()
    }

    suspend fun releve(semId: String): JSONObject = withContext(Dispatchers.IO) {
        getData("relevéEtudiant", "&semestre=" + enc(semId)) as? JSONObject
            ?: throw IOException("Réponse inattendue du serveur")
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun parseJson(body: String): Any? {
        val t = body.trim().removePrefix("﻿")
        return try {
            when {
                t.startsWith("{") -> JSONObject(t)
                t.startsWith("[") -> JSONArray(t)
                else -> null
            }
        } catch (e: JSONException) {
            null
        }
    }

    private fun getData(q: String, extra: String = ""): Any {
        val url = "$base/services/data.php?q=${enc(q)}$extra"
        var attempts = 0
        while (true) {
            val r = request(url)
            val parsed = parseJson(r.body)
            if (parsed is JSONObject && parsed.has("redirect")) {
                // Session passerelle expirée : on passe par le CAS (cookie TGC encore valide, ou reconnexion).
                if (attempts++ >= 2) throw AuthRequiredException()
                val page = request(URL(URL("$base/"), parsed.getString("redirect")).toString())
                if (CasLogin.findForm(page.body, page.url) != null) login(page)
                continue
            }
            if (parsed is JSONObject && (parsed.has("erreur") || parsed.has("error"))) {
                val msg = parsed.optString("erreur", parsed.optString("error", "Erreur"))
                if (msg.contains("auth", true) || msg.contains("connect", true)) {
                    if (attempts++ >= 2) throw AuthRequiredException()
                    val page = request("$base/services/doAuth.php?href=" + enc("$base/"))
                    if (CasLogin.findForm(page.body, page.url) != null) login(page)
                    continue
                }
                throw IOException(msg)
            }
            if (parsed != null) return parsed
            if (CasLogin.findForm(r.body, r.url) != null && attempts++ < 2) {
                login(r)
                continue
            }
            if (r.code == 401 || r.code == 403 || r.body.trimStart().startsWith("<")) throw AuthRequiredException()
            throw IOException("Réponse inattendue du serveur (${r.code})")
        }
    }

    /** Remplit et envoie le formulaire CAS avec les identifiants enregistrés. */
    private fun login(page: Resp) {
        val c = creds ?: throw AuthRequiredException()
        val form = CasLogin.findForm(page.body, page.url) ?: throw AuthRequiredException()
        val fields = LinkedHashMap(form.fields)
        fields[form.userField] = c.user
        fields[form.passField] = c.pass
        val post = fields.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }
        val res = request(form.action, post, referer = page.url)
        if (CasLogin.findForm(res.body, res.url) != null) throw BadCredentialsException()
    }

    /** Requête HTTP avec suivi manuel des redirections (multi-domaines, cookies gérés à chaque saut). */
    private fun request(startUrl: String, postBody: String? = null, referer: String? = null): Resp {
        var url = startUrl
        var body = postBody
        repeat(20) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 15_000
                c.readTimeout = 25_000
                c.setRequestProperty("User-Agent", userAgent)
                c.setRequestProperty("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
                c.setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9")
                cookies.getCookie(url)?.let { c.setRequestProperty("Cookie", it) }
                val b = body
                if (b != null) {
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    if (referer != null) {
                        c.setRequestProperty("Referer", referer)
                        val ru = URL(referer)
                        c.setRequestProperty("Origin", "${ru.protocol}://${ru.authority}")
                    }
                    c.outputStream.use { it.write(b.toByteArray(Charsets.UTF_8)) }
                }
                val code = c.responseCode
                c.headerFields.forEach { (k, v) ->
                    if (k != null && k.equals("Set-Cookie", ignoreCase = true)) v.forEach { cookies.setCookie(url, it) }
                }
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: return Resp(code, "", url)
                    url = URL(URL(url), loc).toString()
                    if (code != 307 && code != 308) body = null
                    return@repeat
                }
                val stream = if (code >= 400) c.errorStream else c.inputStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                cookies.flush()
                return Resp(code, text, url)
            } finally {
                c.disconnect()
            }
        }
        throw IOException("Trop de redirections")
    }
}
