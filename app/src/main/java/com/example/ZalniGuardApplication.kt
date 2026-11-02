package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.data.BlockListRepository

/**
 * Application class providing singleton access to repositories and handling system memory pressure.
 */
class ZalniGuardApplication : Application() {

    lateinit var blockListRepository: BlockListRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        blockListRepository = BlockListRepository(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            // Trim any runtime resources when low memory occurs on 4 GB RAM devices
            System.gc()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        System.gc()
    }

    companion object {
        lateinit var instance: ZalniGuardApplication
            private set
    }
}
