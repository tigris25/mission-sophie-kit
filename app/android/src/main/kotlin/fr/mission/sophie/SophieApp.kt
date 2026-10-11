package fr.mission.sophie

import android.app.Application

class SophieApp : Application() {
    lateinit var controller: GameController
        private set

    override fun onCreate() {
        super.onCreate()
        controller = GameController(this)
    }
}
