package com.fersaiyan.cyanbridge.ui
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.bluetooth.DeviceManager
import org.greenrobot.eventbus.EventBus
import com.fersaiyan.cyanbridge.glasses.BluetoothEvent

/**
 * System Bluetooth state broadcast receiver.
 * Handles BT on/off, pairing, and ACL events.
 */
class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val connectState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                if (connectState == BluetoothAdapter.STATE_OFF) {
                    Log.i("qc" ,"Bluetooth is off --> ")
                    BleOperateManager.getInstance().setBluetoothTurnOff(false)
                    BleOperateManager.getInstance().disconnect()
                    EventBus.getDefault().post(BluetoothEvent(false))
                } else if (connectState == BluetoothAdapter.STATE_ON) {
                    Log.i("qc" ,"Bluetooth is on --> ")
                    BleOperateManager.getInstance().setBluetoothTurnOff(true)
                    BleOperateManager.getInstance().reConnectMac=DeviceManager.getInstance().deviceAddress
                    BleOperateManager.getInstance().connectDirectly(DeviceManager.getInstance().deviceAddress)
                }
            }
            BluetoothDevice.ACTION_BOND_STATE_CHANGED -> { }
            BluetoothDevice.ACTION_ACL_CONNECTED -> { }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> { }
            BluetoothDevice.ACTION_FOUND -> {
                val device =
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device != null) {
                    BleOperateManager.getInstance().createBondBluetoothJieLi(device)
                }
            }
        }
    }
}
