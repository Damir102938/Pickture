package com.moviepicker

import android.app.Application
import android.content.Context
import com.moviepicker.work.Notifications
import com.moviepicker.work.ReleaseCheckWorker

class MoviePickerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        ReleaseCheckWorker.schedule(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as MoviePickerApp).container
