package app.androcleaner.feature.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.androcleaner.core.trash.TrashEntry
import app.androcleaner.core.trash.TrashRepository
import app.androcleaner.feature.scan.ScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrashUiState(val entries: List<TrashEntry> = emptyList(), val selected: Set<String> = emptySet()) {
    val totalBytes: Long get() = entries.sumOf { it.size }
}

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val trash: TrashRepository,
    private val scan: ScanRepository,
) : ViewModel() {

    private val selected = MutableStateFlow<Set<String>>(emptySet())

    val uiState = combine(trash.entries, selected) { entries, sel ->
        TrashUiState(entries, sel intersect entries.mapTo(HashSet()) { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashUiState())

    init {
        viewModelScope.launch { runCatching { trash.load() } }
    }

    fun toggle(id: String) = selected.update { if (id in it) it - id else it + id }

    fun selectAll() = selected.update { uiState.value.entries.mapTo(HashSet()) { it.id } }

    fun restoreSelected() = viewModelScope.launch {
        trash.restore(uiState.value.selected)
        selected.value = emptySet()
        scan.refreshIfScanned()
    }

    fun deleteSelected() = viewModelScope.launch {
        trash.deleteForever(uiState.value.selected)
        selected.value = emptySet()
    }
}
