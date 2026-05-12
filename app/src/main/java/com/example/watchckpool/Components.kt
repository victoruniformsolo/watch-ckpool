@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.watchckpool

import com.example.watchckpool.BuildConfig
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.copy
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.watchckpool.WatchUtils.formatHashrate
import com.example.watchckpool.WatchUtils.formatLargeNumber
import com.example.watchckpool.WatchUtils.formatTimestamp
import com.example.watchckpool.WatchUtils.shortUsersAddy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.collections.reversed

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface
                    )
                )
            ), contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // App Icon / Logo

            Spacer(Modifier.height(32.dp))

            Text(
                "Watch CKPool",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(16.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                strokeWidth = 4.dp,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(48.dp))

            Text(
                "Solo Mining Monitor",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun DashboardScreen(
    repo: MonitorRepository,
    settings: AppSettings,
    lastDataJson: String?,
    lastSyncTime: Long,
    latestLog: LogEntry?,
    onNavigateToLogs: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val cooldownTime = 30000L // 30s
    val canRefresh = System.currentTimeMillis() - lastSyncTime > cooldownTime
    var showConfirmRefresh by remember { mutableStateOf(false) }
    val data = remember(lastDataJson) {
        try {
            lastDataJson?.let { Json.parseToJsonElement(it).jsonObject }
        } catch (e: Exception) {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ... previous code for Sync Status Bar ...
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {


                if (showConfirmRefresh) {
                    AlertDialog(
                        onDismissRequest = { showConfirmRefresh = false },
                        title = { Text("Manual refresh") },
                        text = {
                            Text("Bypasses the logging system and may break logs and timeline integrity.")
                        },
                        confirmButton = {
                            TextButton(
                                //    onClick = {}
                                onClick = {
                                    scope.launch {
                                        isRefreshing = true
                                        try {
                                            val fullUrl =
                                                "${settings.selectedUrl}${settings.btcAddress}"
                                            val result = withContext(Dispatchers.IO) {
                                                val connection = java.net.URL(fullUrl)
                                                    .openConnection() as java.net.HttpURLConnection
                                                connection.connectTimeout = 10000
                                                if (connection.responseCode == 200) {
                                                    connection.inputStream.bufferedReader()
                                                        .use { it.readText() }
                                                } else null
                                            }
                                            if (result != null) {
                                                repo.saveResponse(result)
                                                repo.addLog("Manual refresh successful")
                                            }
                                        } catch (e: Exception) {
                                            repo.addLog("Manual refresh failed: ${e.localizedMessage}")
                                        } finally {
                                            isRefreshing = false
                                        }
                                    }

                                    showConfirmRefresh = false
                                }

                            ) {
                                Text("Confirm", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConfirmRefresh = false }) {
                                Text("Cancel")
                            }
                        })
                }


                val timeText = if (lastSyncTime == 0L) "Never" else {
                    val diff = (System.currentTimeMillis() - lastSyncTime) / 1000
                    if (diff < 60) "Just now" else "${diff / 60} m ago"
                }
                Row{
                Text(
                    text = "Mode: " + if (settings.refreshInterval <= 60) "${settings.refreshInterval}m" else "${settings.refreshInterval/60}h"  ,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    " • Last sync: $timeText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                }
                IconButton(
                    onClick = { showConfirmRefresh = true },
                    modifier = Modifier.size(24.dp),
                    enabled = !isRefreshing && canRefresh && settings.btcAddress.isNotEmpty()
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp), strokeWidth = 2.dp
                        )
                    } else {
                        Icon( // REFRESH ICON
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // --- Latest Log Entry ---
        if (latestLog != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Row(
                    Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable(onClick = onNavigateToLogs),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        Icons.Default.ExpandMore,
                        null,
                        Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.Notes,
                        null,
                        Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = latestLog.msg,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                }
            }
        }
        val shares = data?.get("shares")?.jsonPrimitive?.content ?: "0"
        val bestshare = data?.get("bestshare")?.jsonPrimitive?.content ?: "0"
        val bestever = data?.get("bestever")?.jsonPrimitive?.content ?: "0"
        val authorised = data?.get("authorised")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        val hashrate1m = data?.get("hashrate1m")?.jsonPrimitive?.content ?: "0 H"
        val workers = data?.get("workers")?.jsonPrimitive?.content ?: "0"

        if (settings.btcAddress.isEmpty() || data == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (settings.btcAddress.isEmpty()) "Setup Required" else "Connecting to Pool...",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (settings.btcAddress.isEmpty()) "Enter your BTC address in settings > " 
                            else "Fetching latest miner statistics...",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            if (settings.btcAddress.isEmpty()) Icons.Default.Settings else Icons.Default.Wifi,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

        } else {

            Card(
                modifier = Modifier
                //    .padding(8.dp)
                    .fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(
                        4.dp
                    )
                )
            ) {

                Column(
                    Modifier.padding(24.dp)
                ) {


                    // TOP ROW: Address & Total Hashrate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Side: ID & History
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val shorterAddy = shortUsersAddy(settings.btcAddress)
                                Text(
                                    text = "⛏️",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = shorterAddy,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis

                                )
                            }
                            Text(
                                text = if (authorised > 0) "Mining Since ${formatTimestamp(authorised, true)}" else "Status: Awaiting first share",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        // Right Side: High-Visibility Hashrate
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "TOTAL HASH",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = hashrate1m,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))


                    // BOTTOM ROW: Shares List (Left) & Workers (Right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {


                        Column(Modifier.weight(1f)) { // Fixed width to keep alignment tight

                            // Row for shares
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Shares", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    formatLargeNumber(shares),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            HorizontalDivider(
                                Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                thickness = 0.5.dp
                            )
                            // Row for Best Ever
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Best Ever", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    formatLargeNumber(bestever),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Row for Best Share
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Best Share", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    formatLargeNumber(bestshare),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }


                        Spacer(Modifier.width(16.dp))


                        // Right: Worker Count
                        Column(horizontalAlignment = Alignment.End) {
                            //       Text("WORKERS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Row(verticalAlignment = Alignment.CenterVertically) {

                                Text(
                                    text = "🛠️ ︎",
                                    style = MaterialTheme.typography.titleMedium,

                                    )

                                Text(
                                    text = "$workers︎",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )

                            }
                        }
                    }
                } // END COLUMN

            } // END CARD

            Spacer(Modifier.height(16.dp))

            // Miner Event Settings
            Card(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(
                        2.dp
                    )
                )
            ) {
                Column(Modifier.padding(12.dp)) {

                    Text("Miner Alerts & Logs", style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    EventToggleRow(
                        "Best Share",
                        settings.logMinerBestShare,
                        settings.notifyMinerBestShare,
                        onLogChange = {
                            scope.launch {
                                repo.updateSetting(
                                    "logMinerBestShare", it
                                )
                            }
                        },
                        onNotifyChange = {
                            scope.launch {
                                repo.updateSetting(
                                    "notifyMinerBestShare", it
                                )
                            }
                        })
                    EventToggleRow(
                        "Best Ever",
                        settings.logMinerBestEver,
                        settings.notifyMinerBestEver,
                        onLogChange = {
                            scope.launch {
                                repo.updateSetting(
                                    "logMinerBestEver", it
                                )
                            }
                        },
                        onNotifyChange = {
                            scope.launch {
                                repo.updateSetting(
                                    "notifyMinerBestEver", it
                                )
                            }
                        })
                    EventToggleRow(
                        "Workers Count",
                        settings.logMinerWorkers,
                        settings.notifyMinerWorkers,
                        onLogChange = {
                            scope.launch {
                                repo.updateSetting(
                                    "logMinerWorkers", it
                                )
                            }
                        },
                        onNotifyChange = {
                            scope.launch {
                                repo.updateSetting(
                                    "notifyMinerWorkers", it
                                )
                            }
                        })

                }
            }

        } // END ELSE
    }
}

