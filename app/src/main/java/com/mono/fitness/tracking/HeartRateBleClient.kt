package com.mono.fitness.tracking

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Minimal BLE heart-rate strap client (Heart Rate Service 0x180D / Measurement 0x2A37).
 * Scans, connects to the first advertising HR device, and streams BPM updates.
 */
class HeartRateBleClient(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var gatt: BluetoothGatt? = null
    private var scanning = false

    private val _bpm = MutableStateFlow<Int?>(null)
    val bpm: StateFlow<Int?> = _bpm.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val samples = mutableListOf<Int>()
    val sampleList: List<Int> get() = samples.toList()

    fun averageBpm(): Int? {
        if (samples.isEmpty()) return null
        return samples.average().toInt()
    }

    fun maxBpm(): Int? = samples.maxOrNull()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            stopScan()
            connect(result.device)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connected.value = true
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connected.value = false
                _bpm.value = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val service = g.getService(HR_SERVICE) ?: return
            val char = service.getCharacteristic(HR_MEASUREMENT) ?: return
            g.setCharacteristicNotification(char, true)
            val cccd = char.getDescriptor(CCCD) ?: return
            val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, enable)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = enable
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            parseAndPublish(characteristic.value)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            parseAndPublish(value)
        }
    }

    private fun parseAndPublish(value: ByteArray?) {
        val hr = parseHeartRate(value) ?: return
        samples += hr
        _bpm.value = hr
    }

    @SuppressLint("MissingPermission")
    fun start() {
        samples.clear()
        _bpm.value = null
        val a = adapter ?: return
        if (!a.isEnabled) return
        val scanner = a.bluetoothLeScanner ?: return
        scanning = true
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HR_SERVICE))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(listOf(filter), settings, scanCallback)
        } catch (_: SecurityException) {
            scanning = false
        } catch (_: Exception) {
            scanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        stopScan()
        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        _connected.value = false
        _bpm.value = null
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning) return
        scanning = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        try {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    companion object {
        val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** Parses Heart Rate Measurement characteristic payload (Bluetooth SIG). */
        fun parseHeartRate(value: ByteArray?): Int? {
            if (value == null || value.isEmpty()) return null
            val flags = value[0].toInt() and 0xFF
            val uint16 = flags and 0x01 != 0
            return try {
                if (uint16) {
                    if (value.size < 3) return null
                    ((value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8))
                } else {
                    if (value.size < 2) return null
                    value[1].toInt() and 0xFF
                }.takeIf { it in 30..250 }
            } catch (_: Exception) {
                null
            }
        }
    }
}
