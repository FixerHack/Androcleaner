package app.androcleaner.core.storage

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class StorageInfo(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = totalBytes - freeBytes
    val usedFraction: Float get() = if (totalBytes == 0L) 0f else usedBytes.toFloat() / totalBytes
}

@Singleton
class DeviceStorage @Inject constructor(@ApplicationContext private val context: Context) {
    fun info(): StorageInfo {
        val stats = context.getSystemService(StorageStatsManager::class.java)
        return StorageInfo(
            totalBytes = stats.getTotalBytes(StorageManager.UUID_DEFAULT),
            freeBytes = stats.getFreeBytes(StorageManager.UUID_DEFAULT),
        )
    }
}
