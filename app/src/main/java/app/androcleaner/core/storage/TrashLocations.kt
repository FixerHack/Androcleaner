package app.androcleaner.core.storage

import android.os.Environment
import java.io.File

object TrashLocations {
    const val DIRECTORY_NAME = ".androcleaner-trash"

    val root: File get() = File(Environment.getExternalStorageDirectory(), DIRECTORY_NAME)
}
