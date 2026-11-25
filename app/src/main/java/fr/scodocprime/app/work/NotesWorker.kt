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
import fr.scodocprime.app.data.Parser
import fr.scodocprime.app.data.Prefs
import fr.scodocprime.app.data.ScodocClient
import fr.scodocprime.app.data.Tracker
import java.util.concurrent.TimeUnit

class NotesWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val base = prefs.baseUrl ?: return Result.success()
        if (!prefs.notifEnabled) return Result.success()
        return try {
            val client = ScodocClient(applicationContext, base)
            val (sems, raw) = client.semestres()
            prefs.cacheSemestres(raw)
            val sem = Parser.current(sems) ?: return Result.success()
            val json = client.releve(sem.id)
            prefs.cacheReleve(sem.id, json.toString())
            val news = Tracker.update(prefs, sem.id, Parser.releve(json))
            prefs.authExpiredNotified = false
            prefs.lastCheck = System.currentTimeMillis()
            if (!news.isNullOrEmpty()) {
                prefs.addHighlights(news.map { it.eval.key })
                Notifier.notifyNotes(applicationContext, news)
            }
            Result.success()
        } catch (e: AuthRequiredException) {
            if (!prefs.authExpiredNotified) {
                Notifier.notifyAuth(applicationContext)
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

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<NotesWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
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
