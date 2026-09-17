package com.nilltracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class Session(val start: String, val durationSeconds: Long, val device: String)

data class Summary(
    val today: Long = 0,
    val week: Long = 0,
    val streak: Int = 0,
    val sessions: List<Session> = emptyList()
)

private val http = OkHttpClient()
private val jsonType = "application/json".toMediaType()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NillTrackerApp() }
    }
}

@Composable
fun NillTrackerApp() {
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var device by remember { mutableStateOf("Android") }
    var summary by remember { mutableStateOf(Summary()) }
    var running by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf<Instant?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }
    var settings by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(1000)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF101014),
            surface = Color(0xFF191920),
            primary = Color(0xFF78DCB4),
            onPrimary = Color(0xFF101014)
        )
    ) {
        Surface(Modifier.fillMaxSize()) {
            if (settings) {
                SettingsScreen(
                    url, token, device,
                    { url = it }, { token = it }, { device = it },
                    { settings = false; status = "" },
                    { settings = false; status = "" }
                )
            } else {
                Dashboard(
                    summary = summary,
                    running = running,
                    elapsed = if (running && start != null)
                        Duration.between(start, now).seconds else 0,
                    status = status,
                    onStartStop = {
                        if (!running) {
                            start = Instant.now()
                            running = true
                        } else {
                            val s = start!!
                            val e = Instant.now()
                            val dur = Duration.between(s, e).seconds
                            running = false
                            start = null
                            status = "Saving session…"
                            kotlinx.coroutines.MainScope().launch {
                                val ok = Api.log(url, token, s, e, dur, device)
                                status = if (ok) "Session synced" else "Could not sync session"
                                if (ok) summary = Api.summary(url, token) ?: summary
                            }
                        }
                    },
                    onRefresh = {
                        kotlinx.coroutines.MainScope().launch {
                            status = "Refreshing…"
                            val s = Api.summary(url, token)
                            if (s != null) {
                                summary = s
                                status = "Updated"
                            } else status = "Could not load data"
                        }
                    },
                    onSettings = { settings = true }
                )
            }
        }
    }
}

@Composable
fun Dashboard(
    summary: Summary,
    running: Boolean,
    elapsed: Long,
    status: String,
    onStartStop: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Nill Tracker", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            TextButton(onClick = onSettings) { Text("Settings") }
        }

        Spacer(Modifier.height(20.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp)) {
                Text(if (running) "Studying" else "Ready",
                    color = if (running) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(formatTime(elapsed),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onStartStop,
                    Modifier.fillMaxWidth()
                ) { Text(if (running) "Stop & Save" else "Start Studying") }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Today", formatTime(summary.today), Modifier.weight(1f))
            StatCard("This week", formatTime(summary.week), Modifier.weight(1f))
            StatCard("Streak", "${summary.streak}d", Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Session history", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }

        if (status.isNotBlank()) {
            Text(status, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(summary.sessions) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(formatDate(s.start))
                            Text(s.device, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatTime(s.durationSeconds),
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SettingsScreen(
    url: String, token: String, device: String,
    setUrl: (String) -> Unit, setToken: (String) -> Unit, setDevice: (String) -> Unit,
    onSave: () -> Unit, onCancel: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(url, setUrl, Modifier.fillMaxWidth(),
            label = { Text("Apps Script Web App URL") })
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(token, setToken, Modifier.fillMaxWidth(),
            label = { Text("Secret token") })
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(device, setDevice, Modifier.fillMaxWidth(),
            label = { Text("Device name") })
        Spacer(Modifier.height(20.dp))
        Button(onClick = onSave, Modifier.fillMaxWidth()) { Text("Save") }
        TextButton(onClick = onCancel, Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

object Api {
    suspend fun log(url: String, token: String, start: Instant, end: Instant,
                    duration: Long, device: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("action", "log")
                .put("token", token)
                .put("start", start.toString())
                .put("end", end.toString())
                .put("durationSeconds", duration)
                .put("device", device)
                .toString()
                .toRequestBody(jsonType)
            val req = Request.Builder().url(url).post(body).build()
            http.newCall(req).execute().use { it.isSuccessful &&
                JSONObject(it.body?.string() ?: "{}").optBoolean("ok", false) }
        } catch (_: Exception) { false }
    }

    suspend fun summary(url: String, token: String): Summary? = withContext(Dispatchers.IO) {
        try {
            val full = "$url?action=summary&token=${java.net.URLEncoder.encode(token, "UTF-8")}"
            val req = Request.Builder().url(full).get().build()
            http.newCall(req).execute().use {
                if (!it.isSuccessful) return@withContext null
                val o = JSONObject(it.body?.string() ?: "{}")
                if (!o.optBoolean("ok", false)) return@withContext null
                val arr = o.optJSONArray("sessions")
                val list = mutableListOf<Session>()
                if (arr != null) for (i in 0 until arr.length()) {
                    val x = arr.getJSONObject(i)
                    list += Session(
                        x.optString("start"),
                        x.optLong("durationSeconds"),
                        x.optString("device")
                    )
                }
                Summary(o.optLong("todaySeconds"), o.optLong("weekSeconds"),
                    o.optInt("streak"), list)
            }
        } catch (_: Exception) { null }
    }
}

fun formatTime(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

fun formatDate(iso: String): String {
    return try {
        val instant = Instant.parse(iso)
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
            .withZone(ZoneId.systemDefault()).format(instant)
    } catch (_: Exception) { iso }
}
