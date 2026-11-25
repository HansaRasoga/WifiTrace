package com.group12.wifitrace

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.Locale

/** Tab 1: live RSSI + run a 10-reading trial (1 reading per second). */
class MeasureFragment : Fragment(R.layout.fragment_measure) {

    companion object {
        private const val INTERVAL_MS = 1000L
        private const val NO_SIGNAL = -127 // Android returns this when not connected
    }

    private lateinit var wifiManager: WifiManager
    private lateinit var tvRssi: TextView
    private lateinit var tvQuality: TextView
    private lateinit var tvLink: TextView
    private lateinit var signalBar: LinearProgressIndicator
    private lateinit var groupDistance: ChipGroup
    private lateinit var groupObstacle: ChipGroup
    private lateinit var tvLabelInfo: TextView
    private lateinit var btnStart: MaterialButton
    private lateinit var trialBar: LinearProgressIndicator
    private lateinit var tvProgress: TextView
    private lateinit var tvReadings: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val readings = mutableListOf<Int>()
    private var trialRunning = false
    private var currentLabel = ""
    private var startAfterPermission = false

    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                if (startAfterPermission) {
                    startAfterPermission = false
                    startTrial()
                }
            } else {
                startAfterPermission = false
                context?.let { ctx ->
                    Toast.makeText(
                        ctx,
                        "Location permission is needed to read Wi-Fi info",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    // Refreshes the big live number once per second
    private val liveRunnable = object : Runnable {
        override fun run() {
            updateLiveDisplay()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    // Takes one reading per second while a trial is running
    private val trialRunnable = object : Runnable {
        override fun run() {
            val rssi = currentRssi()
            if (rssi == null) {
                abortTrial("Lost Wi-Fi connection. Trial cancelled, nothing saved.")
                return
            }
            readings.add(rssi)
            trialBar.setProgressCompat(readings.size, true)
            tvProgress.text = "Reading ${readings.size}/${Plan.READINGS_PER_TRIAL}: $rssi dBm"

            if (readings.size >= Plan.READINGS_PER_TRIAL) {
                finishTrial()
            } else {
                handler.postDelayed(this, INTERVAL_MS)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        wifiManager = requireContext().applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager

        tvRssi = view.findViewById(R.id.tvRssi)
        tvQuality = view.findViewById(R.id.tvQuality)
        tvLink = view.findViewById(R.id.tvLink)
        signalBar = view.findViewById(R.id.signalBar)
        groupDistance = view.findViewById(R.id.groupDistance)
        groupObstacle = view.findViewById(R.id.groupObstacle)
        tvLabelInfo = view.findViewById(R.id.tvLabelInfo)
        btnStart = view.findViewById(R.id.btnStart)
        trialBar = view.findViewById(R.id.trialBar)
        tvProgress = view.findViewById(R.id.tvProgress)
        tvReadings = view.findViewById(R.id.tvReadings)

        groupDistance.setOnCheckedStateChangeListener { _, _ -> updateLabelInfo() }
        groupObstacle.setOnCheckedStateChangeListener { _, _ -> updateLabelInfo() }
        btnStart.setOnClickListener { startTrial() }

        trialBar.max = Plan.READINGS_PER_TRIAL
        updateLabelInfo()
        if (!hasLocationPermission()) {
            permissionLauncher.launch(locationPermissions)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) {
            handler.post(liveRunnable)
            updateLabelInfo()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            updateLabelInfo()
            handler.removeCallbacks(liveRunnable)
            handler.post(liveRunnable)
        } else {
            if (!trialRunning) {
                handler.removeCallbacks(liveRunnable)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(liveRunnable)
        if (trialRunning) {
            abortTrial("App left the screen. Trial cancelled, nothing saved.")
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }

    // ---------- Wi-Fi ----------

    private fun hasLocationPermission(): Boolean {
        val ctx = context ?: return false
        val fine = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /** Current RSSI in dBm, or null if the phone is not connected to Wi-Fi. */
    @Suppress("DEPRECATION")
    private fun currentRssi(): Int? {
        val info = wifiManager.connectionInfo ?: return null
        val rssi = info.rssi
        return if (rssi <= NO_SIGNAL) null else rssi
    }

    private fun qualityLabel(rssi: Int): String = when {
        rssi >= -50 -> "Excellent"
        rssi >= -60 -> "Good"
        rssi >= -70 -> "Fair"
        rssi >= -80 -> "Weak"
        else -> "Very weak"
    }

    @Suppress("DEPRECATION")
    private fun updateLiveDisplay() {
        if (!isAdded || view == null) return
        val rssi = currentRssi()
        if (rssi == null) {
            tvRssi.text = "-- dBm"
            tvQuality.text = "Not connected to Wi-Fi"
            signalBar.setProgressCompat(0, false)
            tvLink.text = "Link speed: -- Mbps"
        } else {
            tvRssi.text = "$rssi dBm"
            tvQuality.text = qualityLabel(rssi)
            // Map -100 dBm .. -30 dBm onto 0 .. 100 %
            val percent = ((rssi + 100) * 100 / 70).coerceIn(0, 100)
            signalBar.setProgressCompat(percent, true)
            val speed = wifiManager.connectionInfo?.linkSpeed ?: -1
            tvLink.text = "Link speed: ${if (speed > 0) speed else "--"} Mbps"
        }
    }

    // ---------- Label ----------

    private fun selectedLabel(): String {
        val distance = groupDistance.findViewById<Chip>(groupDistance.checkedChipId)
            ?.tag?.toString() ?: "1"
        val obstacle = groupObstacle.findViewById<Chip>(groupObstacle.checkedChipId)
            ?.tag?.toString() ?: "no_wall"
        return "${distance}m_$obstacle"
    }

    private fun updateLabelInfo() {
        if (!isAdded) return
        val ctx = context ?: return
        val label = selectedLabel()
        val done = LogRepository.readAll(ctx).count { it.label == label }
        val note = if (label in Plan.conditions) "" else "  (not in the planned 11 conditions)"
        tvLabelInfo.text = "Label: $label  ·  $done of ${Plan.REPEATS} trials done$note"
    }

    private fun setInputsEnabled(enabled: Boolean) {
        for (group in listOf(groupDistance, groupObstacle)) {
            for (i in 0 until group.childCount) group.getChildAt(i).isEnabled = enabled
        }
        btnStart.isEnabled = enabled
    }

    // ---------- Trial ----------

    private fun startTrial() {
        if (trialRunning) return
        if (!hasLocationPermission()) {
            startAfterPermission = true
            permissionLauncher.launch(locationPermissions)
            return
        }
        if (currentRssi() == null) {
            tvProgress.text = "Not connected to Wi-Fi. Connect to the test router first."
            return
        }

        currentLabel = selectedLabel()
        readings.clear()
        trialRunning = true
        setInputsEnabled(false)
        trialBar.setProgressCompat(0, false)
        tvReadings.text = ""
        tvProgress.text = "Measuring $currentLabel... keep the phone still."
        handler.postDelayed(trialRunnable, INTERVAL_MS)
    }

    private fun abortTrial(message: String) {
        handler.removeCallbacks(trialRunnable)
        trialRunning = false
        readings.clear()
        setInputsEnabled(true)
        trialBar.setProgressCompat(0, false)
        tvReadings.text = ""
        tvProgress.text = message
    }

    private fun finishTrial() {
        trialRunning = false
        setInputsEnabled(true)
        val ctx = context ?: return
        try {
            val trial = LogRepository.append(ctx, currentLabel, readings.toList())
            tvProgress.text = String.format(
                Locale.US,
                "Saved %s, trial %d. Average %.1f dBm",
                trial.label, trial.number, trial.average
            )
            tvReadings.text = "Readings: " + trial.readings.joinToString(", ")
        } catch (e: Exception) {
            tvProgress.text = "Could not save to CSV: ${e.message}"
        }
        updateLabelInfo()
    }
}
