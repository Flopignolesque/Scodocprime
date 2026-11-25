package fr.scodocprime.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scodocprime.app.data.Absence
import fr.scodocprime.app.data.Module
import fr.scodocprime.app.data.NoteEvent
import fr.scodocprime.app.data.Releve
import fr.scodocprime.app.data.Ue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// =====================================================================
// Accueil
// =====================================================================

fun LazyListScope.homeTab(vm: MainViewModel) {
    val r = vm.releve
    if (r == null) {
        if (!vm.loading) item(key = "empty") { InfoCard("Aucun relevé pour l'instant. Tire vers le bas pour actualiser.") }
        return
    }
    item(key = "header") { HeaderCard(r) }

    val hl = vm.highlights
    val recentEvents = vm.events.filter { it.semId == vm.selected }.take(3)
    item(key = "recent_title") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Dernières notes", Modifier.weight(1f))
            TextButton(onClick = { vm.tab = Tab.News }, modifier = Modifier.padding(top = 8.dp)) { Text("Tout voir") }
        }
    }
    if (recentEvents.isNotEmpty()) {
        items(recentEvents, key = { "recent_" + it.key }) { e ->
            RecentRow(e.moduleCode, e.description, e.value, relTime(e.ts), !e.read) { vm.openEvent(e) }
        }
    } else {
        val latest = r.modules.flatMap { m -> m.evaluations.filter { it.hasNote }.map { m to it } }
            .sortedByDescending { it.second.date ?: "" }
            .take(3)
        if (latest.isEmpty()) {
            item(key = "recent_none") { InfoCard("Pas encore de note ce semestre.") }
        } else {
            items(latest, key = { "latest_" + it.second.key }) { (m, e) ->
                RecentRow(m.code, e.description, e.note?.value, fmtDate(e.date), e.key in hl) {
                    vm.open(Route.ModuleDetail(m.code))
                }
            }
        }
    }

    if (r.ues.isNotEmpty()) {
        item(key = "ue_title") { SectionTitle("Compétences") }
        items(r.ues, key = { "ue_" + it.code }) { ue -> UeCard(ue) { vm.open(Route.UeDetail(ue.code)) } }
    }
    item(key = "updated") {
        val ts = vm.prefs.lastCheck
        if (ts > 0) {
            Text(
                "Mis à jour ${relTime(ts)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }
    }
}

@Composable
fun HeaderCard(r: Releve) {
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
                if (r.absTotal != null) Stat("Abs.", "${r.absTotal} (${r.absInjust ?: 0} inj.)", Modifier.weight(1f))
            }
            if (r.moyenne?.min != null || r.moyenne?.max != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Promo : min ${fmt(r.moyenne?.min)} · max ${fmt(r.moyenne?.max)}", color = cs.onPrimary.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            r.situation?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim(),
                    color = cs.onPrimary.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall,
                )
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
        Text(
            value, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecentRow(code: String, desc: String, value: String?, meta: String?, isNew: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    if (isNew) {
                        Spacer(Modifier.width(8.dp))
                        NewBadge()
                    }
                }
                Text(desc, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                meta?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.width(12.dp))
            NotePill(value, value?.let { fr.scodocprime.app.data.Tracker.isReal(it) } == true)
        }
    }
}

