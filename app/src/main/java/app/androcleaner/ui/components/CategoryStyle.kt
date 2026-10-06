package app.androcleaner.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.androcleaner.R
import app.androcleaner.core.storage.FileCategory
import app.androcleaner.ui.theme.CategoryColors

val FileCategory.color: Color
    get() = when (this) {
        FileCategory.IMAGES -> CategoryColors.Images
        FileCategory.VIDEOS -> CategoryColors.Videos
        FileCategory.AUDIO -> CategoryColors.Audio
        FileCategory.DOCUMENTS -> CategoryColors.Documents
        FileCategory.APKS -> CategoryColors.Apks
        FileCategory.ARCHIVES -> CategoryColors.Archives
        FileCategory.OTHER -> CategoryColors.Other
    }

val FileCategory.icon: ImageVector
    get() = when (this) {
        FileCategory.IMAGES -> Icons.Rounded.Image
        FileCategory.VIDEOS -> Icons.Rounded.Movie
        FileCategory.AUDIO -> Icons.Rounded.Audiotrack
        FileCategory.DOCUMENTS -> Icons.Rounded.Description
        FileCategory.APKS -> Icons.Rounded.Android
        FileCategory.ARCHIVES -> Icons.Rounded.Archive
        FileCategory.OTHER -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }

val FileCategory.label: Int
    get() = when (this) {
        FileCategory.IMAGES -> R.string.cat_images
        FileCategory.VIDEOS -> R.string.cat_videos
        FileCategory.AUDIO -> R.string.cat_audio
        FileCategory.DOCUMENTS -> R.string.cat_documents
        FileCategory.APKS -> R.string.cat_apks
        FileCategory.ARCHIVES -> R.string.cat_archives
        FileCategory.OTHER -> R.string.cat_other
    }
