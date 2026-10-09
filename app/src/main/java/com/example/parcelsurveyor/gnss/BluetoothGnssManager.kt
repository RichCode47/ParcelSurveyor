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

/**
 * Enum representing the Bluetooth connection state with an external GNSS receiver.
 */
enum class ConnectionState {
    /** Bluetooth device is currently disconnected. */
    DISCONNECTED,

    /** Attempting to establish a Bluetooth RFCOMM connection. */
    CONNECTING,

    /** Successfully connected and receiving NMEA data streams. */
    CONNECTED,

    /** An error occurred during connection or data streaming. */
    ERROR
}

/**
 * Manager class responsible for discovering paired Bluetooth devices, establishing RFCOMM socket
 * connections with external RTK GNSS receivers, and streaming / parsing incoming NMEA sentences.
 */
class BluetoothGnssManager {

    companion object {
        /** Standard Serial Port Profile (SPP) UUID for Bluetooth RFCOMM communication. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    /** Active Bluetooth socket connection. */
    private var socket: BluetoothSocket? = null

    /** Coroutine job handling background NMEA stream reading. */
    private var streamJob: Job? = null

    private var locationManager: android.location.LocationManager? = null
    private var locationListener: android.location.LocationListener? = null

    /**
     * Starts internal GPS fallback using Android LocationManager when no Bluetooth receiver is connected.
     */
    @SuppressLint("MissingPermission")
    fun startInternalGps(context: Context) {
        if (locationManager != null) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager ?: return
        locationManager = lm

        val listener = android.location.LocationListener { loc ->
            if (_connectionState.value != ConnectionState.CONNECTED) {
                val pos = GnssPosition(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    altitude = loc.altitude,
                    fixQuality = GnssFixQuality.GPS_SPS,
                    satellitesCount = 8,
                    hdop = if (loc.hasAccuracy()) loc.accuracy.toDouble() / 5.0 else 1.0,
                    timestamp = loc.time,
                    isBluetooth = false
                )
                _currentPosition.value = pos
            }
        }
        locationListener = listener
        try {
            lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 1000L, 0.5f, listener)
            lm.requestLocationUpdates(android.location.LocationManager.NETWORK_PROVIDER, 2000L, 1.0f, listener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Stops internal GPS fallback updates.
     */
    fun stopInternalGps() {
        locationManager?.let { lm ->
            locationListener?.let { l ->
                try {
                    lm.removeUpdates(l)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        locationManager = null
        locationListener = null
    }

    /** Mutable state flow for the current Bluetooth connection state. */
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)

    /** Public read-only state flow for observing Bluetooth connection state changes. */
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /** Mutable state flow for the latest parsed GNSS position fix. */
    private val _currentPosition = MutableStateFlow<GnssPosition?>(null)

    /** Public read-only state flow for observing live GNSS position updates. */
    val currentPosition: StateFlow<GnssPosition?> = _currentPosition.asStateFlow()

    /** Mutable state flow for currently connected Bluetooth device info. */
    private val _connectedDevice = MutableStateFlow<BluetoothDeviceInfo?>(null)

    /** Public read-only state flow for observing connected device info. */
    val connectedDevice: StateFlow<BluetoothDeviceInfo?> = _connectedDevice.asStateFlow()

    /**
     * Retrieves a list of bonded (paired) Bluetooth devices available on the device.
     *
     * @param context Application context used for permission checks.
     * @return A list of [BluetoothDeviceInfo] objects representing paired Bluetooth receivers.
     */
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

    /**
     * Connects to a specified Bluetooth GNSS receiver device and starts reading NMEA sentences in the background.
     *
     * @param deviceInfo The target [BluetoothDeviceInfo] to connect to.
     * @param scope Coroutine scope for running background I/O stream reading.
     */
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

    /**
     * Disconnects from the active Bluetooth GNSS receiver and cancels background stream reading.
     */
    fun disconnect() {
        streamJob?.cancel()
        streamJob = null
        disconnectInternal()
    }

    /**
     * Internal helper to close the Bluetooth socket and reset connection state variables.
     */
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
