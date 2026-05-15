@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.watchckpool

import android.Manifest
import com.example.watchckpool.BuildConfig
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.navigation.navDeepLink
import com.example.watchckpool.ui.theme.WatchCKPoolTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle permission result if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val repo = MonitorRepository(applicationContext)
        // BackgroundWorker.start(applicationContext) // Moved to MainApp with dynamic interval

        setContent {
            val settings by repo.settings.collectAsState(initial = AppSettings())
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            WatchCKPoolTheme(darkTheme = darkTheme) {
                MainApp(repo, settings)
            }
        }
    }
}


@Composable
fun MainApp(repo: MonitorRepository, settings: AppSettings) {
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000) // 2 seconds splash
        isLoading = false
    }

    // Handle Background Worker Rescheduling
    LaunchedEffect(settings.refreshInterval) {
        BackgroundWorker.start(context, settings.refreshInterval)
    }

    val navController = rememberNavController()
    val logs by repo.logs.collectAsState(initial = emptyList())
    val latestLog by repo.latestLog.collectAsState(initial = null)

    val lastData by repo.lastData.collectAsState(initial = null)
    val lastSyncTime by repo.lastSyncTime.collectAsState(initial = 0L)
    var showAbout by remember { mutableStateOf(false) }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(topBar = {
        TopAppBar(title = {
            Column {
                Text(
                    "WATCH CKPOOL",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Solo Mining Monitor • ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }, actions = {
            val themeIcon = when (settings.themeMode) {
                ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                ThemeMode.LIGHT -> Icons.Default.LightMode
                ThemeMode.DARK -> Icons.Default.DarkMode
            }
            IconButton(onClick = {
                val nextMode = when (settings.themeMode) {
                    ThemeMode.SYSTEM -> ThemeMode.LIGHT
                    ThemeMode.LIGHT -> ThemeMode.DARK
                    ThemeMode.DARK -> ThemeMode.SYSTEM
                }
                scope.launch { repo.updateTheme(nextMode) }
            }) {
                Icon(themeIcon, contentDescription = "Switch Theme")
            }



            if (currentRoute == "connection") {
                IconButton(onClick = {
                    navController.navigate("main_tabs") {
                        popUpTo("main_tabs") { inclusive = true }
                    }
                }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Quit ConnectionScreen",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                IconButton(onClick = {
                    navController.navigate("connection")
                }) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = "Connection",
                        tint = if (settings.btcAddress.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary

                    )
                }
            }
            IconButton(onClick = { showAbout = !showAbout }) {
                Icon(
                    if (showAbout) Icons.Default.Close else Icons.Default.Info,
                    contentDescription = "About",
                    tint = if (showAbout) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                )
            }
        })
    }) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            NavHost(navController, "main_tabs") {
                composable(
                    route = "main_tabs",
                    deepLinks = listOf(
                        navDeepLink { uriPattern = "watchckpool://navigate/dashboard" },
                        navDeepLink { uriPattern = "watchckpool://navigate/workers" }
                    )
                ) { backStackEntry ->
                    // Extract initial page from deep link if possible
                    val intent = backStackEntry.arguments?.get("android-support-nav:controller:deep-link-intent") as? android.content.Intent
                    val initialPage = when (intent?.data?.lastPathSegment) {
                        "workers" -> 1
                        else -> 0
                    }
                    MainTabsPager(repo, settings, lastData, lastSyncTime, latestLog, navController, initialPage)
                }
                
                composable("connection") {
                    ConnectionScreen(repo, settings, onBack = { navController.popBackStack() })
                }
                composable("logs") {
                    LogsScreen(
                        logs = logs,
                        onClear = { scope.launch { repo.clearLogs() } },
                        onBack = { navController.popBackStack() })
                }
                composable("physical_logs/{fileName}") { backStackEntry ->
                    val fileName = backStackEntry.arguments?.getString("fileName") ?: ""
                    PhysicalLogsScreen(repo, fileName, onBack = { navController.popBackStack() })
                }
                composable("activity_timeline") {
                    LogTimelineScreen(
                        repo,
                 //       onBack = { navController.popBackStack() },
                        onNavigateToPhysicalLogs = { fileName -> navController.navigate("physical_logs/$fileName") })
                }

            }

            if (showAbout) {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    AboutScreen(onDismiss = { showAbout = false })
                }
            }
        }
    }
}

@Composable
fun MainTabsPager(
    repo: MonitorRepository,
    settings: AppSettings,
    lastData: String?,
    lastSyncTime: Long,
    latestLog: LogEntry?,
    navController: NavHostController,
    initialPage: Int = 0
) {
    val pagerState = rememberPagerState(initialPage = initialPage) { 4 }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 1
        ) { page ->
            when (page) {
                0 -> DashboardScreen(repo, settings, lastData, lastSyncTime, latestLog,
                    onNavigateToLogs = { navController.navigate("logs") })
                1 -> WorkersScreen(repo, settings, lastData)
                2 -> LogTimelineScreen(repo, onNavigateToPhysicalLogs = { fileName -> navController.navigate("physical_logs/$fileName") },)
                3 -> LogAndTimelineScreen(repo, settings,
                    onNavigateToLogs = { navController.navigate("logs") },
                    onNavigateToPhysicalLogs = { fileName -> navController.navigate("physical_logs/$fileName") },
                    onNavigateToTimeline = { navController.navigate("activity_timeline") }

                )

            }
        }

        NavigationBar(windowInsets = WindowInsets(0)) {
            NavigationBarItem(
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                icon = { Icon(Icons.Default.Dashboard, null) },
                label = { Text("Dashboard") })
            NavigationBarItem(
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                icon = { Icon(Icons.Default.Handyman, null) },
                label = { Text("Workers") })
            NavigationBarItem(
                selected = pagerState.currentPage == 2,
                onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                icon = { Icon(Icons.Default.Timeline, null) },
                label = { Text("Timeline") })
            NavigationBarItem(
                selected = pagerState.currentPage == 3,
                onClick = { scope.launch { pagerState.animateScrollToPage(3) } },
                icon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                label = { Text("Log&Data") })
        }
    }
}
