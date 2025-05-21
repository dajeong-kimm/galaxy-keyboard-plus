package org.dslul.openboard.inputmethod.latin

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import org.dslul.openboard.inputmethod.compat.ClipboardManagerCompat
import org.dslul.openboard.inputmethod.latin.utils.JsonUtils
import java.io.File
import java.lang.Exception
import java.util.*

class ClipboardHistoryManager(
        private val latinIME: LatinIME
) : ClipboardManager.OnPrimaryClipChangedListener {

    private lateinit var pinnedHistoryClipsFile: File
    private lateinit var clipboardManager: ClipboardManager
    private val historyEntries: MutableList<ClipboardHistoryEntry>
    private var onHistoryChangeListener: OnHistoryChangeListener? = null

    fun onCreate() {
        pinnedHistoryClipsFile = File(latinIME.filesDir, PINNED_CLIPS_DATA_FILE_NAME)
        clipboardManager = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        fetchPrimaryClip()
        clipboardManager.addPrimaryClipChangedListener(this)
        startLoadPinnedClipsFromDisk()
    }

    fun onPinnedClipsAvailable(pinnedClips: List<ClipboardHistoryEntry>) {
        historyEntries.addAll(pinnedClips)
        sortHistoryEntries()
        if (onHistoryChangeListener != null) {
            pinnedClips.forEach {
                onHistoryChangeListener?.onClipboardHistoryEntryAdded(historyEntries.indexOf(it))
            }
        }
    }

    fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(this)
    }

    override fun onPrimaryClipChanged() {
        // Make sure we read clipboard content only if history settings is set
        if (latinIME.mSettings.current?.mClipboardHistoryEnabled == true) {
            fetchPrimaryClip()
        }
    }

    private fun fetchPrimaryClip() {
        val clipData = clipboardManager.primaryClip ?: return
        if (clipData.itemCount == 0) return
        val clipItem = clipData.getItemAt(0) ?: return

        // --- URI 버전 중복 검사 ---
        clipItem.uri?.let { uri ->
            if (historyEntries.any { it.uri == uri }) return
            // (아래에 추가 로직 계속...)
        }

        // --- 텍스트 버전 중복 검사 ---
        val text = clipItem.coerceToText(latinIME).toString()
        if (text.isBlank()) return
        if (historyEntries.any { it.content == text }) return

        // 타임스탬프
        val timeStamp = System.currentTimeMillis()

        // 1) 이미지 URI가 있으면, content 대신 uri 필드에 담아서 히스토리에 추가
        clipItem.uri?.let { uri ->
            // 읽기 권한 부여 (FileProvider 등 ACL 필요시)
            latinIME.grantUriPermission("*", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            val entry = ClipboardHistoryEntry(
                timeStamp = timeStamp,
                content   = "",     // 텍스트는 비워두고
                uri       = uri     // 여기에 URI 저장
            )
            historyEntries.add(entry)
            sortHistoryEntries()
            val at = historyEntries.indexOf(entry)
            onHistoryChangeListener?.onClipboardHistoryEntryAdded(at)
            return
        }

        // 2) URI가 없으면 기존대로 텍스트 처리
        val content = clipItem.coerceToText(latinIME)?.toString()
        if (content.isNullOrEmpty()) return

        val entry = ClipboardHistoryEntry(
            timeStamp = timeStamp,
            content   = content,
            uri       = null
        )
        historyEntries.add(entry)
        sortHistoryEntries()
        val at = historyEntries.indexOf(entry)
        onHistoryChangeListener?.onClipboardHistoryEntryAdded(at)
    }


    fun toggleClipPinned(ts: Long) {
        val from = historyEntries.indexOfFirst { it.timeStamp == ts }
        val historyEntry = historyEntries[from].apply {
            timeStamp = System.currentTimeMillis()
            isPinned = !isPinned
        }
        sortHistoryEntries()
        val to = historyEntries.indexOf(historyEntry)
        onHistoryChangeListener?.onClipboardHistoryEntryMoved(from, to)
        startSavePinnedClipsToDisk()
    }

    fun clearHistory() {
        ClipboardManagerCompat.clearPrimaryClip(clipboardManager)
        val pos = historyEntries.indexOfFirst { !it.isPinned }
        val count = historyEntries.count { !it.isPinned }
        historyEntries.removeAll { !it.isPinned }
        if (onHistoryChangeListener != null) {
            onHistoryChangeListener?.onClipboardHistoryEntriesRemoved(pos, count)
        }
    }

    private fun sortHistoryEntries() {
        historyEntries.sort()
    }

    private fun checkClipRetentionElapsed() {
        val mins = latinIME.mSettings.current.mClipboardHistoryRetentionTime
        if (mins <= 0) return // No retention limit
        val maxClipRetentionTime = mins * 60 * 1000L
        val now = System.currentTimeMillis()
        historyEntries.removeAll { !it.isPinned && (now - it.timeStamp) > maxClipRetentionTime }
    }

    // We do not want to update history while user is visualizing it, so we check retention only
    // when history is about to be shown
    fun prepareClipboardHistory() = checkClipRetentionElapsed()

    fun getHistorySize() = historyEntries.size

    fun getHistoryEntry(position: Int) = historyEntries[position]

    fun getHistoryEntryContent(timeStamp: Long): ClipboardHistoryEntry? {
        return historyEntries.first { it.timeStamp == timeStamp }
    }

    fun setHistoryChangeListener(l: OnHistoryChangeListener?) {
        onHistoryChangeListener = l
    }

    fun retrieveClipboardContent(): CharSequence {
        val clipData = clipboardManager.primaryClip ?: return ""
        if (clipData.itemCount == 0) return ""
        return clipData.getItemAt(0)?.coerceToText(latinIME) ?: ""
    }

    private fun startLoadPinnedClipsFromDisk() {
        object : Thread("$TAG-load") {
            override fun run() {
                loadFromDisk()
            }
        }.start()
    }

    private fun loadFromDisk() {
        // Debugging
        if (pinnedHistoryClipsFile.exists() && !pinnedHistoryClipsFile.canRead()) {
            Log.w(TAG, "Attempt to read pinned clips file $pinnedHistoryClipsFile without permission")
        }
        var list = emptyList<ClipboardHistoryEntry>()
        try {
            if (pinnedHistoryClipsFile.exists()) {
                val bytes = Base64.decode(pinnedHistoryClipsFile.readText(), Base64.DEFAULT)
                list = JsonUtils.jsonBytesToHistoryEntryList(bytes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't retrieve $pinnedHistoryClipsFile content", e)
        }
        latinIME.mHandler.postUpdateClipboardPinnedClips(list)
    }

    private fun startSavePinnedClipsToDisk() {
        val localCopy = historyEntries.filter { it.isPinned }.map { it.copy() }
        object : Thread("$TAG-save") {
            override fun run() {
                saveToDisk(localCopy)
            }
        }.start()
    }

    private fun saveToDisk(list: List<ClipboardHistoryEntry>) {
        // Debugging
        if (pinnedHistoryClipsFile.exists() && !pinnedHistoryClipsFile.canWrite()) {
            Log.w(TAG, "Attempt to write pinned clips file $pinnedHistoryClipsFile without permission")
        }
        try {
            pinnedHistoryClipsFile.createNewFile()
            val jsonStr = JsonUtils.historyEntryListToJsonStr(list)
            if (!TextUtils.isEmpty(jsonStr)) {
                val rawText = Base64.encodeToString(jsonStr.encodeToByteArray(), Base64.DEFAULT)
                pinnedHistoryClipsFile.writeText(rawText)
            } else {
                pinnedHistoryClipsFile.writeText("")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't write to $pinnedHistoryClipsFile", e)
        }
    }

    /**
     * 클릭된 항목(ts)에 해당하는 historyEntries 내부 순서를
     * 맨 앞으로 옮기고 표시 위치도 갱신해 줍니다.
     */
    fun refreshEntry(ts: Long) {
        val from = historyEntries.indexOfFirst { it.timeStamp == ts }
        if (from == -1) return
        val entry = historyEntries.removeAt(from)
        entry.timeStamp = System.currentTimeMillis()
        historyEntries.add(0, entry)
        onHistoryChangeListener?.onClipboardHistoryEntryMoved(from, 0)
        startSavePinnedClipsToDisk()
    }

    interface OnHistoryChangeListener {
        fun onClipboardHistoryEntryAdded(at: Int)
        fun onClipboardHistoryEntriesRemoved(pos: Int, count: Int)
        fun onClipboardHistoryEntryMoved(from: Int, to: Int)
    }

    companion object {
        const val PINNED_CLIPS_DATA_FILE_NAME = "pinned_clips.data"
        const val TAG = "ClipboardHistoryManager"
    }

    init {
        historyEntries = LinkedList()
    }
}