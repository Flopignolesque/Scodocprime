package fr.scodocprime.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scodocprime.app.data.Evaluation
import fr.scodocprime.app.data.UeModule
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScaffold(vm: MainViewModel, title: String, subtitle: String?, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        subtitle?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                },
            )
        },
        content = content,
    )
}

// =====================================================================
// Ressource / SAÉ
// =====================================================================

@Composable
fun ModuleDetailScreen(vm: MainViewModel, code: String) {
    val r = vm.releve
    val m = r?.module(code)
    if (r == null || m == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val hl = vm.highlights
    LaunchedEffect(code) {
        delay(2000)
        vm.markModuleRead(code)
    }
    DetailScaffold(vm, m.code, m.titre) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (m.sae) "SAÉ" else "Ressource", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(m.titre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("${m.graded} / ${m.evaluations.size} notes", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        m.moyenneIndicative?.let { avg ->
                            Column(horizontalAlignment = Alignment.End) {
                                Text(fmt(avg), fontSize = 34.sp, fontWeight = FontWeight.Black, color = noteColor(avg))
                                Text("moy. indicative", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }

            val ues = r.uesOf(code)
            if (ues.isNotEmpty()) {
                item { SectionTitle("Compte dans") }
                items(ues, key = { "u_" + it.first.code }) { (ue, um) ->
                    val color = parseColor(ue.color, MaterialTheme.colorScheme.primary)
                    Card(Modifier.fillMaxWidth().clickable { vm.open(Route.UeDetail(ue.code)) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(6.dp).height(36.dp).clip(RoundedCornerShape(3.dp)).background(color))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ue.code, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
                                Text(ue.titre, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("coef ${um.coef?.let { fmt(it) } ?: "?"}", style = MaterialTheme.typography.labelMedium)
                                um.moyenne?.let { Text(fmt(it), fontWeight = FontWeight.Bold, color = noteColor(it)) }
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { SectionTitle("Évaluations") }
            if (m.evaluations.isEmpty()) {
                item { InfoCard("Aucune évaluation pour l'instant.") }
            }
            items(m.evaluations, key = { it.key }) { e -> EvaluationCard(e, e.key in hl) }
        }
    }
}

@Composable
private fun EvaluationCard(e: Evaluation, isNew: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(e.description, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false))
                        if (isNew) {
                            Spacer(Modifier.width(8.dp))
                            NewBadge()
                        }
                    }
                    val meta = listOfNotNull(fmtDate(e.date), e.coef?.let { "coef ${fmt(it)}" }).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(12.dp))
                NotePill(e.note?.value, e.hasNote, big = true)
            }
            val n = e.note
            if (e.hasNote && n != null && (n.min != null || n.moy != null || n.max != null)) {
                Spacer(Modifier.height(12.dp))
                PromoBar(n)
            }
        }
    }
}

// =====================================================================
// UE + simulateur
// =====================================================================

@Composable
fun UeDetailScreen(vm: MainViewModel, code: String) {
    val r = vm.releve
    val ue = r?.ue(code)
    if (r == null || ue == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val color = parseColor(ue.color, MaterialTheme.colorScheme.primary)
    val mods = ue.modules.filter { (it.coef ?: 0.0) > 0 }
    val totalCoef = mods.sumOf { it.coef ?: 0.0 }

    DetailScaffold(vm, ue.code, ue.titre) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { UeCard(ue) }
            val extras = listOfNotNull(
                ue.bonus?.takeIf { (it.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0 }?.let { "Bonus +${fmt(it)}" },
                ue.malus?.takeIf { (it.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0 }?.let { "Malus −${fmt(it)}" },
                ue.moyenne?.min?.let { "Promo min ${fmt(it)}" },
                ue.moyenne?.max?.let { "max ${fmt(it)}" },
            )
            if (extras.isNotEmpty()) item { Text(extras.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)) }

            if (mods.isNotEmpty()) {
                item { SectionTitle("Ce qui compte dans cette UE") }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            mods.sortedByDescending { it.coef ?: 0.0 }.forEachIndexed { i, um ->
                                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                                ContributionRow(vm, um, totalCoef, color)
                            }
                        }
                    }
                }
                item { SectionTitle("Simulateur") }
                item { Simulator(mods, totalCoef, color) }
            }
        }
    }
}

