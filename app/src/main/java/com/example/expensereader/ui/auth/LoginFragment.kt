package com.example.expensereader.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.example.expensereader.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class LoginFragment : Fragment(R.layout.fragment_login) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etEmail = view.findViewById<TextInputEditText>(R.id.etEmail)
        val etPass = view.findViewById<TextInputEditText>(R.id.etPassword)
        val btn = view.findViewById<MaterialButton>(R.id.btnLogin)

        // ✅ Prefill from Register (if passed)
        val preEmail = arguments?.getString("prefill_email").orEmpty()
        val prePass = arguments?.getString("prefill_pass").orEmpty()

        if (preEmail.isNotBlank()) etEmail.setText(preEmail)
        if (prePass.isNotBlank()) etPass.setText(prePass)

        btn.setOnClickListener {
            // ✅ TEMP: Skip login validation and Firebase
            viewLifecycleOwner.lifecycleScope.launch {
                findNavController().navigate(
                    R.id.homeFragment,
                    null,
                    navOptions {
                        popUpTo(R.id.authFragment) { inclusive = true }
                        launchSingleTop = true
                    }
                )
            }
        }
    }
}
