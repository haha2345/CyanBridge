package com.fersaiyan.cyanbridge.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.BuildConfig
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.ai.DashScopeVisionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AiVisionPreviewActivity : AppCompatActivity() {

    private var imagePath: String? = null
    private var deleteOnFinish: Boolean = true
    private var recognizeJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_vision_preview)

        imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        deleteOnFinish = intent.getBooleanExtra(EXTRA_DELETE_ON_FINISH, true)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        val imageView = findViewById<ImageView>(R.id.preview_image)
        val statusView = findViewById<TextView>(R.id.preview_status)
        val btnRecognize = findViewById<Button>(R.id.btn_recognize)
        val resultView = findViewById<TextView>(R.id.tv_result)

        val path = imagePath
        if (path.isNullOrBlank()) {
            statusView.text = getString(R.string.ai_vision_preview_missing_path)
            Toast.makeText(this, getString(R.string.ai_vision_preview_missing_path), Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(path)
        if (!file.exists()) {
            statusView.text = getString(R.string.ai_vision_preview_file_missing)
            Toast.makeText(this, getString(R.string.ai_vision_preview_file_missing), Toast.LENGTH_SHORT).show()
            return
        }

        val bmp = BitmapFactory.decodeFile(path)
        if (bmp == null) {
            statusView.text = getString(R.string.ai_vision_preview_decode_failed)
            Toast.makeText(this, getString(R.string.ai_vision_preview_decode_failed), Toast.LENGTH_SHORT).show()
            return
        }

        statusView.text = getString(R.string.ai_vision_preview_hint)
        imageView.setImageBitmap(bmp)

        btnRecognize.setOnClickListener {
            if (recognizeJob?.isActive == true) return@setOnClickListener

            if (BuildConfig.DASHSCOPE_API_KEY.isBlank()) {
                resultView.text = getString(R.string.ai_vision_recognize_missing_key)
                Toast.makeText(this, getString(R.string.ai_vision_recognize_missing_key), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val img = File(path)
            if (!img.exists()) {
                resultView.text = getString(R.string.ai_vision_preview_file_missing)
                Toast.makeText(this, getString(R.string.ai_vision_preview_file_missing), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setRecognizeUiState(
                statusView = statusView,
                button = btnRecognize,
                resultView = resultView,
                inProgress = true,
                message = getString(R.string.ai_vision_recognize_in_progress)
            )

            recognizeJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val client = DashScopeVisionClient()
                    val text = client.describeImage(imageFile = img).trim()
                    withContext(Dispatchers.Main) {
                        setRecognizeUiState(
                            statusView = statusView,
                            button = btnRecognize,
                            resultView = resultView,
                            inProgress = false,
                            message = getString(R.string.ai_vision_recognize_done)
                        )
                        resultView.text = if (text.isNotBlank()) text else getString(R.string.ai_vision_recognize_failed)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Recognize failed: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        setRecognizeUiState(
                            statusView = statusView,
                            button = btnRecognize,
                            resultView = resultView,
                            inProgress = false,
                            message = getString(R.string.ai_vision_recognize_failed)
                        )
                        resultView.text = "${getString(R.string.ai_vision_recognize_failed)}：${e.message ?: e.javaClass.simpleName}"
                    }
                }
            }
        }
    }

    override fun finish() {
        cleanupIfNeeded()
        super.finish()
    }

    override fun onDestroy() {
        recognizeJob?.cancel()
        if (isFinishing) {
            cleanupIfNeeded()
        }
        super.onDestroy()
    }

    private fun cleanupIfNeeded() {
        if (!deleteOnFinish) return
        val path = imagePath ?: return
        try {
            val file = File(path)
            if (file.exists() && file.absolutePath.contains("${File.separator}ai_vision_mvp${File.separator}")) {
                val ok = file.delete()
                Log.i(TAG, "Deleted temp image=$path ok=$ok")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete temp image: ${e.message}")
        }
    }

    private fun setRecognizeUiState(
        statusView: TextView,
        button: Button,
        resultView: TextView,
        inProgress: Boolean,
        message: String,
    ) {
        statusView.text = message
        button.isEnabled = !inProgress
        resultView.isEnabled = !inProgress
    }

    companion object {
        private const val TAG = "AiVisionPreview"
        private const val EXTRA_IMAGE_PATH = "extra_image_path"
        private const val EXTRA_DELETE_ON_FINISH = "extra_delete_on_finish"

        fun start(context: Context, imagePath: String, deleteOnFinish: Boolean = true) {
            context.startActivity(
                Intent(context, AiVisionPreviewActivity::class.java).apply {
                    putExtra(EXTRA_IMAGE_PATH, imagePath)
                    putExtra(EXTRA_DELETE_ON_FINISH, deleteOnFinish)
                }
            )
        }
    }
}
