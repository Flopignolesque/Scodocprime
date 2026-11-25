package fr.scodocprime.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scodocprime.app.data.NoteVal
import fr.scodocprime.app.data.toD

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 12.dp, start = 4.dp),
    )
}

@Composable
fun ErrorCard(msg: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(msg, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun InfoCard(msg: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun NewBadge() {
    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50)) {
        Text(
            "NOUVEAU", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Pastille colorée avec la note. */
@Composable
fun NotePill(value: String?, real: Boolean, big: Boolean = false) {
    val v = value.toD()
    val c = if (real) noteColor(v) else MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.copy(alpha = 0.15f))
            .padding(horizontal = if (big) 16.dp else 12.dp, vertical = if (big) 8.dp else 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (real) fmt(value) else "—", color = c, fontWeight = FontWeight.Black, fontSize = if (big) 26.sp else 18.sp)
    }
}

/** Barre 0–20 : plage min–max de la promo, trait = moyenne promo, point = ta note. */
@Composable
fun PromoBar(note: NoteVal, modifier: Modifier = Modifier) {
    val mine = note.numeric ?: return
    val min = note.min.toD()
    val max = note.max.toD()
    val moy = note.moy.toD()
    val track = MaterialTheme.colorScheme.surfaceVariant
    val range = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val moyC = MaterialTheme.colorScheme.onSurface
    val dot = noteColor(mine)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val y = size.height / 2
            fun x(v: Double) = (v.coerceIn(0.0, 20.0) / 20.0 * size.width).toFloat()
            drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = 8f, cap = StrokeCap.Round)
            if (min != null && max != null) {
                drawLine(range, Offset(x(min), y), Offset(x(max), y), strokeWidth = 8f, cap = StrokeCap.Round)
            }
            if (moy != null) {
                drawLine(moyC, Offset(x(moy), y - 14f), Offset(x(moy), y + 14f), strokeWidth = 4f, cap = StrokeCap.Round)
            }
            drawCircle(Color.White, radius = 13f, center = Offset(x(mine), y))
            drawCircle(dot, radius = 10f, center = Offset(x(mine), y))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val parts = listOfNotNull(min?.let { "min ${fmt(it)}" }, moy?.let { "moy ${fmt(it)}" }, max?.let { "max ${fmt(it)}" })
            if (parts.isNotEmpty()) {
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("20", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun parseColor(c: String?, fallback: Color): Color = try {
    if (c == null) fallback else Color(android.graphics.Color.parseColor(c.trim()))
} catch (_: Exception) {
    fallback
}

/** Date relative lisible ("à l'instant", "il y a 3 h", "12/11"). */
fun relTime(ts: Long): String {
    val d = System.currentTimeMillis() - ts
    return when {
        d < 60_000 -> "à l'instant"
        d < 3_600_000 -> "il y a ${d / 60_000} min"
        d < 86_400_000 -> "il y a ${d / 3_600_000} h"
        d < 7 * 86_400_000L -> "il y a ${d / 86_400_000} j"
        else -> java.text.SimpleDateFormat("dd/MM", java.util.Locale.FRANCE).format(java.util.Date(ts))
    }
}
