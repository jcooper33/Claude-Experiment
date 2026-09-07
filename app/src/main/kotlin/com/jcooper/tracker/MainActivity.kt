package com.jcooper.tracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.jcooper.tracker.databinding.ActivityMainBinding
import com.jcooper.tracker.ui.dashboard.DashboardFragment
import com.jcooper.tracker.ui.food.LogFoodFragment
import com.jcooper.tracker.ui.settings.SettingsFragment
import com.jcooper.tracker.ui.weight.LogWeightFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            showFragment(LogFoodFragment())
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_food -> LogFoodFragment()
                R.id.nav_weight -> LogWeightFragment()
                R.id.nav_dashboard -> DashboardFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> return@setOnItemSelectedListener false
            }
            showFragment(fragment)
            true
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
