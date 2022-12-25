package kyklab.quiet

import android.app.Application
import android.content.Context
import com.google.android.material.color.DynamicColors

class App : Application() {
    companion object {
        private lateinit var application: Application
        val context: Context
            get() = application.applicationContext
    }

    override fun onCreate() {
        super.onCreate()

        application = this

        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}