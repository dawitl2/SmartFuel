package com.smartfuel.mobile

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (permissions.isNotEmpty()) requestPermissions(permissions.toTypedArray(), 70)
        setContent {
            SmartFuelTheme {
                SmartFuelApp()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("smartfuel-alerts", "SmartFuel Alerts", NotificationManager.IMPORTANCE_DEFAULT)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

data class Dashboard(
    val source: String,
    val car: Car,
    val settings: VehicleSettings,
    val fuel: Fuel,
    val liveStatus: LiveStatus,
    val trips: List<Trip>,
    val notifications: List<NotificationItem>,
    val charts: List<ChartPoint>
)

data class Car(val name: String, val make: String, val model: String, val year: Int, val status: String)
data class VehicleSettings(val tankCapacityLiters: Double, val maxRangeKm: Double, val lowFuelNotificationPercent: Double)
data class Fuel(val remainingLiters: Double, val usedLiters: Double, val rangeKm: Double, val percentage: Double)
data class LiveStatus(
    val speedKph: Double,
    val rpm: Double,
    val coolantTempC: Double,
    val engineLoadPercent: Double,
    val odometerKm: Double,
    val tripDistanceKm: Double,
    val drivingSeconds: Int,
    val engineState: String,
    val drivingIntensity: Double
)
data class Trip(val id: String, val startedAt: String, val distanceKm: Double, val averageSpeedKph: Double, val fuelUsedLiters: Double)
data class NotificationItem(val id: String, val title: String, val body: String, val severity: String)
data class ChartPoint(val label: String, val speed: Double, val rpm: Double, val fuel: Double)

data class ManualPlace(
    val id: String,
    val name: String,
    val kilometers: Double,
    val days: List<String>,
    val latitude: Double?,
    val longitude: Double?
)

data class ManualState(
    val tankLiters: Double = 40.0,
    val maxTrackedKm: Double = 200.0,
    val alertKm: Double = 150.0,
    val kilometersDriven: Double = 0.0,
    val alertSent: Boolean = false,
    val trackingEnabled: Boolean = true,
    val homeWifiSsid: String = "EnQ",
    val placeRadiusMeters: Double = 20.0,
    val stopRadiusMeters: Double = 10.0,
    val stopMinutes: Double = 30.0,
    val homeAddress: String = "",
    val homeLat: Double? = null,
    val homeLon: Double? = null,
    val places: List<ManualPlace> = emptyList(),
    val surprisePlaces: List<ManualPlace> = emptyList(),
    val pendingPlaceId: String? = null,
    val pendingPlaceName: String? = null,
    val pendingKm: Double = 0.0,
    val pendingMessage: String? = null,
    val pendingLat: Double? = null,
    val pendingLon: Double? = null,
    val lastPromptPlaceId: String? = null,
    val lastLat: Double? = null,
    val lastLon: Double? = null,
    val tripStartLat: Double? = null,
    val tripStartLon: Double? = null,
    val stationarySinceMs: Long = 0L
) {
    val remainingKm: Double get() = (maxTrackedKm - kilometersDriven).coerceIn(0.0, maxTrackedKm)
    val percentage: Double get() = (remainingKm / maxTrackedKm * 100.0).coerceIn(0.0, 100.0)
    val remainingLiters: Double get() = tankLiters * percentage / 100.0
}

class SmartFuelApi(private val context: Context) {
    private val baseUrl = BuildConfig.API_BASE_URL
    private val prefs = context.getSharedPreferences("smartfuel-cache", Context.MODE_PRIVATE)

    suspend fun getDashboard(source: String): Pair<Dashboard, Boolean> = withContext(Dispatchers.IO) {
        try {
            val raw = get("$baseUrl/api/dashboard?source=$source")
            prefs.edit().putString("dashboard", raw).apply()
            parseDashboard(raw) to true
        } catch (exception: Exception) {
            val cached = prefs.getString("dashboard", null)
            if (cached != null) {
                val cachedDashboard = parseDashboard(cached)
                if (cachedDashboard.source == source) {
                    cachedDashboard to false
                } else {
                    throw exception
                }
            } else {
                throw exception
            }
        }
    }

    suspend fun refuel(eventType: String, litersAdded: Double, source: String) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("eventType", eventType)
            .put("litersAdded", litersAdded)
            .put("note", "Android app refuel event")
        post("$baseUrl/api/refuel?source=$source", payload.toString())
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        return readResponse(connection)
    }

    private fun post(url: String, body: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toByteArray()) }
        return readResponse(connection)
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (status !in 200..299) error("HTTP $status: $body")
        return body
    }
}

fun parseDashboard(raw: String): Dashboard {
    val root = JSONObject(raw)
    val car = root.getJSONObject("car")
    val fuel = root.getJSONObject("fuel")
    val live = root.getJSONObject("liveStatus")
    val settings = root.optJSONObject("settings")

    return Dashboard(
        source = root.optString("source", "mock"),
        car = Car(
            name = car.getString("name"),
            make = car.getString("make"),
            model = car.getString("model"),
            year = car.getInt("year"),
            status = car.getString("status")
        ),
        settings = VehicleSettings(
            tankCapacityLiters = settings?.optDouble("tankCapacityLiters", 40.0) ?: 40.0,
            maxRangeKm = settings?.optDouble("maxRangeKm", 400.0) ?: 400.0,
            lowFuelNotificationPercent = settings?.optDouble("lowFuelNotificationPercent", 70.0) ?: 70.0
        ),
        fuel = Fuel(
            remainingLiters = fuel.getDouble("fuelRemainingLiters"),
            usedLiters = fuel.getDouble("fuelUsedLiters"),
            rangeKm = fuel.getDouble("estimatedRangeKm"),
            percentage = fuel.getDouble("fuelPercentage")
        ),
        liveStatus = LiveStatus(
            speedKph = live.getDouble("speedKph"),
            rpm = live.getDouble("rpm"),
            coolantTempC = live.getDouble("coolantTempC"),
            engineLoadPercent = live.getDouble("engineLoadPercent"),
            odometerKm = live.getDouble("estimatedOdometerKm"),
            tripDistanceKm = live.getDouble("tripDistanceKm"),
            drivingSeconds = live.getInt("drivingSeconds"),
            engineState = live.optString("engineState", "unknown"),
            drivingIntensity = live.optDouble("drivingIntensity", 1.0)
        ),
        trips = root.getJSONArray("trips").mapObjects {
            Trip(
                id = it.getString("id"),
                startedAt = it.getString("startedAt"),
                distanceKm = it.getDouble("distanceKm"),
                averageSpeedKph = it.getDouble("averageSpeedKph"),
                fuelUsedLiters = it.getDouble("fuelUsedLiters")
            )
        },
        notifications = root.getJSONArray("notifications").mapObjects {
            NotificationItem(
                id = it.getString("id"),
                title = it.getString("title"),
                body = it.getString("body"),
                severity = it.getString("severity")
            )
        },
        charts = root.getJSONArray("charts").mapObjects {
            ChartPoint(
                label = it.getString("time"),
                speed = it.getDouble("speed"),
                rpm = it.getDouble("rpm"),
                fuel = it.getDouble("fuel")
            )
        }
    )
}

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
    val items = mutableListOf<T>()
    for (index in 0 until length()) items.add(transform(getJSONObject(index)))
    return items
}

