package com.hostu404.sage

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hostu404.sage.data.Changes
import com.hostu404.sage.data.Forecast
import com.hostu404.sage.data.ForecastStore
import com.hostu404.sage.data.Notice
import com.hostu404.sage.data.Prefs
import com.hostu404.sage.data.Refresh
import com.hostu404.sage.data.Relation
import com.hostu404.sage.data.WhereYouAre
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object About : Screen
    data class Place(val id: Int) : Screen
}

class SageModel(app: Application, private val autoRefresh: Boolean) : AndroidViewModel(app) {
    constructor(app: Application) : this(app, autoRefresh = true)

    private val store = ForecastStore(app)
    private val prefs = Prefs(app)

    var forecast by mutableStateOf<Forecast?>(null)
        private set
    var followed by mutableStateOf(prefs.followed)
        private set
    /** The country you're in, as a two-letter code: from your connection, else the phone. */
    var here by mutableStateOf(prefs.here ?: WhereYouAre.fromPhone(app))
        private set
    var refreshing by mutableStateOf(false)
        private set
    /** What got worse in your countries since the forecast you last saw. */
    var changes by mutableStateOf<List<Notice>>(emptyList())
        private set
    /** The two countries whose relations are open in a sheet. */
    var openRelation by mutableStateOf<Relation?>(null)
        private set

    private var lastRefresh = 0L

    val stack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = stack.last()

    init {
        viewModelScope.launch {
            forecast = withContext(Dispatchers.IO) { store.load() }
        }
    }

    /** For previews and tests: show this forecast, without touching the network. */
    fun show(f: Forecast, here: String? = null) {
        forecast = f
        if (here != null) this.here = here
    }

    /** The countries whose changes are worth pointing out: where you are, and those you follow. */
    private fun watched(f: Forecast): Set<Int> = followed + listOfNotNull(here?.let { f.placeForIso2(it)?.id })

    /** Looks for a newer forecast. Runs when the app comes to the front; at most once an hour. */
    fun refresh() {
        if (!autoRefresh || refreshing || System.currentTimeMillis() - lastRefresh < HOUR) return
        lastRefresh = System.currentTimeMillis()
        refreshing = true
        viewModelScope.launch {
            val (result, found) = withContext(Dispatchers.IO) { store.refresh() to WhereYouAre.fromConnection() }
            if (found != null) {
                here = found
                prefs.here = found
            }
            if (result is Refresh.Updated) {
                forecast = result.new
                result.old?.let { changes = Changes.between(it, result.new, watched(result.new)) }
            }
            refreshing = false
        }
    }

    fun open(s: Screen) {
        if (s == Screen.Home) stack.removeRange(1, stack.size) else stack.add(s)
    }

    /** Back one screen; false if already home. */
    fun back(): Boolean {
        if (openRelation != null) { openRelation = null; return true }
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun showRelation(r: Relation?) {
        openRelation = r
    }

    fun toggleFollow(id: Int) {
        followed = if (id in followed) followed - id else followed + id
        prefs.followed = followed
    }

    fun dismissChanges() {
        changes = emptyList()
    }

    private companion object {
        const val HOUR = 60 * 60 * 1000L
    }
}
