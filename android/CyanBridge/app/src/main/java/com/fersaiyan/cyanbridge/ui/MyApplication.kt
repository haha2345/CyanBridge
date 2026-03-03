package com.fersaiyan.cyanbridge.ui

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.oudmon.ble.base.bluetooth.BleAction
import com.oudmon.ble.base.bluetooth.BleBaseControl
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.communication.LargeDataHandler
import java.io.File
import kotlin.properties.Delegates

/**
 * @Author: Hzy
 * @CreateDate: 2021/6/25 11:50
 *
 * "Programs should be written for other people to read, and only incidentally for machines to
 * execute"
 */
/**
 * 应用入口：
 * - 初始化 BLE
 * - 初始化对话引擎与全局 TTS 播放
 */
class MyApplication : Application() {

    var hardwareVersion: String = ""
    var firmwareVersion: String = ""

    override fun onCreate() {
        super.onCreate()
        application = this
        instance = this
        CONTEXT = applicationContext
        initBle()
        com.fersaiyan.cyanbridge.chat.ChatEngine.init(this)
        // Global auto-play for assistant replies (works even outside ChatActivity).
        com.fersaiyan.cyanbridge.chat.ChatPlaybackManager.start()
        // Initialize GlassesRepository singleton for Compose screens
        com.fersaiyan.cyanbridge.glasses.GlassesRepository.getInstance(this)
    }
    private fun initBle() {
        initReceiver()
        val intentFilter = BleAction.getIntentFilter()
        val myBleReceiver = MyBluetoothReceiver()
        LocalBroadcastManager.getInstance(CONTEXT).registerReceiver(myBleReceiver, intentFilter)
        BleBaseControl.getInstance(CONTEXT).setmContext(this)
    }

    private fun initReceiver() {
        LargeDataHandler.getInstance()
        BleOperateManager.getInstance(this)
        BleOperateManager.getInstance().setApplication(this)
        BleOperateManager.getInstance().init()
        val deviceFilter: IntentFilter = BleAction.getDeviceIntentFilter()
        val deviceReceiver = BluetoothReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(deviceReceiver, deviceFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(deviceReceiver, deviceFilter)
        }
    }

    fun getDeviceIntentFilter(): IntentFilter? {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        return intentFilter
    }

    fun getAppRootFile(context: Context): File {
        // /storage/emulated/0/Android/data/pack_name/files
        return if (context.getExternalFilesDir("") != null) {
            context.getExternalFilesDir("")!!
        } else {
            val externalSaveDir = context.externalCacheDir
            externalSaveDir ?: context.cacheDir
        }
    }

    companion object {
        private var application: Application? = null
        var CONTEXT: Context by Delegates.notNull()
            private set
        private lateinit var instance: MyApplication

        fun getApplication(): Application {
            return application
                    ?: throw RuntimeException(
                            "Application not initialized. onCreate not yet called."
                    )
        }

        fun getInstance(): MyApplication {
            return instance
        }
    }
}