@Composable
fun SmartFuelApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("smartfuel-cache", Context.MODE_PRIVATE) }
    val api = remember { SmartFuelApi(context) }
    val scope = rememberCoroutineScope()
    var dashboard by remember { mutableStateOf<Dashboard?>(null) }
    var manualState by remember { mutableStateOf(loadManualState(prefs)) }
    var loading by remember { mutableStateOf(true) }
    var online by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var liters by remember { mutableStateOf("5") }
    var dataSource by remember { mutableStateOf(prefs.getString("source", "mock") ?: "mock") }
    var activeTab by remember { mutableStateOf("Overview") }
    var controlsOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var activeManualPanel by remember { mutableStateOf<String?>(null) }

    fun saveManual(next: ManualState) {
        val withAlert = if (next.kilometersDriven >= next.alertKm && !next.alertSent) {
            showManualRangeNotification(context, next.kilometersDriven)
            next.copy(alertSent = true)
        } else {
            next
        }
        manualState = withAlert
        saveManualState(prefs, withAlert)
    }

    fun load(source: String = dataSource) {
        if (source == "manual") {
            loading = false
            error = null
            dashboard = null
            manualState = loadManualState(prefs)
            return
        }
        scope.launch {
            loading = true
            error = null
            try {
                val result = api.getDashboard(source)
                dashboard = result.first
                online = result.second
                if (source != "mock" && result.first.source != "mock" && result.first.fuel.percentage <= result.first.settings.lowFuelNotificationPercent) {
                    showLowFuelNotification(context, result.first.fuel.percentage)
                }
            } catch (exception: Exception) {
                error = exception.message
            } finally {
                loading = false
            }
        }
    }

    fun refuel(eventType: String) {
        scope.launch {
            try {
                api.refuel(eventType, liters.toDoubleOrNull() ?: 0.0, dataSource)
                load(dataSource)
            } catch (exception: Exception) {
                error = exception.message
            }
        }
    }

    fun changeSource(source: String) {
        dataSource = source
        prefs.edit().putString("source", source).apply()
        dashboard = null
        error = null
        load(source)
    }

    LaunchedEffect(Unit) { load() }
    LaunchedEffect(dataSource, manualState.trackingEnabled) {
        updateManualTrackingService(context, dataSource, manualState)
    }
    LaunchedEffect(dataSource) {
        while (dataSource == "manual") {
            val latest = loadManualState(prefs)
            if (latest != manualState) manualState = latest
            delay(2000)
        }
    }
    BackHandler(settingsOpen) { settingsOpen = false }
    BackHandler(activeManualPanel != null) { activeManualPanel = null }

    Surface(color = Color(0xFF0A0A0A), modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when {
                dataSource == "manual" -> ManualModeView(
                    state = manualState,
                    onSaveState = ::saveManual,
                    onOpenSettings = { settingsOpen = true }
                )
                loading && dashboard == null -> LoadingView()
                error != null && dashboard == null -> ErrorView(
                    message = error.orEmpty(),
                    source = dataSource,
                    onRetry = { load(dataSource) },
                    onReturnToMock = { changeSource("mock") }
                )
                dashboard != null -> DashboardView(
                    dashboard = dashboard!!,
                    online = online,
                    activeTab = activeTab,
                    controlsOpen = controlsOpen,
                    liters = liters,
                    onTabChange = { activeTab = it },
                    onToggleControls = { controlsOpen = !controlsOpen },
                    onOpenSettings = { settingsOpen = true },
                    onLitersChange = { liters = it },
                    onRefresh = { load(dataSource) },
                    onFullReset = { refuel("full_reset") },
                    onPartialRefuel = { refuel("partial_refuel") },
                    error = error
                )
            }

            if (settingsOpen) {
                SettingsDrawer(
                    dataSource = dataSource,
                    state = manualState,
                    onSourceChange = {
                        settingsOpen = false
                        changeSource(it)
                    },
                    onOpenPanel = {
                        settingsOpen = false
                        activeManualPanel = it
                    },
                    onClose = { settingsOpen = false }
                )
            }

            when (activeManualPanel) {
                "manual" -> ManualEntryDialog(
                    state = manualState,
                    onSaveState = ::saveManual,
                    onClose = { activeManualPanel = null }
                )
                "dedicated" -> PlacesDialog(
                    title = "Dedicated Places",
                    state = manualState,
                    surprise = false,
                    onSaveState = ::saveManual,
                    onClose = { activeManualPanel = null }
                )
                "surprise" -> PlacesDialog(
                    title = "Surprise Places",
                    state = manualState,
                    surprise = true,
                    onSaveState = ::saveManual,
                    onClose = { activeManualPanel = null }
                )
                "location" -> ManualSettingsDialog(
                    state = manualState,
                    onSaveState = ::saveManual,
                    onClose = { activeManualPanel = null }
                )
            }
        }
    }
}

