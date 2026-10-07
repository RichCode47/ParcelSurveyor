package com.example.parcelsurveyor.gnss

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

class BluetoothGnssManager {

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    private var streamJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _currentPosition = MutableStateFlow<GnssPosition?>(null)
    val currentPosition: StateFlow<GnssPosition?> = _currentPosition.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDeviceInfo?>(null)
    val connectedDevice: StateFlow<BluetoothDeviceInfo?> = _connectedDevice.asStateFlow()

    fun getPairedDevices(context: Context): List<BluetoothDeviceInfo> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val permission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                return emptyList()
            }
        }

        return try {
            adapter.bondedDevices?.map { device ->
                BluetoothDeviceInfo(
                    name = device.name ?: "Unknown Device",
                    address = device.address
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(
        deviceInfo: BluetoothDeviceInfo,
        scope: CoroutineScope
    ) {
        disconnect()

        _connectionState.value = ConnectionState.CONNECTING
        _connectedDevice.value = deviceInfo

        streamJob = scope.launch(Dispatchers.IO) {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                    ?: throw IllegalStateException("Bluetooth not available")

                val device: BluetoothDevice = adapter.getRemoteDevice(deviceInfo.address)
                adapter.cancelDiscovery()

                val btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket = btSocket
                btSocket.connect()

                _connectionState.value = ConnectionState.CONNECTED

                val reader = BufferedReader(InputStreamReader(btSocket.inputStream))
                while (_connectionState.value == ConnectionState.CONNECTED) {
                    val nmea = reader.readLine() ?: break
                    val pos = NmeaParser.parseNmeaLine(nmea, isBluetooth = true)
                    if (pos != null) {
                        _currentPosition.value = pos
                    }
                }
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.ERROR
            } finally {
                disconnectInternal()
            }
        }
    }

    fun disconnect() {
        streamJob?.cancel()
        streamJob = null
        disconnectInternal()
    }

    private fun disconnectInternal() {
        try {
            socket?.close()
        } catch (e: Exception) {
            // Ignore socket close exception
        }
        socket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDevice.value = null
    }
}
