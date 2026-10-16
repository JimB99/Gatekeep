package com.gatekeep.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.gatekeep.app.support.AnrWatchdog

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        return super.newApplication(cl, GatekeepHiltTestApplication_Application::class.java.name, context)
    }

    override fun onStart() {
        AnrWatchdog.start(this)
        super.onStart()
    }
}
