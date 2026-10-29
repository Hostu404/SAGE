package com.hostu404.sage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.hostu404.sage.SageModel
import com.hostu404.sage.Screen
import com.hostu404.sage.data.Forecast
import com.hostu404.sage.data.Search

/** Every country, or those matching what you type ("uk", "cote", "drc" work too). */
@Composable
fun SearchScreen(model: SageModel, f: Forecast) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val found = if (query.isBlank()) f.places else Search.find(f, query)
    Column(Modifier.fillMaxSize().background(Sage.bg)) {
        TopBar(title = "Find a country", onBack = { model.back() })
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search countries", color = Sage.faint) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Sage.muted) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { found.firstOrNull()?.let { model.open(Screen.Place(it.id)) } }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Sage.muted, unfocusedBorderColor = Sage.lineStrong,
                focusedContainerColor = Sage.panel, unfocusedContainerColor = Sage.panel,
                focusedTextColor = Sage.text, unfocusedTextColor = Sage.text, cursorColor = Sage.text,
            ),
            modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus),
        )
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom + 8.dp)) {
            item {
                if (found.isEmpty()) {
                    Body("No country by that name. SAGE covers every country with organised violence since 1989, and those in tense relations with another.",
                        color = Sage.muted)
                } else Panel(if (query.isBlank()) "Every country" else "${found.size} found", right = { Note("Fighting in ${nextMonth(f)}") }) {
                    found.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(color = Sage.line)
                        ListRow({ model.open(Screen.Place(p.id)) }) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Name(p.name, Modifier.weight(1f, fill = false), weight = FontWeight.SemiBold)
                                if (p.country?.alert?.on == true) Flag()
                            }
                            StatusTag(p.country)
                            Pct(chanceOf(p.country, "fighting"), Modifier.width(44.dp))
                        }
                    }
                }
            }
        }
    }
}
