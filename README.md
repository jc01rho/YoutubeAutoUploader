# YouTube Auto Uploader

An Android application that automatically uploads MP4 video files and SRT subtitle files to YouTube using the YouTube Data API v3.

## Features

- 🎥 **Automatic Video Upload**: Monitors designated directories for MP4 files and uploads them to YouTube
- 📝 **Subtitle Support**: Automatically matches and uploads SRT subtitle files with videos
- 🔄 **Background Processing**: Uses WorkManager for reliable background uploads
- 📱 **User-friendly Interface**: Simple UI to configure directories and upload settings
- 🔐 **Google Authentication**: Secure OAuth2 authentication with Google
- 📊 **Upload History**: Track upload status and results
- 🔧 **Configurable Settings**: Set upload intervals, privacy settings, and directories

## Setup Instructions

### 1. Google Cloud Console Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the YouTube Data API v3
4. Create credentials (OAuth 2.0 client ID) for Android application
5. Add your app's SHA-1 fingerprint to the credentials

### 2. Configure the App

1. Clone this repository
2. Open the project in Android Studio
3. Add your Google OAuth2 configuration (if needed, create `google-services.json`)
4. Build and install the app on your Android device

### 3. App Configuration

1. **Sign in with Google**: Use your Google account that has access to YouTube
2. **Set Directories**:
   - Video Directory: Path where MP4 files are stored
   - Subtitle Directory: Path where SRT files are stored
   - Processed Directory: Path where uploaded files will be moved
3. **Configure Upload Settings**:
   - Upload Interval: How often to check for new files (in minutes)
   - Privacy Status: private, public, or unlisted
4. **Start Auto Upload**: Enable automatic background uploading

## File Matching

The app automatically matches video files with subtitle files based on filename:
- `video.mp4` will be matched with `video.srt`
- Files without matching subtitles will be uploaded without captions

## Permissions Required

- **Internet**: For YouTube API communication
- **Storage**: To read MP4 and SRT files from device storage
- **Network State**: To check connectivity before uploading
- **Wake Lock**: To keep device awake during uploads

## Dependencies

- YouTube Data API v3
- Google Play Services Auth
- Android WorkManager
- Kotlin Coroutines
- Google HTTP Client

## Architecture

- **Service Layer**: `YouTubeService` handles API communication
- **File Management**: `FileManagerService` handles file operations
- **Background Work**: `YouTubeUploadWorker` processes uploads
- **UI Layer**: Fragments for user interaction

## Troubleshooting

### Common Issues

1. **Authentication Failed**
   - Ensure your SHA-1 fingerprint is added to Google Cloud Console
   - Check if YouTube Data API is enabled
   - Verify OAuth2 credentials are properly configured

2. **Upload Failed**
   - Check internet connectivity
   - Verify file permissions
   - Ensure video files are valid MP4 format

3. **Files Not Found**
   - Check directory paths are correct
   - Verify storage permissions are granted
   - Ensure files exist in specified directories

### Logs

Check the app's log output in the Status section for detailed error messages and upload progress.

## Privacy and Security

- All authentication is handled through Google's OAuth2 system
- No credentials are stored locally
- Videos are uploaded directly to your YouTube channel
- App only requests necessary permissions

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Disclaimer

This app is not affiliated with YouTube or Google. Use at your own risk and ensure you comply with YouTube's Terms of Service and Community Guidelines.
