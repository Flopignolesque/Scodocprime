package fr.scodocprime.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun MainScreen(vm: MainViewModel) {
    BackHandler(enabled = vm.stack.isNotEmpty() || vm.tab != Tab.Home) { vm.back() }
    NotificationPermission(vm.notifEnabled)

    when (val r = vm.stack.lastOrNull()) {
        is Route.ModuleDetail -> ModuleDetailScreen(vm, r.code)
        is Route.UeDetail -> UeDetailScreen(vm, r.code)
        Route.Settings -> SettingsScreen(vm)
        null -> Tabs(vm)
    }

    vm.askSaveCreds?.let { c ->
        AlertDialog(
            onDismissRequest = { vm.dismissSaveCreds(false) },
            title = { Text("Enregistrer tes identifiants ?") },
            text = {
                Text(
                    "Compte : ${c.user}\n\nIls sont chiffrés et restent uniquement sur ce téléphone. " +
                        "Ça permet de te reconnecter tout seul quand la session expire, pour ne rater aucune note.",
                )
            },
            confirmButton = { TextButton(onClick = { vm.dismissSaveCreds(true) }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { vm.dismissSaveCreds(false) }) { Text("Non merci") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Tabs(vm: MainViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (vm.tab) {
                            Tab.Home -> "ScoDoc Prime"
                            Tab.Notes -> "Notes"
                            Tab.News -> "Nouveautés"
                            Tab.Absences -> "Absences"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    if (vm.tab == Tab.News && vm.unreadCount > 0) {
                        IconButton(onClick = { vm.markAllRead() }) { Icon(Icons.Default.Done, "Tout marquer comme lu") }
                    }
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "Actualiser") }
                    IconButton(onClick = { vm.open(Route.Settings) }) { Icon(Icons.Default.Settings, "Paramètres") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = vm.tab == Tab.Home, onClick = { vm.tab = Tab.Home },
                    icon = { Icon(Icons.Default.Home, null) }, label = { Text("Accueil") },
                )
                NavigationBarItem(
                    selected = vm.tab == Tab.Notes, onClick = { vm.tab = Tab.Notes },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("Notes") },
                )
                NavigationBarItem(
                    selected = vm.tab == Tab.News, onClick = { vm.tab = Tab.News },
                    icon = {
                        BadgedBox(badge = {
                            val n = vm.unreadCount
                            if (n > 0) Badge { Text(if (n > 99) "99+" else "$n") }
                        }) { Icon(Icons.Default.Notifications, null) }
                    },
                    label = { Text("Nouveautés") },
                )
                NavigationBarItem(
                    selected = vm.tab == Tab.Absences, onClick = { vm.tab = Tab.Absences },
                    icon = { Icon(Icons.Default.DateRange, null) }, label = { Text("Absences") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = vm.loading,
            onRefresh = { vm.refresh() },
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                vm.error?.let { msg -> item { ErrorCard(msg) } }
                if (vm.tab != Tab.News) semestreChips(vm)
                when (vm.tab) {
                    Tab.Home -> homeTab(vm)
                    Tab.Notes -> notesTab(vm)
                    Tab.News -> newsTab(vm)
                    Tab.Absences -> absencesTab(vm)
                }
            }
        }
    }
}

private fun LazyListScope.semestreChips(vm: MainViewModel) {
    if (vm.semestres.size <= 1) return
    item(key = "sems") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.semestres, key = { it.id }) { s ->
                FilterChip(
                    selected = s.id == vm.selected,
                    onClick = { vm.selectSemestre(s.id) },
                    label = { Text(s.titre, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

@Composable
private fun NotificationPermission(enabled: Boolean) {
    if (Build.VERSION.SDK_INT < 33 || !enabled) return
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
