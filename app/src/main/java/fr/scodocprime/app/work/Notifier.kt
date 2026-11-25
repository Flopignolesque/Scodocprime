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
import fr.scodocprime.app.data.NewNote
import fr.scodocprime.app.ui.fmt

object Notifier {
    private const val CHANNEL_NOTES = "notes"
    private const val CHANNEL_INFO = "info"
    private const val ID_AUTH = 1
    private const val ID_SUMMARY = 2

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_NOTES, "Nouvelles notes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Prévient dès qu'une note apparaît sur ScoDoc"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INFO, "Connexion", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Session expirée"
            }
        )
    }

    private fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission")
    fun notifyNotes(ctx: Context, notes: List<NewNote>) {
        if (!canPost(ctx) || notes.isEmpty()) return
        ensureChannels(ctx)
        val nm = NotificationManagerCompat.from(ctx)
        val group = "fr.scodocprime.NOTES"
        notes.take(6).forEach { n ->
            val v = n.eval.note
            val text = buildString {
                append(n.eval.description).append(" : ")
                append(fmt(v?.value)).append("/20")
                v?.moy?.let { append("  (moy. promo ").append(fmt(it)).append(")") }
            }
            val notif = NotificationCompat.Builder(ctx, CHANNEL_NOTES)
                .setSmallIcon(R.drawable.ic_stat_note)
                .setContentTitle("Nouvelle note · ${n.module.code}")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText("${n.module.titre}\n$text"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setGroup(group)
                .setContentIntent(openApp(ctx))
                .build()
            nm.notify(n.eval.key.hashCode(), notif)
        }
        if (notes.size > 1) {
            val summary = NotificationCompat.Builder(ctx, CHANNEL_NOTES)
                .setSmallIcon(R.drawable.ic_stat_note)
                .setContentTitle("${notes.size} nouvelles notes")
                .setContentText(notes.joinToString(", ") { it.module.code })
                .setGroup(group)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(openApp(ctx))
                .build()
            nm.notify(ID_SUMMARY, summary)
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyAuth(ctx: Context) {
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        val notif = NotificationCompat.Builder(ctx, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle("Session ScoDoc expirée")
            .setContentText("Ouvre l'appli pour te reconnecter et continuer à recevoir les notes.")
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        NotificationManagerCompat.from(ctx).notify(ID_AUTH, notif)
    }
}
