package com.jc01rho.youtubeautomaticuploader.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import com.jc01rho.youtubeautomaticuploader.R
import com.jc01rho.youtubeautomaticuploader.manager.UploadManager
import com.jc01rho.youtubeautomaticuploader.service.YouTubeService
import com.jc01rho.youtubeautomaticuploader.auth.GoogleAuthManager
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var uploadManager: UploadManager
    private lateinit var authManager: GoogleAuthManager
    private lateinit var youTubeService: YouTubeService
    
    private var selectedAccountName: String? = null
    private var selectedChannelId: String? = null
    private var selectedChannelTitle: String? = null
    private var availableChannels: List<YouTubeService.ChannelInfo> = emptyList()
    
    // UI Views
    private lateinit var tvAccountStatus: TextView
    private lateinit var btnSignIn: android.widget.Button
    private lateinit var etVideoDirectory: android.widget.EditText
    private lateinit var etSubtitleDirectory: android.widget.EditText
    private lateinit var etProcessedDirectory: android.widget.EditText
    private lateinit var etUploadInterval: android.widget.EditText
    private lateinit var spinnerPrivacy: android.widget.Spinner
    private lateinit var cbDeleteAfterUpload: android.widget.CheckBox
    private lateinit var btnStartAutoUpload: android.widget.Button
    private lateinit var btnStopAutoUpload: android.widget.Button
    private lateinit var btnUploadNow: android.widget.Button
    private lateinit var btnSelectChannel: android.widget.Button
    private lateinit var tvSelectedChannel: TextView
    private lateinit var layoutChannelSelection: android.widget.LinearLayout
    private lateinit var tvUploadStatus: TextView
    private lateinit var tvLogOutput: TextView

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            updateUI()
        } else {
            Toast.makeText(requireContext(), "Storage permissions are required", Toast.LENGTH_LONG).show()
        }
    }
    
    // Google Auth launcher
    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleAuthResult()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        val root = inflater.inflate(R.layout.fragment_home, container, false)
        
        initViews(root)
        uploadManager = UploadManager(requireContext())
        youTubeService = YouTubeService(requireContext())
        authManager = GoogleAuthManager(requireContext())
        setupGoogleAuth()
        setupUI()
        checkPermissions()
        
        return root
    }
    
    private fun initViews(root: View) {
        tvAccountStatus = root.findViewById(R.id.tv_account_status)
        btnSignIn = root.findViewById(R.id.btn_sign_in)
        etVideoDirectory = root.findViewById(R.id.et_video_directory)
        etSubtitleDirectory = root.findViewById(R.id.et_subtitle_directory)
        etProcessedDirectory = root.findViewById(R.id.et_processed_directory)
        etUploadInterval = root.findViewById(R.id.et_upload_interval)
        spinnerPrivacy = root.findViewById(R.id.spinner_privacy)
        cbDeleteAfterUpload = root.findViewById(R.id.cb_delete_after_upload)
        btnStartAutoUpload = root.findViewById(R.id.btn_start_auto_upload)
        btnStopAutoUpload = root.findViewById(R.id.btn_stop_auto_upload)
        btnUploadNow = root.findViewById(R.id.btn_upload_now)
        btnSelectChannel = root.findViewById(R.id.btn_select_channel)
        tvSelectedChannel = root.findViewById(R.id.tv_selected_channel)
        layoutChannelSelection = root.findViewById(R.id.layout_channel_selection)
        tvUploadStatus = root.findViewById(R.id.tv_upload_status)
        tvLogOutput = root.findViewById(R.id.tv_log_output)
    }
    
    private fun setupGoogleAuth() {
        // Check if already authenticated
        if (authManager.isAuthenticated()) {
            selectedAccountName = "authenticated_user" // We don't have email with OAuth2
            tvAccountStatus.text = "Authenticated"
            tvAccountStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
            btnSignIn.text = "Sign Out"
        } else {
            tvAccountStatus.text = "Not authenticated"
            tvAccountStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            btnSignIn.text = "Sign In with Google"
        }
        
        updateChannelUI()
    }
    
    private fun setupUI() {
        // Setup privacy status spinner
        val privacyOptions = arrayOf("private", "public", "unlisted")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, privacyOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPrivacy.adapter = adapter
        
        // Load saved configuration
        loadConfiguration()
        
        // Setup button listeners
        btnSignIn.setOnClickListener {
            if (authManager.isAuthenticated()) {
                signOut()
            } else {
                signIn()
            }
        }
        
        btnStartAutoUpload.setOnClickListener {
            startAutomaticUpload()
        }
        
        btnStopAutoUpload.setOnClickListener {
            stopAutomaticUpload()
        }
        
        btnUploadNow.setOnClickListener {
            uploadNow()
        }
        
        btnSelectChannel.setOnClickListener {
            selectChannel()
        }
        
        // Add confirmation dialog for delete option
        cbDeleteAfterUpload.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showDeleteConfirmationDialog()
            }
        }
        
        // Observe work status
        uploadManager.getWorkStatus().observe(viewLifecycleOwner) { workInfoList ->
            updateWorkStatus(workInfoList)
        }
        
        uploadManager.getImmediateUploadStatus().observe(viewLifecycleOwner) { workInfoList ->
            updateImmediateUploadStatus(workInfoList)
        }
        
        updateUI()
    }
    
    private fun loadConfiguration() {
        val config = uploadManager.loadConfiguration()
        if (config != null) {
            etVideoDirectory.setText(config.videoDirectory)
            etSubtitleDirectory.setText(config.subtitleDirectory)
            etProcessedDirectory.setText(config.processedDirectory)
            etUploadInterval.setText(config.uploadIntervalMinutes.toString())
            
            val privacyIndex = when (config.privacyStatus) {
                "private" -> 0
                "public" -> 1
                "unlisted" -> 2
                else -> 0
            }
            spinnerPrivacy.setSelection(privacyIndex)
            
            cbDeleteAfterUpload.isChecked = config.deleteAfterUpload
            
            // Load channel information
            selectedChannelId = config.selectedChannelId
            selectedChannelTitle = config.selectedChannelTitle
            updateChannelUI()
        }
    }
    
    private fun updateChannelUI() {
        if (selectedChannelId != null && selectedChannelTitle != null) {
            tvSelectedChannel.text = selectedChannelTitle
            tvSelectedChannel.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
            )
        } else {
            tvSelectedChannel.text = "No channel selected"
            tvSelectedChannel.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark)
            )
        }
    }
    
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.MANAGE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
            }
        }
        
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
    
    private fun signIn() {
        try {
            val authIntent = authManager.startAuthenticationFlow()
            authLauncher.launch(authIntent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to start authentication: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun signOut() {
        authManager.signOut()
        selectedAccountName = null
        selectedChannelId = null
        selectedChannelTitle = null
        tvAccountStatus.text = "Not authenticated"
        tvAccountStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
        btnSignIn.text = "Sign In with Google"
        updateChannelUI()
        updateUI()
    }
    
    private fun handleAuthResult() {
        // In a real implementation, you would handle the OAuth2 callback here
        // For now, show a message to the user about completing the OAuth2 flow
        
        Toast.makeText(requireContext(), 
            "Please complete the OAuth2 flow in the browser and then manually provide the authorization code", 
            Toast.LENGTH_LONG).show()
        
        // TODO: Implement proper OAuth2 callback handling
        // This could involve:
        // 1. Custom URI scheme handling
        // 2. Manual code entry dialog
        // 3. Deep link processing
        
        // For demonstration, we'll show a dialog for manual code entry
        showAuthCodeDialog()
    }
    
    private fun showAuthCodeDialog() {
        val editText = android.widget.EditText(requireContext())
        editText.hint = "Enter authorization code from browser"
        
        AlertDialog.Builder(requireContext())
            .setTitle("Enter Authorization Code")
            .setMessage("Please copy the authorization code from the browser and paste it here:")
            .setView(editText)
            .setPositiveButton("Authenticate") { _, _ ->
                val authCode = editText.text.toString().trim()
                if (authCode.isNotEmpty()) {
                    handleAuthorizationCode(authCode)
                } else {
                    Toast.makeText(requireContext(), "Please enter a valid authorization code", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun handleAuthorizationCode(authorizationCode: String) {
        lifecycleScope.launch {
            try {
                val result = authManager.handleAuthorizationCode(authorizationCode)
                result.onSuccess { _ ->
                    // Authentication successful
                    selectedAccountName = "authenticated_user"
                    tvAccountStatus.text = "Authenticated"
                    tvAccountStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
                    btnSignIn.text = "Sign Out"
                    
                    // Initialize YouTube service
                    val credentials = authManager.getCredentials()
                    if (credentials != null) {
                        youTubeService.initialize(credentials)
                        Toast.makeText(requireContext(), "Authentication successful!", Toast.LENGTH_SHORT).show()
                    }
                    
                    updateChannelUI()
                    updateUI()
                }.onFailure { _ ->
                    Toast.makeText(requireContext(), "Authentication failed", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun startAutomaticUpload() {
        if (!validateInputs()) return
        
        try {
            val config = createUploadConfig()
            uploadManager.startAutomaticUpload(config)
            appendLog("✅ Automatic upload started")
            updateUI()
        } catch (e: Exception) {
            appendLog("❌ Failed to start automatic upload: ${e.message}")
            Toast.makeText(requireContext(), "Failed to start: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun stopAutomaticUpload() {
        try {
            uploadManager.stopAutomaticUpload()
            appendLog("🛑 Automatic upload stopped")
            updateUI()
        } catch (e: Exception) {
            appendLog("❌ Failed to stop automatic upload: ${e.message}")
            Toast.makeText(requireContext(), "Failed to stop: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun uploadNow() {
        if (!validateInputs()) return
        
        try {
            val config = createUploadConfig()
            uploadManager.runUploadNow(config)
            appendLog("🚀 Immediate upload started")
        } catch (e: Exception) {
            appendLog("❌ Failed to start immediate upload: ${e.message}")
            Toast.makeText(requireContext(), "Failed to upload: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun validateInputs(): Boolean {
        if (selectedAccountName == null) {
            Toast.makeText(requireContext(), "Please sign in with Google first", Toast.LENGTH_LONG).show()
            return false
        }
        
        if (selectedChannelId == null) {
            Toast.makeText(requireContext(), "Please select a YouTube channel first", Toast.LENGTH_LONG).show()
            return false
        }
        
        if (etVideoDirectory.text.toString().trim().isEmpty()) {
            Toast.makeText(requireContext(), "Please enter video directory", Toast.LENGTH_LONG).show()
            return false
        }
        
        if (etSubtitleDirectory.text.toString().trim().isEmpty()) {
            Toast.makeText(requireContext(), "Please enter subtitle directory", Toast.LENGTH_LONG).show()
            return false
        }
        
        if (etProcessedDirectory.text.toString().trim().isEmpty()) {
            Toast.makeText(requireContext(), "Please enter processed directory", Toast.LENGTH_LONG).show()
            return false
        }
        
        return true
    }
    
    private fun createUploadConfig(): UploadManager.UploadConfig {
        val privacyStatus = when (spinnerPrivacy.selectedItemPosition) {
            0 -> "private"
            1 -> "public"
            2 -> "unlisted"
            else -> "private"
        }
        
        return UploadManager.UploadConfig(
            accountName = selectedAccountName!!,
            videoDirectory = etVideoDirectory.text.toString().trim(),
            subtitleDirectory = etSubtitleDirectory.text.toString().trim(),
            processedDirectory = etProcessedDirectory.text.toString().trim(),
            uploadIntervalMinutes = etUploadInterval.text.toString().toLongOrNull() ?: 60,
            privacyStatus = privacyStatus,
            categoryId = "22",
            deleteAfterUpload = cbDeleteAfterUpload.isChecked,
            selectedChannelId = selectedChannelId,
            selectedChannelTitle = selectedChannelTitle
        )
    }
    
    private fun updateUI() {
        val isRunning = uploadManager.isRunning()
        val isSignedIn = selectedAccountName != null
        val hasChannelSelected = selectedChannelId != null
        
        btnStartAutoUpload.isEnabled = isSignedIn && hasChannelSelected && !isRunning
        btnStopAutoUpload.isEnabled = isRunning
        btnUploadNow.isEnabled = isSignedIn && hasChannelSelected
        btnSelectChannel.isEnabled = isSignedIn
        
        // Show/hide channel selection layout
        layoutChannelSelection.visibility = if (isSignedIn) View.VISIBLE else View.GONE
        
        tvUploadStatus.text = if (isRunning) "Running" else "Stopped"
        tvUploadStatus.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (isRunning) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            )
        )
    }
    
    private fun updateWorkStatus(workInfoList: List<WorkInfo>) {
        if (workInfoList.isNotEmpty()) {
            val workInfo = workInfoList.first()
            when (workInfo.state) {
                WorkInfo.State.RUNNING -> {
                    appendLog("📤 Automatic upload in progress...")
                }
                WorkInfo.State.SUCCEEDED -> {
                    val resultMessage = workInfo.outputData.getString("result_message")
                    appendLog("✅ Upload completed: $resultMessage")
                }
                WorkInfo.State.FAILED -> {
                    val errorMessage = workInfo.outputData.getString("result_message")
                    appendLog("❌ Upload failed: $errorMessage")
                }
                else -> {
                    // Handle other states if needed
                }
            }
        }
    }
    
    private fun updateImmediateUploadStatus(workInfoList: List<WorkInfo>) {
        if (workInfoList.isNotEmpty()) {
            val workInfo = workInfoList.first()
            when (workInfo.state) {
                WorkInfo.State.RUNNING -> {
                    appendLog("🚀 Immediate upload in progress...")
                }
                WorkInfo.State.SUCCEEDED -> {
                    val resultMessage = workInfo.outputData.getString("result_message")
                    appendLog("✅ Immediate upload completed: $resultMessage")
                }
                WorkInfo.State.FAILED -> {
                    val errorMessage = workInfo.outputData.getString("result_message")
                    appendLog("❌ Immediate upload failed: $errorMessage")
                }
                else -> {
                    // Handle other states if needed
                }
            }
        }
    }
    
    private fun appendLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logMessage = "[$timestamp] $message\n"
        
        activity?.runOnUiThread {
            tvLogOutput.append(logMessage)
            
            // Auto-scroll to bottom
            tvLogOutput.parent?.let { parent ->
                if (parent is androidx.core.widget.NestedScrollView) {
                    parent.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
    }
    
    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Delete Files After Upload")
            .setMessage("Are you sure you want to delete original files after successful upload?\n\n" +
                    "This action cannot be undone. Your original video and subtitle files will be permanently deleted from your device.\n\n" +
                    "Consider using the 'Move to Processed Directory' option instead for safer file management.")
            .setPositiveButton("Yes, Delete Files") { _, _ ->
                // User confirmed, keep checkbox checked
                cbDeleteAfterUpload.isChecked = true
            }
            .setNegativeButton("No, Keep Files") { _, _ ->
                // User declined, uncheck the checkbox
                cbDeleteAfterUpload.isChecked = false
            }
            .setCancelable(false)
            .show()
    }

    private fun selectChannel() {
        if (selectedAccountName == null) {
            Toast.makeText(requireContext(), "Please sign in first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show loading
        btnSelectChannel.isEnabled = false
        btnSelectChannel.text = "Loading..."
        
        // Initialize YouTube service and get channels
        lifecycleScope.launch {
            try {
                val credentials = authManager.getCredentials()
                if (credentials != null) {
                    youTubeService.initialize(credentials)
                    val result = youTubeService.getChannelList()
                    
                    if (result.isSuccess) {
                        availableChannels = result.getOrNull() ?: emptyList()
                        if (availableChannels.isNotEmpty()) {
                            showChannelSelectionDialog()
                        } else {
                            Toast.makeText(requireContext(), "No YouTube channels found", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "Failed to load channels: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Authentication required", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error loading channels: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                btnSelectChannel.isEnabled = true
                btnSelectChannel.text = "Select Channel"
            }
        }
    }
    
    private fun showChannelSelectionDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_channel_selection, null)
        
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerView_channels)
        val adapter = ChannelAdapter(availableChannels) { selectedChannel ->
            // Channel selected
            selectedChannelId = selectedChannel.id
            selectedChannelTitle = selectedChannel.title
            updateChannelUI()
            
            // Save to preferences
            saveChannelSelection(selectedChannel.id, selectedChannel.title)
        }
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        
        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun saveChannelSelection(channelId: String, channelTitle: String) {
        val currentConfig = uploadManager.loadConfiguration()
        if (currentConfig != null) {
            val updatedConfig = currentConfig.copy(
                selectedChannelId = channelId,
                selectedChannelTitle = channelTitle
            )
            uploadManager.saveConfiguration(updatedConfig)
        }
    }

    // Channel Selection RecyclerView Adapter
    private class ChannelAdapter(
        private val channels: List<YouTubeService.ChannelInfo>,
        private val onChannelSelected: (YouTubeService.ChannelInfo) -> Unit
    ) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_channel, parent, false)
            return ChannelViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
            holder.bind(channels[position])
        }
        
        override fun getItemCount(): Int = channels.size
        
        inner class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val titleText: TextView = itemView.findViewById(R.id.tv_channel_title)
            private val statsText: TextView = itemView.findViewById(R.id.tv_channel_stats)
            private val descriptionText: TextView = itemView.findViewById(R.id.tv_channel_description)
            private val thumbnailImage: ImageView = itemView.findViewById(R.id.iv_channel_thumbnail)
            
            fun bind(channel: YouTubeService.ChannelInfo) {
                titleText.text = channel.title
                statsText.text = "${formatNumber(channel.subscriberCount)} subscribers • ${formatNumber(channel.videoCount)} videos"
                descriptionText.text = if (channel.description.isNotEmpty()) {
                    channel.description
                } else {
                    "No description"
                }
                
                // Set click listener
                itemView.setOnClickListener {
                    onChannelSelected(channel)
                }
                
                // TODO: Load thumbnail image using an image loading library like Glide or Picasso
                // For now, use default icon
            }
            
            private fun formatNumber(number: Long): String {
                return when {
                    number >= 1_000_000 -> "${(number / 1_000_000.0).let { "%.1f".format(it) }}M"
                    number >= 1_000 -> "${(number / 1_000.0).let { "%.1f".format(it) }}K"
                    else -> number.toString()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // No need to clean binding as we're using findViewById
    }
}
