package com.example.lessionangel

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import kotlin.random.Random

private data class Measurement(
    val heartRate: Int,
    val effort: Int,
    val fatigue: Int,
    val steps: Int
)

private data class TrainingSession(
    val id: Long,
    val startedAt: Long,
    val finishedAt: Long,
    val readings: List<Measurement>
)

private const val SESSION_PREFERENCES = "training_sessions"
private const val SESSION_DATA_KEY = "sessions"

private fun loadSessions(context: Context): List<TrainingSession> {
    val json = context.getSharedPreferences(SESSION_PREFERENCES, Context.MODE_PRIVATE)
        .getString(SESSION_DATA_KEY, null) ?: return emptyList()
    val array = JSONArray(json)

    return List(array.length()) { index ->
        val session = array.getJSONObject(index)
        val readingsJson = session.getJSONArray("readings")
        val readings = List(readingsJson.length()) { readingIndex ->
            val reading = readingsJson.getJSONObject(readingIndex)
            Measurement(
                heartRate = reading.getInt("heartRate"),
                effort = reading.getInt("effort"),
                fatigue = reading.getInt("fatigue"),
                steps = reading.getInt("steps")
            )
        }
        TrainingSession(
            id = session.getLong("id"),
            startedAt = session.getLong("startedAt"),
            finishedAt = session.getLong("finishedAt"),
            readings = readings
        )
    }
}

private fun saveSessions(context: Context, sessions: List<TrainingSession>) {
    val array = JSONArray()
    sessions.forEach { session ->
        val readings = JSONArray()
        session.readings.forEach { reading ->
            readings.put(
                JSONObject()
                    .put("heartRate", reading.heartRate)
                    .put("effort", reading.effort)
                    .put("fatigue", reading.fatigue)
                    .put("steps", reading.steps)
            )
        }
        array.put(
            JSONObject()
                .put("id", session.id)
                .put("startedAt", session.startedAt)
                .put("finishedAt", session.finishedAt)
                .put("readings", readings)
        )
    }
    context.getSharedPreferences(SESSION_PREFERENCES, Context.MODE_PRIVATE)
        .edit()
        .putString(SESSION_DATA_KEY, array.toString())
        .apply()
}

private val Purple = Color(0xFF4025C6)
private val PurpleSoft = Color(0xFFDCD5FF)
private val Accent = Color(0xFFE5FF00)
private val Dark = Color.White
private val Background = Purple
private val Card = Color(0xFF321B9B)
private val Green = Color(0xFF2FB56B)
private val Red = Color(0xFFE45757)

enum class AppScreen {
    CONNECTION, DATA, RESULTS, HISTORY
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LessionApp()
        }
    }
}

