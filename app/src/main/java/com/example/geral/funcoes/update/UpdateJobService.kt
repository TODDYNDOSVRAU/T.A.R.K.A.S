package com.example.data.update

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The periodic check, every six hours, kept by the system (it survives a restart of the phone).
 * It uses the platform's JobScheduler, so the app needs no extra library. With "Só no Wi-Fi" on, the
 * job only runs on an unmetered network.
 */
class UpdateJobService : JobService() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var running: Job? = null

  override fun onStartJob(params: JobParameters): Boolean {
    UpdateManager.init(this)
    running = scope.launch {
      try {
        UpdateManager.runAutomatic()
      } finally {
        jobFinished(params, false)
      }
    }
    return true
  }

  override fun onStopJob(params: JobParameters): Boolean {
    running?.cancel()
    return true // try again at the next window
  }

  companion object {
    private const val JOB_ID = 4417
    private const val PERIOD_MS = 6L * 60 * 60 * 1000

    /** Sets the job to match the current switches; calling it again with the same settings is harmless. */
    fun schedule(context: Context) {
      val app = context.applicationContext
      val scheduler = app.getSystemService(JobScheduler::class.java) ?: return
      UpdateManager.init(app)
      if (!UpdateManager.autoEnabled || !UpdateManager.installsUpdates) {
        scheduler.cancel(JOB_ID)
        return
      }
      val network = if (UpdateManager.wifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY
      val wanted = JobInfo.Builder(JOB_ID, ComponentName(app, UpdateJobService::class.java))
        .setRequiredNetworkType(network)
        .setPeriodic(PERIOD_MS)
        .setPersisted(true)
        .build()
      val current = scheduler.getPendingJob(JOB_ID)
      if (current != null && current.networkType == network && current.intervalMillis == PERIOD_MS) return
      scheduler.schedule(wanted)
    }
  }
}
