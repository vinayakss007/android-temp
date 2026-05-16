package com.abetworks.abetcrm.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.abetworks.abetcrm.data.repository.LeadRepository
import com.abetworks.abetcrm.util.PhoneUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WhatsAppNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repo: LeadRepository

    companion object {
        private const val TAG = "AbetCRM_WAListener"

        // All WhatsApp package variants
        private val WHATSAPP_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",          // WhatsApp Business
            "com.gbwhatsapp",            // GB WhatsApp (popular in India)
            "com.poor.status"
        )
    }

    override fun onCreate() {
        super.onCreate()
        repo = LeadRepository(applicationContext)
        Log.d(TAG, "WhatsApp Notification Listener started")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in WHATSAPP_PACKAGES) return

        val extras = sbn.notification.extras ?: return

        // Skip group summary notifications
        val isGroupSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) return

        val title   = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text    = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        // Skip group/channel messages (title contains "@" or subText has participant count)
        if (subText.contains("participant") || title.contains("@")) return

        // Skip status updates
        if (text.startsWith("📷") && text.length < 5) return
        if (text == "Photo" || text == "Video" || text == "Audio" || text == "Document" ||
            text == "Sticker" || text == "GIF") return // media-only, no text to capture

        // Extract phone from notification person / contact URI if available
        val phone = extractPhoneFromNotification(sbn) ?: return

        Log.d(TAG, "WA message from $title ($phone): $text")

        scope.launch {
            try {
                val leadId = repo.upsertFromWhatsApp(
                    phone = phone,
                    senderName = title,
                    messageSnippet = text.take(200),
                    timestamp = sbn.postTime
                )
                Log.d(TAG, "Lead upserted from WA id=$leadId")
                SyncManager.scheduleSyncNow(applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upsert lead from WhatsApp", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) { /* no-op */ }

    /**
     * Attempts to extract a phone number from the notification.
     * WhatsApp encodes the sender's phone in the notification Person object
     * as a tel: URI, available via EXTRA_MESSAGING_PERSON or contact URI.
     */
    private fun extractPhoneFromNotification(sbn: StatusBarNotification): String? {
        val extras = sbn.notification.extras ?: return null

        // Android 9+ MessagingStyle — most reliable
        try {
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (messages != null && messages.isNotEmpty()) {
                // Last message sender URI
                val lastMsg = messages.last()
                val senderField = lastMsg?.javaClass?.getDeclaredField("mSender")
                senderField?.isAccessible = true
                val sender = senderField?.get(lastMsg)
                val uriField = sender?.javaClass?.getDeclaredMethod("getUri")
                val uri = uriField?.invoke(sender)?.toString()
                if (uri?.startsWith("tel:") == true) {
                    val phone = uri.removePrefix("tel:").trim()
                    if (phone.isNotBlank()) return PhoneUtils.normalize(phone)
                }
            }
        } catch (_: Exception) {}

        // Fallback: parse from notification tag (WhatsApp uses phone in tag sometimes)
        sbn.tag?.let { tag ->
            val phoneRegex = Regex("\\d{10,15}")
            val match = phoneRegex.find(tag)
            if (match != null) return PhoneUtils.normalize(match.value)
        }

        // Fallback: look in title — WhatsApp Business sometimes adds number in parens
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return null
        val parenRegex = Regex("\\(\\+?(\\d[\\d\\s\\-]{8,14}\\d)\\)")
        val parenMatch = parenRegex.find(title)
        if (parenMatch != null) return PhoneUtils.normalize(parenMatch.groupValues[1])

        // Last resort: use title as identifier (no phone available)
        // We store title as phone placeholder so we can still create a lead
        return if (title.isNotBlank()) "name:${title.take(30)}" else null
    }
}
