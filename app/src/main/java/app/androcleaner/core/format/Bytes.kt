package app.androcleaner.core.format

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Human readable size, e.g. "4.2 GB", using the device locale. */
@Composable
fun formatBytes(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)
