package com.jadroid.launcher

import android.app.Application
import com.jadroid.launcher.di.AppContainer

class JadroidApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.initialize()
    }
}
