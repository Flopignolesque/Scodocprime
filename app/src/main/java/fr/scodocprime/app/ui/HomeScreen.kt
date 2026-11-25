package fr.scodocprime.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import fr.scodocprime.app.data.Evaluation
import fr.scodocprime.app.data.Module
import fr.scodocprime.app.data.Releve
import fr.scodocprime.app.data.Tracker
import fr.scodocprime.app.data.Ue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    var showSettings by remember { mutableStateOf(false) }
    NotificationPermission(vm.notifEnabled)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mes notes", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "Actualiser") }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Paramètres") }
                },
            )
        },
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
                if (vm.semestres.size > 1) {
                    item {
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
                val r = vm.releve
                if (r == null) {
                    if (!vm.loading) item { EmptyCard() }
                } else {
                    item { HeaderCard(r) }
                    if (r.ues.isNotEmpty()) {
                        item { SectionTitle("Compétences") }
                        items(r.ues, key = { "ue_" + it.code }) { UeCard(it) }
                    }
                    if (r.ressources.isNotEmpty()) {
                        item { SectionTitle("Ressources") }
                        items(r.ressources, key = { "r_" + it.code }) { ModuleCard(it, vm.highlights) }
                    }
                    if (r.saes.isNotEmpty()) {
                        item { SectionTitle("SAÉ") }
                        items(r.saes, key = { "s_" + it.code }) { ModuleCard(it, vm.highlights) }
                    }
                    item { LastUpdate(vm.prefs.lastCheck) }
                }
            }
        }
    }

    if (showSettings) SettingsSheet(vm) { showSettings = false }
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

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, start = 4.dp),
    )
}

