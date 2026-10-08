package app.androcleaner.feature.photos

import app.androcleaner.core.storage.DuplicateFinder
import app.androcleaner.core.storage.DuplicateGroup
import app.androcleaner.core.trash.DeleteResult
import app.androcleaner.feature.scan.ScanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PhotosState {
    data object Idle : PhotosState
    data class Analyzing(val stage: Stage, val done: Int, val total: Int) : PhotosState
    data class Done(val report: PhotoReport, val duplicates: List<DuplicateGroup>) : PhotosState

    enum class Stage { SCANNING_FILES, PHOTOS, DUPLICATES }
}

@Singleton
class PhotosRepository @Inject constructor(
    private val analyzer: PhotoAnalyzer,
    private val duplicateFinder: DuplicateFinder,
    private val scan: ScanRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _state = MutableStateFlow<PhotosState>(PhotosState.Idle)
    val state: StateFlow<PhotosState> = _state.asStateFlow()

    fun analyze() {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = PhotosState.Analyzing(PhotosState.Stage.SCANNING_FILES, 0, 0)
            val index = scan.awaitIndex()
            val report = analyzer.analyze { done, total ->
                _state.value = PhotosState.Analyzing(PhotosState.Stage.PHOTOS, done, total)
            }
            val duplicates = duplicateFinder.find(index) { done, total ->
                _state.value = PhotosState.Analyzing(PhotosState.Stage.DUPLICATES, done, total)
            }
            _state.value = PhotosState.Done(report, duplicates)
        }
    }

    /** Moves files to the Androcleaner trash and drops them from the current results. */
    suspend fun moveToTrash(paths: Collection<String>): DeleteResult {
        val result = scan.moveToTrash(paths.toList())
        val removed = paths.toSet() - result.failedPaths.toSet()
        val done = _state.value as? PhotosState.Done ?: return result
        fun keep(path: String) = path !in removed
        val photos = done.report.photos.filter { keep(it.photo.path) }
        _state.value = PhotosState.Done(
            report = done.report.copy(
                photos = photos,
                similar = done.report.similar
                    .map { g -> g.copy(photos = g.photos.filter { keep(it.photo.path) }) }
                    .filter { it.photos.size > 1 },
                blurry = done.report.blurry.filter { keep(it.photo.path) },
                screenshots = done.report.screenshots.filter { keep(it.path) },
            ),
            duplicates = done.duplicates
                .map { g -> g.copy(files = g.files.filter { keep(it.path) }) }
                .filter { it.files.size > 1 },
        )
        return result
    }
}
