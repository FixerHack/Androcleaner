package app.androcleaner.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("No SnackbarHostState provided") }
