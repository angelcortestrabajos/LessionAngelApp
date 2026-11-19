package com.example.lessionangel

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val EMG_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
private val EMG_TX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
private val CLIENT_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

private data class DiscoveredBleDevice(
    val device: BluetoothDevice,
    val name: String
)

private data class EmgSample(
    val timestampMs: Long,
    val adc: Int
)

private data class EmgSession(
    val id: Long,
    val startedAt: Long,
    val finishedAt: Long,
    val samples: List<EmgSample>
)

private enum class AppPage {
    CONNECTION,
    TRAINING,
    ANALYSIS
}

private const val SESSION_PREFERENCES = "training_sessions"
private const val SESSION_DATA_KEY = "sessions"

private fun loadSessions(context: Context): List<EmgSession> {
    val json = context.getSharedPreferences(SESSION_PREFERENCES, Context.MODE_PRIVATE)
        .getString(SESSION_DATA_KEY, null) ?: return emptyList()
    val sessions = JSONArray(json)
    return List(sessions.length()) { index ->
        val savedSession = sessions.getJSONObject(index)
        val savedReadings = savedSession.optJSONArray("readings") ?: JSONArray()
        val samples = buildList {
            for (readingIndex in 0 until savedReadings.length()) {
                val reading = savedReadings.optJSONObject(readingIndex) ?: continue
                val adc = if (reading.has("emgAdc")) reading.optInt("emgAdc", -1)
                    else reading.optInt("adc", -1)
                if (adc in 0..4095) {
                    add(EmgSample(reading.optLong("timestampMs"), adc))
                }
            }
        }
        val startedAt = savedSession.optLong("startedAt", savedSession.optLong("id"))
        EmgSession(
            id = savedSession.optLong("id", startedAt),
            startedAt = startedAt,
            finishedAt = savedSession.optLong("finishedAt", startedAt),
            samples = samples
        )
    }
}

private fun saveSessions(context: Context, sessions: List<EmgSession>) {
    val savedSessions = JSONArray()
    sessions.forEach { session ->
        val readings = JSONArray()
        session.samples.forEach { sample ->
            readings.put(
                JSONObject()
                    .put("timestampMs", sample.timestampMs)
                    .put("emgAdc", sample.adc)
            )
        }
        savedSessions.put(
            JSONObject()
                .put("id", session.id)
                .put("startedAt", session.startedAt)
                .put("finishedAt", session.finishedAt)
                .put("readings", readings)
        )
    }
    context.getSharedPreferences(SESSION_PREFERENCES, Context.MODE_PRIVATE)
        .edit()
        .putString(SESSION_DATA_KEY, savedSessions.toString())
        .apply()
}

private val Purple = Color(0xFF4025C6)
private val PurpleSoft = Color(0xFFDCD5FF)
private val Accent = Color(0xFFE5FF00)
private val CardColor = Color(0xFF321B9B)
private val Red = Color(0xFFE45757)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EmgMonitorApp() }
    }
}

