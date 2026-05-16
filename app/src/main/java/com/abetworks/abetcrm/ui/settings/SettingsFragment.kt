package com.abetworks.abetcrm.ui.settings

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.abetworks.abetcrm.databinding.FragmentSettingsBinding
import com.abetworks.abetcrm.sync.ApiService
import com.abetworks.abetcrm.sync.LoginResult
import com.abetworks.abetcrm.util.Prefs
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(inflater, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()

        // Pre-fill saved values
        b.etApiUrl.setText(Prefs.get(ctx, Prefs.KEY_API_URL, "https://api.abetworks.in/v1"))
        b.etEmail.setText(Prefs.get(ctx, Prefs.KEY_USER_EMAIL))
        b.tvLoggedIn.text = if (Prefs.isLoggedIn(ctx))
            "✅ Logged in as ${Prefs.get(ctx, Prefs.KEY_USER_NAME)}"
        else "❌ Not logged in — sync disabled"

        b.btnSaveApiUrl.setOnClickListener {
            val url = b.etApiUrl.text.toString().trim()
            if (url.isNotBlank()) {
                Prefs.set(ctx, Prefs.KEY_API_URL, url)
                Toast.makeText(ctx, "API URL saved", Toast.LENGTH_SHORT).show()
            }
        }

        b.btnLogin.setOnClickListener {
            val email = b.etEmail.text.toString().trim()
            val pass  = b.etPassword.text.toString()
            if (email.isBlank() || pass.isBlank()) {
                Toast.makeText(ctx, "Enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            b.btnLogin.isEnabled = false
            lifecycleScope.launch {
                when (val result = ApiService(ctx).login(email, pass)) {
                    is LoginResult.Success -> {
                        Prefs.set(ctx, Prefs.KEY_AUTH_TOKEN, result.token)
                        Prefs.set(ctx, Prefs.KEY_TENANT_ID, result.tenantId)
                        Prefs.set(ctx, Prefs.KEY_USER_NAME, result.name)
                        Prefs.set(ctx, Prefs.KEY_USER_EMAIL, email)
                        b.tvLoggedIn.text = "✅ Logged in as ${result.name}"
                        Toast.makeText(ctx, "Login successful", Toast.LENGTH_SHORT).show()
                    }
                    is LoginResult.Error -> {
                        Toast.makeText(ctx, "Login failed: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                }
                b.btnLogin.isEnabled = true
            }
        }

        b.btnLogout.setOnClickListener {
            Prefs.clear(ctx)
            b.tvLoggedIn.text = "❌ Not logged in"
            Toast.makeText(ctx, "Logged out", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
