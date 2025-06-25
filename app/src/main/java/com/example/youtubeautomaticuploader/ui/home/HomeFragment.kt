package com.example.youtubeautomaticuploader.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.work.WorkInfo
import com.example.youtubeautomaticuploader.R
import com.example.youtubeautomaticuploader.databinding.FragmentHomeBinding
import com.example.youtubeautomaticuploader.manager.UploadManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.youtube.YouTubeScopes

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var uploadManager: UploadManager
    private lateinit var googleSignInClient: GoogleSignInClient
    
    private var selectedAccountName: String? = null
    
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
            binding.tvAccountStatus.text = "Not signed in"
            binding.tvAccountStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            binding.btnSignIn.text = "Sign In with Google"
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
            categoryId = "22"
        )
    }
    
    private fun updateUI() {
        val isRunning = uploadManager.isRunning()
        val isSignedIn = selectedAccountName != null
        
        binding.btnStartAutoUpload.isEnabled = isSignedIn && !isRunning
        binding.btnStopAutoUpload.isEnabled = isRunning
        binding.btnUploadNow.isEnabled = isSignedIn
        
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}