package com.example.youtubeautomaticuploader.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.*
import com.example.youtubeautomaticuploader.worker.YouTubeUploadWorker
import java.util.concurrent.TimeUnit

class UploadManager(private val context: Context) {
    
    companion object {
        private const val TAG = "UploadManager"
        private const val WORK_NAME = "youtube_upload_work"
        private const val PREFS_NAME = "upload_manager_prefs"
        private const val KEY_IS_RUNNING = "is_running"
        private const val KEY_ACCOUNT_NAME = "account_name"
        private const val KEY_VIDEO_DIRECTORY = "video_directory"
        private const val KEY_SUBTITLE_DIRECTORY = "subtitle_directory"
        private const val KEY_PROCESSED_DIRECTORY = "processed_directory"
        private const val KEY_UPLOAD_INTERVAL = "upload_interval"
        private const val KEY_PRIVACY_STATUS = "privacy_status"
        private const val KEY_CATEGORY_ID = "category_id"
    }
    
    private val workManager = WorkManager.getInstance(context)
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    data class UploadConfig(
        val accountName: String,
        val videoDirectory: String,
        val subtitleDirectory: String,
        val processedDirectory: String,
        val uploadIntervalMinutes: Long = 60, // Default 1 hour
        val privacyStatus: String = "private", // private, public, unlisted
        val categoryId: String = "22" // People & Blogs
    )
    
    /**
     * Start automatic upload with the given configuration
     */
    fun startAutomaticUpload(config: UploadConfig) {
        try {
            Log.d(TAG, "Starting automatic upload with interval: ${config.uploadIntervalMinutes} minutes")
            
            // Save configuration
            saveConfiguration(config)
            
            // Create work request
            val workRequest = PeriodicWorkRequestBuilder<YouTubeUploadWorker>(
                config.uploadIntervalMinutes,
                TimeUnit.MINUTES
            ).apply {
                setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                
                setInputData(
                    workDataOf(
                        YouTubeUploadWorker.KEY_ACCOUNT_NAME to config.accountName,
                        YouTubeUploadWorker.KEY_VIDEO_DIRECTORY to config.videoDirectory,
                        YouTubeUploadWorker.KEY_SUBTITLE_DIRECTORY to config.subtitleDirectory,
                        YouTubeUploadWorker.KEY_PROCESSED_DIRECTORY to config.processedDirectory,
                        YouTubeUploadWorker.KEY_UPLOAD_INTERVAL_MINUTES to config.uploadIntervalMinutes,
                        YouTubeUploadWorker.KEY_PRIVACY_STATUS to config.privacyStatus,
                        YouTubeUploadWorker.KEY_CATEGORY_ID to config.categoryId
                    )
                )
                
                setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.MINUTES
                )
                
                addTag(WORK_NAME)
            }.build()
            
            // Enqueue work
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
            
            // Mark as running
            sharedPrefs.edit().putBoolean(KEY_IS_RUNNING, true).apply()
            
            Log.d(TAG, "Automatic upload started successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start automatic upload", e)
            throw e
        }
    }
    
    /**
     * Stop automatic upload
     */
    fun stopAutomaticUpload() {
        try {
            workManager.cancelUniqueWork(WORK_NAME)
            sharedPrefs.edit().putBoolean(KEY_IS_RUNNING, false).apply()
            Log.d(TAG, "Automatic upload stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop automatic upload", e)
            throw e
        }
    }
    
    /**
     * Run upload immediately (one-time)
     */
    fun runUploadNow(config: UploadConfig) {
        try {
            Log.d(TAG, "Running immediate upload")
            
            val workRequest = OneTimeWorkRequestBuilder<YouTubeUploadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(
                    workDataOf(
                        YouTubeUploadWorker.KEY_ACCOUNT_NAME to config.accountName,
                        YouTubeUploadWorker.KEY_VIDEO_DIRECTORY to config.videoDirectory,
                        YouTubeUploadWorker.KEY_SUBTITLE_DIRECTORY to config.subtitleDirectory,
                        YouTubeUploadWorker.KEY_PROCESSED_DIRECTORY to config.processedDirectory,
                        YouTubeUploadWorker.KEY_PRIVACY_STATUS to config.privacyStatus,
                        YouTubeUploadWorker.KEY_CATEGORY_ID to config.categoryId
                    )
                )
                .addTag("immediate_upload")
                .build()
            
            workManager.enqueue(workRequest)
            
            Log.d(TAG, "Immediate upload queued")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to run immediate upload", e)
            throw e
        }
    }
    
    /**
     * Check if automatic upload is running
     */
    fun isRunning(): Boolean {
        return sharedPrefs.getBoolean(KEY_IS_RUNNING, false)
    }
    
    /**
     * Get work status
     */
    fun getWorkStatus(): androidx.lifecycle.LiveData<List<WorkInfo>> {
        return workManager.getWorkInfosByTagLiveData(WORK_NAME)
    }
    
    /**
     * Get immediate upload status
     */
    fun getImmediateUploadStatus(): androidx.lifecycle.LiveData<List<WorkInfo>> {
        return workManager.getWorkInfosByTagLiveData("immediate_upload")
    }
    
    /**
     * Save configuration to SharedPreferences
     */
    private fun saveConfiguration(config: UploadConfig) {
        sharedPrefs.edit().apply {
            putString(KEY_ACCOUNT_NAME, config.accountName)
            putString(KEY_VIDEO_DIRECTORY, config.videoDirectory)
            putString(KEY_SUBTITLE_DIRECTORY, config.subtitleDirectory)
            putString(KEY_PROCESSED_DIRECTORY, config.processedDirectory)
            putLong(KEY_UPLOAD_INTERVAL, config.uploadIntervalMinutes)
            putString(KEY_PRIVACY_STATUS, config.privacyStatus)
            putString(KEY_CATEGORY_ID, config.categoryId)
            apply()
        }
    }
    
    /**
     * Load configuration from SharedPreferences
     */
    fun loadConfiguration(): UploadConfig? {
        val accountName = sharedPrefs.getString(KEY_ACCOUNT_NAME, null)
        val videoDirectory = sharedPrefs.getString(KEY_VIDEO_DIRECTORY, null)
        val subtitleDirectory = sharedPrefs.getString(KEY_SUBTITLE_DIRECTORY, null)
        val processedDirectory = sharedPrefs.getString(KEY_PROCESSED_DIRECTORY, null)
        
        return if (accountName != null && videoDirectory != null && 
                   subtitleDirectory != null && processedDirectory != null) {
            UploadConfig(
                accountName = accountName,
                videoDirectory = videoDirectory,
                subtitleDirectory = subtitleDirectory,
                processedDirectory = processedDirectory,
                uploadIntervalMinutes = sharedPrefs.getLong(KEY_UPLOAD_INTERVAL, 60),
                privacyStatus = sharedPrefs.getString(KEY_PRIVACY_STATUS, "private") ?: "private",
                categoryId = sharedPrefs.getString(KEY_CATEGORY_ID, "22") ?: "22"
            )
        } else {
            null
        }
    }
    
    /**
     * Clear all configuration
     */
    fun clearConfiguration() {
        sharedPrefs.edit().clear().apply()
    }
}
