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
import fr.scodocprime.app.data.BadCredentialsException
import fr.scodocprime.app.data.CredentialStore
import fr.scodocprime.app.data.Credentials
import fr.scodocprime.app.data.DEFAULT_URL
import fr.scodocprime.app.data.NoteEvent
import fr.scodocprime.app.data.Parser
import fr.scodocprime.app.data.Prefs
import fr.scodocprime.app.data.Releve
import fr.scodocprime.app.data.ScodocClient
import fr.scodocprime.app.data.Semestre
import fr.scodocprime.app.data.Tracker
import fr.scodocprime.app.work.NotesWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class Screen { Login, Main }
enum class Tab { Home, Notes, News, Absences }
enum class SortMode(val label: String) { Code("Code"), Moyenne("Moyenne"), Recent("Dernière note") }

sealed interface Route {
    data class ModuleDetail(val code: String) : Route
    data class UeDetail(val code: String) : Route
    data object Settings : Route
}

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val creds = CredentialStore(prefs)

    // Navigation
    var screen by mutableStateOf(
        if (prefs.cachedSemestres() != null || creds.has()) Screen.Main else Screen.Login
    )
        private set
    var tab by mutableStateOf(Tab.Home)
    var stack by mutableStateOf<List<Route>>(emptyList())
        private set

    // Données
    var semestres by mutableStateOf<List<Semestre>>(emptyList())
        private set
    var selected by mutableStateOf(prefs.selectedSem)
        private set
    var releve by mutableStateOf<Releve?>(null)
        private set
    var events by mutableStateOf(prefs.events())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    val unreadCount: Int get() = events.count { !it.read && it.semId == selected }
    val highlights: Set<String> get() = events.filter { !it.read }.map { it.key }.toSet()

    // Onglet Notes
    var query by mutableStateOf("")
    var filter by mutableStateOf("all")
    var sort by mutableStateOf(SortMode.Code)

    // Onglet Absences : 0 = toutes, 1 = injustifiées, 2 = retards
    var absFilter by mutableStateOf(0)

    // Paramètres
    var notifEnabled by mutableStateOf(prefs.notifEnabled)
        private set
    var freq by mutableStateOf(prefs.freqMinutes)
        private set
    var quietEnabled by mutableStateOf(prefs.quietEnabled)
        private set
    var quietStart by mutableStateOf(prefs.quietStart)
        private set
    var quietEnd by mutableStateOf(prefs.quietEnd)
        private set
    var autoLogin by mutableStateOf(prefs.autoLogin)
        private set
    var lockEnabled by mutableStateOf(prefs.lockEnabled)
        private set
    var lockDelay by mutableStateOf(prefs.lockDelaySec)
        private set
    var theme by mutableStateOf(prefs.theme)
        private set
    var baseUrl by mutableStateOf(prefs.baseUrl)
        private set
    var savedUser by mutableStateOf(creds.user())
        private set

    // Connexion
    var loginMessage by mutableStateOf<String?>(null)
        private set
    private var captured: Credentials? = null
    var askSaveCreds by mutableStateOf<Credentials?>(null)
        private set
    private var checkingLogin = false

    init {
        loadCache()
        if (screen == Screen.Main) refresh()
        if (prefs.notifEnabled) NotesWorker.schedule(app, prefs.freqMinutes)
    }

    // ---------------- Navigation ----------------

    fun open(r: Route) {
        stack = stack + r
    }

    /** @return true si un retour a été consommé */
    fun back(): Boolean {
        if (stack.isNotEmpty()) {
            stack = stack.dropLast(1)
            return true
        }
        if (tab != Tab.Home) {
            tab = Tab.Home
            return true
        }
        return false
    }

    fun openNews() {
        stack = emptyList()
        tab = Tab.News
        events = prefs.events()
    }

    // ---------------- Données ----------------

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

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val client = ScodocClient(app, prefs.baseUrl, creds.forAutoLogin())
                val (sems, raw) = client.semestres()
                prefs.cacheSemestres(raw)
                semestres = sems
                val semId = selected?.takeIf { s -> sems.any { it.id == s } } ?: Parser.current(sems)?.id
                selected = semId
                if (semId != null) {
                    val json = client.releve(semId)
                    prefs.cacheReleve(semId, json.toString())
                    val r = Parser.releve(json)
                    withContext(Dispatchers.IO) { Tracker.update(prefs, semId, r, seenByUser = true) }
                    releve = r
                } else {
                    releve = null
                }
                events = prefs.events()
                prefs.lastCheck = System.currentTimeMillis()
                prefs.authExpiredNotified = false
                screen = Screen.Main
            } catch (e: BadCredentialsException) {
                loginMessage = "Le mot de passe enregistré a été refusé. Reconnecte-toi puis mets-le à jour."
                screen = Screen.Login
            } catch (e: AuthRequiredException) {
                loginMessage = "Session expirée, reconnecte-toi."
                screen = Screen.Login
            } catch (e: Exception) {
                error = "Pas de connexion au serveur (${e.message ?: "erreur réseau"}). Affichage des dernières données."
            } finally {
                loading = false
            }
        }
    }

    fun selectSemestre(id: String) {
        if (id == selected) return
        selected = id
        prefs.selectedSem = id
        stack = emptyList()
        releve = try {
            prefs.cachedReleve(id)?.let { Parser.releve(JSONObject(it)) }
        } catch (_: Exception) {
            null
        }
        refresh()
    }

    // ---------------- Nouveautés ----------------

    fun markRead(key: String) {
        val l = prefs.events().map { if (it.key == key) it.copy(read = true, notified = true) else it }
        prefs.saveEvents(l)
        events = l
    }

    fun markModuleRead(code: String) {
        val cur = prefs.events()
        if (cur.none { !it.read && it.moduleCode == code && it.semId == selected }) return
        val l = cur.map { if (it.moduleCode == code && it.semId == selected) it.copy(read = true, notified = true) else it }
        prefs.saveEvents(l)
        events = l
    }

    fun markAllRead() {
        val l = prefs.events().map { it.copy(read = true, notified = true) }
        prefs.saveEvents(l)
        events = l
    }

    fun clearHistory() {
        prefs.saveEvents(emptyList())
        events = emptyList()
    }

    fun openEvent(e: NoteEvent) {
        markRead(e.key)
        if (e.semId != selected) selectSemestre(e.semId)
        open(Route.ModuleDetail(e.moduleCode))
    }

    // ---------------- Connexion (WebView) ----------------

    private fun host(url: String) = try { Uri.parse(url).host } catch (_: Exception) { null }

    fun onPageLoaded(url: String) {
        if (host(url) == host(prefs.baseUrl) && !url.contains("doAuth") && !url.contains("ticket=")) checkLogin(false)
    }

    fun onCredentialsTyped(user: String, pass: String) {
        if (user.isNotBlank() && pass.isNotEmpty()) captured = Credentials(user.trim(), pass)
    }

    /** Script JS qui remplit (et éventuellement envoie) le formulaire CAS avec les identifiants enregistrés. */
    fun fillScript(submit: Boolean): String? {
        val c = creds.load() ?: return null
        return """
            (function(u,p,submit){
              var pw=document.querySelector('input[type=password]'); if(!pw) return 'nopw';
              var f=pw.form||document;
              var us=f.querySelector('input[name=username],input#username,input[type=email],input[name=user],input[name=login],input[type=text]');
              function set(el,v){ if(!el) return; var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');
                d.set.call(el,v); el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); }
              set(us,u); set(pw,p);
              if(submit && pw.form){ var b=pw.form.querySelector('[type=submit]'); if(b){b.click();} else if(pw.form.requestSubmit){pw.form.requestSubmit();} else {pw.form.submit();} }
              return 'ok';
            })(${JSONObject.quote(c.user)},${JSONObject.quote(c.pass)},$submit);
        """.trimIndent()
    }

    val hasCreds: Boolean get() = savedUser != null

    fun checkLogin(manual: Boolean) {
        if (checkingLogin) return
        checkingLogin = true
        if (manual) loginMessage = "Vérification…"
        viewModelScope.launch {
            try {
                CookieManager.getInstance().flush()
                ScodocClient(app, prefs.baseUrl).semestres()
                loginMessage = null
                screen = Screen.Main
                stack = emptyList()
                prefs.authExpiredNotified = false
                val c = captured
                if (c != null && creds.load() != c) askSaveCreds = c
                captured = null
                refresh()
            } catch (e: AuthRequiredException) {
                if (manual) loginMessage = "Pas encore connecté : termine la connexion dans la page."
            } catch (e: Exception) {
                if (manual) loginMessage = "Impossible de lire les notes : ${e.message ?: "erreur"}."
            } finally {
                checkingLogin = false
            }
        }
    }

    fun dismissSaveCreds(save: Boolean) {
        askSaveCreds?.let { if (save) saveCreds(it.user, it.pass) }
        askSaveCreds = null
    }

    fun logout() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        stack = emptyList()
        tab = Tab.Home
        loginMessage = null
        screen = Screen.Login
    }

    // ---------------- Identifiants ----------------

    fun saveCreds(user: String, pass: String) {
        creds.save(Credentials(user.trim(), pass))
        savedUser = creds.user()
        prefs.authExpiredNotified = false
        message = "Identifiants enregistrés sur le téléphone (chiffrés)"
    }

    fun deleteCreds() {
        creds.clear()
        savedUser = null
        message = "Identifiants supprimés"
    }

    /** À n'appeler qu'après une authentification biométrique réussie. */
    fun revealPassword(): String? = creds.load()?.pass

    // ---------------- Paramètres ----------------

    fun toggleNotif(v: Boolean) {
        prefs.notifEnabled = v; notifEnabled = v
        if (v) NotesWorker.schedule(app, freq) else NotesWorker.cancel(app)
    }

    fun setFrequency(m: Int) {
        prefs.freqMinutes = m; freq = m
        if (notifEnabled) NotesWorker.schedule(app, m)
    }

    fun setQuiet(enabled: Boolean, start: Int = quietStart, end: Int = quietEnd) {
        prefs.quietEnabled = enabled; prefs.quietStart = start; prefs.quietEnd = end
        quietEnabled = enabled; quietStart = start; quietEnd = end
    }

    fun setAutoLoginEnabled(v: Boolean) {
        prefs.autoLogin = v; autoLogin = v
    }

    fun setLock(v: Boolean) {
        prefs.lockEnabled = v; lockEnabled = v
    }

    fun setLockDelaySec(s: Int) {
        prefs.lockDelaySec = s; lockDelay = s
    }

    fun setThemeMode(t: Int) {
        prefs.theme = t; theme = t
    }

    fun setUrl(input: String) {
        var u = input.trim().ifEmpty { DEFAULT_URL }
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        u = u.trimEnd('/').substringBefore("/services/").removeSuffix("/index.php").removeSuffix("/index.html")
        if (u == prefs.baseUrl) return
        prefs.baseUrl = u
        baseUrl = u
        prefs.selectedSem = null
        selected = null
        semestres = emptyList()
        releve = null
        logout()
    }

    fun checkNow() {
        NotesWorker.runNow(app)
        message = "Vérification lancée en arrière-plan"
    }

    /** Exporte les données brutes (pour adapter l'affichage au format exact de la passerelle). */
    suspend fun exportFile(): File = withContext(Dispatchers.IO) {
        val o = JSONObject()
        o.put("url", prefs.baseUrl)
        o.put("exportedAt", System.currentTimeMillis())
        prefs.cachedSemestres()?.let { o.put("semestresEtudiant", it) }
        selected?.let { s -> prefs.cachedReleve(s)?.let { o.put("relevéEtudiant", JSONObject(it)) } }
        val dir = File(app.cacheDir, "export").apply { mkdirs() }
        File(dir, "scodoc-donnees.json").apply { writeText(o.toString(2)) }
    }
}
