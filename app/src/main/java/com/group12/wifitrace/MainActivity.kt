package com.group12.wifitrace

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Hosts the 4 tabs. All fragments are created once and shown/hidden, so a running
 * measurement keeps going if you peek at another tab.
 */
class MainActivity : AppCompatActivity() {

    private val tabs = linkedMapOf(
        R.id.nav_measure to "measure",
        R.id.nav_history to "history",
        R.id.nav_summary to "summary",
        R.id.nav_settings to "settings"
    )
    private var selectedId = R.id.nav_measure

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, MeasureFragment(), "measure")
                .add(R.id.fragmentContainer, HistoryFragment(), "history")
                .add(R.id.fragmentContainer, SummaryFragment(), "summary")
                .add(R.id.fragmentContainer, SettingsFragment(), "settings")
                .commitNow()
        }

        selectedId = savedInstanceState?.getInt("selected_tab", R.id.nav_measure)
            ?: R.id.nav_measure

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        nav.selectedItemId = selectedId
        showTab(selectedId)
    }

    private fun showTab(id: Int) {
        selectedId = id
        val tx = supportFragmentManager.beginTransaction()
        tabs.forEach { (itemId, tag) ->
            val fragment = supportFragmentManager.findFragmentByTag(tag) ?: return@forEach
            if (itemId == id) tx.show(fragment) else tx.hide(fragment)
        }
        tx.commit()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("selected_tab", selectedId)
    }
}
