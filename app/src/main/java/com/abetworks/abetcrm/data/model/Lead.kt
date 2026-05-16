package com.abetworks.abetcrm.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "leads")
data class Lead(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,          // cloud server ID after sync
    val name: String,
    val phone: String,
    val whatsappNumber: String = "",
    val source: LeadSource = LeadSource.MANUAL,
    val stage: LeadStage = LeadStage.NEW,
    val notes: String = "",
    val lastMessage: String = "",          // last WhatsApp notification text
    val lastMessageTime: Long = 0L,
    val followUpDate: Long? = null,
    val tags: String = "",                 // comma-separated
    val callDurationSeconds: Int = 0,
    val callType: CallType = CallType.NONE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false            // false = pending cloud sync
)

enum class LeadSource { WHATSAPP, CALL, CONTACT, MANUAL, FACEBOOK, WEBSITE }
enum class LeadStage  { NEW, CONTACTED, INTERESTED, WON, LOST }
enum class CallType   { NONE, INCOMING, OUTGOING, MISSED }
