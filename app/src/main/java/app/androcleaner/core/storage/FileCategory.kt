package app.androcleaner.core.storage

enum class FileCategory {
    IMAGES, VIDEOS, AUDIO, DOCUMENTS, APKS, ARCHIVES, OTHER;

    companion object {
        private val byExtension: Map<String, FileCategory> = buildMap {
            listOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng", "raw", "avif", "svg")
                .forEach { put(it, IMAGES) }
            listOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v", "ts", "wmv", "flv")
                .forEach { put(it, VIDEOS) }
            listOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav", "amr", "wma", "mid")
                .forEach { put(it, AUDIO) }
            listOf(
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp",
                "txt", "rtf", "md", "csv", "epub", "fb2", "djvu", "json", "xml", "html",
            ).forEach { put(it, DOCUMENTS) }
            listOf("apk", "apks", "xapk", "apkm").forEach { put(it, APKS) }
            listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst").forEach { put(it, ARCHIVES) }
        }

        fun fromFileName(name: String): FileCategory {
            val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            return byExtension[ext] ?: OTHER
        }
    }
}
