package com.fersaiyan.cyanbridge.glasses

/**
 * EventBus event fired by [com.fersaiyan.cyanbridge.ui.MyBluetoothReceiver]
 * whenever the BLE connection state changes.
 */
data class BluetoothEvent(val connect: Boolean)
