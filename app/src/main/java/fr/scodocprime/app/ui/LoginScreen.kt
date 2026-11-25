package fr.scodocprime.app.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Capture les identifiants tapés dans le formulaire CAS (pour proposer de les enregistrer). */
private class CaptureBridge(private val vm: MainViewModel) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun capture(user: String?, pass: String?) {
        if (user != null && pass != null) main.post { vm.onCredentialsTyped(user, pass) }
    }
}

private const val HOOK_JS = """
(function(){
  if(window.__spHook) return; window.__spHook=1;
  function grab(f){ try{ var pw=f.querySelector('input[type=password]'); if(!pw) return;
    var us=f.querySelector('input[name=username],input#username,input[type=email],input[name=user],input[name=login],input[type=text]');
    ScodocPrime.capture(us?us.value:'', pw.value);}catch(x){} }
  document.addEventListener('submit', function(e){ grab(e.target); }, true);
  document.addEventListener('click', function(e){ var t=e.target; if(t && t.closest){ var b=t.closest('[type=submit],button'); if(b && b.form) grab(b.form);} }, true);
})();
"""

private const val HAS_PASSWORD_JS = "(function(){return !!document.querySelector('input[type=password]');})();"

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(vm: MainViewModel) {
    var progress by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var autoSubmitted by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val activity = ctx.findActivity()

    BackHandler {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else activity?.finish()
    }

    fun fill(submit: Boolean) {
        val js = vm.fillScript(submit) ?: return
        webView?.evaluateJavascript(js, null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Connexion")
                        Text(
                            vm.baseUrl.removePrefix("https://"), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.loadUrl(vm.baseUrl) }) { Icon(Icons.Default.Refresh, "Recharger") }
                    if (vm.hasCreds) {
                        TextButton(onClick = { fill(false) }) {
                            Icon(Icons.Default.Lock, null)
                            Text(" Remplir")
                        }
                    }
                    TextButton(onClick = { vm.checkLogin(true) }) { Text("Terminé") }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            AndroidView(
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.saveFormData = false
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this, true)
                        addJavascriptInterface(CaptureBridge(vm), "ScodocPrime")
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                CookieManager.getInstance().flush()
                                view.evaluateJavascript(HOOK_JS, null)
                                // Page CAS : on remplit avec les identifiants enregistrés (envoi auto une seule fois).
                                view.evaluateJavascript(HAS_PASSWORD_JS) { has ->
                                    if (has == "true" && vm.hasCreds) {
                                        val submit = vm.autoLogin && !autoSubmitted
                                        if (submit) autoSubmitted = true
                                        vm.fillScript(submit)?.let { view.evaluateJavascript(it, null) }
                                    }
                                }
                                if (url != null) vm.onPageLoaded(url)
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        loadUrl(vm.baseUrl)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (progress in 1..99) {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
            vm.loginMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
                ) {
                    Text(msg, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