@Composable
fun UeCard(ue: Ue, onClick: (() -> Unit)? = null) {
    val color = parseColor(ue.color, MaterialTheme.colorScheme.primary)
    val v = ue.moyenne?.numeric
    Card(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(8.dp).fillMaxHeight().background(color))
            Column(Modifier.weight(1f).padding(16.dp)) {
                Text(ue.code, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
                Text(ue.titre, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(
                    ue.rang?.let { "Rang $it" },
                    ue.ects?.let { "ECTS $it" },
                    ue.moyenne?.moy?.let { "Promo ${fmt(it)}" },
                )
                if (details.isNotEmpty()) {
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.padding(end = 8.dp).align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                Text(fmt(ue.moyenne?.value), fontSize = 26.sp, fontWeight = FontWeight.Black, color = noteColor(v))
                if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// =====================================================================
// Notes
// =====================================================================

fun LazyListScope.notesTab(vm: MainViewModel) {
    val r = vm.releve
    item(key = "search") {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            placeholder = { Text("Rechercher une matière, un DS…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = "" }) { Icon(Icons.Default.Clear, "Effacer") }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (r == null) return
    item(key = "filters") {
        val opts = listOf("all" to "Tout", "res" to "Ressources", "sae" to "SAÉ") + r.ues.map { it.code to it.code }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(opts, key = { "f_" + it.first }) { (k, label) ->
                FilterChip(selected = vm.filter == k, onClick = { vm.filter = k }, label = { Text(label) })
            }
        }
    }
    item(key = "sort") { SortSelector(vm) }

    val q = vm.query.trim().lowercase()
    val ueCodes = r.ue(vm.filter)?.modules?.map { it.code }?.toSet()
    val list = r.modules
        .filter {
            when (vm.filter) {
                "all" -> true
                "res" -> !it.sae
                "sae" -> it.sae
                else -> ueCodes?.contains(it.code) ?: true
            }
        }
        .filter { m ->
            q.isEmpty() || m.code.lowercase().contains(q) || m.titre.lowercase().contains(q) ||
                m.evaluations.any { it.description.lowercase().contains(q) }
        }
        .let { l ->
            when (vm.sort) {
                SortMode.Code -> l.sortedBy { it.code }
                SortMode.Moyenne -> l.sortedByDescending { it.moyenneIndicative ?: -1.0 }
                SortMode.Recent -> l.sortedByDescending { it.lastDate ?: "" }
            }
        }
    if (list.isEmpty()) {
        item(key = "none") { InfoCard("Aucune matière ne correspond.") }
    } else {
        val hl = vm.highlights
        items(list, key = { "m_" + it.code }) { m -> ModuleRow(m, m.evaluations.any { it.key in hl }) { vm.open(Route.ModuleDetail(m.code)) } }
    }
}

@Composable
private fun SortSelector(vm: MainViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text("Tri : ${vm.sort.label}")
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortMode.entries.forEach { s ->
                DropdownMenuItem(text = { Text(s.label) }, onClick = { vm.sort = s; open = false })
            }
        }
    }
}

@Composable
private fun ModuleRow(m: Module, hasNew: Boolean, onClick: () -> Unit) {
    val avg = m.moyenneIndicative
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = if (hasNew) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    if (m.sae) {
                        Spacer(Modifier.width(6.dp))
                        Text("SAÉ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (hasNew) {
                        Spacer(Modifier.width(8.dp))
                        NewBadge()
                    }
                }
                Text(m.titre, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (m.evaluations.isEmpty()) "Aucune évaluation" else "${m.graded} / ${m.evaluations.size} notes",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (avg != null) {
                Text(fmt(avg), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = noteColor(avg))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// =====================================================================
// Nouveautés
// =====================================================================

private fun dayLabel(ts: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    val now = Calendar.getInstance()
    fun sameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(c, now)) return "Aujourd'hui"
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(c, now)) return "Hier"
    return SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(Date(ts)).replaceFirstChar { it.uppercase() }
}

fun LazyListScope.newsTab(vm: MainViewModel) {
    val events = vm.events
    if (events.isEmpty()) {
        item(key = "news_empty") {
            InfoCard("Aucune nouvelle note pour l'instant. Dès qu'une note tombe, elle apparaît ici et tu reçois une notif.")
        }
        return
    }
    val sems = vm.semestres.associate { it.id to it.titre }
    events.groupBy { dayLabel(it.ts) }.forEach { (day, list) ->
        item(key = "day_$day") { SectionTitle(day) }
        items(list, key = { "ev_" + it.semId + it.key + it.ts }) { e ->
            NewsRow(e, if (e.semId != vm.selected) sems[e.semId] else null) { vm.openEvent(e) }
        }
    }
    item(key = "news_clear") {
        TextButton(onClick = { vm.clearHistory() }, modifier = Modifier.padding(top = 8.dp)) { Text("Effacer l'historique") }
    }
}

@Composable
private fun NewsRow(e: NoteEvent, semLabel: String?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(10.dp).clip(CircleShape)
                    .background(if (e.read) Color.Transparent else MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${e.moduleCode} · ${e.moduleTitre}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    e.description, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (e.read) FontWeight.Normal else FontWeight.SemiBold,
                )
                val meta = listOfNotNull(relTime(e.ts), e.moy?.let { "moy. promo ${fmt(it)}" }, semLabel).joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            NotePill(e.value, fr.scodocprime.app.data.Tracker.isReal(e.value))
        }
    }
}

// =====================================================================
// Absences
// =====================================================================

fun LazyListScope.absencesTab(vm: MainViewModel) {
    val r = vm.releve
    if (r == null) {
        item(key = "abs_empty") { InfoCard("Aucune donnée pour l'instant.") }
        return
    }
    val list = r.absences
    item(key = "abs_summary") { AbsSummary(r) }

    if (list.isEmpty()) {
        item(key = "abs_nodetail") {
            InfoCard(
                "ScoDoc ne fournit que les totaux pour l'instant. Si tu vois le détail sur le site, " +
                    "exporte les données depuis les paramètres pour qu'on adapte l'appli.",
            )
        }
        return
    }

    item(key = "abs_filters") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Toutes", "Injustifiées", "Retards").forEachIndexed { i, l ->
                FilterChip(selected = vm.absFilter == i, onClick = { vm.absFilter = i }, label = { Text(l) })
            }
        }
    }

    val byMatiere = list.filter { !it.isRetard }.groupBy { it.matiere ?: "Sans matière" }
        .mapValues { it.value.size }.entries.sortedByDescending { it.value }
    if (byMatiere.size > 1 && vm.absFilter == 0) {
        item(key = "abs_by_mat") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Par matière", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val maxN = byMatiere.first().value.toFloat()
                    byMatiere.take(8).forEach { (mat, n) ->
                        Column {
                            Row {
                                Text(mat, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("$n", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { n / maxN },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            )
                        }
                    }
                }
            }
        }
    }

    val filtered = when (vm.absFilter) {
        1 -> list.filter { !it.justifie && !it.isRetard }
        2 -> list.filter { it.isRetard }
        else -> list
    }
    if (filtered.isEmpty()) {
        item(key = "abs_none") { InfoCard("Rien ici, bien joué.") }
    } else {
        item(key = "abs_list_title") { SectionTitle("Détail") }
        items(filtered.withIndex().toList(), key = { "abs_" + it.index }) { (_, a) -> AbsenceRow(a) }
    }
}

