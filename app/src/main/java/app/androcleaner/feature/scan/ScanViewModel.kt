package app.androcleaner.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.androcleaner.core.storage.DeviceStorage
import app.androcleaner.core.storage.StorageInfo
import app.androcleaner.core.trash.DeleteResult
import app.androcleaner.feature.apps.AppsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ScanUiState(
    val scan: ScanState = ScanState.Idle,
    val selected: Set<String> = emptySet(),
    val expanded: Set<JunkType> = emptySet(),
    val storage: StorageInfo? = null,
    val appCacheBytes: Long? = null,
    val cleaning: Boolean = false,
) {
    val selectedBytes: Long
        get() = (scan as? ScanState.Done)?.junk?.sumOf { g -> g.items.sumOf { if (it.path in selected) it.size else 0L } } ?: 0L
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: ScanRepository,
    private val deviceStorage: DeviceStorage,
    private val apps: AppsRepository,
) : ViewModel() {

    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val expanded = MutableStateFlow<Set<JunkType>>(emptySet())
    private val storage = MutableStateFlow<StorageInfo?>(null)
    private val appCache = MutableStateFlow<Long?>(null)
    private val cleaning = MutableStateFlow(false)
    private var selectionForScan = 0L

    private val _results = Channel<DeleteResult>(Channel.BUFFERED)
    val results = _results.receiveAsFlow()

    val uiState = combine(repository.state, selected, expanded, storage, combine(appCache, cleaning, ::Pair)) {
            scan, sel, exp, info, (cache, isCleaning) ->
        ScanUiState(scan, sel, exp, info, cache, isCleaning)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanUiState())

    init {
        refreshStats()
        viewModelScope.launch {
            repository.state.collect { state ->
                if (state !is ScanState.Done) return@collect
                val all = state.junk.flatMap { it.items }
                if (state.index.scannedAt != selectionForScan) {
                    selectionForScan = state.index.scannedAt
                    selected.value = all.filter { it.preselected }.mapTo(HashSet()) { it.path }
                } else {
                    val paths = all.mapTo(HashSet()) { it.path }
                    selected.update { it intersect paths }
                }
            }
        }
    }

    fun scan() = repository.startScan()

    fun toggleItem(path: String) = selected.update { if (path in it) it - path else it + path }

    fun toggleGroup(group: JunkGroup) = selected.update { current ->
        val paths = group.items.map { it.path }
        if (current.containsAll(paths)) current - paths.toSet() else current + paths
    }

    fun toggleExpanded(type: JunkType) = expanded.update { if (type in it) it - type else it + type }

    fun cleanSelected() {
        val done = repository.state.value as? ScanState.Done ?: return
        val items = done.junk.flatMap { it.items }.filter { it.path in selected.value }
        if (items.isEmpty()) return
        viewModelScope.launch {
            cleaning.value = true
            val result = repository.clean(items)
            cleaning.value = false
            _results.send(result)
            refreshStats()
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                storage.value = runCatching { deviceStorage.info() }.getOrNull()
                appCache.value = apps.loadApps().mapNotNull { it.sizes?.cacheBytes }
                    .takeIf { it.isNotEmpty() }?.sum()
            }
        }
    }
}
