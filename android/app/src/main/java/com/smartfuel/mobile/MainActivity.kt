package com.smartfuel.mobile

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartFuelTheme {
                SmartFuelApp()
            }
        }
    }
}

data class Dashboard(
    val car: Car,
    val fuel: Fuel,
    val liveStatus: LiveStatus,
    val trips: List<Trip>,
    val notifications: List<NotificationItem>,
    val charts: List<ChartPoint>
)

data class Car(val name: String, val make: String, val model: String, val year: Int, val status: String)
data class Fuel(val remainingLiters: Double, val usedLiters: Double, val rangeKm: Double, val percentage: Double)
data class LiveStatus(
    val speedKph: Double,
    val rpm: Double,
    val coolantTempC: Double,
    val engineLoadPercent: Double,
    val odometerKm: Double,
    val tripDistanceKm: Double,
    val drivingSeconds: Int
)
data class Trip(val id: String, val startedAt: String, val distanceKm: Double, val averageSpeedKph: Double, val fuelUsedLiters: Double)
data class NotificationItem(val id: String, val title: String, val body: String, val severity: String)
data class ChartPoint(val label: String, val speed: Double, val rpm: Double, val fuel: Double)

class SmartFuelApi(private val context: Context) {
    private val baseUrl = BuildConfig.API_BASE_URL
    private val prefs = context.getSharedPreferences("smartfuel-cache", Context.MODE_PRIVATE)

    suspend fun getDashboard(): Pair<Dashboard, Boolean> = withContext(Dispatchers.IO) {
        try {
            val raw = get("$baseUrl/api/dashboard")
            prefs.edit().putString("dashboard", raw).apply()
            parseDashboard(raw) to true
        } catch (exception: Exception) {
            val cached = prefs.getString("dashboard", null)
            if (cached != null) {
                parseDashboard(cached) to false
            } else {
                throw exception
            }
        }
    }

    suspend fun refuel(eventType: String, litersAdded: Double) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("eventType", eventType)
            .put("litersAdded", litersAdded)
            .put("note", "Android app refuel event")
        post("$baseUrl/api/refuel", payload.toString())
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

    return Dashboard(
        car = Car(
            name = car.getString("name"),
            make = car.getString("make"),
            model = car.getString("model"),
            year = car.getInt("year"),
            status = car.getString("status")
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
            drivingSeconds = live.getInt("drivingSeconds")
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
    for (index in 0 until length()) {
        items.add(transform(getJSONObject(index)))
    }
    return items
}

@Composable
fun SmartFuelApp() {
    val context = LocalContext.current
    val api = remember { SmartFuelApi(context) }
    val scope = rememberCoroutineScope()
    var dashboard by remember { mutableStateOf<Dashboard?>(null) }
    var loading by remember { mutableStateOf(true) }
    var online by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var liters by remember { mutableStateOf("5") }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                val result = api.getDashboard()
                dashboard = result.first
                online = result.second
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
                api.refuel(eventType, liters.toDoubleOrNull() ?: 0.0)
                load()
            } catch (exception: Exception) {
                error = exception.message
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Surface(color = Color(0xFF0A0A0A), modifier = Modifier.fillMaxSize()) {
        when {
            loading && dashboard == null -> LoadingView()
            error != null && dashboard == null -> ErrorView(error.orEmpty(), ::load)
            dashboard != null -> DashboardView(
                dashboard = dashboard!!,
                online = online,
                liters = liters,
                onLitersChange = { liters = it },
                onRefresh = ::load,
                onFullReset = { refuel("full_reset") },
                onPartialRefuel = { refuel("partial_refuel") },
                error = error
            )
        }
    }
}

@Composable
fun DashboardView(
    dashboard: Dashboard,
    online: Boolean,
    liters: String,
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
                StatusBadge(if (online) dashboard.car.status else "cached")
            }
        }

        if (error != null) {
            item { Notice(error) }
        }

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
                Metric("Coolant", "${dashboard.liveStatus.coolantTempC.format0()} C", Modifier.weight(1f))
                Metric("Engine Load", "${dashboard.liveStatus.engineLoadPercent.format0()}%", Modifier.weight(1f))
            }
        }

        item {
            Card {
                Text("Fuel Controls", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                        Text("Full Tank Reset")
                    }
                    Button(onClick = onRefresh, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                        Text("Refresh")
                    }
                }
            }
        }

        item {
            Card {
                Text("Speed Trend", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                MiniBarChart(points = dashboard.charts.map { it.speed }, color = Color(0xFF2563EB))
            }
        }

        item {
            SectionTitle("Recent Trips")
        }
        items(dashboard.trips, key = { it.id }) { trip ->
            Card {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(trip.startedAt.take(10), color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${trip.distanceKm.format1()} km • ${trip.averageSpeedKph.format0()} km/h avg", color = Color(0xFFA1A1AA))
                    }
                    Text("${trip.fuelUsedLiters.format1()} L", color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            SectionTitle("Notifications")
        }
        items(dashboard.notifications, key = { it.id }) { notification ->
            Card {
                Text(notification.title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(notification.body, color = Color(0xFFA1A1AA), fontSize = 13.sp)
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
fun ErrorView(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card {
            Text("Backend unavailable", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = Color(0xFFA1A1AA))
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                Text("Retry")
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
            drawArc(
                color = Color(0xFF3F3F46),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
                size = Size(size.width, size.height)
            )
            drawArc(
                color = Color(0xFF10B981),
                startAngle = -90f,
                sweepAngle = (percentage / 100f * 360f).toFloat(),
                useCenter = false,
                style = stroke,
                size = Size(size.width, size.height)
            )
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

@Composable
fun SmartFuelTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

private fun Double.format0(): String = String.format("%.0f", this)
private fun Double.format1(): String = String.format("%.1f", this)
