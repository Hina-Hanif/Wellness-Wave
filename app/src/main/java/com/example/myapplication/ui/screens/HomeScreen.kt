package com.example.myapplication.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.models.PredictionResponse
import kotlinx.coroutines.launch
import java.util.Calendar
import android.app.usage.UsageStatsManager

@Composable
fun HomeScreen(innerPadding: PaddingValues, onNavigateSettings: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prediction by remember { mutableStateOf<PredictionResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var todayScreenTime by remember { mutableStateOf("0m") }

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    // Fetch AI prediction
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val sharedPrefs = context.getSharedPreferences("wellness_wave_prefs", Context.MODE_PRIVATE)
                val userId = sharedPrefs.getString("user_id", "default_user") ?: "default_user"

                val response = RetrofitClient.instance.getPrediction(userId)
                if (response.isSuccessful) {
                    prediction = response.body()
                }
            } catch (e: Exception) {
                // Ignore for now
            } finally {
                isLoading = false
            }
        }
    }

    // Fetch today's screen time
    LaunchedEffect(Unit) {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            calendar.timeInMillis,
            System.currentTimeMillis()
        )

        var totalMs = 0L
        stats?.forEach { stat ->
            if (stat.totalTimeInForeground > 0) {
                totalMs += stat.totalTimeInForeground
            }
        }

        val hours = (totalMs / (1000 * 60 * 60)).toInt()
        val minutes = ((totalMs / (1000 * 60)) % 60).toInt()

        todayScreenTime = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    val isConnected by com.example.myapplication.data.tracking.ServiceConnectionManager.isServiceConnected.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(innerPadding)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = remember {
                        java.text.SimpleDateFormat("EEEE, MMM d, yyyy", java.util.Locale.getDefault())
                            .format(java.util.Date())
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Here's your wellness summary today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        android.widget.Toast.makeText(context, "Syncing real-time behavior to AI Cloud...", android.widget.Toast.LENGTH_SHORT).show()
                        val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.myapplication.data.network.DataSyncWorker>().build()
                        androidx.work.WorkManager.getInstance(context).enqueue(syncRequest)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onNavigateSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Connection Status Banner
        Surface(
            color = if (isConnected) Color(0xFF00E37C).copy(alpha = 0.15f) else Color(0xFFFF5252).copy(alpha = 0.15f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(if (isConnected) Color(0xFF00E37C) else Color(0xFFFF5252), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isConnected) "AI Brain Connected & Monitoring" else "AI Brain Disconnected. Enable in Settings.",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isConnected) Color(0xFF00C853) else Color(0xFFD50000),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Large Mint Green Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CURRENT AI ANALYSIS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isLoading) "Analyzing..." else (prediction?.stress_level ?: "Balanced"),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = 1f,
                            modifier = Modifier.size(80.dp),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                            strokeWidth = 8.dp,
                        )

                        val aiConfidence = ((prediction?.confidence_score ?: 0.85f) * 100).toInt()

                        val animatedProgress by animateFloatAsState(
                            targetValue = aiConfidence / 100f,
                            animationSpec = tween(1500, easing = FastOutSlowInEasing),
                            label = "progress"
                        )

                        CircularProgressIndicator(
                            progress = if (isLoading) 0.1f else animatedProgress,
                            modifier = Modifier.size(80.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 8.dp,
                            strokeCap = StrokeCap.Round
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isLoading) "..." else "$aiConfidence",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = "CONF",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = prediction?.real_time_feedback ?: "Analyzing your patterns to provide insights...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "TODAY's METRICS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2x2 Grid
        Row(modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Screen Time",
                value = todayScreenTime,
                icon = Icons.Rounded.PhoneAndroid,
                trend = "Live Tracking Active",
                trendPositive = false
            )
            Spacer(modifier = Modifier.width(16.dp))
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Activity",
                value = "Moderate",
                icon = Icons.Rounded.DirectionsRun,
                trend = "Consistent",
                trendPositive = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Sleep Quality",
                value = "85%",
                icon = Icons.Rounded.Nightlight,
                trend = "7h 20m last night",
                trendPositive = true
            )
            Spacer(modifier = Modifier.width(16.dp))
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Focus Trend",
                value = "Improving",
                icon = Icons.Rounded.TrendingUp,
                trend = "Fewer app switches",
                trendPositive = true
            )
        }

        Spacer(modifier = Modifier.height(90.dp)) // Avoid bottom bar overlap
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    trend: String,
    trendPositive: Boolean
) {
    Card(
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = trend,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (trendPositive) MaterialTheme.colorScheme.primary else Color(0xFFFF5252),
                    fontSize = 10.sp
                )
            }
        }
    }
}