@Composable
fun DashboardView(
    dashboard: Dashboard,
    online: Boolean,
    activeTab: String,
    controlsOpen: Boolean,
    liters: String,
    onTabChange: (String) -> Unit,
    onToggleControls: () -> Unit,
    onOpenSettings: () -> Unit,
    onLitersChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onFullReset: () -> Unit,
    onPartialRefuel: () -> Unit,
    error: String?
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SmartFuel", color = Color(0xFF34D399), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(dashboard.car.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("${dashboard.car.year} ${dashboard.car.make} ${dashboard.car.model}", color = Color(0xFFA1A1AA))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Button(onClick = onOpenSettings, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF18181B))) {
                        Text("☰", fontSize = 20.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    StatusBadge(if (online) dashboard.liveStatus.engineState else "cached")
                }
            }
        }

        if (error != null) item { Notice(error) }

        item { TabBar(activeTab, onTabChange) }

        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FuelRing(dashboard.fuel.percentage)
                    Spacer(Modifier.width(18.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${dashboard.fuel.remainingLiters.format1()} L", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("${dashboard.fuel.rangeKm.format0()} km estimated range", color = Color(0xFFA1A1AA))
                        Text("${dashboard.fuel.usedLiters.format1()} L used since reset", color = Color(0xFFA1A1AA))
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Metric("Speed", "${dashboard.liveStatus.speedKph.format0()} km/h", Modifier.weight(1f))
                Metric("RPM", dashboard.liveStatus.rpm.format0(), Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Metric("Engine", dashboard.liveStatus.engineState, Modifier.weight(1f))
                Metric("Intensity", "${dashboard.liveStatus.drivingIntensity.format1()}x", Modifier.weight(1f))
            }
        }

        if (activeTab == "Overview") {
            item {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fuel Controls", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Keep advanced controls tucked away.", color = Color(0xFFA1A1AA), fontSize = 13.sp)
                        }
                        Button(onClick = onToggleControls, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))) {
                            Text(if (controlsOpen) "Hide" else "Extend")
                        }
                    }
                    if (controlsOpen) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = liters,
                                onValueChange = onLitersChange,
                                label = { Text("Liters") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = onPartialRefuel, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))) {
                                Text("Add")
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onFullReset, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                                Text("Full Reset")
                            }
                            Button(onClick = onRefresh, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                                Text("Refresh")
                            }
                        }
                    }
                }
            }
        }

        if (activeTab == "Stats") {
            item {
                Card {
                    Text("Speed Trend", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    MiniBarChart(points = dashboard.charts.map { it.speed }, color = Color(0xFF2563EB))
                    Spacer(Modifier.height(14.dp))
                    Text("RPM Trend", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    MiniBarChart(points = dashboard.charts.map { it.rpm }, color = Color(0xFFE11D48))
                }
            }
        }

        if (activeTab == "Trips") {
            item { SectionTitle("Recent Trips") }
            items(dashboard.trips, key = { it.id }) { trip ->
                Card {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(trip.startedAt.take(10), color = Color.White, fontWeight = FontWeight.Bold)
                            Text("${trip.distanceKm.format1()} km / ${trip.averageSpeedKph.format0()} km/h avg", color = Color(0xFFA1A1AA))
                        }
                        Text("${trip.fuelUsedLiters.format1()} L", color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (activeTab == "Overview") {
            item { SectionTitle("Notifications") }
            items(dashboard.notifications, key = { it.id }) { notification ->
                Card {
                    Text(notification.title, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(notification.body, color = Color(0xFFA1A1AA), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun ManualModeView(state: ManualState, onSaveState: (ManualState) -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    var quickKm by remember { mutableStateOf("10") }
    var manualOpen by remember { mutableStateOf(false) }

    fun save(next: ManualState) = onSaveState(next)
    fun addDistance(km: Double) {
        save(state.copy(kilometersDriven = (state.kilometersDriven + km).coerceIn(0.0, state.maxTrackedKm)))
    }

    if (state.pendingMessage != null) {
        PendingTripDialog(state = state, onSaveState = onSaveState)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SmartFuel", color = Color(0xFF34D399), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("GPS Manual", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(if (state.trackingEnabled) "GPS assist is on" else "Manual-only tracking", color = Color(0xFFA1A1AA))
                }
                Button(onClick = onOpenSettings, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF18181B))) {
                    Text("☰", fontSize = 20.sp)
                }
            }
        }

        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FuelRing(state.percentage)
                    Spacer(Modifier.width(18.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${state.remainingKm.format0()} km", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("${state.remainingLiters.format1()} L estimated", color = Color(0xFFA1A1AA))
                        Text("${state.kilometersDriven.format1()} km driven from ${state.maxTrackedKm.format0()} km", color = Color(0xFFA1A1AA))
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Metric("Driven", "${state.kilometersDriven.format0()} km", Modifier.weight(1f))
                Metric("Remaining", "${state.remainingKm.format0()} km", Modifier.weight(1f))
            }
        }

        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Manual Entry", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Add distance or reset after refuel.", color = Color(0xFFA1A1AA), fontSize = 13.sp)
                    }
                    Button(onClick = { manualOpen = !manualOpen }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))) {
                        Text(if (manualOpen) "Hide" else "Extend")
                    }
                }
                if (manualOpen) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        DarkTextField(
                            value = quickKm,
                            onValueChange = { quickKm = it },
                            label = "Kilometers",
                            modifier = Modifier.weight(1f),
                            numeric = true
                        )
                        Button(onClick = { addDistance(quickKm.toDoubleOrNull() ?: 0.0) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                            Text("Add")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        save(state.copy(kilometersDriven = 0.0, alertSent = false, pendingMessage = null, pendingPlaceId = null))
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                        Text("Refill / Reset")
                    }
                }
            }
        }

        item {
            Card {
                Text("Trip Assist", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Known places: ${state.places.size}. Surprise places: ${state.surprisePlaces.size}. Home: ${if (state.homeAddress.isBlank()) "not set" else state.homeAddress}.", color = Color(0xFFA1A1AA), fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (state.trackingEnabled && !hasLocationPermission(context)) "GPS permission is missing. Open Manual Settings to allow it." else "Open the menu to edit places, alert distance, Wi-Fi and location rules.",
                    color = if (state.trackingEnabled && !hasLocationPermission(context)) Color(0xFFFDE68A) else Color(0xFF34D399),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun PendingTripDialog(state: ManualState, onSaveState: (ManualState) -> Unit) {
    var distanceText by remember(state.pendingMessage) { mutableStateOf(state.pendingKm.takeIf { it > 0.0 }?.format1() ?: "") }
    var saveAsSurprise by remember(state.pendingMessage) { mutableStateOf(false) }
    var saveAsDedicated by remember(state.pendingMessage) { mutableStateOf(false) }
    var placeName by remember(state.pendingMessage) { mutableStateOf(state.pendingPlaceName ?: "New destination") }
    val distance = distanceText.toDoubleOrNull() ?: 0.0

    fun clearPending(next: ManualState = state): ManualState = next.copy(
        pendingPlaceId = null,
        pendingPlaceName = null,
        pendingKm = 0.0,
        pendingMessage = null,
        pendingLat = null,
        pendingLon = null
    )

    Dialog(onDismissRequest = { onSaveState(clearPending()) }) {
        Card {
            Text("Trip Check", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(state.pendingMessage.orEmpty(), color = Color(0xFFA1A1AA), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            DarkTextField(
                value = distanceText,
                onValueChange = { distanceText = it },
                label = "Distance to subtract",
                modifier = Modifier.fillMaxWidth(),
                numeric = true
            )
            Spacer(Modifier.height(8.dp))
            DarkTextField(
                value = placeName,
                onValueChange = { placeName = it },
                label = "Place name",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            CheckRow("Save as surprise place", saveAsSurprise) {
                saveAsSurprise = it
                if (it) saveAsDedicated = false
            }
            CheckRow("Save as dedicated place", saveAsDedicated) {
                saveAsDedicated = it
                if (it) saveAsSurprise = false
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onSaveState(clearPending()) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))) {
                    Text("No")
                }
                Button(
                    onClick = {
                        val place = ManualPlace(
                            id = UUID.randomUUID().toString(),
                            name = placeName.ifBlank { "Saved place" },
                            kilometers = distance.coerceAtLeast(0.0),
                            days = emptyList(),
                            latitude = state.pendingLat,
                            longitude = state.pendingLon
                        )
                        val withDistance = state.copy(kilometersDriven = (state.kilometersDriven + distance).coerceIn(0.0, state.maxTrackedKm))
                        val withSavedPlace = when {
                            saveAsSurprise && distance > 0.0 -> withDistance.copy(surprisePlaces = withDistance.surprisePlaces + place)
                            saveAsDedicated && distance > 0.0 -> withDistance.copy(places = withDistance.places + place.copy(days = listOf("Mon")))
                            else -> withDistance
                        }
                        onSaveState(clearPending(withSavedPlace))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Confirm")
                }
            }
        }
    }
}

@Composable
fun ManualEntryDialog(state: ManualState, onSaveState: (ManualState) -> Unit, onClose: () -> Unit) {
    var tripKm by remember { mutableStateOf("7") }
    var drivenKm by remember { mutableStateOf(state.kilometersDriven.format1()) }
    var remainingKm by remember { mutableStateOf(state.remainingKm.format1()) }
    Dialog(onDismissRequest = onClose) {
        Card {
            Text("Manual Entry", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Use this when GPS assist is off, wrong, or you simply know the distance yourself.", color = Color(0xFFA1A1AA), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            DarkTextField(tripKm, { tripKm = it }, "Add trip distance km", Modifier.fillMaxWidth(), numeric = true)
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                onSaveState(state.copy(kilometersDriven = (state.kilometersDriven + (tripKm.toDoubleOrNull() ?: 0.0)).coerceIn(0.0, state.maxTrackedKm)))
                onClose()
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                Text("Add Trip")
            }
            Spacer(Modifier.height(12.dp))
            DarkTextField(drivenKm, { drivenKm = it }, "Set total driven km", Modifier.fillMaxWidth(), numeric = true)
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                onSaveState(state.copy(kilometersDriven = (drivenKm.toDoubleOrNull() ?: state.kilometersDriven).coerceIn(0.0, state.maxTrackedKm)))
                onClose()
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))) {
                Text("Set Driven")
            }
            Spacer(Modifier.height(12.dp))
            DarkTextField(remainingKm, { remainingKm = it }, "Set remaining range km", Modifier.fillMaxWidth(), numeric = true)
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                val remaining = (remainingKm.toDoubleOrNull() ?: state.remainingKm).coerceIn(0.0, state.maxTrackedKm)
                onSaveState(state.copy(kilometersDriven = (state.maxTrackedKm - remaining).coerceIn(0.0, state.maxTrackedKm)))
                onClose()
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))) {
                Text("Set Remaining")
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                onSaveState(state.copy(kilometersDriven = 0.0, alertSent = false, pendingMessage = null))
                onClose()
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                Text("Full Refill / Reset")
            }
        }
    }
}

