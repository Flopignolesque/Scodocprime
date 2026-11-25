package fr.scodocprime.app.ui

import android.app.Application
import android.net.Uri
import android.webkit.CookieManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.scodocprime.app.data.AuthRequiredException
import fr.scodocprime.app.data.Parser
import fr.scodocprime.app.data.Prefs
import fr.scodocprime.app.data.Releve
import fr.scodocprime.app.data.ScodocClient
import fr.scodocprime.app.data.Semestre
import fr.scodocprime.app.data.Tracker
import fr.scodocprime.app.work.NotesWorker
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class Screen { Setup, Login, Home }

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)

    var screen by mutableStateOf(if (prefs.baseUrl == null) Screen.Setup else Screen.Home)
        private set
    var semestres by mutableStateOf<List<Semestre>>(emptyList())
        private set
    var selected by mutableStateOf(prefs.selectedSem)
        private set
    var releve by mutableStateOf<Releve?>(null)
        private set
    var highlights by mutableStateOf<Set<String>>(emptySet())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var notifEnabled by mutableStateOf(prefs.notifEnabled)
        private set
    var loginMessage by mutableStateOf<String?>(null)
        private set
    private var checkingLogin = false

    init {
        if (screen == Screen.Home) {
            loadCache()
            refresh()
            if (prefs.notifEnabled) NotesWorker.schedule(app)
        }
    }

    private fun loadCache() {
        try {
            prefs.cachedSemestres()?.let { raw ->
                val t = raw.trim()
                semestres = Parser.semestres(if (t.startsWith("[")) JSONArray(t) else JSONObject(t))
            }
            val sem = selected ?: Parser.current(semestres)?.id
            sem?.let { prefs.cachedReleve(it) }?.let { releve = Parser.releve(JSONObject(it)) }
        } catch (_: Exception) {
        }
    }

    fun setUrl(input: String) {
        var u = input.trim()
        if (u.isEmpty()) return
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        u = u.trimEnd('/')
        // Si l'utilisateur colle une URL de page (…/index.php, …/services/…), on garde la racine.
        u = u.substringBefore("/services/").removeSuffix("/index.php").removeSuffix("/index.html")
        if (u != prefs.baseUrl) {
            prefs.selectedSem = null
            selected = null
            semestres = emptyList()
            releve = null
        }
        prefs.baseUrl = u
        loginMessage = null
        screen = Screen.Login
    }

    fun backToSetup() {
        screen = Screen.Setup
    }

    private fun host(url: String) = try { Uri.parse(url).host } catch (_: Exception) { null }

    fun onPageLoaded(url: String) {
        val base = prefs.baseUrl ?: return
        if (host(url) == host(base) && !url.contains("doAuth") && !url.contains("ticket=")) checkLogin(false)
    }

    fun checkLogin(manual: Boolean) {
        val base = prefs.baseUrl ?: return
        if (checkingLogin) return
        checkingLogin = true
        if (manual) loginMessage = "Vérification…"
        viewModelScope.launch {
            try {
                CookieManager.getInstance().flush()
                ScodocClient(app, base).semestres()
                loginMessage = null
                screen = Screen.Home
                prefs.authExpiredNotified = false
                if (prefs.notifEnabled) NotesWorker.schedule(app)
                refresh()
            } catch (e: AuthRequiredException) {
                if (manual) loginMessage = "Pas encore connecté : termine la connexion dans la page."
            } catch (e: Exception) {
                if (manual) loginMessage = "Impossible de lire les notes : ${e.message ?: "erreur"}. Vérifie l'adresse."
            } finally {
                checkingLogin = false
            }
        }
    }

    fun refresh() {
        val base = prefs.baseUrl ?: return
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val client = ScodocClient(app, base)
                val (sems, raw) = client.semestres()
                prefs.cacheSemestres(raw)
                semestres = sems
                val semId = selected?.takeIf { s -> sems.any { it.id == s } } ?: Parser.current(sems)?.id
                selected = semId
                if (semId != null) {
                    val json = client.releve(semId)
                    prefs.cacheReleve(semId, json.toString())
                    val r = Parser.releve(json)
                    val news = Tracker.update(prefs, semId, r).orEmpty()
                    highlights = highlights + prefs.highlights() + news.map { it.eval.key }
                    prefs.setHighlights(emptySet())
                    prefs.lastCheck = System.currentTimeMillis()
                    releve = r
                } else {
                    releve = null
                }
            } catch (e: AuthRequiredException) {
                loginMessage = "Session expirée, reconnecte-toi."
                screen = Screen.Login
            } catch (e: Exception) {
                error = e.message ?: "Erreur réseau"
            } finally {
                loading = false
            }
        }
    }

    fun selectSemestre(id: String) {
        if (id == selected) return
        selected = id
        prefs.selectedSem = id
        releve = try { prefs.cachedReleve(id)?.let { Parser.releve(JSONObject(it)) } } catch (_: Exception) { null }
        refresh()
    }

    fun toggleNotif(enabled: Boolean) {
        prefs.notifEnabled = enabled
        notifEnabled = enabled
        if (enabled) NotesWorker.schedule(app) else NotesWorker.cancel(app)
    }

    fun checkNow() = NotesWorker.runNow(app)

    fun logout() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        releve = null
        semestres = emptyList()
        loginMessage = null
        screen = Screen.Login
    }
}
