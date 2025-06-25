package com.example.youtubeautomaticuploader.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileManagerService(private val context: Context) {
    
    companion object {
        private const val TAG = "FileManagerService"
    }
    
    data class VideoWithSubtitle(
        val videoFile: File,
        val subtitleFile: File? = null
    )
    
    /**
     * Scan directory for MP4 files and match with SRT files
     */
    suspend fun scanForVideosAndSubtitles(
        videoDirectory: String,
        subtitleDirectory: String
    ): List<VideoWithSubtitle> = withContext(Dispatchers.IO) {
        try {
            val videoDir = File(videoDirectory)
            val subtitleDir = File(subtitleDirectory)
            
            if (!videoDir.exists() || !videoDir.isDirectory) {
                Log.w(TAG, "Video directory does not exist: $videoDirectory")
                return@withContext emptyList()
            }
            
            // Get all MP4 files
            val videoFiles = videoDir.listFiles { _, name ->
                name.lowercase().endsWith(".mp4")
            }?.toList() ?: emptyList()
            
            // Get all SRT files if subtitle directory exists
            val subtitleFiles = if (subtitleDir.exists() && subtitleDir.isDirectory) {
                subtitleDir.listFiles { _, name ->
                    name.lowercase().endsWith(".srt")
                }?.associateBy { file ->
                    // Remove .srt extension to match with video files
                    file.nameWithoutExtension
                } ?: emptyMap()
            } else {
                Log.w(TAG, "Subtitle directory does not exist: $subtitleDirectory")
                emptyMap()
            }
            
            // Match video files with subtitle files
            val result = videoFiles.map { videoFile ->
                val videoBaseName = videoFile.nameWithoutExtension
                val matchingSubtitle = subtitleFiles[videoBaseName]
                
                VideoWithSubtitle(
                    videoFile = videoFile,
                    subtitleFile = matchingSubtitle
                )
            }
            
            Log.d(TAG, "Found ${result.size} video files, ${result.count { it.subtitleFile != null }} with subtitles")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning directories", e)
            emptyList()
        }
    }
    
    /**
     * Check if file is a valid MP4 video file
     */
    fun isValidVideoFile(file: File): Boolean {
        return file.exists() && 
               file.isFile && 
               file.canRead() && 
               file.name.lowercase().endsWith(".mp4") &&
               file.length() > 0
    }
    
    /**
     * Check if file is a valid SRT subtitle file
     */
    fun isValidSubtitleFile(file: File): Boolean {
        return file.exists() && 
               file.isFile && 
               file.canRead() && 
               file.name.lowercase().endsWith(".srt") &&
               file.length() > 0
    }
    
    /**
     * Get file size in MB
     */
    fun getFileSizeInMB(file: File): Double {
        return file.length() / (1024.0 * 1024.0)
    }
    
    /**
     * Generate title from filename
     */
    fun generateTitleFromFilename(filename: String): String {
        return filename
            .substringBeforeLast(".")
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { 
                    if (it.isLowerCase()) it.titlecase() else it.toString() 
                }
            }
    }
    
    /**
     * Generate description for video
     */
    fun generateDescription(
        originalFilename: String,
        fileSize: Double,
        hasSubtitle: Boolean
    ): String {
        return buildString {
            appendLine("Uploaded automatically from ${originalFilename}")
            appendLine()
            appendLine("File size: ${String.format("%.2f", fileSize)} MB")
            if (hasSubtitle) {
                appendLine("Includes subtitles")
            }
            appendLine()
            appendLine("Uploaded on: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
        }
    }
    
    /**
     * Move file to processed directory
     */
    suspend fun moveToProcessedDirectory(
        file: File,
        processedDirectory: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val processedDir = File(processedDirectory)
            if (!processedDir.exists()) {
                processedDir.mkdirs()
            }
            
            val destinationFile = File(processedDir, file.name)
            
            // If destination exists, add timestamp to avoid conflicts
            val finalDestination = if (destinationFile.exists()) {
                val timestamp = System.currentTimeMillis()
                val nameWithoutExt = file.nameWithoutExtension
                val extension = file.extension
                File(processedDir, "${nameWithoutExt}_${timestamp}.${extension}")
            } else {
                destinationFile
            }
            
            val success = file.renameTo(finalDestination)
            if (success) {
                Log.d(TAG, "Moved file to: ${finalDestination.absolutePath}")
                Result.success(finalDestination)
            } else {
                Log.e(TAG, "Failed to move file: ${file.absolutePath}")
                Result.failure(Exception("Failed to move file"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error moving file: ${file.absolutePath}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete file after successful upload
     */
    suspend fun deleteFile(file: File): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) {
                Log.w(TAG, "File does not exist: ${file.absolutePath}")
                return@withContext Result.success(true) // Consider as successful if file doesn't exist
            }
            
            val success = file.delete()
            if (success) {
                Log.d(TAG, "File deleted successfully: ${file.absolutePath}")
                Result.success(true)
            } else {
                Log.e(TAG, "Failed to delete file: ${file.absolutePath}")
                Result.failure(Exception("Failed to delete file: ${file.name}"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: ${file.absolutePath}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete multiple files
     */
    suspend fun deleteFiles(files: List<File>): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val deletedFiles = mutableListOf<String>()
            val failedFiles = mutableListOf<String>()
            
            files.forEach { file ->
                val result = deleteFile(file)
                if (result.isSuccess) {
                    deletedFiles.add(file.name)
                } else {
                    failedFiles.add(file.name)
                }
            }
            
            if (failedFiles.isEmpty()) {
                Log.d(TAG, "All files deleted successfully: ${deletedFiles.size} files")
                Result.success(deletedFiles)
            } else {
                Log.w(TAG, "Some files failed to delete. Success: ${deletedFiles.size}, Failed: ${failedFiles.size}")
                Result.failure(Exception("Failed to delete ${failedFiles.size} files: ${failedFiles.joinToString(", ")}"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting multiple files", e)
            Result.failure(e)
        }
    }
}