@Composable
fun PlacesDialog(title: String, state: ManualState, surprise: Boolean, onSaveState: (ManualState) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val places = if (surprise) state.surprisePlaces else state.places
    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var km by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(setOf("Mon")) }
    var saveCurrentLocation by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    fun resetForm() {
        editingId = null
        name = ""
        km = ""
        days = setOf("Mon")
        saveCurrentLocation = true
    }

    fun savePlace() {
        val distance = km.toDoubleOrNull() ?: return
        if (name.isBlank() || distance <= 0.0) return
        val loc = if (saveCurrentLocation) getLastKnownLocation(context) else null
        val place = ManualPlace(
            id = editingId ?: UUID.randomUUID().toString(),
            name = name.trim(),
            kilometers = distance,
            days = if (surprise) emptyList() else days.toList(),
            latitude = loc?.latitude ?: places.firstOrNull { it.id == editingId }?.latitude,
            longitude = loc?.longitude ?: places.firstOrNull { it.id == editingId }?.longitude
        )
        val updated = places.filterNot { it.id == place.id } + place
        onSaveState(if (surprise) state.copy(surprisePlaces = updated) else state.copy(places = updated))
        message = if (place.latitude != null) "Saved with current location." else "Saved without GPS location."
        resetForm()
    }

    Dialog(onDismissRequest = onClose) {
        Card {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Add, edit, delete, or immediately count a saved destination.", color = Color(0xFFA1A1AA), fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    DarkTextField(name, { name = it }, "Name", Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    DarkTextField(km, { km = it }, "One-way km", Modifier.fillMaxWidth(), numeric = true)
                    if (!surprise) {
                        Spacer(Modifier.height(8.dp))
                        DayPicker(days) { days = it }
                    }
                    Spacer(Modifier.height(6.dp))
                    CheckRow("Use current GPS for this place", saveCurrentLocation) { saveCurrentLocation = it }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = ::savePlace, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                            Text(if (editingId == null) "Add" else "Save")
                        }
                        Button(onClick = ::resetForm, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))) {
                            Text("Clear")
                        }
                    }
                    if (message != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(message.orEmpty(), color = Color(0xFFFDE68A), fontSize = 13.sp)
                    }
                }
                items(places, key = { it.id }) { place ->
                    ManualPlaceCard(
                        place = place,
                        onOneWay = { onSaveState(state.copy(kilometersDriven = (state.kilometersDriven + place.kilometers).coerceIn(0.0, state.maxTrackedKm))) },
                        onRoundTrip = { onSaveState(state.copy(kilometersDriven = (state.kilometersDriven + place.kilometers * 2.0).coerceIn(0.0, state.maxTrackedKm))) },
                        onEdit = {
                            editingId = place.id
                            name = place.name
                            km = place.kilometers.format1()
                            days = place.days.toSet().ifEmpty { setOf("Mon") }
                            saveCurrentLocation = false
                        },
                        onRemove = {
                            val updated = places.filterNot { it.id == place.id }
                            onSaveState(if (surprise) state.copy(surprisePlaces = updated) else state.copy(places = updated))
                        }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                Text("Done")
            }
        }
    }
}