@Composable
private fun ContributionRow(vm: MainViewModel, um: UeModule, totalCoef: Double, color: androidx.compose.ui.graphics.Color) {
    val m = vm.releve?.module(um.code)
    val share = if (totalCoef > 0) (um.coef ?: 0.0) / totalCoef else 0.0
    Row(
        Modifier.fillMaxWidth().clickable(enabled = m != null) { vm.open(Route.ModuleDetail(um.code)) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(um.code, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
            Text(m?.titre ?: um.code, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("coef ${um.coef?.let { fmt(it) } ?: "?"} · ${(share * 100).roundToInt()} % de l'UE",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(um.moyenne?.let { fmt(it) } ?: "—", fontWeight = FontWeight.Bold, fontSize = 18.sp,
            color = um.moyenne?.let { noteColor(it) } ?: MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun Simulator(mods: List<UeModule>, totalCoef: Double, color: androidx.compose.ui.graphics.Color) {
    var target by remember { mutableFloatStateOf(10f) }
    val sim = remember(mods) { mutableStateMapOf<String, Float>().apply { mods.forEach { put(it.code, (it.moyenne ?: 10.0).toFloat()) } } }

    val known = mods.filter { it.moyenne != null }
    val remaining = mods.filter { it.moyenne == null }
    val knownSum = known.sumOf { (it.coef ?: 0.0) * (it.moyenne ?: 0.0) }
    val remCoef = remaining.sumOf { it.coef ?: 0.0 }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Objectif : ${fmt(target.toDouble())}/20", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Slider(value = target, onValueChange = { target = (it * 2).roundToInt() / 2f }, valueRange = 0f..20f)
            val msg = when {
                remaining.isEmpty() -> {
                    val cur = if (totalCoef > 0) knownSum / totalCoef else 0.0
                    "Tous les modules sont notés : estimation ${fmt(cur)}/20" +
                        if (cur >= target) " — objectif atteint." else " — objectif pas atteint."
                }
                else -> {
                    val need = (target * totalCoef - knownSum) / remCoef
                    when {
                        need <= 0 -> "Objectif déjà assuré, même avec 0 dans les ${remaining.size} module(s) pas encore noté(s)."
                        need > 20 -> "Objectif impossible : il faudrait ${fmt(need)} de moyenne sur ce qui reste."
                        else -> "Il te faut au moins ${fmt(need)} de moyenne sur les ${remaining.size} module(s) pas encore noté(s) " +
                            "(${remaining.joinToString(", ") { it.code }})."
                    }
                }
            }
            Text(msg, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            val simAvg = if (totalCoef > 0) mods.sumOf { (it.coef ?: 0.0) * (sim[it.code] ?: 10f) } / totalCoef else 0.0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Simulation libre", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Bouge les moyennes pour voir le résultat", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(fmt(simAvg), fontSize = 28.sp, fontWeight = FontWeight.Black, color = noteColor(simAvg))
            }
            mods.forEach { um ->
                val v = sim[um.code] ?: 10f
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(um.code, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.width(72.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Slider(
                        value = v, onValueChange = { sim[um.code] = (it * 2).roundToInt() / 2f }, valueRange = 0f..20f,
                        modifier = Modifier.weight(1f),
                    )
                    Text(fmt(v.toDouble()), modifier = Modifier.width(44.dp), fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            TextButton(onClick = { mods.forEach { sim[it.code] = (it.moyenne ?: 10.0).toFloat() } }) { Text("Réinitialiser") }
            Text(
                "Estimation à partir des coefs de l'UE, sans bonus/malus. La moyenne officielle reste celle de ScoDoc.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
