package fr.scodocprime.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.scodocprime.app.ui.AppTheme
import fr.scodocprime.app.ui.HomeScreen
import fr.scodocprime.app.ui.LoginScreen
import fr.scodocprime.app.ui.MainViewModel
import fr.scodocprime.app.ui.Screen
import fr.scodocprime.app.ui.SetupScreen
import fr.scodocprime.app.work.Notifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannels(this)
        setContent {
            AppTheme {
                val vm: MainViewModel = viewModel()
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (vm.screen) {
                        Screen.Setup -> SetupScreen(vm)
                        Screen.Login -> LoginScreen(vm)
                        Screen.Home -> HomeScreen(vm)
                    }
                }
            }
        }
    }
}