@Composable
fun ManualSettingsDialog(state: ManualState, onSaveState: (ManualState) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var tracking by remember { mutableStateOf(state.trackingEnabled) }
    var ssid by remember { mutableStateOf(state.homeWifiSsid) }
    var homeAddress by remember { mutableStateOf(state.homeAddress) }
    var alertKm by remember { mutableStateOf(state.alertKm.format0()) }
    var placeRadius by remember { mutableStateOf(state.placeRadiusMeters.format0()) }
    var stopRadius by remember { mutableStateOf(state.stopRadiusMeters.format0()) }
    var stopMinutes by remember { mutableStateOf(state.stopMinutes.format0()) }
    var message by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onClose) {
        Card {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("GPS Manual Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Manual tracking works without GPS. GPS Assist is optional and can be turned off here.", color = Color(0xFFA1A1AA), fontSize = 13.sp)
                }
                item {
                    Text("GPS Assist", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    CheckRow("Use GPS Assist inside manual mode", tracking) { tracking = it }
                    Text(locationPermissionLabel(context), color = if (hasLocationPermission(context)) Color(0xFF34D399) else Color(0xFFFDE68A), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                requestSmartFuelPermissions(context)
                                message = "Permission request sent. If Android does not show it, open app settings."
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Allow GPS", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { openAppPermissionSettings(context) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))
                        ) {
                            Text("Android Settings", fontSize = 12.sp)
                        }
                    }
                }
                item {
                    Text("Home Setup", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    DarkTextField(homeAddress, { homeAddress = it }, "Home address", Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = {
                            val resolved = resolveAddressToLocation(context, homeAddress)
                            if (resolved == null) {
                                message = "Could not resolve that address. Try a fuller address or use current GPS at home."
                            } else {
                                onSaveState(state.copy(homeAddress = homeAddress, homeLat = resolved.latitude, homeLon = resolved.longitude))
                                message = "Home address resolved and saved."
                            }
                        }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                            Text("Resolve", fontSize = 12.sp)
                        }
                        Button(onClick = {
                            val loc = getLastKnownLocation(context)
                            if (loc == null) {
                                message = "Location unavailable. Allow GPS permission and try again."
                            } else {
                                onSaveState(state.copy(homeAddress = homeAddress, homeLat = loc.latitude, homeLon = loc.longitude))
                                message = "Current GPS saved as home."
                            }
                        }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))) {
                            Text("Use GPS", fontSize = 12.sp)
                        }
                    }
                    val homeStatus = if (state.homeLat != null && state.homeLon != null) "Home GPS saved" else "Home GPS not set"
                    Spacer(Modifier.height(6.dp))
                    Text(homeStatus, color = if (state.homeLat != null) Color(0xFF34D399) else Color(0xFFFDE68A), fontSize = 13.sp)
                }
                item {
                    Text("Detection Rules", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    DarkTextField(ssid, { ssid = it }, "Home Wi-Fi SSID", Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    DarkTextField(alertKm, { alertKm = it }, "Alert after km", Modifier.fillMaxWidth(), numeric = true)
                    Spacer(Modifier.height(8.dp))
                    DarkTextField(placeRadius, { placeRadius = it }, "Known place radius meters", Modifier.fillMaxWidth(), numeric = true)
                    Spacer(Modifier.height(8.dp))
                    DarkTextField(stopRadius, { stopRadius = it }, "Stopped radius meters", Modifier.fillMaxWidth(), numeric = true)
                    Spacer(Modifier.height(8.dp))
                    DarkTextField(stopMinutes, { stopMinutes = it }, "Stopped minutes", Modifier.fillMaxWidth(), numeric = true)
                }
                if (message != null) {
                    item { Text(message.orEmpty(), color = Color(0xFFFDE68A), fontSize = 13.sp) }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onClose, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))) {
                            Text("Cancel")
                        }
                        Button(onClick = {
                            onSaveState(
                                state.copy(
                                    trackingEnabled = tracking,
                                    homeWifiSsid = ssid.ifBlank { "EnQ" },
                                    homeAddress = homeAddress,
                                    alertKm = alertKm.toDoubleOrNull() ?: state.alertKm,
                                    placeRadiusMeters = placeRadius.toDoubleOrNull() ?: state.placeRadiusMeters,
                                    stopRadiusMeters = stopRadius.toDoubleOrNull() ?: state.stopRadiusMeters,
                                    stopMinutes = stopMinutes.toDoubleOrNull() ?: state.stopMinutes
                                )
                            )
                            onClose()
                        }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ManualPlaceCard(place: ManualPlace, onOneWay: () -> Unit, onRoundTrip: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit) {
    Card {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(place.name, color = Color.White, fontWeight = FontWeight.Bold)
                val days = if (place.days.isEmpty()) "Any day" else place.days.joinToString(", ")
                val location = if (place.latitude != null && place.longitude != null) "Location saved" else "Manual only"
                Text("${place.kilometers.format1()} km one way / $days", color = Color(0xFFA1A1AA), fontSize = 13.sp)
                Text(location, color = Color(0xFF34D399), fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onEdit, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))) {
                    Text("Edit", fontSize = 11.sp)
                }
                Button(onClick = onRemove, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))) {
                    Text("X")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onOneWay, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                Text("One way", fontSize = 12.sp)
            }
            Button(onClick = onRoundTrip, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                Text("Round", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun DayPicker(selectedDays: Set<String>, onChange: (Set<String>) -> Unit) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        days.chunked(4).forEach { rowDays ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                rowDays.forEach { day ->
                    val active = selectedDays.contains(day)
                    Button(
                        onClick = {
                            val next = if (active) selectedDays - day else selectedDays + day
                            onChange(next.ifEmpty { setOf(day) })
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (active) Color.White else Color(0xFF27272A),
                            contentColor = if (active) Color.Black else Color.White
                        )
                    ) {
                        Text(day, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DarkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedLabelColor = Color(0xFF34D399),
            unfocusedLabelColor = Color(0xFFA1A1AA),
            cursorColor = Color(0xFF34D399),
            focusedBorderColor = Color(0xFF34D399),
            unfocusedBorderColor = Color(0xFF3F3F46)
        )
    )
}

@Composable
fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
fun SettingsDrawer(
    dataSource: String,
    state: ManualState,
    onSourceChange: (String) -> Unit,
    onOpenPanel: (String) -> Unit,
    onClose: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xAA000000))
                .clickable { onClose() }
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .widthIn(min = 288.dp, max = 340.dp)
                .background(Color(0xFF111113))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Menu", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("SmartFuel controls", color = Color(0xFFA1A1AA), fontSize = 13.sp)
                }
                Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))) {
                    Text("X")
                }
            }
            Text("Data Mode", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            SourceModeButton("Mock Data", "Demo backend data for UI testing.", "mock", dataSource, onSourceChange)
            SourceModeButton("Firebase", "Reads live backend data from Firestore mode.", "firestore", dataSource, onSourceChange)
            SourceModeButton("GPS Manual", "Local fuel tracker with optional GPS assist.", "manual", dataSource, onSourceChange)

            if (dataSource == "manual") {
                Spacer(Modifier.height(4.dp))
                Text("Manual Tools", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                DrawerAction("Manual Entry", "Add trips, set driven distance, or set remaining range.", "manual", onOpenPanel)
                DrawerAction("Dedicated Places", "${state.places.size} saved places with day rules.", "dedicated", onOpenPanel)
                DrawerAction("Surprise Destinations", "${state.surprisePlaces.size} reusable one-off destinations.", "surprise", onOpenPanel)
                DrawerAction("Manual Settings", "GPS assist, permissions, Wi-Fi, alert and stop rules.", "location", onOpenPanel)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tracking ${if (state.trackingEnabled) "on" else "off"} · ${state.stopMinutes.format0()} min stop · ${state.placeRadiusMeters.format0()} m place radius",
                    color = Color(0xFF34D399),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun DrawerAction(title: String, body: String, panel: String, onOpenPanel: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF18181B), RoundedCornerShape(8.dp))
            .clickable { onOpenPanel(panel) }
            .padding(14.dp)
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = Color(0xFFA1A1AA), fontSize = 13.sp)
    }
}

