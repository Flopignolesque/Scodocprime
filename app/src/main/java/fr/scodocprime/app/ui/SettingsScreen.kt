package fr.scodocprime.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import fr.scodocprime.app.BuildConfig
import fr.scodocprime.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val activity = ctx.findActivity()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showCredDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf<String?>(null) }

    // Le mot de passe affiché se recache tout seul après 20 s ou en quittant l'écran.
    LaunchedEffect(revealed) { if (revealed != null) { delay(20_000); revealed = null } }
    DisposableEffect(Unit) { onDispose { revealed = null } }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    fun authThen(title: String, action: () -> Unit) {
        if (activity == null || !Biometric.available(ctx)) {
            vm.message = "Active un verrouillage d'écran (empreinte, visage ou code) sur ton téléphone."
            return
        }
        Biometric.prompt(activity, title, onError = { }, onSuccess = action)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---------------- Compte ----------------
            item { SectionTitle("Compte") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        val user = vm.savedUser
                        if (user == null) {
                            ListItem(
                                headlineContent = { Text("Enregistrer mes identifiants") },
                                supportingContent = { Text("Remplissage auto du CAS et reconnexion automatique. Chiffrés, restent sur le téléphone.") },
                                leadingContent = { Icon(Icons.Default.Person, null) },
                                modifier = Modifier.clickable { showCredDialog = true },
                                colors = transparent(),
                            )
                        } else {
                            ListItem(
                                headlineContent = { Text(user) },
                                supportingContent = { Text("Identifiant enregistré") },
                                leadingContent = { Icon(Icons.Default.Person, null) },
                                colors = transparent(),
                            )
                            ListItem(
                                headlineContent = {
                                    Text(revealed ?: "••••••••••", fontFamily = if (revealed != null) FontFamily.Monospace else null)
                                },
                                supportingContent = { Text("Mot de passe") },
                                leadingContent = { Icon(Icons.Default.Lock, null) },
                                trailingContent = {
                                    IconButton(onClick = {
                                        if (revealed != null) revealed = null
                                        else authThen("Afficher le mot de passe") { revealed = vm.revealPassword() }
                                    }) {
                                        Icon(
                                            painterResource(if (revealed != null) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                                            if (revealed != null) "Masquer" else "Afficher",
                                        )
                                    }
                                },
                                colors = transparent(),
                            )
                            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { authThen("Modifier les identifiants") { showCredDialog = true } }) {
                                    Icon(Icons.Default.Edit, null)
                                    Text(" Modifier")
                                }
                                OutlinedButton(onClick = { authThen("Supprimer les identifiants") { revealed = null; vm.deleteCreds() } }) {
                                    Icon(Icons.Default.Delete, null)
                                    Text(" Supprimer")
                                }
                            }
                        }
                        SwitchRow(
                            "Reconnexion automatique",
                            "Se reconnecte tout seul au CAS quand la session expire (même en arrière-plan)",
                            vm.autoLogin && vm.savedUser != null,
                            enabled = vm.savedUser != null,
                        ) { vm.setAutoLoginEnabled(it) }
                        ListItem(
                            headlineContent = { Text("Se déconnecter") },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
                            modifier = Modifier.clickable { vm.logout() },
                            colors = transparent(),
                        )
                    }
                }
            }

            // ---------------- Sécurité ----------------
            item { SectionTitle("Sécurité") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        SwitchRow(
                            "Verrouiller l'appli",
                            "Empreinte, visage ou code du téléphone à l'ouverture. Masque aussi l'appli dans les apps récentes.",
                            vm.lockEnabled,
                        ) { on ->
                            authThen(if (on) "Activer le verrouillage" else "Désactiver le verrouillage") { vm.setLock(on) }
                        }
                        if (vm.lockEnabled) {
                            ChoiceRow("Redemander après", listOf(0 to "Immédiat", 60 to "1 min", 300 to "5 min"), vm.lockDelay) {
                                vm.setLockDelaySec(it)
                            }
                        }
                    }
                }
            }

            // ---------------- Notifications ----------------
            item { SectionTitle("Notifications") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        SwitchRow("Nouvelles notes", "Une notif dès qu'une note apparaît", vm.notifEnabled) { vm.toggleNotif(it) }
                        if (vm.notifEnabled) {
                            ChoiceRow("Vérifier toutes les", listOf(15 to "15 min", 30 to "30 min", 60 to "1 h"), vm.freq) {
                                vm.setFrequency(it)
                            }
                            SwitchRow("Heures calmes", "Pas de notif la nuit, elles arrivent au réveil", vm.quietEnabled) {
                                vm.setQuiet(it)
                            }
                            if (vm.quietEnabled) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("De ")
                                    HourPicker(vm.quietStart) { vm.setQuiet(true, start = it) }
                                    Text(" à ")
                                    HourPicker(vm.quietEnd) { vm.setQuiet(true, end = it) }
                                }
                            }
                            ListItem(
                                headlineContent = { Text("Vérifier maintenant") },
                                leadingContent = { Icon(Icons.Default.Refresh, null) },
                                modifier = Modifier.clickable { vm.checkNow() },
                                colors = transparent(),
                            )
                        }
                    }
                }
            }

            // ---------------- Apparence ----------------
            item { SectionTitle("Apparence") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    ChoiceRow("Thème", listOf(0 to "Système", 1 to "Clair", 2 to "Sombre"), vm.theme) { vm.setThemeMode(it) }
                }
            }

            // ---------------- Données ----------------
            item { SectionTitle("Données") }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        ListItem(
                            headlineContent = { Text("Exporter les données brutes") },
                            supportingContent = { Text("Fichier JSON de ton relevé (contient tes notes et ton nom) pour améliorer l'appli") },
                            leadingContent = { Icon(Icons.Default.Share, null) },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    val f = vm.exportFile()
                                    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
                                    val send = Intent(Intent.ACTION_SEND)
                                        .setType("application/json")
                                        .putExtra(Intent.EXTRA_STREAM, uri)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    ctx.startActivity(Intent.createChooser(send, "Exporter les données"))
                                }
                            },
                            colors = transparent(),
                        )
                        ListItem(
                            headlineContent = { Text("Adresse ScoDoc") },
                            supportingContent = { Text(vm.baseUrl) },
                            leadingContent = { Icon(Icons.Default.Edit, null) },
                            modifier = Modifier.clickable { showUrlDialog = true },
                            colors = transparent(),
                        )
                        ListItem(
                            headlineContent = { Text("Version") },
                            supportingContent = { Text("ScoDoc Prime ${BuildConfig.VERSION_NAME}") },
                            leadingContent = { Icon(Icons.Default.Info, null) },
                            colors = transparent(),
                        )
                    }
                }
            }
        }
    }

    if (showCredDialog) {
        CredentialsDialog(
            initialUser = vm.savedUser ?: "",
            onDismiss = { showCredDialog = false },
            onSave = { u, p ->
                vm.saveCreds(u, p)
                showCredDialog = false
            },
        )
    }
    if (showUrlDialog) {
        var url by remember { mutableStateOf(vm.baseUrl) }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Adresse ScoDoc étudiant") },
            text = {
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            },
            confirmButton = {
                TextButton(onClick = { showUrlDialog = false; vm.setUrl(url) }) { Text("Valider") }
            },
            dismissButton = { TextButton(onClick = { showUrlDialog = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun transparent() = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) { { Text(subtitle) } } else null,
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
        colors = transparent(),
    )
}

@Composable
private fun ChoiceRow(title: String, options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (v, label) ->
                FilterChip(selected = selected == v, onClick = { onSelect(v) }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun HourPicker(hour: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text("${hour}h", fontWeight = FontWeight.Bold)
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (0..23).forEach { h ->
                DropdownMenuItem(text = { Text("${h}h") }, onClick = { onPick(h); open = false })
            }
        }
    }
}

@Composable
private fun CredentialsDialog(initialUser: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var user by remember { mutableStateOf(initialUser) }
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Identifiants de l'université") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = user, onValueChange = { user = it }, singleLine = true,
                    label = { Text("Identifiant ou email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, singleLine = true,
                    label = { Text("Mot de passe") },
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { show = !show }) {
                            Icon(painterResource(if (show) R.drawable.ic_visibility_off else R.drawable.ic_visibility), null)
                        }
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Chiffré avec le coffre-fort du téléphone, jamais envoyé ailleurs que sur le CAS de la fac.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(user, pass) }, enabled = user.isNotBlank() && pass.isNotEmpty()) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
