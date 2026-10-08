package app.androcleaner.feature.protection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.androcleaner.core.settings.AppSettings
import app.androcleaner.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ProtectionViewModel @Inject constructor(
    private val repository: ProtectionRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val report = repository.report
    val auditing = repository.auditing
    val cloud = repository.cloud
    val settings = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    init {
        if (repository.report.value == null) repository.audit()
    }

    fun audit() = repository.audit()

    fun cloudCheck(scope: CloudScope) = repository.cloudCheck(scope)

    fun cancelCloud() = repository.cancelCloudCheck()
}
