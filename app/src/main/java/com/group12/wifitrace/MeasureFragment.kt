package com.group12.wifitrace

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/** Tab 1: live RSSI + run a 10-reading trial (1 reading per second). */
class MeasureFragment : Fragment(R.layout.fragment_measure), SensorEventListener {

    companion object {
        private const val INTERVAL_MS = 1000L
        private const val SWEEP_INTERVAL_MS = 250L
        private const val NO_SIGNAL = -127 // Android returns this when not connected
    }

    private lateinit var wifiManager: WifiManager
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var magSensor: Sensor? = null

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

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

    // New UI elements for available networks
    private lateinit var headerNetworks: LinearLayout
    private lateinit var containerNetworks: LinearLayout
    private lateinit var ivChevron: ImageView
    private lateinit var btnScan: MaterialButton
    private lateinit var btnWifiSettings: MaterialButton
    private lateinit var tvScanStatus: TextView
    private lateinit var rvNetworks: RecyclerView
    private lateinit var layoutLocationDisabled: LinearLayout
    private lateinit var btnLocationSettings: MaterialButton
    private lateinit var layoutPermissionRequired: LinearLayout
    private lateinit var btnGrantPermission: MaterialButton
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var adapter: WifiAdapter

    // Compass UI
    private lateinit var headerCompass: LinearLayout
    private lateinit var containerCompass: LinearLayout
    private lateinit var ivChevronCompass: ImageView
    private lateinit var compassView: CompassBearingView
    private lateinit var tvHeading: TextView
    private lateinit var tvCompassRssi: TextView
    private lateinit var tvCalibrationHint: TextView
    private lateinit var btnStartSweep: MaterialButton
    private lateinit var tvSweepResult: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val readings = mutableListOf<Int>()
    private var trialRunning = false
    private var currentLabel = ""
    private var startAfterPermission = false
    private var isExpanded = false
    private var isCompassExpanded = false

