package com.example.youtubeautomaticuploader.ui.home

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
import com.example.youtubeautomaticuploader.R
import com.example.youtubeautomaticuploader.databinding.FragmentHomeBinding
import com.example.youtubeautomaticuploader.manager.UploadManager
import com.example.youtubeautomaticuploader.service.YouTubeService
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.youtube.YouTubeScopes
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var uploadManager: UploadManager
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var youTubeService: YouTubeService
    
    private var selectedAccountName: String? = null
    private var selectedChannelId: String? = null
    private var selectedChannelTitle: String? = null
    private var availableChannels: List<YouTubeService.ChannelInfo> = emptyList()

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
    
    // Google Sign-In launcher
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleSignInResult(result.data)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        
        uploadManager = UploadManager(requireContext())
        youTubeService = YouTubeService(requireContext())
        setupGoogleSignIn()
        setupUI()
        checkPermissions()
        
        return binding.root
    }
    
    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(YouTubeScopes.YOUTUBE_UPLOAD))
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
        
        // Check if already signed in
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account != null) {
            selectedAccountName = account.email
            binding.tvAccountStatus.text = "Signed in as: ${account.email}"
            binding.tvAccountStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
            binding.btnSignIn.text = "Sign Out"
        }
        
        updateChannelUI()
    }
    
    private fun setupUI() {
        // Setup privacy status spinner
        val privacyOptions = arrayOf("private", "public", "unlisted")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, privacyOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPrivacy.adapter = adapter
        
        // Load saved configuration
        loadConfiguration()
        
        // Setup button listeners
        binding.btnSignIn.setOnClickListener {
            if (selectedAccountName != null) {
                signOut()
            } else {
                signIn()
            }
        }
        
        binding.btnStartAutoUpload.setOnClickListener {
            startAutomaticUpload()
        }
        
        binding.btnStopAutoUpload.setOnClickListener {
            stopAutomaticUpload()
        }
        
        binding.btnUploadNow.setOnClickListener {
            uploadNow()
        }
        
        binding.btnSelectChannel.setOnClickListener {
            selectChannel()
        }
        
        // Add confirmation dialog for delete option
        binding.cbDeleteAfterUpload.setOnCheckedChangeListener { _, isChecked ->
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
            binding.etVideoDirectory.setText(config.videoDirectory)
            binding.etSubtitleDirectory.setText(config.subtitleDirectory)
            binding.etProcessedDirectory.setText(config.processedDirectory)
            binding.etUploadInterval.setText(config.uploadIntervalMinutes.toString())
            
            val privacyIndex = when (config.privacyStatus) {
                "private" -> 0
                "public" -> 1
                "unlisted" -> 2
                else -> 0
            }
            binding.spinnerPrivacy.setSelection(privacyIndex)
            
            binding.cbDeleteAfterUpload.isChecked = config.deleteAfterUpload
            
            // Load channel information
            selectedChannelId = config.selectedChannelId
            selectedChannelTitle = config.selectedChannelTitle
            updateChannelUI()
        }
    }
    
    private fun updateChannelUI() {
        if (selectedChannelId != null && selectedChannelTitle != null) {
            binding.tvSelectedChannel.text = selectedChannelTitle
            binding.tvSelectedChannel.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
            )
        } else {
            binding.tvSelectedChannel.text = "No channel selected"
            binding.tvSelectedChannel.setTextColor(
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
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }
    
    private fun signOut() {
        googleSignInClient.signOut().addOnCompleteListener {
            selectedAccountName = null
            selectedChannelId = null
            selectedChannelTitle = null
            binding.tvAccountStatus.text = "Not signed in"
            binding.tvAccountStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            binding.btnSignIn.text = "Sign In with Google"
            updateChannelUI()
            updateUI()
        }
    }
    
    private fun handleSignInResult(data: android.content.Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.result
            
            selectedAccountName = account.email
            binding.tvAccountStatus.text = "Signed in as: ${account.email}"
            binding.tvAccountStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
            binding.btnSignIn.text = "Sign Out"
            updateChannelUI()
            updateUI()
            
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Sign in failed: ${e.message}", Toast.LENGTH_LONG).show()
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
        
        if (binding.etVideoDirectory.text.toString().trim().isEmpty()) {
            Toast.makeText(requireContext(), "Please enter video directory", Toast.LENGTH_LONG).show()
            return false
        }
        
        if (binding.etSubtitleDirectory.text.toString().trim().isEmpty()) {
            Toast.makeText(requireContext(), "Please enter subtitle directory", Toast.LENGTH_LONG).show()
            return false
        }
        
        if (binding.etProcessedDirectory.text.toString().trim().isEmpty()) {
            Toast.makeText(requireContext(), "Please enter processed directory", Toast.LENGTH_LONG).show()
            return false
        }
        
        return true
    }
    
    private fun createUploadConfig(): UploadManager.UploadConfig {
        val privacyStatus = when (binding.spinnerPrivacy.selectedItemPosition) {
            0 -> "private"
            1 -> "public"
            2 -> "unlisted"
            else -> "private"
        }
        
        return UploadManager.UploadConfig(
            accountName = selectedAccountName!!,
            videoDirectory = binding.etVideoDirectory.text.toString().trim(),
            subtitleDirectory = binding.etSubtitleDirectory.text.toString().trim(),
            processedDirectory = binding.etProcessedDirectory.text.toString().trim(),
            uploadIntervalMinutes = binding.etUploadInterval.text.toString().toLongOrNull() ?: 60,
            privacyStatus = privacyStatus,
            categoryId = "22",
            deleteAfterUpload = binding.cbDeleteAfterUpload.isChecked,
            selectedChannelId = selectedChannelId,
            selectedChannelTitle = selectedChannelTitle
        )
    }
    
    private fun updateUI() {
        val isRunning = uploadManager.isRunning()
        val isSignedIn = selectedAccountName != null
        val hasChannelSelected = selectedChannelId != null
        
        binding.btnStartAutoUpload.isEnabled = isSignedIn && hasChannelSelected && !isRunning
        binding.btnStopAutoUpload.isEnabled = isRunning
        binding.btnUploadNow.isEnabled = isSignedIn && hasChannelSelected
        binding.btnSelectChannel.isEnabled = isSignedIn
        
        // Show/hide channel selection layout
        binding.layoutChannelSelection.visibility = if (isSignedIn) View.VISIBLE else View.GONE
        
        binding.tvUploadStatus.text = if (isRunning) "Running" else "Stopped"
        binding.tvUploadStatus.setTextColor(
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
            binding.tvLogOutput.append(logMessage)
            
            // Auto-scroll to bottom
            binding.tvLogOutput.parent?.let { parent ->
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
                binding.cbDeleteAfterUpload.isChecked = true
            }
            .setNegativeButton("No, Keep Files") { _, _ ->
                // User declined, uncheck the checkbox
                binding.cbDeleteAfterUpload.isChecked = false
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
        binding.btnSelectChannel.isEnabled = false
        binding.btnSelectChannel.text = "Loading..."
        
        // Initialize YouTube service and get channels
        lifecycleScope.launch {
            try {
                youTubeService.initialize(selectedAccountName!!)
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
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error loading channels: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSelectChannel.isEnabled = true
                binding.btnSelectChannel.text = "Select Channel"
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
        _binding = null
    }
}
