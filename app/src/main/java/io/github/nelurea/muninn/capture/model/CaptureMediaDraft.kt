package io.github.nelurea.muninn.capture.model

import java.io.File

data class CaptureMediaDraft(
    val mediaIndex: Int,
    val sourceUrl: String,
    val mimeType: String,
    val fileName: String,

    val sourceFile: File,

    val isHighlighted: Boolean = false
)