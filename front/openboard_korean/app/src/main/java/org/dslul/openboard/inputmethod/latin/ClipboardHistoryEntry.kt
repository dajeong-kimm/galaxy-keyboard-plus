package org.dslul.openboard.inputmethod.latin

import android.net.Uri

data class ClipboardHistoryEntry (
    var timeStamp: Long,
    val content: CharSequence,
    var uri: Uri? = null,
    var isPinned: Boolean = false
) : Comparable<ClipboardHistoryEntry> {

    override fun compareTo(other: ClipboardHistoryEntry): Int {
        val result = other.isPinned.compareTo(isPinned)
        return if (result != 0) result else other.timeStamp.compareTo(timeStamp)
    }
}