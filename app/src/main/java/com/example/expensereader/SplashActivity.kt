package com.example.expensereader

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.expensereader.data.AuthRepository

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // ✅ Fade + soft pop animation
        val center = findViewById<View>(R.id.centerContent)
        center.alpha = 0f
        center.scaleX = 0.96f
        center.scaleY = 0.96f

        center.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(900)
            .setStartDelay(120)
            .start()

        Handler(Looper.getMainLooper()).postDelayed({

            val authRepo = AuthRepository()

            val intent = if (authRepo.isSignedIn()) {
                Intent(this, MainActivity::class.java)
            } else {
                Intent(this, MainActivity::class.java) // change to LoginActivity if you have
            }

            startActivity(intent)
            finish()

        }, 2000)
    }
}
