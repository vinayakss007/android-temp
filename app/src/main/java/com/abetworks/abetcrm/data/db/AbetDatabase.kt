package com.abetworks.abetcrm.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.abetworks.abetcrm.data.model.*
import kotlinx.coroutines.flow.Flow

// ── Lead DAO ────────────────────────────────────────────────────────────────
@Dao
interface LeadDao {

    @Query("SELECT * FROM leads ORDER BY updatedAt DESC")
    fun getAllLeads(): Flow<List<Lead>>

    @Query("SELECT * FROM leads WHERE stage = :stage ORDER BY updatedAt DESC")
    fun getLeadsByStage(stage: LeadStage): Flow<List<Lead>>

    @Query("SELECT * FROM leads WHERE phone = :phone OR whatsappNumber = :phone LIMIT 1")
    suspend fun getLeadByPhone(phone: String): Lead?

    @Query("SELECT * FROM leads WHERE id = :id")
    suspend fun getLeadById(id: Long): Lead?

    @Query("SELECT * FROM leads WHERE synced = 0")
    suspend fun getUnsyncedLeads(): List<Lead>

    @Query("""
        SELECT * FROM leads WHERE
        name LIKE '%' || :query || '%' OR
        phone LIKE '%' || :query || '%' OR
        lastMessage LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
    """)
    fun searchLeads(query: String): Flow<List<Lead>>

    @Query("""
        SELECT * FROM leads WHERE
        followUpDate IS NOT NULL AND
        followUpDate <= :threshold AND
        stage NOT IN ('WON','LOST')
        ORDER BY followUpDate ASC
    """)
    suspend fun getOverdueFollowUps(threshold: Long): List<Lead>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(lead: Lead): Long

    @Update
    suspend fun update(lead: Lead)

    @Query("UPDATE leads SET stage = :stage, updatedAt = :now, synced = 0 WHERE id = :id")
    suspend fun updateStage(id: Long, stage: LeadStage, now: Long = System.currentTimeMillis())

    @Query("UPDATE leads SET lastMessage = :msg, lastMessageTime = :time, updatedAt = :time, synced = 0 WHERE id = :id")
    suspend fun updateLastMessage(id: Long, msg: String, time: Long)

    @Query("UPDATE leads SET synced = 1, remoteId = :remoteId WHERE id = :id")
    suspend fun markSynced(id: Long, remoteId: String)

    @Delete
    suspend fun delete(lead: Lead)

    @Query("SELECT COUNT(*) FROM leads")
    suspend fun totalCount(): Int

    @Query("SELECT COUNT(*) FROM leads WHERE stage = 'WON'")
    suspend fun wonCount(): Int

    @Query("SELECT COUNT(*) FROM leads WHERE source = 'WHATSAPP'")
    suspend fun whatsappCount(): Int
}

// ── Activity DAO ────────────────────────────────────────────────────────────
@Dao
interface ActivityDao {

    @Query("SELECT * FROM activities WHERE leadId = :leadId ORDER BY timestamp DESC")
    fun getActivitiesForLead(leadId: Long): Flow<List<Activity>>

    @Query("SELECT * FROM activities WHERE synced = 0")
    suspend fun getUnsyncedActivities(): List<Activity>

    @Insert
    suspend fun insert(activity: Activity): Long

    @Query("UPDATE activities SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("DELETE FROM activities WHERE leadId = :leadId")
    suspend fun deleteForLead(leadId: Long)
}

// ── Converters ──────────────────────────────────────────────────────────────
class Converters {
    @TypeConverter fun fromLeadSource(v: LeadSource) = v.name
    @TypeConverter fun toLeadSource(v: String) = LeadSource.valueOf(v)
    @TypeConverter fun fromLeadStage(v: LeadStage) = v.name
    @TypeConverter fun toLeadStage(v: String) = LeadStage.valueOf(v)
    @TypeConverter fun fromCallType(v: CallType) = v.name
    @TypeConverter fun toCallType(v: String) = CallType.valueOf(v)
    @TypeConverter fun fromActivityType(v: ActivityType) = v.name
    @TypeConverter fun toActivityType(v: String) = ActivityType.valueOf(v)
}

// ── Database ─────────────────────────────────────────────────────────────────
@Database(
    entities = [Lead::class, Activity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AbetDatabase : RoomDatabase() {

    abstract fun leadDao(): LeadDao
    abstract fun activityDao(): ActivityDao

    companion object {
        @Volatile private var INSTANCE: AbetDatabase? = null

        fun getInstance(context: Context): AbetDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AbetDatabase::class.java,
                    "abetcrm.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
