package com.abetworks.abetcrm.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.abetworks.abetcrm.R
import com.abetworks.abetcrm.databinding.ActivityMainBinding
import com.abetworks.abetcrm.ui.leads.LeadsFragment
import com.abetworks.abetcrm.ui.pipeline.PipelineFragment
import com.abetworks.abetcrm.ui.settings.SettingsFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    lateinit var viewModel: LeadViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            Toast.makeText(this, "Some permissions denied. Lead capture may be limited.", Toast.LENGTH_LONG).show()
        }
        checkNotificationListenerPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this, LeadViewModelFactory(application))[LeadViewModel::class.java]

        setupNavigation(savedInstanceState)
        observeToasts()
        requestPermissions()
    }

    private fun setupNavigation(savedState: Bundle?) {
        if (savedState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LeadsFragment())
                .commit()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_leads    -> LeadsFragment()
                R.id.nav_pipeline -> PipelineFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> return@setOnItemSelectedListener false
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            true
        }
    }

    private fun observeToasts() {
        lifecycleScope.launch {
            viewModel.toastMsg.collect { msg ->
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestPermissions() {
        val needed = mutableListOf<String>()
        val required = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.POST_NOTIFICATIONS
        )
        for (perm in required) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needed.add(perm)
            }
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
        else checkNotificationListenerPermission()
    }

    private fun checkNotificationListenerPermission() {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true
        if (!enabled) {
            AlertDialog.Builder(this)
                .setTitle("Enable WhatsApp Capture")
                .setMessage(
                    "AbetCRM needs Notification Access to automatically capture WhatsApp leads.\n\n" +
                    "Tap OK → find AbetCRM → enable the toggle."
                )
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .setNegativeButton("Skip") { d, _ -> d.dismiss() }
                .show()
        }
    }
}
