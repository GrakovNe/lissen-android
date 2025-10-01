package org.grakovne.lissen

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import org.acra.ReportField
import org.acra.config.httpSender
import org.acra.config.toast
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.acra.security.TLS
import org.acra.sender.HttpSender
import org.grakovne.lissen.common.RunningComponent
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class LissenApplication : Application() {
  @Inject
  lateinit var runningComponents: Set<@JvmSuppressWildcards RunningComponent>

  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    initCrashReporting()
  }

  override fun onCreate() {
    super.onCreate()
    appContext = applicationContext

    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    } else {
      Timber.plant(LoggingReleaseTree())
    }

    runningComponents.forEach { it.onCreate() }
  }

  private fun initCrashReporting() {
    initAcra {
      sharedPreferencesName = "secure_prefs"

      buildConfigClass = BuildConfig::class.java
      reportFormat = StringFormat.JSON

      httpSender {
        uri = "https://acrarium.grakovne.org/report"
        basicAuthLogin = BuildConfig.ACRA_REPORT_LOGIN
        basicAuthPassword = BuildConfig.ACRA_REPORT_PASSWORD
        httpMethod = HttpSender.Method.POST
        dropReportsOnTimeout = false
        tlsProtocols = listOf(TLS.V1_3, TLS.V1_2)
      }

      toast {
        text = getString(R.string.app_crach_toast)
      }

      reportContent =
        listOf(
          ReportField.APP_VERSION_NAME,
          ReportField.APP_VERSION_CODE,
          ReportField.ANDROID_VERSION,
          ReportField.PHONE_MODEL,
          ReportField.STACK_TRACE,
        )
    }
  }

  companion object {
    lateinit var appContext: Context
      private set
  }
}
