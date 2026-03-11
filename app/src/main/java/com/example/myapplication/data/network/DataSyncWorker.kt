package com.example.myapplication.data.network

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myapplication.api.RetrofitClient
import com.example.myapplication.data.UsageDataManager

class DataSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val usageDataManager = UsageDataManager(applicationContext)
        val api = RetrofitClient.instance

        return try {
            val metrics = usageDataManager.getDailyMetrics(moodScore = 0)

            Log.d("DataSyncWorker", "============================================")
            Log.d("DataSyncWorker", "  WELLNESS WAVE - SYNCING DATA TO FIREBASE  ")
            Log.d("DataSyncWorker", "============================================")
            Log.d("DataSyncWorker", "  User ID        : ${metrics.user_id}")
            Log.d("DataSyncWorker", "  Date           : ${metrics.date}")
            Log.d("DataSyncWorker", "  Screen Time    : ${metrics.screen_time} min")
            Log.d("DataSyncWorker", "  Typing CPS     : ${metrics.typing_cps}")
            Log.d("DataSyncWorker", "  Typing Pauses  : ${metrics.typing_pauses_count}")
            Log.d("DataSyncWorker", "  Scroll Speed   : ${metrics.scrolling_speed_avg}")
            Log.d("DataSyncWorker", "  Erraticness    : ${metrics.scroll_erraticness}")
            Log.d("DataSyncWorker", "  App Switches   : ${metrics.session_count}")
            Log.d("DataSyncWorker", "  Night Usage    : ${metrics.night_usage} min")
            Log.d("DataSyncWorker", "  Social Time    : ${metrics.social_time} min")
            Log.d("DataSyncWorker", "--------------------------------------------")

            val response = api.submitDailyData(metrics)

            if (response.isSuccessful) {
                Log.d("DataSyncWorker", "✅ SUCCESS — Data sent to Firebase via FastAPI!")
                Log.d("DataSyncWorker", "============================================")
                Result.success()
            } else {
                Log.e("DataSyncWorker", "❌ FAILED — Backend responded: ${response.code()} ${response.errorBody()?.string()}")
                Log.d("DataSyncWorker", "============================================")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("DataSyncWorker", "❌ ERROR — Could not reach backend: ${e.message}")
            Log.d("DataSyncWorker", "  (Is the backend running? Check your API base URL)")
            Log.d("DataSyncWorker", "============================================")
            Result.retry()
        }
    }
}
