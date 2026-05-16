package com.abetworks.abetcrm.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.abetworks.abetcrm.data.repository.LeadRepository
import com.abetworks.abetcrm.sync.ApiService
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ── SyncManager (schedules WorkManager jobs) ──────────────────────────────
object SyncManager {

    private const val TAG = "AbetCRM_Sync"
    private const val SYNC_WORK = "abetcrm_sync"
    private const val PERIODIC_SYNC = "abetcrm_periodic_sync"

    fun scheduleSyncNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(SYNC_WORK, ExistingWorkPolicy.KEEP, req)
        Log.d(TAG, "Sync enqueued")
    }

    fun schedulePeriodicSync(context: Context) {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_SYNC, ExistingPeriodicWorkPolicy.KEEP, req)
        Log.d(TAG, "Periodic sync scheduled every 15 min")
    }
}

// ── SyncWorker ─────────────────────────────────────────────────────────────
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val TAG = "AbetCRM_SyncWorker"
    private val repo = LeadRepository(context)
    private val api  = ApiService(context)

    override suspend fun doWork(): Result {
        return try {
            syncLeads()
            syncActivities()
            Log.d(TAG, "Sync complete")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.retry()
        }
    }

    private suspend fun syncLeads() {
        val unsynced = repo.getUnsyncedLeads()
        Log.d(TAG, "Syncing ${unsynced.size} leads")
        for (lead in unsynced) {
            val body = JSONObject().apply {
                put("name", lead.name)
                put("phone", lead.phone)
                put("whatsappNumber", lead.whatsappNumber)
                put("source", lead.source.name)
                put("stage", lead.stage.name)
                put("notes", lead.notes)
                put("lastMessage", lead.lastMessage)
                put("callType", lead.callType.name)
                put("callDurationSeconds", lead.callDurationSeconds)
                put("followUpDate", lead.followUpDate ?: JSONObject.NULL)
                put("tags", lead.tags)
                put("createdAt", lead.createdAt)
            }
            val remoteId = api.upsertLead(lead.remoteId, body)
            if (remoteId != null) {
                repo.markLeadSynced(lead.id, remoteId)
                Log.d(TAG, "Lead ${lead.id} synced → remoteId=$remoteId")
            }
        }
    }

    private suspend fun syncActivities() {
        val unsynced = repo.getUnsyncedActivities()
        Log.d(TAG, "Syncing ${unsynced.size} activities")
        for (act in unsynced) {
            val body = JSONObject().apply {
                put("leadId", act.leadId)
                put("type", act.type.name)
                put("description", act.description)
                put("timestamp", act.timestamp)
            }
            val ok = api.postActivity(body)
            if (ok) repo.markActivitySynced(act.id)
        }
    }
}
