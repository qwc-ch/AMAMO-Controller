package com.gree.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.gree.remote.ui.GreeRemoteRoot
import com.gree.remote.ui.theme.GreeRemoteTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as GreeRemoteApp
        setContent {
            // 动态取色偏好由设置页控制（设置 → 通用），默认开启
            val dynamicColor by app.container.dynamicColor.collectAsState(initial = true)
            GreeRemoteTheme(dynamicColor = dynamicColor) {
                GreeRemoteRoot(app.container)
            }
        }
    }
}
