package com.abetworks.abetcrm

import android.app.Application
import com.abetworks.abetcrm.service.SyncManager
import com.abetworks.abetcrm.util.NotificationHelper

class AbetCRMApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        SyncManager.schedulePeriodicSync(this)
    }
}
