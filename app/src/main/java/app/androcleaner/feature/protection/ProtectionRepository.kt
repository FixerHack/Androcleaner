package app.androcleaner.feature.protection

import app.androcleaner.core.db.ApkReputationDao
import app.androcleaner.core.db.ApkReputationEntity
import app.androcleaner.core.settings.SettingsRepository
import app.androcleaner.core.storage.FileCategory
import app.androcleaner.feature.scan.ScanState
import app.androcleaner.feature.scan.ScanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** An APK to check: an installed app's base APK or an installer file in storage. */
data class ApkTarget(val key: String, val label: String, val path: String, val packageName: String?)

data class ProtectionReport(
    val apps: List<AppRisk>,
    /** APK installer files found by the storage scan. */
    val apkFiles: List<ApkTarget>,
    /** target key -> SHA-256 */
    val hashes: Map<String, String> = emptyMap(),
    val reputation: Map<String, ApkReputationEntity> = emptyMap(),
) {
    fun reputationOf(key: String): ApkReputationEntity? = hashes[key]?.let(reputation::get)

    val threats: List<String>
        get() = (apps.map { it.packageName } + apkFiles.map { it.key }).filter { reputationOf(it)?.isThreat == true }
}

val ApkReputationEntity.isThreat: Boolean
    get() = (vtMalicious ?: 0) >= THREAT_ENGINES || !mbSignature.isNullOrEmpty()

/** 1–2 engines are often false positives; we show them, but don't call it malware. */
val ApkReputationEntity.isSuspicious: Boolean
    get() = !isThreat && ((vtMalicious ?: 0) > 0 || (vtSuspicious ?: 0) > 0)

private const val THREAT_ENGINES = 3

sealed interface CloudState {
    data object Idle : CloudState
    data class Running(val done: Int, val total: Int, val current: String) : CloudState
    data class Finished(val checked: Int, val threats: Int) : CloudState
    data object NoKeys : CloudState
    data object InvalidKey : CloudState
    data class Failed(val message: String) : CloudState
}

enum class CloudScope { SIDELOADED, ALL }

@Singleton
class ProtectionRepository @Inject constructor(
    private val scanner: SecurityScanner,
    private val virusTotal: VirusTotalClient,
    private val malwareBazaar: MalwareBazaarClient,
    private val reputationDao: ApkReputationDao,
    private val settings: SettingsRepository,
    private val scan: ScanRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var auditJob: Job? = null
    private var cloudJob: Job? = null

    private val _report = MutableStateFlow<ProtectionReport?>(null)
    val report: StateFlow<ProtectionReport?> = _report.asStateFlow()

    private val _auditing = MutableStateFlow(false)
    val auditing: StateFlow<Boolean> = _auditing.asStateFlow()

    private val _cloud = MutableStateFlow<CloudState>(CloudState.Idle)
    val cloud: StateFlow<CloudState> = _cloud.asStateFlow()

    fun audit() {
        if (auditJob?.isActive == true) return
        auditJob = scope.launch {
            _auditing.value = true
            val apps = scanner.auditApps()
            val apkFiles = (scan.state.value as? ScanState.Done)?.index?.files.orEmpty()
                .filter { it.category == FileCategory.APKS && it.name.endsWith(".apk", ignoreCase = true) }
                .map { ApkTarget(it.path, it.name, it.path, null) }
            val previous = _report.value
            // Keep cloud results we already have for unchanged APKs.
            val hashes = HashMap<String, String>()
            (apps.map { ApkTarget(it.packageName, it.label, it.apkPath, it.packageName) } + apkFiles).forEach { target ->
                scanner.sha256Cached(target.path)?.let { hashes[target.key] = it }
            }
            val reputation = reputationDao.get(hashes.values.toList()).associateBy { it.sha256 }
            _report.value = ProtectionReport(apps, apkFiles, hashes, previous?.reputation.orEmpty() + reputation)
            _auditing.value = false
        }
    }

    fun cloudCheck(scopeOf: CloudScope) {
        if (cloudJob?.isActive == true) return
        cloudJob = scope.launch {
            val vtKey = settings.virusTotalKey()
            val mbKey = settings.malwareBazaarKey()
            if (vtKey == null && mbKey == null) {
                _cloud.value = CloudState.NoKeys
                return@launch
            }
            val report = _report.value ?: return@launch
            val targets = report.apps
                .filter { scopeOf == CloudScope.ALL || it.findings.contains(Finding.SIDELOADED) }
                .map { ApkTarget(it.packageName, it.label, it.apkPath, it.packageName) } + report.apkFiles

            val freshAfter = System.currentTimeMillis() - CACHE_MS
            var checked = 0
            for ((i, target) in targets.withIndex()) {
                _cloud.value = CloudState.Running(i, targets.size, target.label)
                val sha = scanner.sha256(target.path) ?: continue
                _report.update { it?.copy(hashes = it.hashes + (target.key to sha)) }

                val cached = _report.value?.reputation?.get(sha) ?: reputationDao.get(listOf(sha)).firstOrNull()
                val vtFresh = vtKey == null || (cached?.vtMalicious != null && cached.checkedAt > freshAfter)
                val mbFresh = mbKey == null || (cached?.mbSignature != null && cached.checkedAt > freshAfter)
                if (vtFresh && mbFresh && cached != null) {
                    checked++
                    continue
                }

                var entity = cached ?: ApkReputationEntity(sha, 0, null, null, null, null)
                if (!mbFresh) {
                    when (val r = malwareBazaar.lookup(sha, mbKey)) {
                        is LookupResult.Found -> entity = entity.copy(mbSignature = r.value)
                        LookupResult.NotFound -> entity = entity.copy(mbSignature = "")
                        LookupResult.InvalidKey -> { _cloud.value = CloudState.InvalidKey; return@launch }
                        else -> Unit
                    }
                }
                if (!vtFresh) {
                    var result = virusTotal.lookup(sha, vtKey)
                    if (result == LookupResult.RateLimited) {
                        delay(TimeUnit.SECONDS.toMillis(61))
                        result = virusTotal.lookup(sha, vtKey)
                    }
                    when (result) {
                        is LookupResult.Found -> entity = entity.copy(
                            vtMalicious = result.value.malicious,
                            vtSuspicious = result.value.suspicious,
                            vtTotal = result.value.total,
                        )
                        LookupResult.NotFound -> entity = entity.copy(vtMalicious = -1, vtSuspicious = 0, vtTotal = 0)
                        LookupResult.InvalidKey -> { _cloud.value = CloudState.InvalidKey; return@launch }
                        is LookupResult.Failed -> { _cloud.value = CloudState.Failed(result.message); return@launch }
                        LookupResult.RateLimited -> Unit
                    }
                    // Free VirusTotal keys allow 4 requests per minute.
                    if (i < targets.lastIndex) delay(VT_SPACING_MS)
                }
                entity = entity.copy(checkedAt = System.currentTimeMillis())
                reputationDao.put(entity)
                _report.update { it?.copy(reputation = it.reputation + (sha to entity)) }
                checked++
            }
            _cloud.value = CloudState.Finished(checked, _report.value?.threats?.size ?: 0)
        }
    }

    fun cancelCloudCheck() {
        cloudJob?.cancel()
        _cloud.value = CloudState.Idle
    }

    private companion object {
        val CACHE_MS = TimeUnit.DAYS.toMillis(7)
        const val VT_SPACING_MS = 15_500L
    }
}
