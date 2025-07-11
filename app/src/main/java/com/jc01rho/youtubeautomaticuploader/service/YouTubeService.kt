package com.jc01rho.youtubeautomaticuploader.service

import android.content.Context
import android.util.Log
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.http.HttpCredentialsAdapter
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.Video
import com.google.api.services.youtube.model.VideoSnippet
import com.google.api.services.youtube.model.VideoStatus
import com.google.api.client.http.FileContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class YouTubeService(private val context: Context) {
    
    companion object {
        private const val TAG = "YouTubeService"
        private const val APPLICATION_NAME = "YouTube Auto Uploader"
    }
    
    private var youTube: YouTube? = null
    private var credentials: GoogleCredentials? = null
    
    /**
     * Initialize YouTube service with Google Cloud credentials
     */
    fun initialize(googleCredentials: GoogleCredentials) {
        try {
            credentials = googleCredentials
            
            val transport = GoogleNetHttpTransport.newTrustedTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()
            val httpCredentialsAdapter = HttpCredentialsAdapter(googleCredentials)
            
            youTube = YouTube.Builder(transport, jsonFactory, httpCredentialsAdapter)
                .setApplicationName(APPLICATION_NAME)
                .build()
                
            Log.d(TAG, "YouTube service initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize YouTube service", e)
            throw e
        }
    }
    
    /**
     * Upload video to YouTube
     */
    suspend fun uploadVideo(
        videoFile: File,
        title: String,
        description: String,
        tags: List<String> = emptyList(),
        categoryId: String = "22", // People & Blogs
        privacyStatus: String = "private", // private, public, unlisted
        channelId: String? = null // Optional channel ID for multi-channel accounts
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val youTubeService = youTube ?: throw IllegalStateException("YouTube service not initialized")
            
            // Create video object
            val video = Video().apply {
                snippet = VideoSnippet().apply {
                    this.title = title
                    this.description = description
                    this.tags = tags
                    this.categoryId = categoryId
                    // Set channel ID if provided for multi-channel accounts
                    channelId?.let { this.channelId = it }
                }
                status = VideoStatus().apply {
                    this.privacyStatus = privacyStatus
                }
            }
            
            // Create media content
            val mediaContent = FileContent("video/mp4", videoFile)
            
            // Execute upload
            val videoInsert = youTubeService.videos()
                .insert(listOf("snippet", "status"), video, mediaContent)
            
            // Set upload progress listener
            videoInsert.mediaHttpUploader.apply {
                isDirectUploadEnabled = false
                setProgressListener { uploader ->
                    when (uploader.uploadState) {
                        com.google.api.client.googleapis.media.MediaHttpUploader.UploadState.INITIATION_STARTED -> {
                            Log.d(TAG, "Upload initiation started for: ${videoFile.name}")
                        }
                        com.google.api.client.googleapis.media.MediaHttpUploader.UploadState.MEDIA_IN_PROGRESS -> {
                            Log.d(TAG, "Upload progress: ${(uploader.progress * 100).toInt()}% for ${videoFile.name}")
                        }
                        com.google.api.client.googleapis.media.MediaHttpUploader.UploadState.MEDIA_COMPLETE -> {
                            Log.d(TAG, "Upload completed for: ${videoFile.name}")
                        }
                        else -> {
                            Log.d(TAG, "Upload state: ${uploader.uploadState} for ${videoFile.name}")
                        }
                    }
                }
            }
            
            val uploadedVideo = videoInsert.execute()
            Log.d(TAG, "Video uploaded successfully. Video ID: ${uploadedVideo.id}")
            
            Result.success(uploadedVideo.id)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload video: ${videoFile.name}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Upload subtitle/caption file to YouTube video
     */
    suspend fun uploadSubtitle(
        videoId: String,
        subtitleFile: File,
        language: String = "en",
        name: String = "Subtitle"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val youTubeService = youTube ?: throw IllegalStateException("YouTube service not initialized")
            
            val caption = com.google.api.services.youtube.model.Caption().apply {
                snippet = com.google.api.services.youtube.model.CaptionSnippet().apply {
                    this.videoId = videoId
                    this.language = language
                    this.name = name
                    this.isDraft = false
                }
            }
            
            val mediaContent = FileContent("text/plain", subtitleFile)
            
            val captionInsert = youTubeService.captions()
                .insert(listOf("snippet"), caption, mediaContent)
            
            val uploadedCaption = captionInsert.execute()
            Log.d(TAG, "Subtitle uploaded successfully. Caption ID: ${uploadedCaption.id}")
            
            Result.success(uploadedCaption.id)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload subtitle for video: $videoId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get list of YouTube channels for the authenticated user
     */
    suspend fun getChannelList(): Result<List<ChannelInfo>> = withContext(Dispatchers.IO) {
        try {
            val youTubeService = youTube ?: throw IllegalStateException("YouTube service not initialized")
            
            val channelsRequest = youTubeService.channels()
                .list(listOf("snippet", "contentDetails", "statistics"))
                .setMine(true)
            
            val channelsResponse = channelsRequest.execute()
            val channels = channelsResponse.items
            
            val channelInfoList = channels.map { channel ->
                ChannelInfo(
                    id = channel.id,
                    title = channel.snippet.title,
                    description = channel.snippet.description ?: "",
                    thumbnailUrl = channel.snippet.thumbnails?.default?.url ?: "",
                    subscriberCount = channel.statistics?.subscriberCount?.toLong() ?: 0L,
                    videoCount = channel.statistics?.videoCount?.toLong() ?: 0L
                )
            }
            
            Log.d(TAG, "Found ${channelInfoList.size} channels")
            Result.success(channelInfoList)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get channel list", e)
            Result.failure(e)
        }
    }
    
    /**
     * Set the selected channel for uploads
     */
    fun setSelectedChannel(channelId: String) {
        // Store the selected channel ID for future uploads
        // This will be used when uploading videos
        Log.d(TAG, "Selected channel ID: $channelId")
    }
    
    data class ChannelInfo(
        val id: String,
        val title: String,
        val description: String,
        val thumbnailUrl: String,
        val subscriberCount: Long,
        val videoCount: Long
    )

    /**
     * Check if service is initialized and ready
     */
    fun isInitialized(): Boolean {
        return youTube != null && credentials != null
    }
}
