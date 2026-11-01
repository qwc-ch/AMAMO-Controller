package com.gree.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gree.remote.ui.GreeRemoteRoot
import com.gree.remote.ui.theme.GreeRemoteTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as GreeRemoteApp
        setContent {
            GreeRemoteTheme {
                GreeRemoteRoot(app.container)
            }
        }
    }
}