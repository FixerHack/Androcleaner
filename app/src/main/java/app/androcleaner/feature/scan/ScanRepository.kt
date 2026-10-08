package app.androcleaner.feature.scan

import app.androcleaner.core.storage.FileIndex
import app.androcleaner.core.storage.ScanEvent
import app.androcleaner.core.storage.StorageScanner
import app.androcleaner.core.trash.DeleteResult
import app.androcleaner.core.trash.TrashRepository
import app.androcleaner.feature.apps.AppsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(val filesScanned: Int, val bytesScanned: Long, val currentDirectory: String) : ScanState
    data class Done(val index: FileIndex, val junk: List<JunkGroup>) : ScanState {
        val junkBytes: Long = junk.sumOf { it.totalBytes }
    }
}

/** Single source of truth for the latest storage scan, shared by all screens. */
@Singleton
class ScanRepository @Inject constructor(
    private val scanner: StorageScanner,
    private val junkAnalyzer: JunkAnalyzer,
    private val apps: AppsRepository,
    private val trash: TrashRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scanJob: Job? = null

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    val index: FileIndex? get() = (state.value as? ScanState.Done)?.index

    fun startScan() {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch {
            _state.value = ScanState.Scanning(0, 0, "")
            scanner.scan().collect { event ->
                when (event) {
                    is ScanEvent.Progress -> _state.value =
                        ScanState.Scanning(event.filesScanned, event.bytesScanned, event.currentDirectory)
                    is ScanEvent.Finished -> _state.value = analyze(event.index)
                }
            }
        }
    }

    /** Re-scans in the background when files changed outside a scan (e.g. restored from trash). */
    fun refreshIfScanned() {
        if (state.value is ScanState.Done) startScan()
    }

    /** Returns the current index, scanning first if there is none yet. */
    suspend fun awaitIndex(): FileIndex {
        (state.value as? ScanState.Done)?.let { return it.index }
        startScan()
        return state.filterIsInstance<ScanState.Done>().first().index
    }

    /** Deletes junk items: cache-like files permanently, everything else to trash. */
    suspend fun clean(items: List<JunkItem>): DeleteResult {
        val (toTrash, toDelete) = items.partition { it.useTrash }
        val trashed = trash.moveToTrash(toTrash.map { it.path })
        val deleted = trash.deletePermanently(toDelete.map { it.path })
        val failed = trashed.failedPaths + deleted.failedPaths
        removeFromIndex(items.map { it.path }.filterNot { it in failed })
        return DeleteResult(trashed.freedBytes + deleted.freedBytes, failed)
    }

    /** Moves arbitrary user files (large files, duplicates, photos) to trash. */
    suspend fun moveToTrash(paths: List<String>): DeleteResult {
        val result = trash.moveToTrash(paths)
        removeFromIndex(paths.filterNot { it in result.failedPaths })
        return result
    }

    private suspend fun removeFromIndex(paths: List<String>) {
        val current = index ?: return
        _state.value = analyze(current.without(paths))
    }

    private suspend fun analyze(index: FileIndex): ScanState.Done = withContext(Dispatchers.Default) {
        ScanState.Done(index, junkAnalyzer.analyze(index, apps.installedVersions()))
    }
}
