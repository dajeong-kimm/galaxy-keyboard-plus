package org.dslul.openboard.inputmethod.keyboard.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.dslul.openboard.inputmethod.latin.ClipboardHistoryEntry
import org.dslul.openboard.inputmethod.latin.ClipboardHistoryManager
import org.dslul.openboard.inputmethod.latin.R

class ClipboardAdapter(
    private val clipboardLayoutParams: ClipboardLayoutParams,
    private val keyEventListener: OnKeyEventListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {          //  ⬅️ 변경

    /* ---------- 외부에서 주입받는 값 ---------- */
    var clipboardHistoryManager: ClipboardHistoryManager? = null
    var pinnedIconResId = 0
    var itemBackgroundId = 0
    var itemTypeFace: Typeface? = null
    var itemTextColor = 0
    var itemTextSize = 0f

    /* ---------- 뷰타입 구분용 상수 ---------- */
    companion object {
        private const val TYPE_TEXT  = 0
        private const val TYPE_IMAGE = 1
    }

    // ① 항목 수
    override fun getItemCount() = clipboardHistoryManager?.getHistorySize() ?: 0

    // ② 뷰타입 판별
    override fun getItemViewType(position: Int): Int =
        if (clipboardHistoryManager?.getHistoryEntry(position)?.uri != null)
            TYPE_IMAGE else TYPE_TEXT                                       //  ⬅️ 변경

    // ③ 뷰홀더 생성
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_IMAGE -> {                                                //  ⬅️ 변경
                val v = inflater.inflate(R.layout.clipboard_entry_image, parent, false)
                ImageViewHolder(v)
            }
            else -> { // TYPE_TEXT
                val v = inflater.inflate(R.layout.clipboard_entry_key, parent, false)
                TextViewHolder(v)
            }
        }
    }

    // ④ 데이터 바인딩
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val entry = clipboardHistoryManager?.getHistoryEntry(position) ?: return
        when (holder) {
            is ImageViewHolder -> holder.bind(entry)
            is TextViewHolder  -> holder.bind(entry)
        }
    }

    /* ------------------------------------------------------------------ */
    /* ------------------------   ViewHolders   -------------------------- */
    /* ------------------------------------------------------------------ */

    /** 이미지용 */
    inner class ImageViewHolder(view: View)
        : RecyclerView.ViewHolder(view), View.OnClickListener, View.OnLongClickListener {

        private val thumb   = view.findViewById<ImageView>(R.id.clipboard_entry_image)
        private val pinIcon = view.findViewById<ImageView>(R.id.clipboard_entry_pinned_icon)

        init {
            view.setBackgroundResource(itemBackgroundId)
            view.setOnClickListener(this)
            view.setOnLongClickListener(this)
            clipboardLayoutParams.setItemProperties(view)
            pinIcon.setImageResource(pinnedIconResId)
        }

        fun bind(e: ClipboardHistoryEntry) {
            itemView.tag = e.timeStamp
            thumb.setImageURI(e.uri)
            pinIcon.visibility = if (e.isPinned) View.VISIBLE else View.GONE
        }

        override fun onClick(v: View) {
            val e = clipboardHistoryManager?.getHistoryEntry(bindingAdapterPosition) ?: return
            val cm = v.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            // URI 가 있으면 이미지 복사
            val clip = ClipData.newUri(v.context.contentResolver, "Image", e.uri)
            cm.setPrimaryClip(clip)
        }
        override fun onLongClick(v: View): Boolean {
            clipboardHistoryManager?.toggleClipPinned(v.tag as Long)
            return true
        }
    }

    /** 텍스트용 */
    inner class TextViewHolder(view: View)
        : RecyclerView.ViewHolder(view), View.OnClickListener, View.OnLongClickListener {

        private val content = view.findViewById<TextView>(R.id.clipboard_entry_content)
        private val pinIcon = view.findViewById<ImageView>(R.id.clipboard_entry_pinned_icon)

        init {
            view.setBackgroundResource(itemBackgroundId)
            view.setOnClickListener(this)
            view.setOnLongClickListener(this)
            clipboardLayoutParams.setItemProperties(view)

            pinIcon.setImageResource(pinnedIconResId)
            content.apply {
                typeface = itemTypeFace
                setTextColor(itemTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, itemTextSize)
            }
        }

        fun bind(e: ClipboardHistoryEntry) {
            itemView.tag = e.timeStamp
            content.text = e.content
            pinIcon.visibility = if (e.isPinned) View.VISIBLE else View.GONE
        }

        override fun onClick(view: View) {
            // ① bindingAdapterPosition 으로 안전하게 위치 가져오기
            val pos = bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return

            // ② 관리자로 리스트 맨 앞으로 이동시키기
            val entry = clipboardHistoryManager?.getHistoryEntry(pos) ?: return
            clipboardHistoryManager?.refreshEntry(entry.timeStamp)

            // ③ 실제로 시스템 클립보드에 복사
            val cm = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = if (entry.uri != null) {
                ClipData.newUri(view.context.contentResolver, "Image", entry.uri)
            } else {
                ClipData.newPlainText("Text", entry.content)
            }
            cm.setPrimaryClip(clip)
        }

        override fun onLongClick(v: View): Boolean {
            clipboardHistoryManager?.toggleClipPinned(v.tag as Long)
            return true
        }
    }
}