@Composable
fun EventToggleRow(
    label: String,
    log: Boolean,
    notify: Boolean,
    onLogChange: (Boolean) -> Unit,
    onNotifyChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = "Notify",
                modifier = Modifier.size(16.dp),
                tint = if (notify) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            Checkbox(
                checked = notify, onCheckedChange = onNotifyChange, modifier = Modifier.scale(0.8f)
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.Notes,
                contentDescription = "Log",
                modifier = Modifier.size(16.dp),
                tint = if (log) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            Checkbox(checked = log, onCheckedChange = onLogChange, modifier = Modifier.scale(0.8f))


        }
    }
}

enum class WorkerSortOrder { NAME, HASHRATE, LAST_SEEN }

@Composable
fun WorkersScreen(
    repo: MonitorRepository,
    settings: AppSettings,
    lastDataJson: String?,
    //   onNavigateToWorkerPhysicalLogs: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var showOnlyInactive by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(WorkerSortOrder.NAME) }
    var showSortMenu by remember { mutableStateOf(false) }

    val allWorkers = remember(lastDataJson) {
        try {
            val json = lastDataJson?.let { Json.parseToJsonElement(it).jsonObject }
            json?.get("worker")?.jsonArray?.map { it.jsonObject } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    val processedWorkers = remember(allWorkers, searchQuery, showOnlyInactive, sortOrder) {
        allWorkers.filter { worker ->
            val name = WatchUtils.cleanWorkerName(worker["workername"]?.jsonPrimitive?.content)
            val matchesSearch = name.contains(searchQuery, ignoreCase = true)

            val lastShare = worker["lastshare"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            val isInactive = (System.currentTimeMillis() / 1000) - lastShare > 900 // 15 minutes

            val matchesFilter = if (showOnlyInactive) isInactive else true
            matchesSearch && matchesFilter
        }.sortedWith { a, b ->
            when (sortOrder) {
                WorkerSortOrder.NAME -> {
                    val nameA = WatchUtils.cleanWorkerName(a["workername"]?.jsonPrimitive?.content)
                    val nameB = WatchUtils.cleanWorkerName(b["workername"]?.jsonPrimitive?.content)
                    nameA.compareTo(nameB)
                }

                WorkerSortOrder.HASHRATE -> {
                    val rateA = WatchUtils.parseHashrate(a["hashrate1m"]?.jsonPrimitive?.content)
                    val rateB = WatchUtils.parseHashrate(b["hashrate1m"]?.jsonPrimitive?.content)
                    rateB.compareTo(rateA) // Descending
                }

                WorkerSortOrder.LAST_SEEN -> {
                    val timeA = a["lastshare"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                    val timeB = b["lastshare"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                    timeB.compareTo(timeA) // Most recent first
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(), colors = CardDefaults.cardColors(
                 containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                 alpha = 0.5f
             )
            )
        ) {
            Column(Modifier.padding(8.dp)) {


                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search worker name...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = showOnlyInactive,
                            onClick = { showOnlyInactive = !showOnlyInactive },
                            label = { Text("Inactive") },
                            leadingIcon = if (showOnlyInactive) {
                                {
                                    Icon(
                                        Icons.Default.SignalCellularAlt1Bar,
                                        null,
                                        Modifier.size(18.dp)
                                    )
                                }
                            } else null)
                        Spacer(Modifier.width(8.dp))

                        Box {
                            FilterChip(
                                selected = false,
                                onClick = { showSortMenu = true },
                                label = { Text("Sort: ${sortOrder.name}") },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp)
                                    )
                                })
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }) {
                                DropdownMenuItem(text = { Text("Name") }, onClick = {
                                    sortOrder = WorkerSortOrder.NAME; showSortMenu = false
                                })
                                DropdownMenuItem(
                                    text = { Text("Hashrate (High to Low)") },
                                    onClick = {
                                        sortOrder = WorkerSortOrder.HASHRATE; showSortMenu = false
                                    })
                                DropdownMenuItem(
                                    text = { Text("Last Seen (Newest First)") },
                                    onClick = {
                                        sortOrder = WorkerSortOrder.LAST_SEEN; showSortMenu = false
                                    })
                            }
                        }
                    }

                    Text(
                        text = "${processedWorkers.size} workers",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        LazyColumn(Modifier.weight(1f)) {
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(
                            2.dp
                        )
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {


                        Text(
                            "Worker Alerts & Logs", style = MaterialTheme.typography.titleSmall
                        )
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        EventToggleRow(
                            "Best Share",
                            settings.logWorkerBestShare,
                            settings.notifyWorkerBestShare,
                            onLogChange = {
                                scope.launch {
                                    repo.updateSetting(
                                        "logWorkerBestShare", it
                                    )
                                }
                            },
                            onNotifyChange = {
                                scope.launch {
                                    repo.updateSetting(
                                        "notifyWorkerBestShare", it
                                    )
                                }
                            })
                        EventToggleRow(
                            "Best Ever",
                            settings.logWorkerBestEver,
                            settings.notifyWorkerBestEver,
                            onLogChange = {
                                scope.launch {
                                    repo.updateSetting(
                                        "logWorkerBestEver", it
                                    )
                                }
                            },
                            onNotifyChange = {
                                scope.launch {
                                    repo.updateSetting(
                                        "notifyWorkerBestEver", it
                                    )
                                }
                            })
                        EventToggleRow(
                            "Hashrate 1m (for testing)",
                            settings.logWorkerHashrate,
                            settings.notifyWorkerHashrate,
                            onLogChange = {
                                scope.launch {
                                    repo.updateSetting(
                                        "logWorkerHashrate", it
                                    )
                                }
                            },
                            onNotifyChange = {
                                scope.launch {
                                    repo.updateSetting(
                                        "notifyWorkerHashrate", it
                                    )
                                }
                            })
                    }
                }
            }

            if (processedWorkers.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (allWorkers.isEmpty()) "No workers detected" else "No workers match filter")
                    }
                }
            }
            items(processedWorkers) { worker ->
                WorkerRow(worker)
            }
        }
    }
}