@androidx.compose.runtime.Composable
private fun EmgMonitorApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val devices = remember { mutableStateListOf<DiscoveredBleDevice>() }
    val graphData = remember { mutableStateListOf<Float>() }
    val lineBuffer = remember { StringBuilder() }

    var page by remember { mutableStateOf(AppPage.CONNECTION) }
    var selectedDevice by remember { mutableStateOf<DiscoveredBleDevice?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var scanId by remember { mutableIntStateOf(0) }
    var adc by remember { mutableIntStateOf(0) }
    var sensorTimeMs by remember { mutableStateOf<Long?>(null) }
    var sampleCount by remember { mutableIntStateOf(0) }
    var isRecording by remember { mutableStateOf(false) }
    var sessionStartedAt by remember { mutableStateOf<Long?>(null) }
    var showSessions by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var selectedSession by remember { mutableStateOf<EmgSession?>(null) }
    var sessionToExport by remember { mutableStateOf<EmgSession?>(null) }
    val sessionSamples = remember { mutableStateListOf<EmgSample>() }
    val sessions = remember(context) {
        val savedSessions = loadSessions(context)
        saveSessions(context, savedSessions)
        mutableStateListOf<EmgSession>().apply { addAll(savedSessions) }
    }
    var status by remember { mutableStateOf("Listo para buscar la ESP32.") }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingScan by remember { mutableStateOf(false) }
    var gatt by remember { mutableStateOf<BluetoothGatt?>(null) }

    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val session = sessionToExport
        sessionToExport = null
        if (uri != null && session != null) {
            try {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado.")
                output.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write("session_id,session_started_at_ms,session_finished_at_ms,sensor_timestamp_ms,emg_adc\n")
                    session.samples.forEach { sample ->
                        writer.append(session.id.toString())
                            .append(',')
                            .append(session.startedAt.toString())
                            .append(',')
                            .append(session.finishedAt.toString())
                            .append(',')
                            .append(sample.timestampMs.toString())
                            .append(',')
                            .append(sample.adc.toString())
                            .append('\n')
                    }
                }
                Toast.makeText(context, "Sesión exportada a CSV.", Toast.LENGTH_SHORT).show()
            } catch (exception: Exception) {
                Toast.makeText(
                    context,
                    "No se pudo exportar la sesión: ${exception.message ?: "error de archivo"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val exportSession: (EmgSession) -> Unit = { session ->
        sessionToExport = session
        val fileDate = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
            .format(Date(session.startedAt))
        csvExportLauncher.launch("sesion_emg_$fileDate.csv")
    }

    val saveCurrentSession: () -> EmgSession? = {
        val startedAt = sessionStartedAt
        if (startedAt != null && sessionSamples.isNotEmpty()) {
            isRecording = false
            val session = EmgSession(
                id = startedAt,
                startedAt = startedAt,
                finishedAt = System.currentTimeMillis(),
                samples = sessionSamples.toList()
            )
            sessions.add(0, session)
            saveSessions(context, sessions)
            sessionSamples.clear()
            sessionStartedAt = null
            session
        } else null
    }

    val finishSession: () -> Unit = {
        val savedSession = saveCurrentSession()
        if (savedSession != null) {
            status = "Sesión guardada con ${savedSession.samples.size} muestras EMG."
        } else {
            isRecording = false
            if (sessionSamples.isEmpty()) sessionStartedAt = null
            status = "Sesión detenida."
        }
    }

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val consumePacket: (ByteArray) -> Unit = { packet ->
        val copiedPacket = packet.copyOf()
        mainHandler.post {
            copiedPacket.forEach { byte ->
                if (byte == '\n'.code.toByte()) {
                    val fields = lineBuffer.toString().trimEnd('\r').split(',')
                    lineBuffer.clear()
                    if (fields.size == 2) {
                        val timestamp = fields[0].toLongOrNull()
                        val value = fields[1].toIntOrNull()
                        if (timestamp != null && value != null && value in 0..4095) {
                            sensorTimeMs = timestamp
                            adc = value
                            sampleCount += 1
                            if (isRecording) {
                                sessionSamples.add(EmgSample(timestamp, value))
                            }
                            graphData.add(value.toFloat())
                            if (graphData.size > 250) graphData.removeAt(0)
                        }
                    }
                } else if (lineBuffer.length < 32) {
                    lineBuffer.append(byte.toInt().toChar())
                } else {
                    lineBuffer.clear()
                }
            }
        }
    }

    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = result.scanRecord?.deviceName
                    ?: runCatching { device.name }.getOrNull()
                    ?: "BLE ${device.address.takeLast(5)}"
                mainHandler.post {
                    val index = devices.indexOfFirst { it.device.address == device.address }
                    val found = DiscoveredBleDevice(device, name)
                    if (index == -1) devices.add(found) else devices[index] = found
                }
            }

            override fun onScanFailed(errorCode: Int) {
                mainHandler.post {
                    isScanning = false
                    status = "Falló el escaneo BLE (código $errorCode)."
                    error = status
                }
            }
        }
    }

    val gattCallback = remember {
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                changedGatt: BluetoothGatt,
                connectionStatus: Int,
                newState: Int
            ) {
                if (connectionStatus == BluetoothGatt.GATT_SUCCESS &&
                    newState == BluetoothProfile.STATE_CONNECTED
                ) {
                    val discoveryStarted =
                        runCatching { changedGatt.discoverServices() }.getOrDefault(false)
                    mainHandler.post {
                        if (gatt === changedGatt) {
                            status = if (discoveryStarted) {
                                "Conectado; preparando la señal EMG..."
                            } else {
                                "No se pudieron consultar los servicios BLE."
                            }
                            if (!discoveryStarted) {
                                isConnecting = false
                                error = status
                            }
                        }
                    }
                } else {
                    mainHandler.post {
                        if (gatt === changedGatt) {
                            isConnected = false
                            isConnecting = false
                            finishSession()
                            status = "Conexión BLE interrumpida."
                            error = if (connectionStatus == BluetoothGatt.GATT_SUCCESS) {
                                "La ESP32 se desconectó."
                            } else {
                                "Falló la conexión GATT (código $connectionStatus)."
                            }
                        }
                    }
                }
            }

            override fun onServicesDiscovered(changedGatt: BluetoothGatt, discoveryStatus: Int) {
                if (discoveryStatus != BluetoothGatt.GATT_SUCCESS) {
                    mainHandler.post {
                        if (gatt === changedGatt) {
                            isConnecting = false
                            error = "No se pudieron descubrir los servicios BLE (código $discoveryStatus)."
                            status = error!!
                        }
                    }
                    return
                }

                val tx = changedGatt.getService(EMG_SERVICE_UUID)
                    ?.getCharacteristic(EMG_TX_UUID)
                val cccd = tx?.getDescriptor(CLIENT_CONFIG_UUID)
                if (tx == null || cccd == null) {
                    mainHandler.post {
                        if (gatt === changedGatt) {
                            isConnecting = false
                            error = "La ESP32 no ofrece el servicio EMG esperado."
                            status = error!!
                        }
                    }
                    return
                }

                val notificationsEnabled = runCatching {
                    changedGatt.setCharacteristicNotification(tx, true)
                }.getOrDefault(false)
                val descriptorWriteStarted = if (!notificationsEnabled) {
                    false
                } else {
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            changedGatt.writeDescriptor(
                                cccd,
                                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            ) == BluetoothGatt.GATT_SUCCESS
                        } else {
                            @Suppress("DEPRECATION")
                            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            changedGatt.writeDescriptor(cccd)
                        }
                    }.getOrDefault(false)
                }
                if (!descriptorWriteStarted) {
                    mainHandler.post {
                        if (gatt === changedGatt) {
                            isConnecting = false
                            error = "No se pudieron activar las notificaciones EMG."
                            status = error!!
                        }
                    }
                }
            }

            override fun onDescriptorWrite(
                changedGatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                writeStatus: Int
            ) {
                if (descriptor.uuid != CLIENT_CONFIG_UUID) return
                mainHandler.post {
                    if (gatt === changedGatt) {
                        isConnecting = false
                        isConnected = writeStatus == BluetoothGatt.GATT_SUCCESS
                        error = if (isConnected) null else "Falló la suscripción EMG (código $writeStatus)."
                        status = if (isConnected) {
                            "Recibiendo señal EMG de ${selectedDevice?.name ?: "ESP32"}."
                        } else {
                            error!!
                        }
                        if (isConnected) lineBuffer.clear()
                    }
                }
            }

            override fun onCharacteristicChanged(
                changedGatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (characteristic.uuid == EMG_TX_UUID) {
                    @Suppress("DEPRECATION")
                    consumePacket(characteristic.value ?: return)
                }
            }

            override fun onCharacteristicChanged(
                changedGatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                if (characteristic.uuid == EMG_TX_UUID) consumePacket(value)
            }
        }
    }

    val beginScan: () -> Unit = {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            error = "Bluetooth LE no está disponible o Bluetooth está apagado."
            status = error!!
        } else {
            try {
                scanner.stopScan(scanCallback)
                devices.clear()
                selectedDevice = null
                error = null
                status = "Buscando la ESP32..."
                isScanning = true
                scanId += 1
                val requestId = scanId
                scanner.startScan(
                    null,
                    ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build(),
                    scanCallback
                )
                mainHandler.postDelayed({
                    if (isScanning && scanId == requestId) {
                        runCatching { scanner.stopScan(scanCallback) }
                        isScanning = false
                        status = if (devices.isEmpty()) {
                            "No se encontraron placas BLE. Comprueba que la ESP32 esté encendida."
                        } else {
                            "Escaneo finalizado. Selecciona la ESP32."
                        }
                    }
                }, 12_000L)
            } catch (securityException: SecurityException) {
                isScanning = false
                error = "Falta permiso para buscar dispositivos BLE."
                status = error!!
            } catch (exception: RuntimeException) {
                isScanning = false
                error = "No se pudo iniciar el escaneo BLE: ${exception.message ?: "error desconocido"}"
                status = error!!
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = permissions.any { permission ->
            results[permission] != true &&
                ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (denied) {
            pendingScan = false
            error = "Concede los permisos Bluetooth para buscar la ESP32."
            status = error!!
        } else if (pendingScan) {
            pendingScan = false
            beginScan()
        }
    }

    fun requestScan() {
        val missing = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            beginScan()
        } else {
            pendingScan = true
            status = "Solicitando permisos Bluetooth..."
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    fun connect() {
        val selected = selectedDevice ?: return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
            gatt?.close()
            isConnected = false
            isConnecting = true
            error = null
            status = "Conectando por BLE con ${selected.name}..."
            gatt = selected.device.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
            if (gatt == null) {
                isConnecting = false
                error = "Android no pudo iniciar la conexión GATT."
                status = error!!
            }
        } catch (securityException: SecurityException) {
            isConnecting = false
            error = "Falta permiso para conectar por BLE."
            status = error!!
        } catch (exception: RuntimeException) {
            isConnecting = false
            error = "No se pudo iniciar la conexión BLE: ${exception.message ?: "error desconocido"}"
            status = error!!
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback) }
            finishSession()
            gatt?.close()
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = CardColor,
            secondary = PurpleSoft,
            tertiary = Accent,
            background = Purple,
            surface = CardColor,
            error = Red
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Purple) {
            when (page) {
                AppPage.CONNECTION -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.brand_logo),
                            contentDescription = "Logo de Lession",
                            modifier = Modifier.size(160.dp)
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Conectar con el sensor",
                            color = Color.White,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = { requestScan() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isScanning && !isConnecting
                        ) {
                            Text(if (isScanning) "Buscando..." else "Buscar ESP32 por BLE")
                        }
                        androidx.compose.foundation.layout.Box {
                            OutlinedButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = devices.isNotEmpty(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text(
                                    selectedDevice?.let { "${it.name} (${it.device.address})" }
                                        ?: "Seleccionar dispositivo BLE"
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                devices.forEach { found ->
                                    DropdownMenuItem(
                                        text = { Text("${found.name} (${found.device.address})") },
                                        onClick = {
                                            selectedDevice = found
                                            menuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = ::connect,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedDevice != null && !isConnecting && !isConnected
                        ) {
                            Text(if (isConnecting) "Conectando..." else "Conectar")
                        }
                        Text(
                            status,
                            color = if (error == null) Color.White else Red,
                            fontSize = 13.sp
                        )
                        Button(
                            onClick = { page = AppPage.TRAINING },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isConnected
                        ) {
                            Text("Continuar")
                        }
                        NavigationButtons(
                            onBack = { (context as? Activity)?.finish() },
                            onExit = { showExitDialog = true }
                        )
                    }
                }

                AppPage.TRAINING -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Entrenamiento EMG",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = {
                                sessionSamples.clear()
                                graphData.clear()
                                sampleCount = 0
                                sessionStartedAt = System.currentTimeMillis()
                                selectedSession = null
                                isRecording = true
                                status = "Grabando muestras EMG reales..."
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isConnected && !isRecording && sessionStartedAt == null
                        ) {
                            Text("Iniciar sesión entrenamiento")
                        }
                        Button(
                            onClick = {
                                isRecording = false
                                status = "Entrenamiento detenido. Guarda para ver el análisis."
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isRecording,
                            colors = ButtonDefaults.buttonColors(containerColor = Red)
                        ) {
                            Text("Detener")
                        }
                        Text(
                            if (isRecording) "● Midiendo en tiempo real"
                            else status,
                            color = if (error == null) Color.White else Red,
                            fontSize = 13.sp
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = CardColor)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                Text("Datos del sensor", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    if (sensorTimeMs != null) "$adc ADC" else "Esperando datos…",
                                    color = Accent,
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Rango ADC: 0–4095", color = Color.White, fontSize = 13.sp)
                                Text(
                                    "Tiempo del sensor: ${sensorTimeMs?.let { "$it ms" } ?: "—"}",
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                                Text(
                                    "Muestras de esta sesión: ${sessionSamples.size}",
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                                Text(
                                    "Envolvente EMG rectificada y suavizada (cuentas ADC; no son mV).",
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                                SignalChart(
                                    data = graphData,
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                )
                            }
                        }
                        Button(
                            onClick = {
                                isRecording = false
                                val saved = saveCurrentSession()
                                if (saved != null) {
                                    selectedSession = saved
                                    status = "Sesión guardada para analizar."
                                    page = AppPage.ANALYSIS
                                } else {
                                    status = "No hay muestras EMG para analizar."
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = sessionStartedAt != null && sessionSamples.isNotEmpty() && !isRecording
                        ) {
                            Text("Guardar y ver análisis")
                        }
                        OutlinedButton(
                            onClick = { showSessions = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Sesiones guardadas (${sessions.size})")
                        }
                        NavigationButtons(
                            onBack = { page = AppPage.CONNECTION },
                            onExit = { showExitDialog = true }
                        )
                    }
                }

                AppPage.ANALYSIS -> {
                    val analysisSession = selectedSession
                    val samples = analysisSession?.samples ?: emptyList()
                    val values = samples.map { it.adc }
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Análisis de la sesión",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = CardColor)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (analysisSession != null) {
                                    Text(
                                        DateFormat.getDateTimeInstance().format(Date(analysisSession.startedAt)),
                                        color = Color.White
                                    )
                                    Text(
                                        "Duración: ${((analysisSession.finishedAt - analysisSession.startedAt) / 1000).coerceAtLeast(0)} s",
                                        color = Color.White
                                    )
                                }
                                Text("Muestras: ${samples.size}", color = Color.White)
                                Text("Mínimo ADC: ${values.minOrNull() ?: "—"}", color = Color.White)
                                Text("Máximo ADC: ${values.maxOrNull() ?: "—"}", color = Color.White)
                                Text("Promedio ADC: ${values.takeIf { it.isNotEmpty() }?.average()?.toInt() ?: "—"}", color = Color.White)
                                Text("Última lectura: ${values.lastOrNull()?.let { "$it ADC" } ?: "—"}", color = Color.White)
                                Text(
                                    "La placa entrega una envolvente EMG rectificada y suavizada en cuentas ADC (0–4095).",
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                                SignalChart(
                                    data = samples.takeLast(250).map { it.adc.toFloat() },
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { analysisSession?.let(exportSession) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = analysisSession != null,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Descargar sesión en CSV")
                        }
                        Button(
                            onClick = {
                                selectedSession = null
                                sessionSamples.clear()
                                graphData.clear()
                                sampleCount = 0
                                sessionStartedAt = null
                                isRecording = false
                                page = AppPage.TRAINING
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isConnected
                        ) {
                            Text("Hacer otra sesión")
                        }
                        Button(
                            onClick = {
                                analysisSession?.let { deleted ->
                                    sessions.removeAll { it.id == deleted.id }
                                    saveSessions(context, sessions)
                                }
                                selectedSession = null
                                sessionSamples.clear()
                                sessionStartedAt = null
                                isRecording = false
                                page = AppPage.TRAINING
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = analysisSession != null,
                            colors = ButtonDefaults.buttonColors(containerColor = Red)
                        ) {
                            Text("Borrar sesión actual")
                        }
                        NavigationButtons(
                            onBack = { page = AppPage.TRAINING },
                            onExit = { showExitDialog = true }
                        )
                    }
                }
            }

            if (showSessions) {
                AlertDialog(
                    onDismissRequest = { showSessions = false },
                    title = { Text("Sesiones guardadas") },
                    text = {
                        if (sessions.isEmpty()) {
                            Text("Todavía no hay sesiones guardadas.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 420.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(sessions.toList(), key = { it.id }) { session ->
                                    Card(colors = CardDefaults.cardColors(containerColor = CardColor)) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                DateFormat.getDateTimeInstance().format(Date(session.startedAt)),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text("${session.samples.size} muestras EMG", color = Color.White)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                TextButton(
                                                    onClick = {
                                                        selectedSession = session
                                                        showSessions = false
                                                        page = AppPage.ANALYSIS
                                                    }
                                                ) { Text("Ver") }
                                                TextButton(
                                                    onClick = { exportSession(session) }
                                                ) { Text("Exportar CSV") }
                                                TextButton(
                                                    onClick = {
                                                        sessions.removeAll { it.id == session.id }
                                                        saveSessions(context, sessions)
                                                    }
                                                ) { Text("Borrar", color = Red) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSessions = false }) { Text("Cerrar") }
                    }
                )
            }

            if (showExitDialog) {
                AlertDialog(
                    onDismissRequest = { showExitDialog = false },
                    title = { Text("Salir de la aplicación") },
                    text = { Text("¿Quieres cerrar la aplicación?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (sessionStartedAt != null) finishSession()
                                (context as? Activity)?.finish()
                            }
                        ) { Text("Salir", color = Red) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExitDialog = false }) { Text("Cancelar") }
                    }
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun NavigationButtons(
    onBack: () -> Unit,
    onExit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text("Atrás")
        }
        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text("Salir")
        }
    }
}

@androidx.compose.runtime.Composable
private fun SignalChart(data: List<Float>, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .background(CardColor, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        for (i in 0..4) {
            val y = size.height * i / 4
            drawLine(
                color = PurpleSoft,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }
        if (data.size < 2) return@Canvas

        val path = Path()
        data.forEachIndexed { index, value ->
            val x = size.width * index / (data.size - 1)
            val y = size.height * (1f - (value / 4095f).coerceIn(0f, 1f))
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = Accent)
    }
}
