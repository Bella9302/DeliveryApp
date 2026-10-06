package com.itmda3b33.deliveryapp

import android.app.Application

/** Application class. Holds the [AppContainer] for the whole app. */
class DeliveryApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}