@Composable
fun LessionApp() {

    val context = LocalContext.current.applicationContext
    var screen by remember { mutableStateOf(AppScreen.CONNECTION) }
    var historyReturnScreen by remember { mutableStateOf(AppScreen.CONNECTION) }

    var connectionType by remember { mutableStateOf("Bluetooth") }
    var selectedDevice by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    var recording by remember { mutableStateOf(false) }

    var heartRate by remember { mutableIntStateOf(80) }
    var effort by remember { mutableIntStateOf(20) }
    var fatigue by remember { mutableIntStateOf(10) }
    var steps by remember { mutableIntStateOf(0) }
    var sessionStartedAt by remember { mutableLongStateOf(0L) }
    var selectedSession by remember { mutableStateOf<TrainingSession?>(null) }

    val graphData = remember { mutableStateListOf<Float>() }
    val sessionReadings = remember { mutableStateListOf<Measurement>() }
    val sessions = remember(context) {
        mutableStateListOf<TrainingSession>().apply {
            addAll(loadSessions(context))
        }
    }

    val devices = if (connectionType == "Bluetooth") {
        listOf(
            "ESP32-Lession",
            "Sensor muscular 01",
            "Banda deportiva"
        )
    } else {
        listOf(
            "Lession_WiFi",
            "ESP32_Network",
            "Red de entrenamiento"
        )
    }

    // Simulación de mediciones en tiempo real
    LaunchedEffect(recording) {
        while (recording) {
            heartRate = Random.nextInt(75, 160)
            effort = Random.nextInt(20, 100)
            fatigue = Random.nextInt(10, 90)
            steps += Random.nextInt(1, 5)

            sessionReadings.add(Measurement(heartRate, effort, fatigue, steps))
            graphData.add(heartRate.toFloat())

            if (graphData.size > 40) {
                graphData.removeAt(0)
            }

            delay(1000)
        }
    }

    fun clearActiveSession() {
        graphData.clear()
        sessionReadings.clear()
        heartRate = 80
        effort = 20
        fatigue = 10
        steps = 0
        sessionStartedAt = 0L
        recording = false
    }

    val bluetoothPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.all { it.value }
        if (allGranted) {
            connected = true
        } else {
            Toast.makeText(
                context,
                "Se necesitan permisos de Bluetooth para continuar.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun requestConnectionPermissionsIfNeeded() {
        val shouldRequestBluetoothPermissions = connectionType == "Bluetooth" &&
            bluetoothPermissions.any { permission ->
                ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
            }

        if (shouldRequestBluetoothPermissions) {
            bluetoothPermissionLauncher.launch(bluetoothPermissions)
        } else {
            connected = true
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Card,
            onPrimary = Color.White,
            secondary = PurpleSoft,
            onSecondary = Color.White,
            tertiary = Accent,
            onTertiary = Color.White,
            background = Background,
            surface = Card,
            onBackground = Color.White,
            onSurface = Color.White,
            error = Red
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Background
        ) {
            when (screen) {

                // PANTALLA 1: CONEXIÓN

                AppScreen.CONNECTION -> {

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {

                        Image(
                            painter = painterResource(R.drawable.brand_logo),
                            contentDescription = "Logo de Lession",
                            modifier = Modifier.size(150.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "Sistema de entrenamiento",
                            color = Dark,
                            fontSize = 16.sp
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "Modo demostración: no se reciben datos reales del sensor.",
                            color = Color.White,
                            fontSize = 12.sp
                        )

                        Spacer(Modifier.height(32.dp))

                        Text(
                            text = "¿Con qué desea conectarse?",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = Dark
                        )

                        Spacer(Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {

                            Button(
                                onClick = {
                                    connectionType = "Bluetooth"
                                    selectedDevice = ""
                                    connected = false
                                    expanded = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Bluetooth")
                            }

                            Button(
                                onClick = {
                                    connectionType = "WiFi"
                                    selectedDevice = ""
                                    connected = false
                                    expanded = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Wi-Fi")
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        Box {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.Black
                                )
                            ) {
                                Text(
                                    selectedDevice.ifEmpty {
                                        "Seleccionar $connectionType"
                                    },
                                    color = Color.Black
                                )
                            }

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                containerColor = Color.White
                            ) {
                                devices.forEach { device ->
                                    DropdownMenuItem(
                                        text = { Text(device, color = Color.Black) },
                                        onClick = {
                                            selectedDevice = device
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Button(
                            onClick = {
                                if (selectedDevice.isNotEmpty()) {
                                    requestConnectionPermissionsIfNeeded()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedDevice.isNotEmpty()
                        ) {
                            Text("Conectarse")
                        }

                        if (connected) {
                            Spacer(Modifier.height(15.dp))

                            Text(
                                text = "✓ $selectedDevice seleccionado (modo demostración)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    screen = AppScreen.DATA
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Continuar")
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                historyReturnScreen = AppScreen.CONNECTION
                                screen = AppScreen.HISTORY
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Text("Ver sesiones guardadas (${sessions.size})")
                        }
                    }
                }

                // PANTALLA 2: TOMA DE DATOS

                AppScreen.DATA -> {

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {

                        Text(
                            "Toma de datos",
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Bold,
                            color = Dark
                        )

                        Spacer(Modifier.height(20.dp))

                        Button(
                            onClick = {
                                clearActiveSession()
                                selectedSession = null
                                sessionStartedAt = System.currentTimeMillis()
                                recording = true
                            },
                            enabled = !recording,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Comenzar con la toma de datos")
                        }

                        Spacer(Modifier.height(10.dp))

                        Button(
                            onClick = {
                                recording = false
                            },
                            enabled = recording,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Red,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Detener")
                        }

                        Spacer(Modifier.height(20.dp))

                        Text(
                            if (recording) "● Midiendo en tiempo real"
                            else "Medición detenida",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(15.dp))

                        // RECTÁNGULO DE GRÁFICOS

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Card
                            )
                        ) {

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {

                                Text(
                                    "Monitor de rendimiento",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(Modifier.height(15.dp))

                                MetricRow("Frecuencia cardíaca", "$heartRate BPM")
                                MetricRow("Nivel de esfuerzo", "$effort %")
                                MetricRow("Fatiga muscular", "$fatigue %")
                                MetricRow("Pasos", "$steps")

                                Spacer(Modifier.height(20.dp))

                                Text(
                                    "Frecuencia cardíaca en tiempo real",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )

                                Spacer(Modifier.height(12.dp))

                                HeartRateChart(
                                    data = graphData,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                )
                            }
                        }

                        Spacer(Modifier.height(15.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    recording = false
                                    val startedAt = sessionStartedAt.takeIf { it > 0L }
                                        ?: System.currentTimeMillis()
                                    val saved = TrainingSession(
                                        id = System.currentTimeMillis(),
                                        startedAt = startedAt,
                                        finishedAt = System.currentTimeMillis(),
                                        readings = sessionReadings.toList()
                                    )
                                    sessions.add(saved)
                                    saveSessions(context, sessions)
                                    selectedSession = saved
                                    screen = AppScreen.RESULTS
                                },
                                enabled = sessionReadings.isNotEmpty()
                            ) {
                                Text("Guardar y mostrar datos")
                            }
                        }
                    }
                }

                // PANTALLA 3: RESULTADOS

                AppScreen.RESULTS -> {
                    val resultSession = selectedSession
                    val resultReadings = resultSession?.readings ?: sessionReadings.toList()
                    val resultHeartRate = resultReadings.lastOrNull()?.heartRate ?: heartRate
                    val resultEffort = resultReadings.lastOrNull()?.effort ?: effort
                    val resultFatigue = resultReadings.lastOrNull()?.fatigue ?: fatigue
                    val resultSteps = resultReadings.lastOrNull()?.steps ?: steps

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {

                        Text(
                            "Resultados de la sesión",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Dark
                        )

                        Spacer(Modifier.height(20.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = Card
                            )
                        ) {

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {

                                item {
                                    Text(
                                        "Resumen del entrenamiento",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                item {
                                    resultSession?.let {
                                        Text(
                                            DateFormat.getDateTimeInstance().format(Date(it.startedAt)),
                                            color = Color.White
                                        )
                                    }
                                    MetricRow("Frecuencia cardíaca final", "$resultHeartRate BPM")
                                    MetricRow("Esfuerzo final", "$resultEffort %")
                                    MetricRow("Fatiga final", "$resultFatigue %")
                                    MetricRow("Pasos registrados", "$resultSteps")
                                    MetricRow("Cantidad de mediciones", "${resultReadings.size}")
                                }

                                item {
                                    HorizontalDivider()
                                    Text(
                                        "Análisis detallado",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                item {
                                    Text(
                                        "Frecuencia cardíaca: " +
                                                when {
                                                    resultHeartRate < 100 ->
                                                        "Nivel moderado de actividad."
                                                    resultHeartRate < 140 ->
                                                        "Actividad cardiovascular elevada."
                                                    else ->
                                                        "Frecuencia cardíaca alta. Se recomienda controlar el esfuerzo."
                                                }
                                    )
                                }

                                item {
                                    Text(
                                        "Esfuerzo muscular: " +
                                                when {
                                                    resultEffort < 40 -> "Bajo."
                                                    resultEffort < 70 -> "Moderado."
                                                    else -> "Elevado."
                                                }
                                    )
                                }

                                item {
                                    Text(
                                        "Fatiga: " +
                                                when {
                                                    resultFatigue < 40 -> "Baja."
                                                    resultFatigue < 70 -> "Moderada."
                                                    else -> "Elevada."
                                                }
                                    )
                                }

                                item {
                                    Text(
                                        "Estos resultados son orientativos y se calculan " +
                                                "con los valores registrados durante la sesión."
                                    )
                                }

                                item {
                                    Text(
                                        "Evolución de frecuencia cardíaca",
                                        fontWeight = FontWeight.Bold
                                    )
                                    HeartRateChart(
                                        data = resultReadings.map { it.heartRate.toFloat() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(15.dp))

                        // BOTÓN SALIR

                        var showExitDialog by remember {
                            mutableStateOf(false)
                        }

                        Button(
                            onClick = {
                                showExitDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Red,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Salir")
                        }

                        // CONFIRMACIÓN DE SALIDA

                        val currentActivity = LocalContext.current as? Activity

                        if (showExitDialog) {
                            AlertDialog(
                                onDismissRequest = {
                                    showExitDialog = false
                                },
                                containerColor = Color.White,
                                titleContentColor = Color.Black,
                                textContentColor = Color.Black,
                                title = {
                                    Text("Confirmar salida", color = Color.Black)
                                },
                                text = {
                                    Text("¿Está seguro de salir?", color = Color.Black)
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            currentActivity?.finish()
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Text("Sí, salir", color = Color.Black)
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            showExitDialog = false
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Text("Cancelar", color = Color.Black)
                                    }
                                }
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = {
                                historyReturnScreen = AppScreen.RESULTS
                                screen = AppScreen.HISTORY
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Ver sesiones guardadas (${sessions.size})")
                        }

                        Spacer(Modifier.height(8.dp))

                        // NUEVA SESIÓN

                        Button(
                            onClick = {
                                clearActiveSession()
                                selectedSession = null
                                screen = AppScreen.DATA
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Hacer una nueva sesión de entrenamiento")
                        }

                        Spacer(Modifier.height(8.dp))

                        // BORRAR SESIÓN

                        OutlinedButton(
                            onClick = {
                                selectedSession?.let { deleted ->
                                    sessions.removeAll { it.id == deleted.id }
                                    saveSessions(context, sessions)
                                }
                                clearActiveSession()
                                selectedSession = null
                                screen = AppScreen.CONNECTION
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Text("Borrar sesión")
                        }
                    }
                }

                AppScreen.HISTORY -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        Text(
                            "Sesiones guardadas",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(Modifier.height(16.dp))

                        if (sessions.isEmpty()) {
                            Text("Todavía no hay sesiones guardadas.", color = Color.White)
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    sessions.sortedByDescending { it.startedAt },
                                    key = { it.id }
                                ) { session ->
                                    val lastReading = session.readings.lastOrNull()
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Card)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                        ) {
                                            Text(
                                                DateFormat.getDateTimeInstance()
                                                    .format(Date(session.startedAt)),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "${session.readings.size} mediciones" +
                                                        (lastReading?.let {
                                                            " · ${it.heartRate} BPM · esfuerzo ${it.effort}%"
                                                        } ?: ""),
                                                color = Color.White
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        selectedSession = session
                                                        screen = AppScreen.RESULTS
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Ver resultados")
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        sessions.removeAll { it.id == session.id }
                                                        saveSessions(context, sessions)
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = Color.White
                                                    )
                                                ) {
                                                    Text("Borrar")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { screen = historyReturnScreen },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Text("Volver")
                        }
                    }
                }
            }
        }
    }
}

// INDICADORES DE DATOS

@Composable
fun MetricRow(
    name: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            name,
            color = Dark,
            fontSize = 14.sp
        )

        Text(
            value,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 15.sp
        )
    }
}

// GRÁFICO EN TIEMPO REAL

@Composable
fun HeartRateChart(
    data: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .background(
                Card,
                RoundedCornerShape(12.dp)
            )
            .padding(8.dp)
    ) {

        val chartWidth = size.width
        val chartHeight = size.height

        // Líneas horizontales

        for (i in 0..4) {
            val y = chartHeight * i / 4

            drawLine(
                color = PurpleSoft,
                start = Offset(0f, y),
                end = Offset(chartWidth, y),
                strokeWidth = 1f
            )
        }

        if (data.size < 2) return@Canvas

        val maxValue = 180f
        val minValue = 50f

        val path = Path()

        data.forEachIndexed { index, value ->

            val x = chartWidth * index / (data.size - 1)

            val normalized = (
                    (value - minValue) / (maxValue - minValue)
                    ).coerceIn(0f, 1f)

            val y = chartHeight * (1f - normalized)

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = Accent,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 4f
            )
        )
    }
}
