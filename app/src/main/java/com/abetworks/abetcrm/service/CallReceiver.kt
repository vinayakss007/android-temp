package com.abetworks.abetcrm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.abetworks.abetcrm.data.model.CallType
import com.abetworks.abetcrm.data.repository.LeadRepository
import com.abetworks.abetcrm.util.PhoneUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CallReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "AbetCRM_CallReceiver"
        private var callStartTime = 0L
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var lastNumber = ""
        private var isOutgoing = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {

            Intent.ACTION_NEW_OUTGOING_CALL -> {
                lastNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER) ?: return
                isOutgoing = true
                callStartTime = System.currentTimeMillis()
                Log.d(TAG, "Outgoing call to $lastNumber")
            }

            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        lastNumber = incomingNumber ?: ""
                        isOutgoing = false
                        lastState = TelephonyManager.CALL_STATE_RINGING
                        Log.d(TAG, "Incoming call from $lastNumber")
                    }

                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        callStartTime = System.currentTimeMillis()
                        lastState = TelephonyManager.CALL_STATE_OFFHOOK
                    }

                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        val duration = if (callStartTime > 0)
                            ((System.currentTimeMillis() - callStartTime) / 1000).toInt() else 0
                        callStartTime = 0

                        if (lastNumber.isBlank()) { lastState = TelephonyManager.CALL_STATE_IDLE; return }

                        val callType = when {
                            isOutgoing -> CallType.OUTGOING
                            lastState == TelephonyManager.CALL_STATE_RINGING && duration == 0 -> CallType.MISSED
                            else -> CallType.INCOMING
                        }

                        val phone = lastNumber
                        val repo = LeadRepository(context)

                        // Lookup contact name from device
                        val name = getContactName(context, phone)

                        scope.launch {
                            try {
                                val leadId = repo.upsertFromCall(phone, name, callType, duration)
                                Log.d(TAG, "Lead upserted id=$leadId type=$callType duration=${duration}s")
                                // Trigger sync
                                SyncManager.scheduleSyncNow(context)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to upsert lead from call", e)
                            }
                        }

                        lastState = TelephonyManager.CALL_STATE_IDLE
                        isOutgoing = false
                    }
                }
            }
        }
    }

    private fun getContactName(context: Context, phone: String): String? {
        return try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phone)
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) { null }
    }
}