@Composable
fun SourceModeButton(title: String, body: String, source: String, current: String, onSourceChange: (String) -> Unit) {
    val active = source == current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (active) Color.White else Color(0xFF18181B), RoundedCornerShape(8.dp))
            .clickable { onSourceChange(source) }
            .padding(14.dp)
    ) {
        Text(title, color = if (active) Color.Black else Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = if (active) Color(0xFF3F3F46) else Color(0xFFA1A1AA), fontSize = 13.sp)
    }
}

@Composable
fun TabBar(activeTab: String, onTabChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("Overview", "Stats", "Trips").forEach { tab ->
            Button(
                onClick = { onTabChange(tab) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeTab == tab) Color.White else Color(0xFF18181B),
                    contentColor = if (activeTab == tab) Color.Black else Color.White
                )
            ) {
                Text(tab, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFF10B981))
            Spacer(Modifier.height(12.dp))
            Text("Loading SmartFuel", color = Color.White)
        }
    }
}

@Composable
fun ErrorView(message: String, source: String, onRetry: () -> Unit, onReturnToMock: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card {
            Text("Backend unavailable", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = Color(0xFFA1A1AA))
            if (source == "firestore") {
                Spacer(Modifier.height(8.dp))
                Text("Firestore mode is selected. Return to mock mode to use demo data while Firebase is being configured.", color = Color(0xFFFDE68A), fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onRetry, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                    Text("Retry")
                }
                if (source == "firestore") {
                    Button(onClick = onReturnToMock, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                        Text("Mock")
                    }
                }
            }
        }
    }
}

@Composable
fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF18181B), RoundedCornerShape(8.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFF18181B), RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text(label, color = Color(0xFFA1A1AA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatusBadge(text: String) {
    Text(
        text = text,
        color = Color(0xFF34D399),
        modifier = Modifier
            .background(Color(0xFF052E2B), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun Notice(message: String) {
    Text(
        text = message,
        color = Color(0xFFFDE68A),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF451A03), RoundedCornerShape(8.dp))
            .padding(12.dp)
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
}

@Composable
fun FuelRing(percentage: Double) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(126.dp)) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            drawArc(Color(0xFF3F3F46), -90f, 360f, false, size = Size(size.width, size.height), style = stroke)
            drawArc(Color(0xFF10B981), -90f, (percentage / 100f * 360f).toFloat(), false, size = Size(size.width, size.height), style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${percentage.format0()}%", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Fuel", color = Color(0xFFA1A1AA), fontSize = 12.sp)
        }
    }
}

