package com.hostu404.sage.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hostu404.sage.SageModel
import com.hostu404.sage.Screen
import com.hostu404.sage.data.Forecast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(model: SageModel, f: Forecast) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { model.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                title = {
                    TextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text("Find a country") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
            )
        },
    ) { pad ->
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        val q = query.trim()
        val matches = f.places.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
            .sortedBy { if (it.name.startsWith(q, ignoreCase = true)) 0 else 1 }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(),
            bottom = pad.calculateBottomPadding() + 16.dp)) {
            if (matches.isEmpty()) item {
                Text("No country called \"$q\". SAGE covers every country with organised violence since 1989, " +
                    "and those in tense relations with another.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
            }
            items(matches, key = { it.id }) { place ->
                Row(Modifier.fillMaxWidth().clickable { model.open(Screen.Place(place.id)) }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(place.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (place.country?.alert?.on == true) AlertChip()
                    Chip(place.country?.status ?: "Relations only", statusColor(place.country),
                        Modifier.padding(start = 6.dp))
                }
                HorizontalDivider()
            }
        }
    }
}