@Composable
private fun ErrorCard(msg: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun EmptyCard() {
    Card(Modifier.fillMaxWidth()) {
        Text("Aucun relevé disponible pour l'instant. Tire vers le bas pour actualiser.",
            modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun HeaderCard(r: Releve) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary)))
            .padding(22.dp),
    ) {
        Column {
            r.nom?.let { Text(it, color = cs.onPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            val sub = listOfNotNull(r.titreSemestre, r.formation).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, color = cs.onPrimary.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            Text("Moyenne générale", color = cs.onPrimary.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(fmt(r.moyenne?.value), color = cs.onPrimary, fontSize = 52.sp, fontWeight = FontWeight.Black, lineHeight = 56.sp)
                Text(" /20", color = cs.onPrimary.copy(alpha = 0.8f), fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                r.rang?.let { Stat("Rang", it, Modifier.weight(1f)) }
                r.moyenne?.moy?.let { Stat("Promo", fmt(it), Modifier.weight(1f)) }
                r.ects?.let { Stat("ECTS", it, Modifier.weight(1f)) }
                if (r.absTotal != null) Stat("Abs. (inj.)", "${r.absTotal} (${r.absInjust ?: 0})", Modifier.weight(1f))
            }
            if (r.moyenne?.min != null || r.moyenne?.max != null) {
                Spacer(Modifier.height(8.dp))
                Text("Min ${fmt(r.moyenne?.min)} · Max ${fmt(r.moyenne?.max)}", color = cs.onPrimary.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall)
            }
            r.situation?.let {
                Spacer(Modifier.height(8.dp))
                Text(it.replace(Regex("<[^>]*>"), " ").trim(), color = cs.onPrimary.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(value, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun parseColor(c: String?, fallback: Color): Color = try {
    if (c == null) fallback else Color(android.graphics.Color.parseColor(c.trim()))
} catch (_: Exception) {
    fallback
}

@Composable
private fun UeCard(ue: Ue) {
    val color = parseColor(ue.color, MaterialTheme.colorScheme.primary)
    val v = ue.moyenne?.numeric
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(8.dp).fillMaxHeight().background(color))
            Column(Modifier.weight(1f).padding(16.dp)) {
                Text(ue.code, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
                Text(ue.titre, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(
                    ue.rang?.let { "Rang $it" },
                    ue.ects?.let { "ECTS $it" },
                    ue.moyenne?.moy?.let { "Promo ${fmt(it)}" },
                    ue.bonus?.takeIf { (it.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0 }?.let { "Bonus +${fmt(it)}" },
                    ue.malus?.takeIf { (it.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0 }?.let { "Malus −${fmt(it)}" },
                )
                if (details.isNotEmpty()) {
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(Modifier.padding(16.dp).align(Alignment.CenterVertically)) {
                Text(fmt(ue.moyenne?.value), fontSize = 26.sp, fontWeight = FontWeight.Black, color = noteColor(v))
            }
        }
    }
}

@Composable
private fun ModuleCard(m: Module, highlights: Set<String>) {
    val hasNew = m.evaluations.any { it.key in highlights }
    var expanded by rememberSaveable(m.code) { mutableStateOf(hasNew) }
    val graded = m.evaluations.count { e -> e.note?.value?.let { Tracker.isReal(it) } == true }
    val avg = m.moyenneIndicative
    Card(
        Modifier.fillMaxWidth(),
        border = if (hasNew) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold)
                    if (hasNew) {
                        Spacer(Modifier.width(8.dp))
                        NewBadge()
                    }
                }
                Text(m.titre, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (m.evaluations.isEmpty()) "Aucune évaluation" else "$graded / ${m.evaluations.size} notes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (avg != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmt(avg), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = noteColor(avg))
                    Text("moy. indic.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(4.dp))
            }
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
        }
        AnimatedVisibility(expanded && m.evaluations.isNotEmpty()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                m.evaluations.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    EvaluationRow(e, e.key in highlights)
                }
            }
        }
    }
}

@Composable
private fun NewBadge() {
    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50)) {
        Text("NOUVEAU", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun EvaluationRow(e: Evaluation, isNew: Boolean) {
    val note = e.note
    val real = note?.value?.let { Tracker.isReal(it) } == true
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false))
                if (isNew) {
                    Spacer(Modifier.width(6.dp))
                    NewBadge()
                }
            }
            val meta = listOfNotNull(fmtDate(e.date), e.coef?.let { "coef ${fmt(it)}" }).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (real && (note?.moy != null || note?.min != null)) {
                Text(
                    "Promo : moy ${fmt(note?.moy)} · min ${fmt(note?.min)} · max ${fmt(note?.max)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        val v = note?.numeric
        val c = if (real) noteColor(v) else MaterialTheme.colorScheme.outline
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(c.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (real) fmt(note?.value) else "—", color = c, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
    }
}

@Composable
private fun LastUpdate(ts: Long) {
    if (ts <= 0) return
    Text(
        "Mis à jour " + DateUtils.getRelativeTimeSpanString(ts, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Paramètres", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            ListItem(
                headlineContent = { Text("Notifications de nouvelles notes") },
                supportingContent = { Text("Vérification toutes les 30 min environ") },
                leadingContent = { Icon(Icons.Default.Notifications, null) },
                trailingContent = { Switch(checked = vm.notifEnabled, onCheckedChange = { vm.toggleNotif(it) }) },
            )
            ListItem(
                headlineContent = { Text("Vérifier maintenant en arrière-plan") },
                leadingContent = { Icon(Icons.Default.Refresh, null) },
                modifier = Modifier.clickable { vm.checkNow(); onDismiss() },
            )
            ListItem(
                headlineContent = { Text("Changer d'adresse ScoDoc") },
                supportingContent = { Text(vm.prefs.baseUrl ?: "") },
                leadingContent = { Icon(Icons.Default.Edit, null) },
                modifier = Modifier.clickable { onDismiss(); vm.backToSetup() },
            )
            ListItem(
                headlineContent = { Text("Se déconnecter") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
                modifier = Modifier.clickable { onDismiss(); vm.logout() },
            )
            Spacer(Modifier.size(8.dp))
        }
    }
}
