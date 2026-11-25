package fr.scodocprime.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFFB4C5FF), secondary = Color(0xFFBFC6DC), tertiary = Color(0xFFDEBCDF))
        else -> lightColorScheme(primary = Color(0xFF3355CC), secondary = Color(0xFF585E71), tertiary = Color(0xFF7A5279))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

fun noteColor(v: Double?): Color = when {
    v == null -> Color(0xFF8A8F98)
    v >= 14 -> Color(0xFF14935A)
    v >= 10 -> Color(0xFF43A047)
    v >= 8 -> Color(0xFFE6891A)
    else -> Color(0xFFD64545)
}

/** "12.50" -> "12,5" ; laisse tel quel ce qui n'est pas un nombre (ABS, EXC…). */
fun fmt(v: String?): String {
    if (v == null) return "–"
    val d = v.replace(',', '.').trim().toDoubleOrNull() ?: return v
    return fmt(d)
}

fun fmt(d: Double): String {
    val s = String.format(Locale.FRANCE, "%.2f", d)
    return s.trimEnd('0').trimEnd(',')
}

/** "2024-10-05..." -> "05/10/2024" */
fun fmtDate(v: String?): String? {
    if (v == null) return null
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(v) ?: return v
    val (y, mo, d) = m.destructured
    return "$d/$mo/$y"
}
