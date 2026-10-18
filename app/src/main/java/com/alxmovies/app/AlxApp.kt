package com.alxmovies.app

import android.app.Application
import com.alxmovies.app.data.Graph

class AlxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
