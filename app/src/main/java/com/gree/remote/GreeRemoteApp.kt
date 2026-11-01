package com.gree.remote

import android.app.Application

class GreeRemoteApp : Application(), HasContainer {

    override val container: AppContainer by lazy { AppContainer(this) }
}