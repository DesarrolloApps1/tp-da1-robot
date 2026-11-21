package com.vigia

import android.app.Application
import com.vigia.di.AppContainer

class VigiaApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
