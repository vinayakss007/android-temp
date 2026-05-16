package com.abetworks.abetcrm.data.repository

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import com.abetworks.abetcrm.data.db.AbetDatabase
import com.abetworks.abetcrm.data.model.*
import com.abetworks.abetcrm.util.PhoneUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LeadRepository(private val context: Context) {

    private val db = AbetDatabase.getInstance(context)
    private val leadDao = db.leadDao()
    private val activityDao = db.activityDao()

    // ── Observe ──────────────────────────────────────────────────────────────

    fun allLeads(): Flow<List<Lead>> = leadDao.getAllLeads()
    fun leadsByStage(stage: LeadStage): Flow<List<Lead>> = leadDao.getLeadsByStage(stage)
    fun searchLeads(q: String): Flow<List<Lead>> = leadDao.searchLeads(q)
    fun activitiesForLead(id: Long): Flow<List<Activity>> = activityDao.getActivitiesForLead(id)

    // ── Create / Update ──────────────────────────────────────────────────────

    suspend fun upsertFromCall(
        phone: String,
        name: String?,
        callType: CallType,
        durationSeconds: Int
    ): Long = withContext(Dispatchers.IO) {
        val normalized = PhoneUtils.normalize(phone)
        val existing = leadDao.getLeadByPhone(normalized)

        val activityType = when (callType) {
            CallType.INCOMING -> ActivityType.CALL_INCOMING
            CallType.OUTGOING -> ActivityType.CALL_OUTGOING
            CallType.MISSED   -> ActivityType.CALL_MISSED
            else              -> ActivityType.CALL_INCOMING
        }

        val leadId: Long
        if (existing != null) {
            leadDao.update(existing.copy(
                callType = callType,
                callDurationSeconds = durationSeconds,
                updatedAt = System.currentTimeMillis(),
                synced = false
            ))
            leadId = existing.id
        } else {
            leadId = leadDao.insert(Lead(
                name = name ?: PhoneUtils.format(phone),
                phone = normalized,
                whatsappNumber = normalized,
                source = LeadSource.CALL,
                stage = LeadStage.NEW,
                callType = callType,
                callDurationSeconds = durationSeconds
            ))
        }

        activityDao.insert(Activity(
            leadId = leadId,
            type = activityType,
            description = buildCallDescription(callType, durationSeconds, name ?: phone)
        ))
        leadId
    }

    suspend fun upsertFromWhatsApp(
        phone: String,
        senderName: String,
        messageSnippet: String,
        timestamp: Long
    ): Long = withContext(Dispatchers.IO) {
        val normalized = PhoneUtils.normalize(phone)
        val existing = leadDao.getLeadByPhone(normalized)

        val leadId: Long
        if (existing != null) {
            leadDao.updateLastMessage(existing.id, messageSnippet, timestamp)
            // upgrade stage from NEW → CONTACTED if still new
            if (existing.stage == LeadStage.NEW) {
                leadDao.updateStage(existing.id, LeadStage.CONTACTED)
            }
            leadId = existing.id
        } else {
            leadId = leadDao.insert(Lead(
                name = senderName,
                phone = normalized,
                whatsappNumber = normalized,
                source = LeadSource.WHATSAPP,
                stage = LeadStage.CONTACTED,
                lastMessage = messageSnippet,
                lastMessageTime = timestamp
            ))
        }

        activityDao.insert(Activity(
            leadId = leadId,
            type = ActivityType.WHATSAPP_MESSAGE,
            description = "WhatsApp: \"$messageSnippet\""
        ))
        leadId
    }

    suspend fun addNote(leadId: Long, note: String) = withContext(Dispatchers.IO) {
        activityDao.insert(Activity(
            leadId = leadId,
            type = ActivityType.NOTE,
            description = note
        ))
        val lead = leadDao.getLeadById(leadId) ?: return@withContext
        leadDao.update(lead.copy(updatedAt = System.currentTimeMillis(), synced = false))
    }

    suspend fun updateStage(leadId: Long, stage: LeadStage) = withContext(Dispatchers.IO) {
        val lead = leadDao.getLeadById(leadId) ?: return@withContext
        leadDao.updateStage(leadId, stage)
        activityDao.insert(Activity(
            leadId = leadId,
            type = ActivityType.STAGE_CHANGE,
            description = "Stage: ${lead.stage.name} → ${stage.name}"
        ))
    }

    suspend fun saveLead(lead: Lead): Long = withContext(Dispatchers.IO) {
        if (lead.id == 0L) leadDao.insert(lead)
        else { leadDao.update(lead.copy(updatedAt = System.currentTimeMillis(), synced = false)); lead.id }
    }

    suspend fun deleteLead(lead: Lead) = withContext(Dispatchers.IO) {
        leadDao.delete(lead)
    }

    // ── Contact Import ───────────────────────────────────────────────────────

    suspend fun importContacts(): Int = withContext(Dispatchers.IO) {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        ) ?: return@withContext 0

        var imported = 0
        cursor.use {
            val nameCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameCol) ?: continue
                val phone = PhoneUtils.normalize(it.getString(phoneCol) ?: continue)
                val existing = leadDao.getLeadByPhone(phone)
                if (existing == null) {
                    val id = leadDao.insert(Lead(
                        name = name, phone = phone,
                        whatsappNumber = phone,
                        source = LeadSource.CONTACT,
                        stage = LeadStage.NEW
                    ))
                    activityDao.insert(Activity(
                        leadId = id,
                        type = ActivityType.CONTACT_IMPORTED,
                        description = "Imported from contacts"
                    ))
                    imported++
                }
            }
        }
        imported
    }

    // ── Call Log Import ──────────────────────────────────────────────────────

    suspend fun importCallLog(limitDays: Int = 30): Int = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() - (limitDays * 86_400_000L)
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DURATION,
                CallLog.Calls.DATE
            ),
            "${CallLog.Calls.DATE} > ?",
            arrayOf(since.toString()),
            "${CallLog.Calls.DATE} DESC"
        ) ?: return@withContext 0

        var imported = 0
        cursor.use {
            val nameCol     = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val numberCol   = it.getColumnIndex(CallLog.Calls.NUMBER)
            val typeCol     = it.getColumnIndex(CallLog.Calls.TYPE)
            val durationCol = it.getColumnIndex(CallLog.Calls.DURATION)
            while (it.moveToNext()) {
                val phone    = PhoneUtils.normalize(it.getString(numberCol) ?: continue)
                val name     = it.getString(nameCol)
                val duration = it.getInt(durationCol)
                val callType = when (it.getInt(typeCol)) {
                    CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                    CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                    CallLog.Calls.MISSED_TYPE   -> CallType.MISSED
                    else -> CallType.NONE
                }
                upsertFromCall(phone, name, callType, duration)
                imported++
            }
        }
        imported
    }

    // ── Sync helpers ──────────────────────────────────────────────────────────

    suspend fun getUnsyncedLeads() = leadDao.getUnsyncedLeads()
    suspend fun getUnsyncedActivities() = activityDao.getUnsyncedActivities()
    suspend fun markLeadSynced(id: Long, remoteId: String) = leadDao.markSynced(id, remoteId)
    suspend fun markActivitySynced(id: Long) = activityDao.markSynced(id)
    suspend fun getOverdueFollowUps() = leadDao.getOverdueFollowUps(System.currentTimeMillis())

    // ── Stats ──────────────────────────────────────────────────────────────────
    suspend fun stats() = Triple(leadDao.totalCount(), leadDao.wonCount(), leadDao.whatsappCount())

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildCallDescription(type: CallType, duration: Int, name: String): String {
        val mins = duration / 60; val secs = duration % 60
        val dur = if (duration > 0) " (${mins}m ${secs}s)" else ""
        return when (type) {
            CallType.INCOMING -> "Incoming call from $name$dur"
            CallType.OUTGOING -> "Outgoing call to $name$dur"
            CallType.MISSED   -> "Missed call from $name"
            else              -> "Call with $name$dur"
        }
    }
}
