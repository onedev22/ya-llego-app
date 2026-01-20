package com.amurayada.yallego

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.amurayada.yallego.data.AppPreferences

class MainActivity : AppCompatActivity() {

    private lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appPreferences = AppPreferences(this)

        when {
            !appPreferences.isTutorialShown() -> {
                startActivity(Intent(this, TutorialActivity::class.java))
            }
            !appPreferences.isUserLoggedIn() -> {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            else -> {
                
                redirectBasedOnUserType()
            }
        }

        finish()
    }

    private fun redirectBasedOnUserType() {
        when (appPreferences.getUserType()) {
            "conductor" -> {  
                startActivity(Intent(this, DriverHomeActivity::class.java))
            }
            "pasajero" -> {   
                startActivity(Intent(this, HomeActivity::class.java))
            }
            else -> {
                
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }
    }
}