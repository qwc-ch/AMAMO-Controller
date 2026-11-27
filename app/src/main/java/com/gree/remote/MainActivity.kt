package com.gree.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.gree.remote.ui.GreeRemoteRoot
import com.gree.remote.ui.theme.DisplayPrefs
import com.gree.remote.ui.theme.GreeRemoteTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as GreeRemoteApp
        setContent {
            val displayPrefs by app.container.displayPrefs.collectAsState(initial = DisplayPrefs())
            val displayCustom by app.container.displayCustom.collectAsState(initial = false)
            GreeRemoteTheme(displayPrefs = displayPrefs, displayCustom = displayCustom) {
                val windowSizeClass = calculateWindowSizeClass(this)
                GreeRemoteRoot(app.container, windowSizeClass)
            }
        }
    }
}
