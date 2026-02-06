package com.fersaiyan.cyanbridge.ui.chat

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.fersaiyan.cyanbridge.databinding.ActivityChatBinding

/**
 * 对话页面：
 * - 显示聊天列表
 * - 发送文本
 * - 点击重播按钮播放 TTS
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var viewModel: ChatViewModel
    private val audioPlayer = ChatAudioPlayer()
    private val adapter = ChatAdapter { message ->
        val path = message.audioPath ?: return@ChatAdapter
        audioPlayer.play(path)
    }

    /**
     * 初始化 UI、绑定列表与输入框。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]

        binding.btnBack.setOnClickListener { finish() }

        binding.rvChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvChat.adapter = adapter

        binding.btnSend.setOnClickListener { submitInput() }
        binding.etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                submitInput()
                true
            } else {
                false
            }
        }
        binding.etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                binding.btnSend.isEnabled = !s.isNullOrBlank()
            }
        })

        viewModel.messages.observe(this) { list ->
            adapter.submitList(list) {
                if (list.isNotEmpty()) {
                    binding.rvChat.scrollToPosition(list.size - 1)
                }
            }
            binding.tvEmpty.visibility = if (list.isNullOrEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }

        binding.btnSend.isEnabled = false
    }

    /**
     * 页面销毁时释放播放器。
     */
    override fun onDestroy() {
        super.onDestroy()
        audioPlayer.stop()
    }

    /**
     * 发送输入框内容。
     */
    private fun submitInput() {
        val text = binding.etInput.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return
        binding.etInput.setText("")
        viewModel.sendText(text)
    }

}
