package app.androcleaner.feature.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.androcleaner.core.storage.DeviceStorage
import app.androcleaner.core.storage.FileCategory
import app.androcleaner.core.storage.IndexedFile
import app.androcleaner.core.storage.StorageInfo
import app.androcleaner.core.trash.DeleteResult
import app.androcleaner.feature.scan.ScanRepository
import app.androcleaner.feature.scan.ScanState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CategorySlice(val category: FileCategory?, val bytes: Long)

data class StorageUiState(
    val scanning: Boolean = false,
    val hasIndex: Boolean = false,
    val storage: StorageInfo? = null,
    /** Indexed categories plus a null-category slice for system & apps. */
    val slices: List<CategorySlice> = emptyList(),
    val filter: FileCategory? = null,
    val largest: List<IndexedFile> = emptyList(),
    val selected: Set<String> = emptySet(),
) {
    val selectedBytes: Long get() = largest.sumOf { if (it.path in selected) it.size else 0L }
}

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val repository: ScanRepository,
    private val deviceStorage: DeviceStorage,
) : ViewModel() {

    private val filter = MutableStateFlow<FileCategory?>(null)
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val storage = MutableStateFlow<StorageInfo?>(null)

    private val _results = Channel<DeleteResult>(Channel.BUFFERED)
    val results = _results.receiveAsFlow()

    val uiState = combine(repository.state, filter, selected, storage) { scan, cat, sel, info ->
        val done = scan as? ScanState.Done
        val slices = buildList {
            if (done != null) {
                FileCategory.entries.forEach { c -> done.index.bytesByCategory[c]?.let { add(CategorySlice(c, it)) } }
                if (info != null) add(CategorySlice(null, (info.usedBytes - done.index.totalBytes).coerceAtLeast(0)))
            }
        }.sortedByDescending { it.bytes }
        val largest = done?.index?.files.orEmpty()
            .asSequence()
            .filter { cat == null || it.category == cat }
            .sortedByDescending { it.size }
            .take(LARGEST_LIMIT)
            .toList()
        StorageUiState(
            scanning = scan is ScanState.Scanning,
            hasIndex = done != null,
            storage = info,
            slices = slices,
            filter = cat,
            largest = largest,
            selected = sel intersect largest.mapTo(HashSet()) { it.path },
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StorageUiState())

    init {
        refreshStorage()
    }

    fun scan() = repository.startScan()

    fun setFilter(category: FileCategory?) {
        filter.value = category
    }

    fun toggle(path: String) = selected.update { if (path in it) it - path else it + path }

    fun moveSelectedToTrash() {
        val paths = uiState.value.selected.toList()
        if (paths.isEmpty()) return
        viewModelScope.launch {
            val result = repository.moveToTrash(paths)
            selected.value = emptySet()
            _results.send(result)
            refreshStorage()
        }
    }

    private fun refreshStorage() {
        viewModelScope.launch {
            storage.value = withContext(Dispatchers.IO) { runCatching { deviceStorage.info() }.getOrNull() }
        }
    }

    private companion object {
        const val LARGEST_LIMIT = 100
    }
}