@Composable
fun WorkerRow(worker: kotlinx.serialization.json.JsonObject) {
    var expanded by remember { mutableStateOf(false) }
    val name = WatchUtils.cleanWorkerName(worker["workername"]?.jsonPrimitive?.content)
    val hashrate = worker["hashrate1m"]?.jsonPrimitive?.content ?: "0H"
    val lastShare = worker["lastshare"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        hashrate,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        WatchUtils.formatTimeAgo(lastShare),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(thickness = 0.5.dp)
                    Row(
                        Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f)) {


                            InfoRow(
                                "Shares",
                                formatLargeNumber(worker["shares"]?.jsonPrimitive?.content ?: "0")
                            )
                            InfoRow(
                                "Best Ever",
                                formatLargeNumber(worker["bestever"]?.jsonPrimitive?.content ?: "0")
                            )
                            InfoRow(
                                "Best Share", formatLargeNumber(
                                    worker["bestshare"]?.jsonPrimitive?.content?.take(10) ?: "0"
                                )
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(0.5f)) {
                            InfoRow("5m", worker["hashrate5m"]?.jsonPrimitive?.content ?: "-")
                            InfoRow("1h", worker["hashrate1hr"]?.jsonPrimitive?.content ?: "-")
                            InfoRow("1d", worker["hashrate1d"]?.jsonPrimitive?.content ?: "-")
                            InfoRow("7d", worker["hashrate7d"]?.jsonPrimitive?.content ?: "-")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ConnectionScreen(repo: MonitorRepository, settings: AppSettings, onBack: () -> Unit) {
    var input by remember { mutableStateOf(settings.btcAddress) }
    var inputServ by remember { mutableStateOf(settings.selectedUrl) }
    var isIntervalLocked by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val isValid = input.matches(Regex("^[13][a-km-zA-HJ-NP-Z1-9]{25,34}$|^(bc1)[a-z0-9]{39,59}$"))
    val urls = mapOf(
        "US" to "https://solo.ckpool.org/users/",
        "EU" to "https://eusolo.ckpool.org/users/",
        "AU" to "https://ausolo.ckpool.org/users/"
    )

    val isDirty = input != settings.btcAddress || inputServ != settings.selectedUrl

    Column(Modifier.padding(16.dp)) {
        Text(
            "Connection Setup",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Server:", style = MaterialTheme.typography.titleMedium)
            urls.forEach { (label, url) ->

                RadioButton(
                    selected = (url == inputServ),
                    onClick = { inputServ = url })
                Text(label)

            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("BTC Address") },
            isError = input.isNotEmpty() && !isValid,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = {
                if (input.isNotEmpty() && !isValid) Text("Invalid BTC address format")})


        Spacer(Modifier.height(16.dp))

        var testResult by remember { mutableStateOf<String?>(null) }
        var isTesting by remember { mutableStateOf(false) }

        Button(
            onClick = {
                scope.launch {
                    isTesting = true
                    testResult = "Testing connection..."
                    repo.addLog("Manual test started for $input on $inputServ")
                    try {
                        val testUrl = "$inputServ$input"
                        val result = withContext(Dispatchers.IO) {
                            val connection =
                                java.net.URL(testUrl).openConnection() as java.net.HttpURLConnection
                            connection.connectTimeout = 5000
                            connection.readTimeout = 5000
                            val code = connection.responseCode
                            if (code == 200) {
                                val content =
                                    connection.inputStream.bufferedReader().use { it.readText() }
                                if (content.contains("hashrate") || content.contains("address") || content.contains(
                                        "worker"
                                    )
                                ) {
                                    scope.launch {
                                        repo.updateUrl(inputServ)
                                        repo.updateBtc(input)
                                        onBack()
                                    }


                                    withContext(Dispatchers.Main) { repo.saveResponse(content) }
                                    "Success! Valid JSON received."
                                } else {
                                    "Connected, but response format unexpected."

                                }
                            } else {
                                "Error: Server returned code $code"
                            }
                        }
                        testResult = result
                        repo.addLog("Test result: $result")
                    } catch (e: Exception) {
                        testResult = "Connection failed: ${e.localizedMessage}"
                        repo.addLog("Test failed: ${e.localizedMessage}")
                    } finally {
                        isTesting = false
                    }
                }
            },
            enabled = isValid && !isTesting && isDirty,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onSecondary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            }
            Text("Test Connection")
        }

        testResult?.let {
            Text(
                it,
                modifier = Modifier.padding(top = 8.dp),
                color = if (it.startsWith("Success")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("Background Check Frequency", style = MaterialTheme.typography.titleMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val refreshIntervals = listOf(15, 30, 60, 360, 720)
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                refreshIntervals.forEach { minutes ->
                    SegmentedButton(
                        selected = settings.refreshInterval == minutes,
                        onClick = {
                            scope.launch {
                                isIntervalLocked = true
                                repo.updateRefreshInterval(minutes)
                                delay(5000)
                                isIntervalLocked = false
                            }
                        },
                        enabled = !isIntervalLocked,
                        shape = SegmentedButtonDefaults.itemShape(
                            index = refreshIntervals.indexOf(
                                minutes
                            ), count = refreshIntervals.count()
                        )
                    ) {
                        Text(
                            text = if (minutes > 60) "${minutes / 60}h" else "${minutes}m"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogAndTimelineScreen(
    repo: MonitorRepository,
    settings: AppSettings,
    onNavigateToLogs: () -> Unit,
    onNavigateToPhysicalLogs: (String) -> Unit,
    onNavigateToTimeline: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(16.dp)) {


        Text("Show logs", style = MaterialTheme.typography.titleMedium)

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            SegmentedButton(
                selected = false,
                onClick = onNavigateToLogs,
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
            ) {
                Text("System")
            }

            SegmentedButton(
                selected = false,
                onClick = { onNavigateToPhysicalLogs("miner_logs.txt") },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
            ) {
                Text("Miner")
            }

            SegmentedButton(
                selected = false,
                onClick = { onNavigateToPhysicalLogs("workers_logs.txt") },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
            ) {
                Text("Workers")
            }
        }

    }

}


@Composable
fun PhysicalLogsScreen(repo: MonitorRepository, fileName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var logContent by remember { mutableStateOf("") }
    var showDeleteMenu by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf<String?>(null) }

    fun refreshLogs() {
        logContent = repo.getPhysicalLogContent(fileName)
    }

    LaunchedEffect(fileName) {
        refreshLogs()
    }

    if (showConfirmation) {
        AlertDialog(onDismissRequest = { showConfirmation = false }, title = {
            val titMsg = when (selectedFilter) {
                "workers" -> "workers"
                "hashrate1m" -> "hashrate1m"
                "bestshare" -> "bestshare"
                "bestever" -> "bestever"
                else -> "ALL "
            }

            Text("Clear $titMsg")
        }, text = {
            val longerMsg = when (selectedFilter) {
                "workers" -> "only Workers "
                "hashrate1m" -> "only hashrate1m (spam) "
                "bestshare" -> "only bestshare"
                "bestever" -> "only bestever"
                else -> "ALL "
            }

            Text("Remove $longerMsg records from $fileName?")
        }, confirmButton = {
            TextButton(onClick = {
                repo.clearPhysicalLog(fileName, selectedFilter)
                refreshLogs()
                showConfirmation = false
            }) {
                val btnNote = when (selectedFilter) {
                    "workers" -> "workers"
                    "hashrate1m" -> "hashrate1m"
                    "bestshare" -> "bestshare"
                    "bestever" -> "bestever"
                    else -> "ALL "
                }
                Text(
                    text = "Delete $btnNote", color = MaterialTheme.colorScheme.error
                )
            }
        }, dismissButton = {
            TextButton(onClick = { showConfirmation = false }) {
                Text("Skip")
            }
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(fileName)
                    val file = repo.getLogFile(fileName)
                    val size = file?.length() ?: 0L
                    val rowCount =
                        if (logContent.isBlank() || logContent.startsWith("Log ") || logContent.startsWith(
                                "Error"
                            )
                        ) 0
                        else logContent.trim().lines().size
                    val sizeStr = when {
                        size < 1024 -> "$size B"
                        size < 1024 * 1024 -> "${size / 1024} KB"
                        else -> String.format(
                            Locale.US, "%.2f MB", size.toDouble() / (1024 * 1024)
                        )
                    }
                    Text(
                        text = "$rowCount rows • $sizeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
            }, actions = {
                IconButton(onClick = {
                    val file = repo.getLogFile(fileName)
                    if (file != null) {
                        val uri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Export Log"))
                    }
                }) {
                    Icon(Icons.Default.Share, null)
                }
                Box {
                    IconButton(onClick = { showDeleteMenu = true }) {
                        Icon(Icons.Default.Delete, null)
                    }
                    DropdownMenu(
                        expanded = showDeleteMenu, onDismissRequest = { showDeleteMenu = false }) {

                        if (fileName == "miner_logs.txt") {
                            DropdownMenuItem(text = { Text("Clear Worker Count Logs") }, onClick = {
                                selectedFilter = "workers"
                                showConfirmation = true
                                showDeleteMenu = false
                            })
                        }
                        if (fileName == "workers_logs.txt") {
                            DropdownMenuItem(text = { Text("Clear Hashrate Logs") }, onClick = {
                                selectedFilter = "hashrate1m"
                                showConfirmation = true
                                showDeleteMenu = false
                            })
                        }
                        DropdownMenuItem(text = { Text("Clear BestShare Logs") }, onClick = {
                            selectedFilter = "bestshare"
                            showConfirmation = true
                            showDeleteMenu = false
                        })
                        DropdownMenuItem(text = { Text("Clear BestEver Logs") }, onClick = {
                            selectedFilter = "bestever"
                            showConfirmation = true
                            showDeleteMenu = false
                        })
                        DropdownMenuItem(text = {
                            Text(
                                "Clear Entire File", color = MaterialTheme.colorScheme.error
                            )
                        }, onClick = {
                            selectedFilter = null
                            showConfirmation = true
                            showDeleteMenu = false
                        })

                    }
                }
            })
        }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(logContent, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun LogsScreen(logs: List<LogEntry>, onClear: () -> Unit, onBack: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Scaffold(

        topBar = {
            TopAppBar(title = { Text("System Logs") }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
            }, actions = {

                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear Logs")
                }
            })
        }) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            items(logs) { log ->
                Column(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = dateFormat.format(Date(log.time)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(text = log.msg, style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp), thickness = 0.5.dp)
                }
            }
            if (logs.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No logs available")
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var latestUrl by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {

        Text(
            "About",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

       // HorizontalDivider(Modifier.padding(vertical = 12.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(
                "Built with love and assisted by Google Gemini AI.",
                style = MaterialTheme.typography.bodySmall
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(
                "Dedicated to all ckpool solominers eagerly awaiting any new Bestever and Bestshare.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                Text(
                    "Thanks to", style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "ckpool.org",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "and",
                    style = MaterialTheme.typography.bodyMedium,

                    )
                Spacer(Modifier.width(4.dp))
                Text(
                    "bitaxe.org",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

        }

/*

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        Text(
            "Built with love and assisted by Google Gemini AI.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "For all ckpool solominers eagerly awaiting any new Bestshare and Bestever submitted.",
            style = MaterialTheme.typography.bodySmall
        )

        Row(modifier = Modifier.padding(vertical = 6.dp)) {
            Text(
                "Thanks to", style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "ckpool.org",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "and",
                style = MaterialTheme.typography.bodyMedium,

                )
            Spacer(Modifier.width(4.dp))
            Text(
                "bitaxe.org",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
*/

        HorizontalDivider(Modifier.padding(vertical = 12.dp))


        Text("App Behavior", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(4.dp))
        Column(
            modifier = Modifier
                .padding(6.dp)
                .fillMaxWidth()
        ) {
            BehaviorItem(
                "Background Monitoring",
                "Checks the CKPool API every (15,30 or 60) minutes to monitor activity."
            )

            BehaviorItem(
                "Updates",
                "Compares the latest data with previous results to detect, log, and notify changes."
            )

            BehaviorItem(
                "Notifications",
                "Sends notifications for selected values changes related to ⛏️ miner or 🛠️ workers."
            )

            BehaviorItem(
                "User Logs",
                "Tracks selected values and records individual worker activity and achievements."
            )

            BehaviorItem(
                "System Logs",
                "Provides system status and diagnostic feedback. Logs are automatically purged after 100 entries."
            )


        }



        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text("Support & Donate", style = MaterialTheme.typography.titleMedium)
        Text(
            "This app is Free and Open Source. If you find it useful, consider supporting development through donations or using the recommended services below.",
            style = MaterialTheme.typography.bodySmall
        )


        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text("Recommended Services", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        ServiceCard(
            name = "⛏️ MRR - Rent or Lease mining hashpower",
            description = "Rent high-performance hashing power to boost your solo mining efforts.",
            onClick = { uriHandler.openUri("https://www.miningrigrentals.com/?ref=2001") })
        Spacer(Modifier.height(8.dp))
        ServiceCard(
            name = "💰️  Bitrefill - Live on Crypto",
            description = "Use your Bitcoin and cryptocurrency for everyday life. Purchase GiftCards, eSIMs...",
            onClick = { uriHandler.openUri("https://www.bitrefill.com/buy/?code=dsjbx8wg") })



        Spacer(Modifier.height(8.dp))
        Text("Crypto Exchanges", style = MaterialTheme.typography.titleSmall)

        Spacer(Modifier.height(4.dp))
        ServiceCard(
            name = "💸 Kucoin - Favorite",
            description = "Fifty-Fifty commission split (sharing half of my commissions back).",
            onClick = { uriHandler.openUri("https://www.kucoin.com/r/rf/QBSYCKEF") })

        Spacer(Modifier.height(8.dp))
        ServiceCard(
            name = "💸 Binance - World's leading",
            description = "Fifty-Fifty commission split (sharing half of my commissions back).",
            onClick = { uriHandler.openUri("https://accounts.binance.com/en/register?ref=PRG6D2L8") })


        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        // Footer
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.Red,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Thank you for using Watch CKPool!",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Developer Support Version",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text("Donation Addresses", style = MaterialTheme.typography.titleMedium)
        Text("(Tap to copy)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)


        Spacer(Modifier.height(8.dp))

        DonateRow("BTC", "bc1qttw40dl6awd2p24qztv6e8ldfghnhnhralpzpn")
        DonateRow("ETH,BNB", "0x34291eef96cf3d3e2b0de83917a6bc4ad32d4d3a")

        DonateRow("ZEC", "t1U6nwN52unm5wHgTwCtzAAfwukbRmWZwKw")
        DonateRow("SOL", "Csicm7WA3YSs5gyoR94fQvUH9CCZnoSmFyTgFeTALAZ8")

        DonateRow("TRX", "TH3peMk4WpKFBgs1DPfxcySzv4ypAstVHL")


        HorizontalDivider(Modifier.padding(vertical = 16.dp))




        Text("GitHub & Legal", style = MaterialTheme.typography.titleMedium)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Row(
                Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Current Version: ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    updateStatus?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (it.contains("New")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                if (latestUrl != null) {
                    Button(
                        onClick = { uriHandler.openUri(latestUrl!!) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Get Update", fontSize = 12.sp)
                    }
                } else {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isChecking = true
                                updateStatus = "Checking GitHub..."
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        val conn =
                                            java.net.URL("https://api.github.com/repos/victoruniformsolo/watch-ckpool/releases/latest")
                                                .openConnection() as java.net.HttpURLConnection
                                        conn.connectTimeout = 5000
                                        if (conn.responseCode == 200) {
                                            conn.inputStream.bufferedReader().use { it.readText() }
                                        } else null
                                    }
                                    if (result != null) {
                                        val json = Json.parseToJsonElement(result).jsonObject
                                        val tagName = json["tag_name"]?.jsonPrimitive?.content ?: ""
                                        val htmlUrl = json["html_url"]?.jsonPrimitive?.content
                                        if (tagName != BuildConfig.VERSION_NAME && tagName.isNotEmpty()) {
                                            updateStatus = "New version available: $tagName"
                                            latestUrl = htmlUrl
                                        } else {
                                            updateStatus = "You have the latest version"
                                        }
                                    } else {
                                        updateStatus = "No releases found or API error"
                                    }
                                } catch (e: Exception) {
                                    updateStatus = "Check failed: ${e.localizedMessage}"
                                } finally {
                                    isChecking = false
                                }
                            }
                        },
                        enabled = !isChecking,
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, null, Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        Text(
            "Open Source under MIT License.\n" + "Source code available on GitHub.\n\n" + "Disclaimer: This app is provided 'as is' without warranty of any kind. Use at your own risk. Mining rewards depend on pool performance and luck.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Close About")
        }
    }
}

@Composable
fun BehaviorItem(title: String, description: String) {
    Row(modifier = Modifier.padding(vertical = 8.dp)) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DonateRow(label: String, address: String) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        Modifier
            .padding(vertical = 8.dp)
            .clickable {
                clipboardManager.setText(AnnotatedString(address))
                Toast.makeText(context, "$label Address copied to clipboard", Toast.LENGTH_SHORT)
                    .show()
            }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Copy",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.width(10.dp))
            Text(
                address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

    }
}

@Composable
fun ServiceCard(name: String, description: String, onClick: () -> Unit) {


    Card(
        modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = 0.5f
            )
        )
    ) {
        Column(Modifier.padding(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                IconButton(onClick = onClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.Launch,
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row(
                modifier = Modifier
                    .padding(6.dp)
                    .fillMaxWidth()

            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

            }
        }
    }

}

@Composable
fun LogTimelineScreen(
    repo: MonitorRepository,
    onNavigateToPhysicalLogs: (String) -> Unit
) {
    var events by remember { mutableStateOf<List<TimelineEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val minerLogs = repo.getPhysicalLogContent("miner_logs.txt")
        val workerLogs = repo.getPhysicalLogContent("workers_logs.txt")
        events = LogParser.parseAllLogs(minerLogs, workerLogs)
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (events.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No activity recorded yet", color = MaterialTheme.colorScheme.outline)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .padding(horizontal = 16.dp)
        ) {
            items(events) { event ->
                TimelineRow(event, onNavigateToPhysicalLogs)
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

}


@Composable
fun TimelineRow(event: TimelineEvent, onNavigateToPhysicalLogs: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Left side: Miner logs
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (event.source == "minerlog") {
                EventContent(event, isLeft = true)
            }
        }

        // Center: Timeline axis
        Box(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight(), contentAlignment = Alignment.Center
        ) {
            // Vertical Line
            Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
            ) {
                drawLine(
                    color = Color.Gray.copy(alpha = 0.3f),
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Icon Badge
            Surface(
                shape = CircleShape,
                color = if (event.source == "minerlog") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .size(32.dp)
                    .clickable {
                        val fileName =
                            if (event.source == "minerlog") "miner_logs.txt" else "workers_logs.txt"
                        onNavigateToPhysicalLogs(fileName)
                    },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (event.source == "minerlog") "⛏️" else "🛠️", fontSize = 14.sp)
                }
            }
        }

        // Right side: Worker logs
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (event.source == "workerslog") {
                EventContent(event, isLeft = false)
            }
        }
    }
}

@Composable
fun EventContent(event: TimelineEvent, isLeft: Boolean) {
    val alignment = if (isLeft) Alignment.End else Alignment.Start

    Column(horizontalAlignment = alignment) {
        Text(
            text = event.time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = event.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ), shape = MaterialTheme.shapes.small, modifier = Modifier.padding(top = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(6.dp), horizontalAlignment = alignment
            ) {
                Text(
                    text = event.key.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (event.key) {
                            "bestshare" -> formatHashrate(event.newValue.toDouble())
                            "bestever" -> formatHashrate(event.newValue.toDouble())
                            else -> event.newValue
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = event.change,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (event.isPositive) Color(0xFF169423) else Color(0xFFE30B0B),
                   //     color = if (event.isPositive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}




