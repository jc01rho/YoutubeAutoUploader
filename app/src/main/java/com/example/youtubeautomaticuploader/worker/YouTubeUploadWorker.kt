package com.example.youtubeautomaticuploader.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.youtubeautomaticuploader.service.FileManagerService
import com.example.youtubeautomaticuploader.service.YouTubeService
import kotlinx.coroutines.delay

class YouTubeUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "YouTubeUploadWorker"
        const val KEY_ACCOUNT_NAME = "account_name"
        const val KEY_VIDEO_DIRECTORY = "video_directory"
        const val KEY_SUBTITLE_DIRECTORY = "subtitle_directory"
        const val KEY_PROCESSED_DIRECTORY = "processed_directory"
        const val KEY_UPLOAD_INTERVAL_MINUTES = "upload_interval_minutes"
        const val KEY_PRIVACY_STATUS = "privacy_status"
        const val KEY_CATEGORY_ID = "category_id"
        
        // Output keys
        const val KEY_RESULT_MESSAGE = "result_message"
        const val KEY_UPLOADED_COUNT = "uploaded_count"
        const val KEY_FAILED_COUNT = "failed_count"
    }
    
    override suspend fun doWork(): Result {
        return try {
            val accountName = inputData.getString(KEY_ACCOUNT_NAME)
                ?: return Result.failure(workDataOf(KEY_RESULT_MESSAGE to "Account name not provided"))
            
            val videoDirectory = inputData.getString(KEY_VIDEO_DIRECTORY)
                ?: return Result.failure(workDataOf(KEY_RESULT_MESSAGE to "Video directory not provided"))
            
            val subtitleDirectory = inputData.getString(KEY_SUBTITLE_DIRECTORY)
                ?: return Result.failure(workDataOf(KEY_RESULT_MESSAGE to "Subtitle directory not provided"))
            
            val processedDirectory = inputData.getString(KEY_PROCESSED_DIRECTORY)
                ?: return Result.failure(workDataOf(KEY_RESULT_MESSAGE to "Processed directory not provided"))
            
            val privacyStatus = inputData.getString(KEY_PRIVACY_STATUS) ?: "private"
            val categoryId = inputData.getString(KEY_CATEGORY_ID) ?: "22"
            
            Log.d(TAG, "Starting upload work with account: $accountName")
            
            // Initialize services
            val youTubeService = YouTubeService(applicationContext)
            val fileManagerService = FileManagerService(applicationContext)
            
            // Initialize YouTube service
            youTubeService.initialize(accountName)
            
            // Scan for videos and subtitles
            val videosWithSubtitles = fileManagerService.scanForVideosAndSubtitles(
                videoDirectory,
                subtitleDirectory
            )
            
            if (videosWithSubtitles.isEmpty()) {
                Log.d(TAG, "No videos found to upload")
                return Result.success(workDataOf(
                    KEY_RESULT_MESSAGE to "No videos found to upload",
                    KEY_UPLOADED_COUNT to 0,
                    KEY_FAILED_COUNT to 0
                ))
            }
            
            Log.d(TAG, "Found ${videosWithSubtitles.size} videos to process")
            
            var uploadedCount = 0
            var failedCount = 0
            val results = mutableListOf<String>()
            
            // Process each video
            for ((index, videoWithSubtitle) in videosWithSubtitles.withIndex()) {
                try {
                    val videoFile = videoWithSubtitle.videoFile
                    val subtitleFile = videoWithSubtitle.subtitleFile
                    
                    // Validate video file
                    if (!fileManagerService.isValidVideoFile(videoFile)) {
                        Log.w(TAG, "Invalid video file: ${videoFile.name}")
                        failedCount++
                        results.add("❌ Invalid video file: ${videoFile.name}")
                        continue
                    }
                    
                    // Generate metadata
                    val title = fileManagerService.generateTitleFromFilename(videoFile.name)
                    val fileSize = fileManagerService.getFileSizeInMB(videoFile)
                    val description = fileManagerService.generateDescription(
                        videoFile.name,
                        fileSize,
                        subtitleFile != null
                    )
                    
                    Log.d(TAG, "Uploading video ${index + 1}/${videosWithSubtitles.size}: ${videoFile.name}")
                    
                    // Upload video
                    val uploadResult = youTubeService.uploadVideo(
                        videoFile = videoFile,
                        title = title,
                        description = description,
                        tags = listOf("auto-upload", "video"),
                        categoryId = categoryId,
                        privacyStatus = privacyStatus
                    )
                    
                    if (uploadResult.isSuccess) {
                        val videoId = uploadResult.getOrNull()!!
                        Log.d(TAG, "Video uploaded successfully: $videoId")
                        
                        // Upload subtitle if available
                        if (subtitleFile != null && fileManagerService.isValidSubtitleFile(subtitleFile)) {
                            Log.d(TAG, "Uploading subtitle for video: $videoId")
                            val subtitleResult = youTubeService.uploadSubtitle(
                                videoId = videoId,
                                subtitleFile = subtitleFile,
                                language = "en",
                                name = "Auto-generated subtitle"
                            )
                            
                            if (subtitleResult.isSuccess) {
                                Log.d(TAG, "Subtitle uploaded successfully")
                                results.add("✅ ${videoFile.name} (with subtitle)")
                            } else {
                                Log.w(TAG, "Failed to upload subtitle: ${subtitleResult.exceptionOrNull()?.message}")
                                results.add("✅ ${videoFile.name} (subtitle failed)")
                            }
                        } else {
                            results.add("✅ ${videoFile.name}")
                        }
                        
                        // Move video to processed directory
                        fileManagerService.moveToProcessedDirectory(videoFile, processedDirectory)
                        
                        // Move subtitle to processed directory if exists
                        subtitleFile?.let { 
                            fileManagerService.moveToProcessedDirectory(it, processedDirectory)
                        }
                        
                        uploadedCount++
                        
                    } else {
                        val error = uploadResult.exceptionOrNull()?.message ?: "Unknown error"
                        Log.e(TAG, "Failed to upload video: ${videoFile.name}, Error: $error")
                        failedCount++
                        results.add("❌ ${videoFile.name}: $error")
                    }
                    
                    // Add delay between uploads to avoid rate limiting
                    if (index < videosWithSubtitles.size - 1) {
                        delay(5000) // 5 seconds delay
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing video: ${videoWithSubtitle.videoFile.name}", e)
                    failedCount++
                    results.add("❌ ${videoWithSubtitle.videoFile.name}: ${e.message}")
                }
            }
            
            val resultMessage = buildString {
                appendLine("Upload completed!")
                appendLine("✅ Uploaded: $uploadedCount")
                appendLine("❌ Failed: $failedCount")
                appendLine()
                appendLine("Details:")
                results.forEach { result ->
                    appendLine(result)
                }
            }
            
            Log.d(TAG, "Upload work completed. Uploaded: $uploadedCount, Failed: $failedCount")
            
            Result.success(workDataOf(
                KEY_RESULT_MESSAGE to resultMessage,
                KEY_UPLOADED_COUNT to uploadedCount,
                KEY_FAILED_COUNT to failedCount
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "Upload work failed", e)
            Result.failure(workDataOf(
                KEY_RESULT_MESSAGE to "Upload failed: ${e.message}"
            ))
        }
    }
}
