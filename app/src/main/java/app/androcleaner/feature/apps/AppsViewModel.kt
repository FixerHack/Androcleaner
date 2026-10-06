package app.androcleaner.feature.apps

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class AppSort { SIZE, CACHE, UNUSED }

data class AppsUiState(
    val loading: Boolean = true,
    val apps: List<AppInfo> = emptyList(),
    val sort: AppSort = AppSort.SIZE,
    val showSystem: Boolean = false,
    val hasSizes: Boolean = true,
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val repository: AppsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val all = MutableStateFlow<List<AppInfo>?>(null)
    private val sort = MutableStateFlow(
        if (savedStateHandle.get<Boolean>("sortByCache") == true) AppSort.CACHE else AppSort.SIZE,
    )
    private val showSystem = MutableStateFlow(false)

    val uiState = combine(all, sort, showSystem) { apps, s, system ->
        if (apps == null) return@combine AppsUiState(sort = s, showSystem = system)
        val unusedBefore = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(UNUSED_DAYS)
        val visible = apps
            .filter { system || !it.isSystem }
            .let { list ->
                when (s) {
                    AppSort.SIZE -> list.sortedByDescending { it.sizes?.totalBytes ?: 0 }
                    AppSort.CACHE -> list.sortedByDescending { it.sizes?.cacheBytes ?: 0 }
                    AppSort.UNUSED -> list
                        .filter { (it.lastUsedAt ?: 0) < unusedBefore && it.installedAt < unusedBefore }
                        .sortedBy { it.lastUsedAt ?: 0 }
                }
            }
        AppsUiState(
            loading = false,
            apps = visible,
            sort = s,
            showSystem = system,
            hasSizes = apps.any { it.sizes != null },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    fun refresh() {
        viewModelScope.launch { all.value = repository.loadApps() }
    }

    fun setSort(value: AppSort) {
        sort.value = value
    }

    fun toggleSystem() {
        showSystem.value = !showSystem.value
    }

    private companion object {
        const val UNUSED_DAYS = 30L
    }
}
