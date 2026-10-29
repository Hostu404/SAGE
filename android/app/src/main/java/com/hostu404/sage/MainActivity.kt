package com.hostu404.sage

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.hostu404.sage.ui.App
import com.hostu404.sage.ui.SageTheme

class MainActivity : ComponentActivity() {
    private val model: SageModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark bars with light icons, like the rest of the app.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent { SageTheme { App(model) } }
    }

    override fun onResume() {
        super.onResume()
        // Coming back to the app is when it looks for a newer forecast.
        model.refresh()
    }
}
