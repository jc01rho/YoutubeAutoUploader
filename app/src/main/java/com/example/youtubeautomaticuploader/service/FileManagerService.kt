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
     * Generate title from SRT file or filename
     * Priority: SRT file content (address + time) > filename
     */
    suspend fun generateTitle(videoFile: File, subtitleFile: File?): String {
        // First try to extract title from SRT file
        if (subtitleFile != null && isValidSubtitleFile(subtitleFile)) {
            val srtTitle = parseSrtForTitle(subtitleFile)
            if (srtTitle != null) {
                Log.d(TAG, "Using title from SRT: $srtTitle")
                return srtTitle
            }
        }
        
        // Fallback to filename-based title
        val filenameTitle = generateTitleFromFilename(videoFile.name)
        Log.d(TAG, "Using title from filename: $filenameTitle")
        return filenameTitle
    }
    
    /**
     * Generate title from filename (fallback method)
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
    
    /**
     * Parse SRT file to extract title information from address and time
     */
    suspend fun parseSrtForTitle(srtFile: File): String? = withContext(Dispatchers.IO) {
        try {
            if (!isValidSubtitleFile(srtFile)) {
                Log.w(TAG, "Invalid SRT file: ${srtFile.name}")
                return@withContext null
            }
            
            val content = srtFile.readText(Charsets.UTF_8)
            val lines = content.lines()
            
            var address: String? = null
            var time: String? = null
            
            // Find the first occurrence of "주소:" and "시간:" in the entire content
            // Process line by line and also check within combined content
            val cleanContent = content.replace("<[^>]*>".toRegex(), "") // Remove all HTML tags
            
            // Try to find in combined content first (for multi-line entries)
            if (address == null) {
                val addressMatch = "주소:\\s*([^\\n\\r]+)".toRegex().find(cleanContent)
                if (addressMatch != null) {
                    address = addressMatch.groupValues[1].trim()
                    Log.d(TAG, "Found address in content: $address")
                }
            }
            
            if (time == null) {
                val timeMatch = "시간:\\s*([^\\n\\r]+)".toRegex().find(cleanContent)
                if (timeMatch != null) {
                    val fullTime = timeMatch.groupValues[1].trim()
                    time = formatTimeForTitle(fullTime)
                    Log.d(TAG, "Found time in content: $time")
                }
            }
            
            // If not found in combined content, try line by line (fallback)
            if (address == null || time == null) {
                for (i in lines.indices) {
                    var currentLine = lines[i].trim()
                    
                    // Remove HTML tags from current line
                    currentLine = currentLine.replace("<[^>]*>".toRegex(), "")
                    
                    if (address == null && currentLine.contains("주소:")) {
                        val addressMatch = "주소:\\s*(.+)".toRegex().find(currentLine)
                        if (addressMatch != null) {
                            address = addressMatch.groupValues[1].trim()
                            Log.d(TAG, "Found address in line: $address")
                        }
                    }
                    
                    if (time == null && currentLine.contains("시간:")) {
                        val timeMatch = "시간:\\s*(.+)".toRegex().find(currentLine)
                        if (timeMatch != null) {
                            val fullTime = timeMatch.groupValues[1].trim()
                            time = formatTimeForTitle(fullTime)
                            Log.d(TAG, "Found time in line: $time")
                        }
                    }
                    
                    // If both are found, break early
                    if (address != null && time != null) {
                        break
                    }
                }
            }
            
            // Create title in format: "address, time"
            return@withContext when {
                address != null && time != null -> {
                    val title = "$address, $time"
                    Log.d(TAG, "Generated title from SRT: $title")
                    title
                }
                address != null -> {
                    Log.d(TAG, "Generated title with address only: $address")
                    address
                }
                time != null -> {
                    Log.d(TAG, "Generated title with time only: $time")
                    time
                }
                else -> {
                    Log.w(TAG, "No address or time found in SRT file: ${srtFile.name}")
                    null
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing SRT file: ${srtFile.name}", e)
            null
        }
    }
    
    /**
     * Format time string for title
     * Input: "2025. 5. 18. 17시 53분 8초"
     * Output: "2025. 5. 18. 17시 53분"
     */
    private fun formatTimeForTitle(timeString: String): String {
        return try {
            // Remove seconds part (anything after the last "분")
            val minuteIndex = timeString.lastIndexOf("분")
            if (minuteIndex > 0) {
                timeString.substring(0, minuteIndex + 1)
            } else {
                timeString
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to format time string: $timeString", e)
            timeString
        }
    }
}
