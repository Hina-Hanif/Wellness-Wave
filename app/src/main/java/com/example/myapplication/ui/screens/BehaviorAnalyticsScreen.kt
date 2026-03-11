package com.example.myapplication.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.models.PredictionHistoryResponse
import kotlinx.coroutines.launch
import android.content.Context
import androidx.compose.ui.platform.LocalContext

@Composable
fun BehaviorAnalyticsScreen(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var historyData by remember { mutableStateOf<PredictionHistoryResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val sharedPrefs = context.getSharedPreferences("wellness_wave_prefs", Context.MODE_PRIVATE)
                val userId = sharedPrefs.getString("user_id", "default_user") ?: "default_user"
                
                val response = RetrofitClient.instance.getHistory(userId)
                if (response.isSuccessful) {
                    historyData = response.body()
                }
            } catch (e: Exception) {
                // Ignore for now, fallback to defaults
            } finally {
                isLoading = false
            }
        }
    }

    // Default mock data if history is not available
    val screenTimePoints = historyData?.history?.map { it.screen_time_hours / 10f } ?: listOf(0.4f, 0.5f, 0.3f, 0.6f, 0.8f, 0.5f, 0.4f)
    val typingBarPoints = listOf(0.2f, 0.8f, 0.6f, 0.3f, 0.9f, 0.5f, 0.4f, 0.7f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(innerPadding)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Behavior Analytics",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Deep dive into your digital habits",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Screen Time Line Chart Card
        AnalyticsCard(
            title = "Screen Time",
            subtitle = "Past 7 Days",
            value = "4h 12m avg",
            valueColor = MaterialTheme.colorScheme.primary
        ) {
            LineChartCanvas(
                points = screenTimePoints,
                lineColor = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Typing Metrics Bar Chart Card
        AnalyticsCard(
            title = "Typing Intensity",
            subtitle = "Hourly Blocks",
            value = "64 WPM peak",
            valueColor = MaterialTheme.colorScheme.secondary
        ) {
            BarChartCanvas(
                bars = typingBarPoints,
                barColor = MaterialTheme.colorScheme.secondary
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Detailed Stats Grid
        Row(modifier = Modifier.fillMaxWidth()) {
            SmallStatCard(
                modifier = Modifier.weight(1f),
                title = "Avg Pause",
                value = "1.2s",
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            SmallStatCard(
                modifier = Modifier.weight(1f),
                title = "App Switches",
                value = "14 /hr",
                color = MaterialTheme.colorScheme.secondary
            )
        }
        
        Spacer(modifier = Modifier.height(90.dp))
    }
}

@Composable
fun AnalyticsCard(
    title: String,
    subtitle: String,
    value: String,
    valueColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = valueColor
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            content()
        }
    }
}

@Composable
fun SmallStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun LineChartCanvas(points: List<Float>, lineColor: Color) {
    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            val width = size.width
            val height = size.height
            val spacing = width / (points.size - 1)

            val path = Path().apply {
                moveTo(0f, height * (1 - points[0]))
                for (i in 1 until points.size) {
                    val x = i * spacing
                    val y = height * (1 - points[i])
                    val prevX = (i - 1) * spacing
                    val prevY = height * (1 - points[i - 1])
                    cubicTo(
                        prevX + spacing / 2f, prevY,
                        x - spacing / 2f, y,
                        x, y
                    )
                }
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx())
            )

            val fillPath = Path().apply {
                addPath(path)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.2f), Color.Transparent)
                )
            )
            
            // Draw Points
            for (i in points.indices) {
                val x = i * spacing
                val y = height * (1 - points[i])
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(x, y)
                )
                drawCircle(
                    color = lineColor,
                    radius = 2.5.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(x, y)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BarChartCanvas(bars: List<Float>, barColor: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            bars.forEach { heightFactor ->
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .fillMaxHeight(heightFactor)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(barColor, barColor.copy(alpha = 0.4f))
                            ),
                            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                        )
                )
            }
        }
    }
}
