package app.androcleaner.core.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Full-content hash of a file, valid while size and mtime are unchanged. */
@Entity(tableName = "file_hashes")
data class FileHashEntity(
    @PrimaryKey val path: String,
    val size: Long,
    val modified: Long,
    val hash: String,
)

/** Perceptual hash and sharpness of a photo, valid while the MediaStore row is unchanged. */
@Entity(tableName = "photo_features")
data class PhotoFeatureEntity(
    @PrimaryKey val mediaId: Long,
    val modified: Long,
    val size: Long,
    val dHash: Long,
    val sharpness: Float,
)

/** Security scan result of an installed app or an APK file, keyed by SHA-256. */
@Entity(tableName = "apk_reputation")
data class ApkReputationEntity(
    @PrimaryKey val sha256: String,
    val checkedAt: Long,
    /** null = not checked / no key; -1 = unknown to VirusTotal. */
    val vtMalicious: Int?,
    val vtSuspicious: Int?,
    val vtTotal: Int?,
    /** null = not checked; "" = not found in MalwareBazaar; otherwise malware family/signature. */
    val mbSignature: String?,
)

@Dao
interface FileHashDao {
    @Query("SELECT * FROM file_hashes WHERE path IN (:paths)")
    suspend fun get(paths: List<String>): List<FileHashEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(items: List<FileHashEntity>)
}

@Dao
interface PhotoFeatureDao {
    @Query("SELECT * FROM photo_features")
    suspend fun all(): List<PhotoFeatureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(items: List<PhotoFeatureEntity>)
}

@Dao
interface ApkReputationDao {
    @Query("SELECT * FROM apk_reputation WHERE sha256 IN (:hashes)")
    suspend fun get(hashes: List<String>): List<ApkReputationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(item: ApkReputationEntity)
}

@Database(
    entities = [FileHashEntity::class, PhotoFeatureEntity::class, ApkReputationEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileHashes(): FileHashDao
    abstract fun photoFeatures(): PhotoFeatureDao
    abstract fun apkReputation(): ApkReputationDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "androcleaner.db")
            // Everything here is a cache that can be recomputed.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun fileHashes(db: AppDatabase) = db.fileHashes()

    @Provides fun photoFeatures(db: AppDatabase) = db.photoFeatures()

    @Provides fun apkReputation(db: AppDatabase) = db.apkReputation()
}