@Composable
fun MiniBarChart(points: List<Double>, color: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val max = points.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val barWidth = size.width / (points.size * 1.7f)
        points.forEachIndexed { index, value ->
            val left = index * (barWidth * 1.7f)
            val barHeight = (value / max * size.height).toFloat()
            drawRoundRect(
                color = color,
                topLeft = Offset(left, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )
        }
    }
}

fun loadManualState(prefs: android.content.SharedPreferences): ManualState {
    val raw = prefs.getString("manual_state", null) ?: return ManualState()
    return try {
        val root = JSONObject(raw)
        ManualState(
            tankLiters = root.optDouble("tankLiters", 40.0),
            maxTrackedKm = root.optDouble("maxTrackedKm", 200.0),
            alertKm = root.optDouble("alertKm", 150.0),
            kilometersDriven = root.optDouble("kilometersDriven", 0.0),
            alertSent = root.optBoolean("alertSent", false),
            trackingEnabled = root.optBoolean("trackingEnabled", true),
            homeWifiSsid = root.optString("homeWifiSsid", "EnQ"),
            placeRadiusMeters = root.optDouble("placeRadiusMeters", 20.0),
            stopRadiusMeters = root.optDouble("stopRadiusMeters", 10.0),
            stopMinutes = root.optDouble("stopMinutes", 30.0),
            homeAddress = root.optString("homeAddress", ""),
            homeLat = root.optNullableDouble("homeLat"),
            homeLon = root.optNullableDouble("homeLon"),
            places = root.optJSONArray("places").toPlaces(),
            surprisePlaces = root.optJSONArray("surprisePlaces").toPlaces(),
            pendingPlaceId = root.optNullableString("pendingPlaceId"),
            pendingPlaceName = root.optNullableString("pendingPlaceName"),
            pendingKm = root.optDouble("pendingKm", 0.0),
            pendingMessage = root.optNullableString("pendingMessage"),
            pendingLat = root.optNullableDouble("pendingLat"),
            pendingLon = root.optNullableDouble("pendingLon"),
            lastPromptPlaceId = root.optNullableString("lastPromptPlaceId"),
            lastLat = root.optNullableDouble("lastLat"),
            lastLon = root.optNullableDouble("lastLon"),
            tripStartLat = root.optNullableDouble("tripStartLat"),
            tripStartLon = root.optNullableDouble("tripStartLon"),
            stationarySinceMs = root.optLong("stationarySinceMs", 0L)
        )
    } catch (_: Exception) {
        ManualState()
    }
}

fun saveManualState(prefs: android.content.SharedPreferences, state: ManualState) {
    val root = JSONObject()
        .put("tankLiters", state.tankLiters)
        .put("maxTrackedKm", state.maxTrackedKm)
        .put("alertKm", state.alertKm)
        .put("kilometersDriven", state.kilometersDriven)
        .put("alertSent", state.alertSent)
        .put("trackingEnabled", state.trackingEnabled)
        .put("homeWifiSsid", state.homeWifiSsid)
        .put("placeRadiusMeters", state.placeRadiusMeters)
        .put("stopRadiusMeters", state.stopRadiusMeters)
        .put("stopMinutes", state.stopMinutes)
        .put("homeAddress", state.homeAddress)
        .putNullable("homeLat", state.homeLat)
        .putNullable("homeLon", state.homeLon)
        .put("places", JSONArray().apply { state.places.forEach { put(it.toJson()) } })
        .put("surprisePlaces", JSONArray().apply { state.surprisePlaces.forEach { put(it.toJson()) } })
        .putNullable("pendingPlaceId", state.pendingPlaceId)
        .putNullable("pendingPlaceName", state.pendingPlaceName)
        .put("pendingKm", state.pendingKm)
        .putNullable("pendingMessage", state.pendingMessage)
        .putNullable("pendingLat", state.pendingLat)
        .putNullable("pendingLon", state.pendingLon)
        .putNullable("lastPromptPlaceId", state.lastPromptPlaceId)
        .putNullable("lastLat", state.lastLat)
        .putNullable("lastLon", state.lastLon)
        .putNullable("tripStartLat", state.tripStartLat)
        .putNullable("tripStartLon", state.tripStartLon)
        .put("stationarySinceMs", state.stationarySinceMs)
    prefs.edit().putString("manual_state", root.toString()).apply()
}

fun ManualPlace.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("kilometers", kilometers)
    .put("days", JSONArray().apply { days.forEach { put(it) } })
    .putNullable("latitude", latitude)
    .putNullable("longitude", longitude)

fun JSONArray?.toPlaces(): List<ManualPlace> {
    if (this == null) return emptyList()
    val places = mutableListOf<ManualPlace>()
    for (index in 0 until length()) {
        val item = getJSONObject(index)
        val daysJson = item.optJSONArray("days")
        val days = mutableListOf<String>()
        if (daysJson != null) for (dayIndex in 0 until daysJson.length()) days.add(daysJson.getString(dayIndex))
        places.add(
            ManualPlace(
                id = item.optString("id", UUID.randomUUID().toString()),
                name = item.optString("name", "Place"),
                kilometers = item.optDouble("kilometers", 0.0),
                days = days,
                latitude = item.optNullableDouble("latitude"),
                longitude = item.optNullableDouble("longitude")
            )
        )
    }
    return places
}

fun JSONObject.putNullable(name: String, value: Any?): JSONObject {
    put(name, value ?: JSONObject.NULL)
    return this
}

fun JSONObject.optNullableDouble(name: String): Double? = if (has(name) && !isNull(name)) optDouble(name) else null
fun JSONObject.optNullableString(name: String): String? = if (has(name) && !isNull(name)) optString(name) else null

