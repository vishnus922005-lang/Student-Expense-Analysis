package com.example.expensereader.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.expensereader.R
import com.example.expensereader.data.AuthRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class RegisterFragment : Fragment(R.layout.fragment_register) {

    private val repo = AuthRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etEmail = view.findViewById<TextInputEditText>(R.id.etEmail)
        val etPass = view.findViewById<TextInputEditText>(R.id.etPassword)
        val etConfirm = view.findViewById<TextInputEditText>(R.id.etConfirm)
        val etFirst = view.findViewById<TextInputEditText>(R.id.etFirstName)
        val btn = view.findViewById<MaterialButton>(R.id.btnRegister)

        btn.setOnClickListener {
            val email = etEmail.text?.toString()?.trim().orEmpty()
            val pass = etPass.text?.toString().orEmpty()
            val confirm = etConfirm.text?.toString().orEmpty()
            val firstName = etFirst.text?.toString()?.trim().orEmpty()

            if (firstName.isBlank()) {
                Toast.makeText(requireContext(), "Enter First Name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (email.isBlank() || pass.length < 6) {
                Toast.makeText(requireContext(), "Enter valid email & 6+ password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pass != confirm) {
                Toast.makeText(requireContext(), "Password not matching", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    repo.register(email, pass)

                    // ✅ IMPORTANT: Firebase auto-login happens on register
                    // You want to go to Login screen => force logout here
                    repo.logout()

                    // ✅ Go to Login and prefill
                    findNavController().navigate(
                        R.id.action_registerFragment_to_loginFragment,
                        bundleOf(
                            "prefill_email" to email,
                            "prefill_pass" to pass
                        )
                    )
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        e.message ?: "Register failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
