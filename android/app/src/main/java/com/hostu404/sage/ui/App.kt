package com.hostu404.sage.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hostu404.sage.SageModel
import com.hostu404.sage.Screen
import com.hostu404.sage.data.Relation
import com.hostu404.sage.data.Words

@Composable
fun App(model: SageModel) {
    BackHandler(enabled = model.stack.size > 1) { model.back() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val forecast = model.forecast
        if (forecast == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Surface
        }
        when (val s = model.screen) {
            Screen.Home -> HomeScreen(model, forecast)
            Screen.Search -> SearchScreen(model, forecast)
            Screen.About -> AboutScreen(model, forecast)
            is Screen.Place -> PlaceScreen(model, forecast, s.id)
        }
        model.openRelation?.let { RelationSheet(it) { model.showRelation(null) } }
    }
}

/** Two countries: how tense, the chances, and SAGE's few sentences. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelationSheet(r: Relation, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        RelationDetails(r, Modifier.padding(horizontal = 20.dp).navigationBarsPadding().verticalScroll(rememberScrollState()))
    }
}

@Composable
fun RelationDetails(r: Relation, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(r.title, style = MaterialTheme.typography.headlineSmall)
        Chip("Tension: ${r.tension}", tensionColor(r.tension), Modifier.padding(top = 8.dp))
        Text(r.summary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
        SectionTitle("In the next six months")
        ChanceLine(r.clash6, label = "Deadly clashes")
        ChanceLine(r.war6, Modifier.padding(top = 14.dp), label = "War")
        Text(
            "Deadly clashes: at least one death in fighting between the two governments' forces. " +
                "War: 1,000 or more deaths in a month." +
                (r.lastFought?.let { " They last fought in $it." } ?: ""),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp),
        )
        Disclaimer()
    }
}

/** "Deadly fighting in November: extremely unlikely (under 1%)". */
fun chanceSentence(what: String, month: String?, p: Double): String =
    "$what${month?.let { " in ${Words.month(it)}" } ?: ""}: ${Words.chance(p)} (${Words.pct(p)})"
