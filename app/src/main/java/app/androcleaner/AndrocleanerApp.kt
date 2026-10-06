package app.androcleaner

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import app.androcleaner.core.trash.TrashRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AndrocleanerApp : Application() {

    @Inject lateinit var trash: TrashRepository

    override fun onCreate() {
        super.onCreate()
        MainScope().launch {
            // Fails harmlessly until "All files access" is granted.
            runCatching {
                trash.purgeExpired()
                trash.load()
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun packageManager(@ApplicationContext context: Context): PackageManager = context.packageManager
}
