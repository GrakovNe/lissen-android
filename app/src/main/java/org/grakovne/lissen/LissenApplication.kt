package org.grakovne.lissen

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import org.acra.ReportField
import org.acra.config.httpSender
import org.acra.config.toast
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.acra.sender.HttpSender
import org.grakovne.lissen.common.RunningComponent
import javax.inject.Inject

@HiltAndroidApp
class LissenApplication : Application() {

    @Inject
    lateinit var runningComponents: Set<@JvmSuppressWildcards RunningComponent>

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        runningComponents.forEach { it.onCreate() }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        initAcra {
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON

            httpSender {
                uri = "https://acrarium.grakovne.org/report"
                basicAuthLogin = "6KmG6qnp09eDT4Wo"
                basicAuthPassword = "ZWBOP83LM0SjrOFC"
                httpMethod = HttpSender.Method.POST
                dropReportsOnTimeout = false
            }

            toast {
                text = getString(R.string.app_crach_toast)
            }

            reportContent = listOf(
                ReportField.APP_VERSION_NAME,
                ReportField.APP_VERSION_CODE,
                ReportField.ANDROID_VERSION,
                ReportField.PHONE_MODEL,
                ReportField.STACK_TRACE,
                ReportField.LOGCAT
            )
        }
    }

    companion object {

        lateinit var appContext: Context
            private set
    }
}
