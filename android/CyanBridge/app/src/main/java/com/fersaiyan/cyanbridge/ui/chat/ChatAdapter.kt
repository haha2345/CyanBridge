package com.fersaiyan.cyanbridge.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.chat.ChatMessageEntity
import com.fersaiyan.cyanbridge.chat.ChatRoles

/**
 * 对话列表适配器：
 * - 左侧 assistant 气泡
 * - 右侧 user 气泡
 */
class ChatAdapter(
        private val onReplayClick: (ChatMessageEntity) -> Unit,
) : ListAdapter<ChatMessageEntity, RecyclerView.ViewHolder>(DIFF) {

    /** 根据角色选择不同的 ViewType。 */
    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).role == ChatRoles.USER) TYPE_USER else TYPE_ASSISTANT
    }

    /** 创建 ViewHolder。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            val view = inflater.inflate(R.layout.item_chat_user, parent, false)
            UserViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_chat_assistant, parent, false)
            AssistantViewHolder(view, onReplayClick)
        }
    }

    /** 绑定数据到 ViewHolder。 */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is UserViewHolder -> holder.bind(item)
            is AssistantViewHolder -> holder.bind(item)
        }
    }

    private class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageView: TextView = itemView.findViewById(R.id.tv_message)

        /** 绑定用户消息。 */
        fun bind(item: ChatMessageEntity) {
            messageView.text = item.content
        }
    }

    private class AssistantViewHolder(
            itemView: View,
            private val onReplayClick: (ChatMessageEntity) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val messageView: TextView = itemView.findViewById(R.id.tv_message)
        private val replayView: TextView = itemView.findViewById(R.id.btn_replay)
        private val visionImageView: android.widget.ImageView =
                itemView.findViewById(R.id.iv_vision_image)

        /** 绑定助手消息并处理重播按钮。 */
        fun bind(item: ChatMessageEntity) {
            messageView.text = item.content

            // ── Vision image ──
            val imgPath = item.imagePath
            if (!imgPath.isNullOrBlank()) {
                val file = java.io.File(imgPath)
                if (file.exists()) {
                    val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                    visionImageView.setImageBitmap(bmp)
                    visionImageView.visibility = View.VISIBLE
                    visionImageView.setOnClickListener {
                        try {
                            val dlg = android.app.AlertDialog.Builder(itemView.context)
                            val imgView =
                                    android.widget.ImageView(itemView.context).apply {
                                        setImageBitmap(bmp)
                                        adjustViewBounds = true
                                        setPadding(0, 0, 0, 0)
                                    }
                            dlg.setView(imgView)
                            dlg.setPositiveButton("关闭", null)
                            dlg.show()
                        } catch (e: Exception) {
                            android.util.Log.w("ChatAdapter", "Cannot open image: ${e.message}")
                        }
                    }
                } else {
                    visionImageView.visibility = View.GONE
                    visionImageView.setOnClickListener(null)
                }
            } else {
                visionImageView.visibility = View.GONE
                visionImageView.setOnClickListener(null)
            }

            // ── Replay button ──
            if (item.audioPath.isNullOrBlank()) {
                replayView.visibility = View.GONE
                replayView.setOnClickListener(null)
            } else {
                replayView.visibility = View.VISIBLE
                replayView.setOnClickListener { onReplayClick(item) }
            }
        }
    }

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_ASSISTANT = 2

        private val DIFF =
                object : DiffUtil.ItemCallback<ChatMessageEntity>() {
                    override fun areItemsTheSame(
                            oldItem: ChatMessageEntity,
                            newItem: ChatMessageEntity
                    ): Boolean {
                        return oldItem.id == newItem.id
                    }

                    override fun areContentsTheSame(
                            oldItem: ChatMessageEntity,
                            newItem: ChatMessageEntity
                    ): Boolean {
                        return oldItem == newItem
                    }
                }
    }
}
