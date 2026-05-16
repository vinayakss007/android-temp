package com.abetworks.abetcrm.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "activities",
    foreignKeys = [ForeignKey(
        entity = Lead::class,
        parentColumns = ["id"],
        childColumns = ["leadId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Activity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val leadId: Long,
    val type: ActivityType,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)

enum class ActivityType {
    CALL_INCOMING, CALL_OUTGOING, CALL_MISSED,
    WHATSAPP_MESSAGE, NOTE, STAGE_CHANGE, CONTACT_IMPORTED
}