@Composable
private fun AbsSummary(r: Releve) {
    val retards = r.absences.count { it.isRetard }
    val total = r.absTotal?.toIntOrNull() ?: r.absences.count { !it.isRetard }
    val inj = r.absInjust?.toIntOrNull() ?: r.absences.count { !it.isRetard && !it.justifie }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                BigStat("Total", "$total", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                BigStat("Injustifiées", "$inj", if (inj > 0) Color(0xFFD64545) else Color(0xFF43A047), Modifier.weight(1f))
                BigStat("Justifiées", "${(total - inj).coerceAtLeast(0)}", Color(0xFF43A047), Modifier.weight(1f))
                if (retards > 0) BigStat("Retards", "$retards", Color(0xFFE6891A), Modifier.weight(1f))
            }
            r.absMetrique?.let {
                Spacer(Modifier.height(8.dp))
                Text("Unité : $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BigStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Black, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun AbsenceRow(a: Absence) {
    val (label, color) = when {
        a.isRetard -> "Retard" to Color(0xFFE6891A)
        a.justifie -> "Justifiée" to Color(0xFF43A047)
        else -> "Injustifiée" to Color(0xFFD64545)
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val hours = listOfNotNull(a.debut, a.fin).joinToString(" – ")
                Text(
                    listOfNotNull(fmtDate(a.date), hours.ifEmpty { null }).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(a.matiere ?: "Matière non précisée", style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                a.enseignant?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
                Text(label, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}
