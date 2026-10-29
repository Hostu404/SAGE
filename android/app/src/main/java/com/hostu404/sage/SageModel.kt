package com.hostu404.sage

import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hostu404.sage.data.Forecast
import com.hostu404.sage.data.ForecastStore
import com.hostu404.sage.data.Prefs
import com.hostu404.sage.data.Refresh
import com.hostu404.sage.data.Seen
import com.hostu404.sage.data.Watchlist
import com.hostu404.sage.data.WhereYouAre
import com.hostu404.sage.data.WorldShapes
import com.hostu404.sage.widget.SageWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object About : Screen
    data class Place(val id: Int) : Screen
    /** Two countries, lower id first as in the forecast file. */
    data class Pair(val a: Int, val b: Int) : Screen
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
    /** Your watchlist as you last marked it seen. */
    var seen by mutableStateOf(prefs.seen)
        private set
    /** What the home map and its list show: "fighting" or "major_war". */
    var measure by mutableStateOf("fighting")

    /** Where you were on the home screen, so coming back puts you there again. */
    val homeList = LazyListState()

    private var lastRefresh = 0L

    val stack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = stack.last()

    init {
        viewModelScope.launch {
            val f = withContext(Dispatchers.IO) { store.load() }
            forecast = f
            if (seen == null) markSeen()
        }
        // The map takes a moment to read; have it ready before it's needed.
        viewModelScope.launch(Dispatchers.Default) { WorldShapes.get(app) }
    }

    /** For previews and tests: show this forecast, without touching the network. */
    fun show(f: Forecast, here: String? = null, seen: Seen? = null) {
        forecast = f
        if (here != null) this.here = here
        this.seen = seen ?: Watchlist.snapshot(f, watched(f))
    }

    /** Where you are first, then the countries you follow, by name. */
    fun watched(f: Forecast): List<Int> {
        val hereId = here?.let { f.placeForIso2(it)?.id }
        val rest = followed.filter { it != hereId }.sortedBy { f.place(it)?.name ?: "" }
        return listOfNotNull(hereId) + rest
    }

    /** Looks for a newer forecast. Runs when the app comes to the front; at most once an hour. */
    fun refresh() {
        if (!autoRefresh || refreshing || System.currentTimeMillis() - lastRefresh < HOUR) return
        lastRefresh = System.currentTimeMillis()
        refreshing = true
        viewModelScope.launch {
            val app = getApplication<Application>()
            // A refresh that goes wrong in any way just leaves the forecast you have.
            val (result, found) = withContext(Dispatchers.IO) {
                runCatching { store.refresh() }.getOrElse { Refresh.Failed(it.message ?: "couldn't check") } to
                    runCatching { WhereYouAre.fromConnection() }.getOrNull()
            }
            if (found != null) {
                here = found
                prefs.here = found
            }
            if (result is Refresh.Updated) forecast = result.new
            forecast?.let { f -> watched(f).filter { seen?.countries?.containsKey(it) == false }.forEach { addToSeen(f, it) } }
            refreshing = false
            withContext(Dispatchers.IO) { SageWidget.updateAll(app) }
        }
    }

    /** Takes what your countries look like now as the starting point for "since you last looked". */
    fun markSeen() {
        val f = forecast ?: return
        seen = Watchlist.snapshot(f, watched(f))
        prefs.seen = seen
    }

    private fun addToSeen(f: Forecast, id: Int) {
        val s = seen ?: return
        seen = s.with(id, f)
        prefs.seen = seen
    }

    fun open(s: Screen) {
        if (s == Screen.Home) stack.removeRange(1, stack.size) else stack.add(s)
    }

    /** Back one screen; false if already home. */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun toggleFollow(id: Int) {
        followed = if (id in followed) followed - id else followed + id
        prefs.followed = followed
        val f = forecast ?: return
        // A new country starts from how it looks now, so what moves later shows.
        if (id in followed && seen?.countries?.containsKey(id) == false) addToSeen(f, id)
    }

    private companion object {
        const val HOUR = 60 * 60 * 1000L
    }
}