    // Sweep logic
    private var sweepRunning = false
    private var bestAzimuth: Float? = null
    private var bestRssi: Int = Int.MIN_VALUE
    private var worstAzimuth: Float? = null
    private var worstRssi: Int = Int.MAX_VALUE
    private var currentAzimuth: Float = 0f

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            displayScanResults(success)
        }
    }

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

    private val sweepRunnable = object : Runnable {
        override fun run() {
            if (!sweepRunning) return
            val rssi = currentRssi() ?: NO_SIGNAL
            if (rssi > NO_SIGNAL) {
                if (rssi > bestRssi) {
                    bestRssi = rssi
                    bestAzimuth = currentAzimuth
                }
                if (rssi < worstRssi) {
                    worstRssi = rssi
                    worstAzimuth = currentAzimuth
                }
                updateSweepDisplay()
            }
            handler.postDelayed(this, SWEEP_INTERVAL_MS)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        wifiManager = requireContext().applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor == null) {
            accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        }

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

        // Find available networks UI
        headerNetworks = view.findViewById(R.id.headerNetworks)
        containerNetworks = view.findViewById(R.id.containerNetworks)
        ivChevron = view.findViewById(R.id.ivChevron)
        btnScan = view.findViewById(R.id.btnScan)
        btnWifiSettings = view.findViewById(R.id.btnWifiSettings)
        tvScanStatus = view.findViewById(R.id.tvScanStatus)
        rvNetworks = view.findViewById(R.id.rvNetworks)
        layoutLocationDisabled = view.findViewById(R.id.layoutLocationDisabled)
        btnLocationSettings = view.findViewById(R.id.btnLocationSettings)
        layoutPermissionRequired = view.findViewById(R.id.layoutPermissionRequired)
        btnGrantPermission = view.findViewById(R.id.btnGrantPermission)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)

        // Find compass UI
        headerCompass = view.findViewById(R.id.headerCompass)
        containerCompass = view.findViewById(R.id.containerCompass)
        ivChevronCompass = view.findViewById(R.id.ivChevronCompass)
        compassView = view.findViewById(R.id.compassView)
        tvHeading = view.findViewById(R.id.tvHeading)
        tvCompassRssi = view.findViewById(R.id.tvCompassRssi)
        tvCalibrationHint = view.findViewById(R.id.tvCalibrationHint)
        btnStartSweep = view.findViewById(R.id.btnStartSweep)
        tvSweepResult = view.findViewById(R.id.tvSweepResult)

        setupNetworksList()

        headerNetworks.setOnClickListener { toggleNetworksDrawer() }
        btnScan.setOnClickListener { startWifiScan() }
        btnWifiSettings.setOnClickListener { openWifiSettings() }
        btnLocationSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
        btnGrantPermission.setOnClickListener {
            permissionLauncher.launch(locationPermissions)
        }

        headerCompass.setOnClickListener { toggleCompassDrawer() }
        btnStartSweep.setOnClickListener { toggleSweep() }

        groupDistance.setOnCheckedStateChangeListener { _, _ -> updateLabelInfo() }
        groupObstacle.setOnCheckedStateChangeListener { _, _ -> updateLabelInfo() }
        btnStart.setOnClickListener { startTrial() }

        trialBar.max = Plan.READINGS_PER_TRIAL
        updateLabelInfo()
        if (!hasLocationPermission()) {
            permissionLauncher.launch(locationPermissions)
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        requireContext().registerReceiver(wifiScanReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        requireContext().unregisterReceiver(wifiScanReceiver)
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) {
            handler.post(liveRunnable)
            updateLabelInfo()
            registerSensors()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            updateLabelInfo()
            handler.removeCallbacks(liveRunnable)
            handler.post(liveRunnable)
            registerSensors()
        } else {
            if (!trialRunning) {
                handler.removeCallbacks(liveRunnable)
            }
            unregisterSensors()
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(liveRunnable)
        unregisterSensors()
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
        return ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
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
            tvCompassRssi.text = "RSSI: -- dBm"
        } else {
            tvRssi.text = "$rssi dBm"
            tvQuality.text = qualityLabel(rssi)
            // Map -100 dBm .. -30 dBm onto 0 .. 100 %
            val percent = ((rssi + 100) * 100 / 70).coerceIn(0, 100)
            signalBar.setProgressCompat(percent, true)
            val speed = wifiManager.connectionInfo?.linkSpeed ?: -1
            val freq = wifiManager.connectionInfo?.frequency ?: -1
            val band = when {
                freq >= 5925 -> "6 GHz"
                freq >= 4900 -> "5 GHz"
                freq >= 2400 -> "2.4 GHz"
                else -> "--"
            }
            tvLink.text = "Link speed: ${if (speed > 0) speed else "--"} Mbps ($band)"
            tvCompassRssi.text = "RSSI: $rssi dBm"
        }
        // Refresh list to update "Connected" badge and signal strengths if needed
        if (isExpanded) {
            adapter.notifyDataSetChanged()
        }
    }

    // ---------- Available Networks ----------

    private fun setupNetworksList() {
        adapter = WifiAdapter()
        rvNetworks.layoutManager = LinearLayoutManager(context)
        rvNetworks.adapter = adapter
    }

    private fun toggleNetworksDrawer() {
        isExpanded = !isExpanded
        containerNetworks.visibility = if (isExpanded) View.VISIBLE else View.GONE
        ivChevron.rotation = if (isExpanded) 90f else 0f
        if (isExpanded) {
            startWifiScan()
        }
    }

    private fun startWifiScan() {
        if (!hasLocationPermission()) {
            permissionLauncher.launch(locationPermissions)
            return
        }

        val success = wifiManager.startScan()
        if (!success) {
            // Scan throttled
            displayScanResults(false)
        } else {
            tvScanStatus.text = "Scanning..."
        }
    }

    private fun displayScanResults(success: Boolean) {
        if (!isAdded) return
        val ctx = requireContext()
        val locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locationEnabled = LocationManagerCompat.isLocationEnabled(locationManager)

        if (!hasLocationPermission()) {
            rvNetworks.visibility = View.GONE
            layoutEmptyState.visibility = View.GONE
            layoutLocationDisabled.visibility = View.GONE
            layoutPermissionRequired.visibility = View.VISIBLE
            tvScanStatus.text = "Permission required"
            return
        }

        layoutPermissionRequired.visibility = View.GONE

        if (!locationEnabled) {
            rvNetworks.visibility = View.GONE
            layoutEmptyState.visibility = View.GONE
            layoutLocationDisabled.visibility = View.VISIBLE
            tvScanStatus.text = "Location disabled"
            return
        }

        layoutLocationDisabled.visibility = View.GONE

        val results = wifiManager.scanResults
        val sorted = results.sortedByDescending { it.level }
        adapter.submitList(sorted)

        if (sorted.isEmpty()) {
            rvNetworks.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        } else {
            rvNetworks.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE
        }

        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(System.currentTimeMillis())
        tvScanStatus.text = if (success) "Updated: $time" else "Showing cached (throttled): $time"
    }

    private fun openWifiSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val intent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            startActivity(intent)
        } else {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            startActivity(intent)
        }
    }

    // ---------- Compass ----------

    private fun registerSensors() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        } ?: run {
            accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            magSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    private fun unregisterSensors() {
        sensorManager.unregisterListener(this)
    }

    private fun toggleCompassDrawer() {
        isCompassExpanded = !isCompassExpanded
        containerCompass.visibility = if (isCompassExpanded) View.VISIBLE else View.GONE
        ivChevronCompass.rotation = if (isCompassExpanded) 90f else 0f
    }

    private fun toggleSweep() {
        if (!sweepRunning) {
            // Start sweep
            sweepRunning = true
            bestAzimuth = null
            bestRssi = Int.MIN_VALUE
            worstAzimuth = null
            worstRssi = Int.MAX_VALUE
            btnStartSweep.text = "Stop & Reset Sweep"
            tvSweepResult.text = "Slowly rotate 360°..."
            compassView.setPeakAzimuth(null)
            compassView.setMinAzimuth(null)
            handler.post(sweepRunnable)
        } else {
            // Stop sweep
            sweepRunning = false
            btnStartSweep.text = "Start 360° Body Sweep"
            handler.removeCallbacks(sweepRunnable)
            updateSweepDisplay(final = true)
        }
    }

    private fun updateSweepDisplay(final: Boolean = false) {
        val best = bestAzimuth
        val worst = worstAzimuth
        if (best != null && worst != null) {
            val drop = bestRssi - worstRssi
            val dir = getCardinalDirection(best)
            val worstDir = getCardinalDirection(worst)
            val text = if (final) {
                "Sweep Complete!\nStrongest: $bestRssi dBm facing $dir (${best.roundToInt()}°)\nWeakest: $worstRssi dBm facing $worstDir (${worst.roundToInt()}°)\nBody shielding drop: $drop dB"
            } else {
                "Scanning... Strongest: $bestRssi dBm ($dir)\nDrop detected: $drop dB"
            }
            tvSweepResult.text = text
            compassView.setPeakAzimuth(best)
            compassView.setMinAzimuth(worst)
        }
    }

    private fun getCardinalDirection(azimuth: Float): String {
        val degrees = (azimuth + 360) % 360
        return when {
            degrees >= 337.5 || degrees < 22.5 -> "N"
            degrees >= 22.5 && degrees < 67.5 -> "NE"
            degrees >= 67.5 && degrees < 112.5 -> "E"
            degrees >= 112.5 && degrees < 157.5 -> "SE"
            degrees >= 157.5 && degrees < 202.5 -> "S"
            degrees >= 202.5 && degrees < 247.5 -> "SW"
            degrees >= 247.5 && degrees < 292.5 -> "W"
            degrees >= 292.5 && degrees < 337.5 -> "NW"
            else -> "N"
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            updateAzimuth(Math.toDegrees(orientationAngles[0].toDouble()).toFloat())
        } else {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            }
            SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            updateAzimuth(Math.toDegrees(orientationAngles[0].toDouble()).toFloat())
        }
    }

    private fun updateAzimuth(angle: Float) {
        currentAzimuth = (angle + 360) % 360
        compassView.setAzimuth(currentAzimuth)
        val dir = getCardinalDirection(currentAzimuth)
        tvHeading.text = "Heading: $dir · ${currentAzimuth.roundToInt()}°"
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD || sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            tvCalibrationHint.visibility = if (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    private inner class WifiAdapter : RecyclerView.Adapter<WifiAdapter.ViewHolder>() {
        private var items = listOf<ScanResult>()

        fun submitList(newList: List<ScanResult>) {
            items = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_wifi_network, parent, false)
            return ViewHolder(view)
        }

        @Suppress("DEPRECATION")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val result = items[position]
            val ssid = if (result.SSID.isNullOrBlank()) "Hidden Network" else result.SSID
            holder.tvSsid.text = ssid

            val currentBssid = wifiManager.connectionInfo?.bssid
            holder.tvConnectedBadge.visibility = if (result.BSSID == currentBssid) View.VISIBLE else View.GONE

            val freq = result.frequency
            val band = when {
                freq >= 5925 -> "6 GHz"
                freq >= 4900 -> "5 GHz"
                else -> "2.4 GHz"
            }
            holder.tvBand.text = "$band ($freq MHz)"
            holder.tvSignal.text = "${result.level} dBm"

            // Color coding signal strength
            val color = when {
                result.level >= -50 -> 0xFF4CAF50.toInt() // Green
                result.level >= -70 -> 0xFFFFC107.toInt() // Amber
                else -> 0xFFF44336.toInt() // Red
            }
            holder.tvSignal.setTextColor(color)
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvSsid: TextView = view.findViewById(R.id.tvSsid)
            val tvConnectedBadge: TextView = view.findViewById(R.id.tvConnectedBadge)
            val tvBand: TextView = view.findViewById(R.id.tvBand)
            val tvSignal: TextView = view.findViewById(R.id.tvSignal)
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
        
        // Save frequency band info
        val freq = wifiManager.connectionInfo?.frequency ?: 0
        val is5Ghz = freq >= 4900
        ctx.getSharedPreferences("wifi_trace_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("last_measured_band_is_5ghz", is5Ghz)
            .apply()

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
