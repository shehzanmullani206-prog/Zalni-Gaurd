package com.example

import android.app.Application
import com.example.data.BlockListRepository

/**
 * Application class providing singleton access to repositories and app-wide state.
 */
class ZalniGuardApplication : Application() {

    lateinit var blockListRepository: BlockListRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        blockListRepository = BlockListRepository(this)
    }

    companion object {
        lateinit var instance: ZalniGuardApplication
            private set
    }
}
