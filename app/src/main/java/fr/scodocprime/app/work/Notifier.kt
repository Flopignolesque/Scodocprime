package fr.scodocprime.app.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fr.scodocprime.app.MainActivity
import fr.scodocprime.app.R
import fr.scodocprime.app.data.NoteEvent
import fr.scodocprime.app.ui.fmt

object Notifier {
    private const val CHANNEL_NOTES = "notes"
    private const val CHANNEL_INFO = "info"
    private const val ID_AUTH = 1
    private const val ID_SUMMARY = 2
    const val EXTRA_OPEN_NEWS = "open_news"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_NOTES, "Nouvelles notes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Prévient dès qu'une note apparaît sur ScoDoc"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INFO, "Connexion", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Session expirée ou identifiants refusés"
            }
        )
    }

    private fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context, news: Boolean): PendingIntent = PendingIntent.getActivity(
        ctx, if (news) 1 else 0,
        Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_NEWS, news),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission")
    fun notifyNotes(ctx: Context, notes: List<NoteEvent>) {
        if (!canPost(ctx) || notes.isEmpty()) return
        ensureChannels(ctx)
        val nm = NotificationManagerCompat.from(ctx)
        val group = "fr.scodocprime.NOTES"
        notes.take(6).forEach { n ->
            val text = buildString {
                append(n.description).append(" : ").append(fmt(n.value)).append("/20")
                n.moy?.let { append("  (moy. promo ").append(fmt(it)).append(")") }
            }
            val notif = NotificationCompat.Builder(ctx, CHANNEL_NOTES)
                .setSmallIcon(R.drawable.ic_stat_note)
                .setContentTitle("Nouvelle note · ${n.moduleCode}")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText("${n.moduleTitre}\n$text"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setGroup(group)
                .setContentIntent(openApp(ctx, true))
                .build()
            nm.notify(n.key.hashCode(), notif)
        }
        if (notes.size > 1) {
            val summary = NotificationCompat.Builder(ctx, CHANNEL_NOTES)
                .setSmallIcon(R.drawable.ic_stat_note)
                .setContentTitle("${notes.size} nouvelles notes")
                .setContentText(notes.joinToString(", ") { it.moduleCode })
                .setGroup(group)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(openApp(ctx, true))
                .build()
            nm.notify(ID_SUMMARY, summary)
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyAuth(ctx: Context, badCredentials: Boolean) {
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        val notif = NotificationCompat.Builder(ctx, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle(if (badCredentials) "Identifiants refusés" else "Session ScoDoc expirée")
            .setContentText(
                if (badCredentials) "Mets à jour ton mot de passe dans les paramètres de l'appli."
                else "Ouvre l'appli pour te reconnecter et continuer à recevoir les notes."
            )
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, false))
            .build()
        NotificationManagerCompat.from(ctx).notify(ID_AUTH, notif)
    }
}
