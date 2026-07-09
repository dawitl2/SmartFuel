package com.smartfuel.mobile

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
    val homeLat: Double? = null,
    val homeLon: Double? = null,
    val places: List<ManualPlace> = emptyList(),
    val surprisePlaces: List<ManualPlace> = emptyList(),
    val pendingPlaceId: String? = null,
    val pendingPlaceName: String? = null,
    val pendingKm: Double = 0.0,
    val pendingMessage: String? = null,
    val lastPromptPlaceId: String? = null,
    val lastLat: Double? = null,
    val lastLon: Double? = null,
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
    BackHandler(settingsOpen) { settingsOpen = false }

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
                    onSourceChange = {
                        settingsOpen = false
                        changeSource(it)
                    },
                    onClose = { settingsOpen = false }
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
                        Text("Settings", fontSize = 12.sp)
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
    var placeName by remember { mutableStateOf("") }
    var placeKm by remember { mutableStateOf("") }
    var surpriseName by remember { mutableStateOf("") }
    var surpriseKm by remember { mutableStateOf("") }
    var selectedDays by remember { mutableStateOf(setOf("Mon")) }
    var locationMessage by remember { mutableStateOf<String?>(null) }

    fun save(next: ManualState) = onSaveState(next)
    fun addDistance(km: Double) {
        save(state.copy(kilometersDriven = (state.kilometersDriven + km).coerceIn(0.0, state.maxTrackedKm)))
    }
    fun currentLocation(): Location? = getLastKnownLocation(context)
    fun addPlace(name: String, kmText: String, days: List<String>, surprise: Boolean) {
        val km = kmText.toDoubleOrNull() ?: return
        if (name.isBlank() || km <= 0.0) return
        val loc = currentLocation()
        val place = ManualPlace(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            kilometers = km,
            days = days,
            latitude = loc?.latitude,
            longitude = loc?.longitude
        )
        if (surprise) {
            save(state.copy(surprisePlaces = state.surprisePlaces + place))
            surpriseName = ""
            surpriseKm = ""
        } else {
            save(state.copy(places = state.places + place))
            placeName = ""
            placeKm = ""
        }
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
                    Text("Manual Fuel", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("Local 200 km fuel tracker", color = Color(0xFFA1A1AA))
                }
                Button(onClick = onOpenSettings, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF18181B))) {
                    Text("Settings", fontSize = 12.sp)
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
                        Text("${state.kilometersDriven.format1()} km driven from 200 km", color = Color(0xFFA1A1AA))
                    }
                }
            }
        }

        if (state.pendingMessage != null) {
            item {
                Card {
                    Text("Location suggestion", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(state.pendingMessage, color = Color(0xFFA1A1AA), fontSize = 13.sp)
                    if (state.pendingKm > 0.0) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = {
                                save(state.copy(
                                    kilometersDriven = (state.kilometersDriven + state.pendingKm).coerceIn(0.0, state.maxTrackedKm),
                                    pendingMessage = null,
                                    pendingPlaceId = null
                                ))
                            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                                Text("One way", fontSize = 12.sp)
                            }
                            Button(onClick = {
                                save(state.copy(
                                    kilometersDriven = (state.kilometersDriven + state.pendingKm * 2.0).coerceIn(0.0, state.maxTrackedKm),
                                    pendingMessage = null,
                                    pendingPlaceId = null
                                ))
                            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                                Text("Round", fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { save(state.copy(pendingMessage = null, pendingPlaceId = null)) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))) {
                        Text("Ignore")
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Metric("Driven", "${state.kilometersDriven.format0()} km", Modifier.weight(1f))
                Metric("Alert", "${state.alertKm.format0()} km", Modifier.weight(1f))
            }
        }

        item {
            Card {
                Text("Manual Entry", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = quickKm,
                        onValueChange = { quickKm = it },
                        label = { Text("Kilometers") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { addDistance(quickKm.toDoubleOrNull() ?: 0.0) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                        Text("Add")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = {
                        save(state.copy(kilometersDriven = 0.0, alertSent = false, pendingMessage = null, pendingPlaceId = null))
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                        Text("Refill")
                    }
                    Button(onClick = {
                        val loc = currentLocation()
                        if (loc == null) {
                            locationMessage = "Location is unavailable. Allow location permission and open Maps/GPS once if needed."
                        } else {
                            save(state.copy(homeLat = loc.latitude, homeLon = loc.longitude))
                            locationMessage = "Home location saved."
                        }
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))) {
                        Text("Home")
                    }
                }
                if (locationMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(locationMessage.orEmpty(), color = Color(0xFFFDE68A), fontSize = 13.sp)
                }
            }
        }

        item {
            Card {
                Text("Smart Location", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Checks saved places and long stops while the app is open.", color = Color(0xFFA1A1AA), fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    val checked = refreshManualLocationSuggestion(context, state)
                    if (checked === state) {
                        locationMessage = "No saved place or long stop detected yet."
                    } else {
                        save(checked)
                        if (checked.pendingMessage != null) showManualLocationNotification(context, checked.pendingMessage)
                    }
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))) {
                    Text("Check Location")
                }
            }
        }

        item {
            Card {
                Text("Designated Places", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = placeName, onValueChange = { placeName = it }, label = { Text("Place name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = placeKm, onValueChange = { placeKm = it }, label = { Text("One-way km") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                DayPicker(selectedDays = selectedDays, onChange = { selectedDays = it })
                Spacer(Modifier.height(10.dp))
                Button(onClick = { addPlace(placeName, placeKm, selectedDays.toList(), false) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                    Text("Add designated place")
                }
            }
        }

        items(state.places, key = { it.id }) { place ->
            ManualPlaceCard(
                place = place,
                onOneWay = { addDistance(place.kilometers) },
                onRoundTrip = { addDistance(place.kilometers * 2.0) },
                onRemove = { save(state.copy(places = state.places.filterNot { it.id == place.id })) }
            )
        }

        item {
            Card {
                Text("Surprise Places", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = surpriseName, onValueChange = { surpriseName = it }, label = { Text("Place name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = surpriseKm, onValueChange = { surpriseKm = it }, label = { Text("One-way km") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Button(onClick = { addPlace(surpriseName, surpriseKm, emptyList(), true) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))) {
                    Text("Add surprise place")
                }
            }
        }

        items(state.surprisePlaces, key = { it.id }) { place ->
            ManualPlaceCard(
                place = place,
                onOneWay = { addDistance(place.kilometers) },
                onRoundTrip = { addDistance(place.kilometers * 2.0) },
                onRemove = { save(state.copy(surprisePlaces = state.surprisePlaces.filterNot { it.id == place.id })) }
            )
        }
    }
}

@Composable
fun ManualPlaceCard(place: ManualPlace, onOneWay: () -> Unit, onRoundTrip: () -> Unit, onRemove: () -> Unit) {
    Card {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(place.name, color = Color.White, fontWeight = FontWeight.Bold)
                val days = if (place.days.isEmpty()) "Any day" else place.days.joinToString(", ")
                val location = if (place.latitude != null && place.longitude != null) "Location saved" else "Manual only"
                Text("${place.kilometers.format1()} km one way / $days", color = Color(0xFFA1A1AA), fontSize = 13.sp)
                Text(location, color = Color(0xFF34D399), fontSize = 12.sp)
            }
            Button(onClick = onRemove, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))) {
                Text("X")
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
fun SettingsDrawer(dataSource: String, onSourceChange: (String) -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xAA000000))
                .clickable { onClose() }
        )
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(min = 288.dp, max = 340.dp)
                .background(Color(0xFF111113))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Settings", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Data mode", color = Color(0xFFA1A1AA), fontSize = 13.sp)
                }
                Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))) {
                    Text("X")
                }
            }
            SourceModeButton("Mock Data", "Demo backend data for UI testing.", "mock", dataSource, onSourceChange)
            SourceModeButton("Firebase", "Reads live backend data from Firestore mode.", "firestore", dataSource, onSourceChange)
            SourceModeButton("Manual", "Local phone-only tracker with places and refills.", "manual", dataSource, onSourceChange)
            Spacer(Modifier.height(6.dp))
            Card {
                Text("Manual defaults", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Tank: 40 L", color = Color(0xFFA1A1AA), fontSize = 13.sp)
                Text("Tracked range: 200 km", color = Color(0xFFA1A1AA), fontSize = 13.sp)
                Text("Phone alert: 150 km driven", color = Color(0xFFA1A1AA), fontSize = 13.sp)
            }
        }
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
            homeLat = root.optNullableDouble("homeLat"),
            homeLon = root.optNullableDouble("homeLon"),
            places = root.optJSONArray("places").toPlaces(),
            surprisePlaces = root.optJSONArray("surprisePlaces").toPlaces(),
            pendingPlaceId = root.optNullableString("pendingPlaceId"),
            pendingPlaceName = root.optNullableString("pendingPlaceName"),
            pendingKm = root.optDouble("pendingKm", 0.0),
            pendingMessage = root.optNullableString("pendingMessage"),
            lastPromptPlaceId = root.optNullableString("lastPromptPlaceId"),
            lastLat = root.optNullableDouble("lastLat"),
            lastLon = root.optNullableDouble("lastLon"),
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
        .putNullable("homeLat", state.homeLat)
        .putNullable("homeLon", state.homeLon)
        .put("places", JSONArray().apply { state.places.forEach { put(it.toJson()) } })
        .put("surprisePlaces", JSONArray().apply { state.surprisePlaces.forEach { put(it.toJson()) } })
        .putNullable("pendingPlaceId", state.pendingPlaceId)
        .putNullable("pendingPlaceName", state.pendingPlaceName)
        .put("pendingKm", state.pendingKm)
        .putNullable("pendingMessage", state.pendingMessage)
        .putNullable("lastPromptPlaceId", state.lastPromptPlaceId)
        .putNullable("lastLat", state.lastLat)
        .putNullable("lastLon", state.lastLon)
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
    val stationarySince = if (state.lastLat != null && state.lastLon != null && distanceMeters(state.lastLat, state.lastLon, location.latitude, location.longitude) < 80.0) {
        state.stationarySinceMs.takeIf { it > 0L } ?: now
    } else {
        now
    }
    var next = state.copy(lastLat = location.latitude, lastLon = location.longitude, stationarySinceMs = stationarySince)

    if (state.homeLat != null && state.homeLon != null && distanceMeters(state.homeLat, state.homeLon, location.latitude, location.longitude) < 130.0) {
        return next.copy(pendingMessage = null, pendingPlaceId = null)
    }

    val knownPlace = (state.places + state.surprisePlaces).firstOrNull { place ->
        place.latitude != null &&
            place.longitude != null &&
            distanceMeters(place.latitude, place.longitude, location.latitude, location.longitude) < 180.0 &&
            state.lastPromptPlaceId != place.id
    }
    if (knownPlace != null) {
        next = next.copy(
            pendingPlaceId = knownPlace.id,
            pendingPlaceName = knownPlace.name,
            pendingKm = knownPlace.kilometers,
            pendingMessage = "You look parked near ${knownPlace.name}. Should SmartFuel count that trip?",
            lastPromptPlaceId = knownPlace.id
        )
    } else if (now - stationarySince >= 20 * 60 * 1000L && state.pendingMessage == null) {
        next = next.copy(
            pendingPlaceId = "new-stop",
            pendingPlaceName = "New stop",
            pendingKm = 0.0,
            pendingMessage = "You seem stopped somewhere new. Add this place below with its distance, then use One way or Round."
        )
    }
    return next
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

private fun Double.format0(): String = String.format("%.0f", this)
private fun Double.format1(): String = String.format("%.1f", this)
