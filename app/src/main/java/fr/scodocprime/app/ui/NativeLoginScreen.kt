package fr.scodocprime.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.scodocprime.app.R

/** Point d'entrée de la connexion : écran natif, ou le site en secours. */
@Composable
fun LoginScreen(vm: MainViewModel) {
    if (vm.webLogin) WebLoginScreen(vm) else NativeLoginScreen(vm)
}

@Composable
private fun NativeLoginScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    var user by rememberSaveable { mutableStateOf(vm.savedUser ?: "") }
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }

    BackHandler { ctx.findActivity()?.finish() }

    fun submit() {
        focus.clearFocus()
        vm.loginWithCredentials(user, pass)
    }

    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_stat_note), null, tint = cs.onPrimary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("ScoDoc Prime", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = cs.primary)
        Text("Tes notes de l'IUT de Blagnac", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))

        vm.loginMessage?.let {
            InfoCard(it)
            Spacer(Modifier.height(16.dp))
        }

        if (vm.hasCreds) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cs.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Identifiants enregistrés", style = MaterialTheme.typography.labelMedium, color = cs.onPrimaryContainer)
                    Text(vm.savedUser ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = cs.onPrimaryContainer)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { focus.clearFocus(); vm.loginWithSaved() },
                        enabled = !vm.loginBusy,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) { Text("Continuer avec ce compte") }
                }
            }
            Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f))
                Text("  ou un autre compte  ", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                HorizontalDivider(Modifier.weight(1f))
            }
        }

        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("Identifiant ou email de l'université") },
            leadingIcon = { Icon(Icons.Default.Person, null) },
            singleLine = true,
            enabled = !vm.loginBusy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Mot de passe") },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            trailingIcon = {
                IconButton(onClick = { show = !show }) {
                    Icon(
                        painterResource(if (show) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                        if (show) "Masquer" else "Afficher",
                    )
                }
            },
            singleLine = true,
            enabled = !vm.loginBusy,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )

        vm.loginError?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = cs.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(20.dp))
        val enabled = !vm.loginBusy && user.isNotBlank() && pass.isNotEmpty()
        if (vm.hasCreds) {
            OutlinedButton(onClick = { submit() }, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                LoginButtonContent(vm.loginBusy)
            }
        } else {
            Button(onClick = { submit() }, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                LoginButtonContent(vm.loginBusy)
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { vm.webLogin = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Problème ? Se connecter via le site")
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Tes identifiants sont envoyés uniquement au CAS de l'université. " +
                    "Si tu les enregistres, ils restent chiffrés sur ce téléphone.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
private fun LoginButtonContent(busy: Boolean) {
    if (busy) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text("Connexion…")
    } else {
        Text("Se connecter")
    }
}
