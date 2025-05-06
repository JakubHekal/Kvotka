import android.content.Context
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.jakubhekal.kvotka.UsageNotificationService
import java.time.Duration
import java.util.concurrent.TimeUnit

class AutoStartWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    companion object {
        private const val TAG = "AutoStartWorker"

        private fun getWorkManager(context: Context) = WorkManager.getInstance(context)

        fun cancel(context: Context, workManager: WorkManager? = null) {
            (workManager ?: getWorkManager(context)).cancelAllWorkByTag(TAG)
        }

        fun enqueue(context: Context) {
            val workManager = getWorkManager(context)
            cancel(context, workManager)
            workManager.enqueue(
                PeriodicWorkRequestBuilder<AutoStartWorker>(Duration.ofHours(1))
                    .setInitialDelay(1, TimeUnit.HOURS)
                    .addTag(TAG).build()
            )
        }

    }

    override fun doWork(): Result {
        UsageNotificationService.start(applicationContext)
        return Result.success()
    }

}