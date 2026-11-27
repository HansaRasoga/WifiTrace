package com.group12.wifitrace

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.Locale

/** Tab 3: line chart + table of the 11 planned conditions with change vs the 1 m baseline. */
class SummaryFragment : Fragment(R.layout.fragment_summary) {

    private lateinit var chart: LineChartView
    private lateinit var tvPlanProgress: TextView
    private lateinit var planBar: LinearProgressIndicator
    private lateinit var tableContainer: LinearLayout
    private lateinit var switchTheoretical: MaterialSwitch
    private lateinit var cardInsights: View
    private lateinit var tvInsightStats: TextView
    private lateinit var tvRecommendations: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chart = view.findViewById(R.id.chart)
        tvPlanProgress = view.findViewById(R.id.tvPlanProgress)
        planBar = view.findViewById(R.id.planBar)
        tableContainer = view.findViewById(R.id.tableContainer)
        
        switchTheoretical = view.findViewById(R.id.switchTheoretical)
        cardInsights = view.findViewById(R.id.cardInsights)
        tvInsightStats = view.findViewById(R.id.tvInsightStats)
        tvRecommendations = view.findViewById(R.id.tvRecommendations)

        switchTheoretical.setOnCheckedChangeListener { _, _ -> refresh() }
        
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) refresh()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refresh()
    }

    private fun refresh() {
        if (!isAdded || view == null) return
        val ctx = context ?: return
        val trials = LogRepository.readAll(ctx)
        val byLabel = trials.groupBy { it.label }
        
        val is5Ghz = ctx.getSharedPreferences("wifi_trace_prefs", Context.MODE_PRIVATE)
            .getBoolean("last_measured_band_is_5ghz", false)

        // ----- Chart: one series per obstacle state -----
        val measuredSeries = Obstacle.entries.mapNotNull { obstacle ->
            val points = byLabel.mapNotNull { (label, list) ->
                val parsed = Plan.parse(label)
                if (parsed == null || parsed.second != obstacle) {
                    null
                } else {
                    parsed.first.toFloat() to list.map { it.average }.average().toFloat()
                }
            }.sortedBy { it.first }
            if (points.isEmpty()) null
            else LineChartView.Series(obstacle.title, obstacle.color, points, obstacle.dash)
        }
        
        val finalSeries = measuredSeries.toMutableList()
        
        // Add theoretical curve if requested and baseline exists
        val baselineLabel = Plan.labelFor(1, Obstacle.NO_WALL)
        val baselineRssi = byLabel[baselineLabel]?.map { it.average }?.average()
        
        if (switchTheoretical.isChecked && baselineRssi != null) {
            val theoreticalPoints = (1..15).map { d ->
                d.toFloat() to SignalEstimator.expectedRssi(baselineRssi, d, Obstacle.NO_WALL, is5Ghz).toFloat()
            }
            finalSeries.add(
                LineChartView.Series(
                    "Theoretical",
                    Color.parseColor("#90A4AE"), // Blue-grey
                    theoreticalPoints,
                    listOf(5f, 5f),
                    showDots = false
                )
            )
        }
        
        chart.setData(finalSeries)

        // ----- Progress through the plan (max 3 counted per condition) -----
        val done = Plan.conditions.sumOf { minOf(byLabel[it]?.size ?: 0, Plan.REPEATS) }
        planBar.max = Plan.totalTrials
        planBar.setProgressCompat(done, true)
        tvPlanProgress.text = "$done of ${Plan.totalTrials} planned trials done"

        // ----- Table -----
        tableContainer.removeAllViews()
        addRow("Condition", "Trials", "Avg", "Δ Base", "Obs", header = true)
        
        val avgMap = byLabel.mapValues { it.value.map { t -> t.average }.average() }

        for (label in Plan.conditions) {
            val list = byLabel[label].orEmpty()
            val avg = avgMap[label]
            
            val avgText = avg?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
            val deltaText = if (avg != null && baselineRssi != null) {
                if (label == baselineLabel) "base"
                else String.format(Locale.US, "%+.1f", avg - baselineRssi)
            } else "—"
            
            val obsText = if (avg != null) {
                val parsed = Plan.parse(label)
                if (parsed != null && parsed.second != Obstacle.NO_WALL) {
                    val noWallLabel = Plan.labelFor(parsed.first, Obstacle.NO_WALL)
                    val noWallAvg = avgMap[noWallLabel]
                    if (noWallAvg != null) {
                         String.format(Locale.US, "%+.1f", avg - noWallAvg)
                    } else "—"
                } else "—"
            } else "—"
            
            addRow(Plan.pretty(label), "${list.size}/${Plan.REPEATS}", avgText, deltaText, obsText, header = false)
        }
        
        // ----- Diagnostics -----
        if (avgMap.isNotEmpty() && baselineRssi != null) {
            cardInsights.visibility = View.VISIBLE
            val diag = SignalEstimator.generateDiagnostics(baselineRssi, avgMap, is5Ghz)
            
            tvInsightStats.text = String.format(
                Locale.US,
                "Band: %s | Path-loss Exponent (n): %.2f\nAvg Wall Loss: %.1f dB | Body Loss: %.1f dB",
                if (is5Ghz) "5 GHz" else "2.4 GHz",
                diag.pathLossExponent,
                diag.wallLoss,
                diag.bodyLoss
            )
            
            tvRecommendations.text = diag.recommendations.joinToString("\n• ", prefix = "• ")
        } else {
            cardInsights.visibility = View.GONE
        }
    }

    private fun addRow(cond: String, trials: String, avg: String, delta: String, obs: String, header: Boolean) {
        val row = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_summary_row, tableContainer, false)
        
        val tvCond = row.findViewById<TextView>(R.id.tvCond)
        val tvTrials = row.findViewById<TextView>(R.id.tvTrials)
        val tvAvg = row.findViewById<TextView>(R.id.tvAvg)
        val tvDelta = row.findViewById<TextView>(R.id.tvDelta)
        val tvObs = row.findViewById<TextView>(R.id.tvObs)

        tvCond.text = cond
        tvTrials.text = trials
        tvAvg.text = avg
        tvDelta.text = delta
        tvObs.text = obs
        
        if (header) {
            val bold = Typeface.DEFAULT_BOLD
            tvCond.typeface = bold
            tvTrials.typeface = bold
            tvAvg.typeface = bold
            tvDelta.typeface = bold
            tvObs.typeface = bold
        }

        tableContainer.addView(row)
    }
}
