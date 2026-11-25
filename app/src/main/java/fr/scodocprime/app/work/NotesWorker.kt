package fr.scodocprime.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.scodocprime.app.data.AuthRequiredException
import fr.scodocprime.app.data.BadCredentialsException
import fr.scodocprime.app.data.CredentialStore
import fr.scodocprime.app.data.Parser
import fr.scodocprime.app.data.Prefs
import fr.scodocprime.app.data.ScodocClient
import fr.scodocprime.app.data.Tracker
import java.util.Calendar
import java.util.concurrent.TimeUnit

class NotesWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        if (!prefs.notifEnabled) return Result.success()
        return try {
            val client = ScodocClient(ctx, prefs.baseUrl, CredentialStore(prefs).forAutoLogin())
            val (sems, raw) = client.semestres()
            prefs.cacheSemestres(raw)
            val sem = Parser.current(sems) ?: return Result.success()
            val json = client.releve(sem.id)
            prefs.cacheReleve(sem.id, json.toString())
            Tracker.update(prefs, sem.id, Parser.releve(json), seenByUser = false)
            prefs.authExpiredNotified = false
            prefs.lastCheck = System.currentTimeMillis()
            flushPending(ctx, prefs)
            Result.success()
        } catch (e: AuthRequiredException) {
            if (!prefs.authExpiredNotified) {
                Notifier.notifyAuth(ctx, e is BadCredentialsException)
                prefs.authExpiredNotified = true
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC = "notes-check"
        private const val NOW = "notes-check-now"
        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Envoie les notifs en attente (hors heures calmes) et les marque comme notifiées. */
        fun flushPending(ctx: Context, prefs: Prefs) {
            if (prefs.isQuietNow(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))) return
            val events = prefs.events()
            val pending = events.filter { !it.notified && !it.read }
            if (pending.isEmpty()) return
            Notifier.notifyNotes(ctx, pending)
            prefs.saveEvents(events.map { if (!it.notified) it.copy(notified = true) else it })
        }

        fun schedule(ctx: Context, minutes: Int) {
            val req = PeriodicWorkRequestBuilder<NotesWorker>(minutes.toLong().coerceAtLeast(15), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun runNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<NotesWorker>().setConstraints(constraints).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC)
        }
    }
}