fun refreshManualLocationSuggestion(context: Context, state: ManualState): ManualState {
    val location = getLastKnownLocation(context) ?: return state
    val now = System.currentTimeMillis()
    val stationarySince = if (state.lastLat != null && state.lastLon != null && distanceMeters(state.lastLat, state.lastLon, location.latitude, location.longitude) <= state.stopRadiusMeters) {
        state.stationarySinceMs.takeIf { it > 0L } ?: now
    } else {
        now
    }
    val movedFromLast = if (state.lastLat != null && state.lastLon != null) {
        distanceMeters(state.lastLat, state.lastLon, location.latitude, location.longitude)
    } else {
        0.0
    }
    val tripStartLat = when {
        state.tripStartLat == null -> state.lastLat ?: location.latitude
        movedFromLast > 500.0 -> state.tripStartLat
        else -> state.tripStartLat
    }
    val tripStartLon = when {
        state.tripStartLon == null -> state.lastLon ?: location.longitude
        movedFromLast > 500.0 -> state.tripStartLon
        else -> state.tripStartLon
    }
    var next = state.copy(
        lastLat = location.latitude,
        lastLon = location.longitude,
        tripStartLat = tripStartLat,
        tripStartLon = tripStartLon,
        stationarySinceMs = stationarySince
    )

    if (state.homeLat != null && state.homeLon != null && distanceMeters(state.homeLat, state.homeLon, location.latitude, location.longitude) < 130.0) {
        return next.copy(pendingMessage = null, pendingPlaceId = null, tripStartLat = location.latitude, tripStartLon = location.longitude)
    }

    val knownPlace = (state.places + state.surprisePlaces).firstOrNull { place ->
        place.latitude != null &&
            place.longitude != null &&
            distanceMeters(place.latitude, place.longitude, location.latitude, location.longitude) <= state.placeRadiusMeters &&
            state.lastPromptPlaceId != place.id
    }
    if (knownPlace != null) {
        next = next.copy(
            pendingPlaceId = knownPlace.id,
            pendingPlaceName = knownPlace.name,
            pendingKm = knownPlace.kilometers,
            pendingMessage = "Are you at ${knownPlace.name}? Confirm to count ${knownPlace.kilometers.format1()} km.",
            pendingLat = location.latitude,
            pendingLon = location.longitude,
            lastPromptPlaceId = knownPlace.id
        )
    } else if (now - stationarySince >= (state.stopMinutes * 60_000.0).toLong() && state.pendingMessage == null && tripStartLat != null && tripStartLon != null) {
        val guessedKm = (distanceMeters(tripStartLat, tripStartLon, location.latitude, location.longitude) / 1000.0 * 1.2).coerceAtLeast(0.1)
        next = next.copy(
            pendingPlaceId = "new-stop",
            pendingPlaceName = "New stop",
            pendingKm = guessedKm,
            pendingMessage = "You seem stopped somewhere new. Is this a trip? SmartFuel guessed ${guessedKm.format1()} km; edit it before confirming.",
            pendingLat = location.latitude,
            pendingLon = location.longitude
        )
    }
    return next
}

fun updateManualTrackingService(context: Context, dataSource: String, state: ManualState) {
    val intent = Intent(context, ManualLocationService::class.java)
    if (dataSource == "manual" && state.trackingEnabled && hasLocationPermission(context)) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.onFailure {
            context.stopService(intent)
        }
    } else {
        context.stopService(intent)
    }
}

fun hasLocationPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

fun hasBackgroundLocationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

fun locationPermissionLabel(context: Context): String {
    val foreground = hasLocationPermission(context)
    val background = hasBackgroundLocationPermission(context)
    return when {
        foreground && background -> "GPS permission: allowed, including background."
        foreground -> "GPS permission: allowed while app/service is active. Use Settings for all-the-time access."
        else -> "GPS permission: not allowed yet."
    }
}

fun requestSmartFuelPermissions(context: Context) {
    val activity = context as? Activity ?: return
    val permissions = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (!hasLocationPermission(context)) {
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    if (permissions.isNotEmpty()) {
        activity.requestPermissions(permissions.toTypedArray(), 71)
    }
}

fun openAppPermissionSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

fun getLastKnownLocation(context: Context): Location? {
    if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
    ) {
        return null
    }
    return try {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        manager.getProviders(true)
            .mapNotNull { provider -> manager.getLastKnownLocation(provider) }
            .maxByOrNull { it.time }
    } catch (_: Exception) {
        null
    }
}

fun distanceMeters(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
    val result = FloatArray(1)
    Location.distanceBetween(fromLat, fromLon, toLat, toLon, result)
    return result[0].toDouble()
}

fun resolveAddressToLocation(context: Context, address: String): Location? {
    if (address.isBlank()) return null
    return try {
        @Suppress("DEPRECATION")
        val result = Geocoder(context, Locale.getDefault()).getFromLocationName(address, 1)?.firstOrNull() ?: return null
        Location("home-address").apply {
            latitude = result.latitude
            longitude = result.longitude
        }
    } catch (_: Exception) {
        null
    }
}

fun showLowFuelNotification(context: Context, percentage: Double) {
    if (!canPostNotifications(context)) return
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    val notification = NotificationCompat.Builder(context, "smartfuel-alerts")
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("SmartFuel low fuel")
        .setContentText("Estimated fuel is ${percentage.format0()}%, below the 70% alert level.")
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(70, notification)
}

fun showManualRangeNotification(context: Context, kilometersDriven: Double) {
    if (!canPostNotifications(context)) return
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)
    val notification = NotificationCompat.Builder(context, "smartfuel-alerts")
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("SmartFuel manual range")
        .setContentText("Manual tracker reached ${kilometersDriven.format0()} km. Refill planning is recommended.")
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(150, notification)
}

fun showManualLocationNotification(context: Context, message: String?) {
    if (!canPostNotifications(context) || message == null) return
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(context, 2, intent, PendingIntent.FLAG_IMMUTABLE)
    val notification = NotificationCompat.Builder(context, "smartfuel-alerts")
        .setSmallIcon(android.R.drawable.ic_dialog_map)
        .setContentTitle("SmartFuel trip check")
        .setContentText(message)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(151, notification)
}

fun canPostNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

@Composable
fun SmartFuelTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

fun Double.format0(): String = String.format("%.0f", this)
fun Double.format1(): String = String.format("%.1f", this)
