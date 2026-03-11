package com.example.myapplication

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.myapplication.data.network.DataSyncWorker
import java.util.concurrent.TimeUnit
import com.example.myapplication.ui.screens.*
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.data.tracking.WellnessMonitorService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure the background monitoring service is alive
        WellnessMonitorService.startService(this)
        val monitor = UsageMonitor(this)

        if (!monitor.hasUsagePermission()) {
            monitor.requestUsagePermission()
        } else {
            val screenTime = monitor.getTodayScreenTimeMinutes()
            android.util.Log.d("PHONE_MONITOR", "Screen time: $screenTime minutes")
        }
        
        enableEdgeToEdge()
        setContent {
            val sharedPrefs = getSharedPreferences("wellness_wave_prefs", Context.MODE_PRIVATE)
            val isDarkTheme = remember { mutableStateOf(sharedPrefs.getBoolean("dark_theme", false)) }
            
            MyApplicationTheme(darkTheme = isDarkTheme.value) {
                MyApplicationApp(isDarkTheme)
            }
        }
    }
}

@Composable
fun MyApplicationApp(isDarkTheme: MutableState<Boolean>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("wellness_wave_prefs", Context.MODE_PRIVATE) }
    
    // Check for login status and onboarding status
    val isFirstTime = remember { sharedPrefs.getBoolean("is_first_time", true) }
    val isLoggedIn = remember { sharedPrefs.getBoolean("logged_in", false) }
    
    var currentDestination by rememberSaveable { 
        mutableStateOf(
            if (isFirstTime) AppDestinations.ONBOARDING 
            else if (!isLoggedIn) AppDestinations.LOGIN 
            else AppDestinations.HOME
        ) 
    }

    if (currentDestination == AppDestinations.ONBOARDING) {
        OnboardingScreen(PaddingValues(0.dp)) {
            sharedPrefs.edit().putBoolean("is_first_time", false).apply()
            currentDestination = AppDestinations.LOGIN
        }
    } else if (currentDestination == AppDestinations.LOGIN) {
        LoginScreen(
            onLoginSuccess = { 
                sharedPrefs.edit().putBoolean("logged_in", true).apply()
                // Schedule Background Sync
                val syncRequest = PeriodicWorkRequestBuilder<DataSyncWorker>(1, TimeUnit.HOURS).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "behavioral_sync",
                    ExistingPeriodicWorkPolicy.KEEP,
                    syncRequest
                )
                currentDestination = AppDestinations.HOME 
            },
            onNavigateToSignup = { currentDestination = AppDestinations.SIGNUP }
        )
    } else if (currentDestination == AppDestinations.SIGNUP) {
        SignupScreen(
            onSignupSuccess = { currentDestination = AppDestinations.LOGIN },
            onNavigateToLogin = { currentDestination = AppDestinations.LOGIN }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentPadding = PaddingValues(0.dp),
                    tonalElevation = 8.dp,
                    modifier = Modifier.height(90.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NavItem(AppDestinations.HOME, currentDestination) { currentDestination = it }
                        NavItem(AppDestinations.ANALYTICS, currentDestination) { currentDestination = it }
                        NavItem(AppDestinations.INSIGHTS, currentDestination) { currentDestination = it }
                        NavItem(AppDestinations.TRENDS, currentDestination) { currentDestination = it }
                    }
                }
            }
        ) { innerPadding ->
            when (currentDestination) {
                AppDestinations.HOME -> HomeScreen(innerPadding, onNavigateSettings = { currentDestination = AppDestinations.SETTINGS })
                AppDestinations.ANALYTICS -> BehaviorAnalyticsScreen(innerPadding)
                AppDestinations.INSIGHTS -> AIInsightsScreen(innerPadding)
                AppDestinations.TRENDS -> TrendsHistoryScreen(innerPadding)
                AppDestinations.SETTINGS -> PrivacySettingsScreen(innerPadding, isDarkTheme) { currentDestination = AppDestinations.HOME }
                else -> HomeScreen(innerPadding) { currentDestination = AppDestinations.SETTINGS }
            }
        }
    }
}

@Composable
fun RowScope.NavItem(
    destination: AppDestinations,
    currentDestination: AppDestinations,
    onSelect: (AppDestinations) -> Unit
) {
    val selected = destination == currentDestination
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = { onSelect(destination) },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                destination.icon,
                contentDescription = destination.label,
                modifier = Modifier.size(28.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector
) {
    ONBOARDING("Intro", Icons.Default.Info),
    HOME("Home", Icons.Default.Home),
    ANALYTICS("Analytics", Icons.Default.Timeline),
    INSIGHTS("Insights", Icons.Default.Lightbulb),
    TRENDS("Trends", Icons.Default.DateRange), 
    SETTINGS("Settings", Icons.Default.Settings),
    LOGIN("Login", Icons.Default.Lock),
    SIGNUP("Signup", Icons.Default.AccountCircle)
}