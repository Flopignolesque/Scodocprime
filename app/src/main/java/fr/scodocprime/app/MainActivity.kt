package fr.scodocprime.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import fr.scodocprime.app.data.Prefs
import fr.scodocprime.app.ui.AppTheme
import fr.scodocprime.app.ui.Biometric
import fr.scodocprime.app.ui.LoginScreen
import fr.scodocprime.app.ui.MainScreen
import fr.scodocprime.app.ui.MainViewModel
import fr.scodocprime.app.ui.Screen
import fr.scodocprime.app.work.Notifier

class MainActivity : FragmentActivity() {
    private lateinit var vm: MainViewModel
    private lateinit var prefs: Prefs
    private var locked by mutableStateOf(false)
    private var authInProgress = false
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannels(this)
        prefs = Prefs(this)
        vm = ViewModelProvider(this)[MainViewModel::class.java]
        if (savedInstanceState == null) locked = lockActive()
        applySecure(prefs.lockEnabled)
        handleIntent(intent)

        setContent {
            AppTheme(vm.theme) {
                LaunchedEffect(vm.lockEnabled) { applySecure(vm.lockEnabled) }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (locked) {
                        LockScreen()
                    } else {
                        when (vm.screen) {
                            Screen.Login -> LoginScreen(vm)
                            Screen.Main -> MainScreen(vm)
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun LockScreen() {
        LaunchedEffect(Unit) { unlock() }
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("ScoDoc Prime est verrouillé", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { unlock() }) { Text("Déverrouiller") }
        }
    }

    private fun lockActive() = prefs.lockEnabled && Biometric.available(this)

    private fun unlock() {
        if (authInProgress) return
        if (!Biometric.available(this)) {
            // Plus de verrouillage d'écran sur le téléphone : on ne bloque pas l'accès.
            locked = false
            return
        }
        authInProgress = true
        Biometric.prompt(
            this, "Déverrouiller ScoDoc Prime",
            onError = { authInProgress = false },
        ) {
            authInProgress = false
            stoppedAt = 0L
            locked = false
        }
    }

    private fun applySecure(on: Boolean) {
        if (on) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStop() {
        super.onStop()
        if (!authInProgress && !isChangingConfigurations) stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (authInProgress || stoppedAt == 0L) return
        val away = SystemClock.elapsedRealtime() - stoppedAt
        if (lockActive() && away >= prefs.lockDelaySec * 1000L) locked = true
        stoppedAt = 0L
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(Notifier.EXTRA_OPEN_NEWS, false) == true) {
            vm.openNews()
            intent.removeExtra(Notifier.EXTRA_OPEN_NEWS)
        }
    }
}
