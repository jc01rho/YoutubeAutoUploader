package com.jc01rho.youtubeautomaticuploader

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import com.jc01rho.youtubeautomaticuploader.auth.GoogleAuthManager
import com.jc01rho.youtubeautomaticuploader.service.YouTubeService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var authManager: GoogleAuthManager
    private lateinit var youTubeService: YouTubeService

    // Activity Result API for handling authentication
    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Handle authentication result
        Toast.makeText(this, "Please complete OAuth2 flow and provide authorization code", Toast.LENGTH_LONG).show()
        
        // Example: Handle authorization code (you'll need to implement this)
        // handleAuthorizationCode("AUTHORIZATION_CODE_FROM_CALLBACK")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize auth manager and YouTube service
        authManager = GoogleAuthManager(this)
        youTubeService = YouTubeService(this)

        setSupportActionBar(findViewById(R.id.toolbar))

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab)?.setOnClickListener { view ->
            if (authManager.isAuthenticated()) {
                Snackbar.make(view, "Ready to upload videos!", Snackbar.LENGTH_LONG)
                    .setAction("Action", null)
                    .setAnchorView(R.id.fab).show()
            } else {
                // Start authentication flow
                startAuthentication()
            }
        }
        
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }    /**
     * Start Google OAuth2 authentication flow
     */
    private fun startAuthentication() {
        try {
            val authIntent = authManager.startAuthenticationFlow()
            authLauncher.launch(authIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start authentication: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Handle authorization code from OAuth2 callback
     */
    private fun handleAuthorizationCode(authorizationCode: String) {
        lifecycleScope.launch {
            try {
                val result = authManager.handleAuthorizationCode(authorizationCode)
                result.onSuccess { _ ->
                    // Initialize YouTube service with credentials
                    val credentials = authManager.getCredentials()
                    if (credentials != null) {
                        youTubeService.initialize(credentials)
                        Toast.makeText(this@MainActivity, "Authentication successful!", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { _ ->
                    Toast.makeText(this@MainActivity, "Authentication failed", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}