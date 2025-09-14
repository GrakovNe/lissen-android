package org.grakovne.lissen.wear

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LissenWearApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    appContext = applicationContext
  }

  companion object {
    lateinit var appContext: Context
      private set
  }
}