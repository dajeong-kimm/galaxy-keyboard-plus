package org.dslul.openboard.inputmethod.backup.model

import android.net.Uri

data class GalleryImage (
    val uri: Uri,             // content:// URI
    val filename: String,     // DISPLAY_NAME
    val contentId: String,    // MediaStore._ID (고유 ID)
    val timestamp: Long,    // DATE_TAKEN 또는 DATE_ADDED
    val mimeType: String      // MIME_TYPE
)