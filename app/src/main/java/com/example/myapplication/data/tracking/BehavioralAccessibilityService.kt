package com.example.myapplication.data.tracking

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.util.SparseArray
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicInteger

class BehavioralAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BehavioralTracking"
        
        // ML Features
        var typingCps = 0f
        var backspaceCount = 0
        var typingHesitationMs = 0L // Average delay between keystrokes
        
        var scrollVelocityAvg = 0f
        var scrollErraticness = 0f // StdDev of velocity / Avg velocity
        
        private val characterCount = AtomicInteger(0)
        private var lastTypingTime = 0L
        private var totalKeystrokeIntervalMs = 0L
        private var keystrokeCount = 0
        
        var typingPausesCount = 0 // Number of times user stopped typing for > 2 seconds
        var maxTypingPauseMs = 0L // Longest pause made during an active session
        
        
        private val scrollVelocities = mutableListOf<Float>()
        
        // App Switching tracking
        private val switchTimestamps = mutableListOf<Long>()
        private var lastPackage = ""
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handleTypingEvent(event)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> handleScrollEvent(event)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowStateChanged(event)
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val currentPackage = event.packageName?.toString() ?: return
        if (currentPackage != lastPackage && currentPackage != "com.example.myapplication") {
            lastPackage = currentPackage
            val currentTime = System.currentTimeMillis()
            switchTimestamps.add(currentTime)
            
            // Keep last 10 switches within a 5-min window
            val windowMs = 5 * 60 * 1000L
            switchTimestamps.removeAll { it < currentTime - windowMs }
            
            Log.d(TAG, "App Switched to: $currentPackage. Total switches in past 5m: ${switchTimestamps.size}")
            
            if (switchTimestamps.size >= 8) {
                // Rapid switching detected
                Log.w(TAG, "Rapid App Switching Detected! Triggering Focus Alert.")
                NotificationHelper.sendBehavioralAlert(
                    this,
                    "Focus Alert",
                    "Focus seems low today with frequent app switching. Try a short walk to recharge?"
                )
                switchTimestamps.clear()
            }
        }
    }

    private fun handleTypingEvent(event: AccessibilityEvent) {
        val currentTime = System.currentTimeMillis()
        val added = event.addedCount
        val removed = event.removedCount
        
        if (added > 0) {
            characterCount.addAndGet(added)
            if (lastTypingTime != 0L) {
                val delta = currentTime - lastTypingTime
                
                // Track typing pauses
                if (delta > 2000) { // 2 seconds delay
                    typingPausesCount++
                    if (delta > maxTypingPauseMs) {
                        maxTypingPauseMs = delta
                    }
                    Log.d(TAG, "Typing Pause Detected: ${delta}ms. Pause Count: $typingPausesCount")
                }
                
                if (delta < 3000) { // Only count if typing is continuous
                    totalKeystrokeIntervalMs += delta
                    keystrokeCount += added
                    typingHesitationMs = totalKeystrokeIntervalMs / keystrokeCount.coerceAtLeast(1)
                    
                    val currentCps = (added.toFloat() / (delta / 1000f)).coerceAtMost(15f)
                    typingCps = (typingCps * 0.8f) + (currentCps * 0.2f)
                    
                    Log.d(TAG, "Typing Speed: " + String.format("%.2f", typingCps) + " CPS | Hesitation Avg: ${typingHesitationMs}ms")
                }
            }
            lastTypingTime = currentTime
        }
        
        if (removed > 0) {
            backspaceCount += removed
            Log.d(TAG, "Backspace Used. Total Count: $backspaceCount")
        }
    }

    private fun handleScrollEvent(event: AccessibilityEvent) {
        // High granularity scroll analysis
        val delta = (event.scrollX + event.scrollY).toFloat()
        if (delta != 0f) {
            scrollVelocities.add(delta)
            if (scrollVelocities.size > 100) scrollVelocities.removeAt(0)
            
            scrollVelocityAvg = scrollVelocities.average().toFloat()
            val variance = scrollVelocities.map { (it - scrollVelocityAvg) * (it - scrollVelocityAvg) }.average().toFloat()
            val stdDev = kotlin.math.sqrt(variance.toDouble()).toFloat()
            
            // Erraticness is high when speed varies wildly
            scrollErraticness = if (scrollVelocityAvg.absoluteValue > 0.1f) stdDev / scrollVelocityAvg.absoluteValue else 0f
            
            if (scrollVelocities.size % 10 == 0) {
                Log.d(TAG, "Scroll Status - Speed: " + String.format("%.2f", scrollVelocityAvg) + " | Erraticness: " + String.format("%.2f", scrollErraticness))
            }
            
            checkScrollAnomaly()
        }
    }

    private fun checkScrollAnomaly() {
        if (scrollErraticness > 1.8f && scrollVelocities.size > 20) {
            // High erraticness detected - likely anxiety or restlessness
            Log.w(TAG, "High Scroll Erraticness Detected! Triggering Mindful Moment Alert.")
            NotificationHelper.sendBehavioralAlert(
                this,
                "Mindful Moment",
                "You've been scrolling quite rapidly. Take a 2-minute breathing break?"
            )
            // Reset to avoid spamming
            scrollVelocities.clear()
            scrollErraticness = 0f
        }
    }

    private val Float.absoluteValue: Float get() = if (this < 0) -this else this

    override fun onInterrupt() {
        Log.e(TAG, "Accessibility Service Interrupted")
        ServiceConnectionManager.setConnected(false)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Behavioral Accessibility Service Connected")
        ServiceConnectionManager.setConnected(true)
        
        // Show a visual popup to the user on the screen when it connects successfully
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(
                applicationContext, 
                "Wellness Wave AI Active \uD83C\uDF0A\nNow monitoring background behavior", 
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        ServiceConnectionManager.setConnected(false)
        return super.onUnbind(intent)
    }
}
