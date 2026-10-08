package app.androcleaner.feature.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.androcleaner.core.trash.DeleteResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class PhotoSection { SIMILAR, BLURRY, SCREENSHOTS, DUPLICATES }

@HiltViewModel
class PhotosViewModel @Inject constructor(private val repository: PhotosRepository) : ViewModel() {

    val state: StateFlow<PhotosState> = repository.state

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _results = Channel<DeleteResult>(Channel.BUFFERED)
    val results = _results.receiveAsFlow()

    private var preselectedFor: PhotoSection? = null

    fun analyze() = repository.analyze()

    fun toggle(path: String) = _selected.update { if (path in it) it - path else it + path }

    fun setSelected(paths: Set<String>) {
        _selected.value = paths
    }

    /** Default selection: everything except the copy we'd keep, or old screenshots. */
    fun preselect(section: PhotoSection) {
        if (preselectedFor == section) return
        val done = state.value as? PhotosState.Done ?: return
        preselectedFor = section
        val monthAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        _selected.value = when (section) {
            PhotoSection.SIMILAR -> done.report.similar.flatMap { g -> g.photos.filter { it != g.best } }.map { it.photo.path }
            PhotoSection.BLURRY -> emptyList()
            PhotoSection.SCREENSHOTS -> done.report.screenshots.filter { it.takenAt < monthAgo }.map { it.path }
            PhotoSection.DUPLICATES -> done.duplicates.flatMap { g -> g.files.filter { it != g.suggestedKeep } }.map { it.path }
        }.toSet()
    }

    fun trash(paths: Set<String> = _selected.value) {
        if (paths.isEmpty()) return
        viewModelScope.launch {
            val result = repository.moveToTrash(paths)
            _selected.update { it - paths }
            _results.send(result)
        }
    }
}
