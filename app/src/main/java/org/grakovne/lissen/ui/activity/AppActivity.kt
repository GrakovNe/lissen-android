package org.grakovne.lissen.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import org.grakovne.lissen.ui.screens.AppScreen
import org.grakovne.lissen.ui.theme.LissenTheme

@AndroidEntryPoint
class AppActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            enableEdgeToEdge()
            setContent {
                LissenTheme {
                    AppScreen()
                }
            }
        }
    }
}