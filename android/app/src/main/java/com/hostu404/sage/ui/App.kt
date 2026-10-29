package com.hostu404.sage.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hostu404.sage.SageModel
import com.hostu404.sage.Screen

@Composable
fun App(model: SageModel) {
    BackHandler(enabled = model.stack.size > 1) { model.back() }
    Surface(Modifier.fillMaxSize(), color = Sage.bg) {
        val forecast = model.forecast
        if (forecast == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Sage.muted) }
            return@Surface
        }
        when (val s = model.screen) {
            Screen.Home -> HomeScreen(model, forecast)
            Screen.Search -> SearchScreen(model, forecast)
            Screen.About -> AboutScreen(model, forecast)
            is Screen.Place -> PlaceScreen(model, forecast, s.id)
            is Screen.Pair -> PairScreen(model, forecast, s.a, s.b)
        }
    }
}
