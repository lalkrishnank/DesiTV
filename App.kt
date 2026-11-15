package com.syam.desitv

import android.app.Application
import com.syam.desitv.sync.SyncScheduler

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncScheduler.ensureScheduled(this)
    }
}
