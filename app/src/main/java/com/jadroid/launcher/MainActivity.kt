package com.jadroid.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jadroid.launcher.di.AppContainer
import com.jadroid.launcher.ui.JadroidRoot

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as JadroidApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            JadroidRoot(container)
        }
    }
}
