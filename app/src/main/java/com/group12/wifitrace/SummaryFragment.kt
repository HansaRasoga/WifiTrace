package com.group12.wifitrace

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.Locale

/** Tab 3: line chart + table of the 11 planned conditions with change vs the 1 m baseline. */
class SummaryFragment : Fragment(R.layout.fragment_summary) {

    private lateinit var chart: LineChartView
    private lateinit var tvPlanProgress: TextView
    private lateinit var planBar: LinearProgressIndicator
    private lateinit var tableContainer: LinearLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chart = view.findViewById(R.id.chart)
        tvPlanProgress = view.findViewById(R.id.tvPlanProgress)
        planBar = view.findViewById(R.id.planBar)
        tableContainer = view.findViewById(R.id.tableContainer)
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

        // ----- Chart: one series per obstacle state -----
        val series = Obstacle.entries.mapNotNull { obstacle ->
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
        chart.setData(series)

        // ----- Progress through the plan (max 3 counted per condition) -----
        val done = Plan.conditions.sumOf { minOf(byLabel[it]?.size ?: 0, Plan.REPEATS) }
        planBar.max = Plan.totalTrials
        planBar.setProgressCompat(done, true)
        tvPlanProgress.text = "$done of ${Plan.totalTrials} planned trials done"

        // ----- Table -----
        val baselineLabel = Plan.conditions.first()
        val baseline = byLabel[baselineLabel]?.map { it.average }?.average()

        tableContainer.removeAllViews()
        addRow("Condition", "Trials", "Avg dBm", "Change", header = true)
        for (label in Plan.conditions) {
            val list = byLabel[label].orEmpty()
            val avgText: String
            val deltaText: String
            if (list.isEmpty()) {
                avgText = "—"
                deltaText = "—"
            } else {
                val avg = list.map { it.average }.average()
                avgText = String.format(Locale.US, "%.1f", avg)
                deltaText = when {
                    label == baselineLabel -> "baseline"
                    baseline == null -> "—"
                    else -> String.format(Locale.US, "%+.1f dB", avg - baseline)
                }
            }
            addRow(Plan.pretty(label), "${list.size}/${Plan.REPEATS}", avgText, deltaText, header = false)
        }
    }

    private fun addRow(cond: String, trials: String, avg: String, delta: String, header: Boolean) {
        val row = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_summary_row, tableContainer, false)
        val views = listOf(
            row.findViewById<TextView>(R.id.tvCond) to cond,
            row.findViewById<TextView>(R.id.tvTrials) to trials,
            row.findViewById<TextView>(R.id.tvAvg) to avg,
            row.findViewById<TextView>(R.id.tvDelta) to delta
        )
        for ((tv, text) in views) {
            tv.text = text
            if (header) tv.setTypeface(null, Typeface.BOLD)
        }
        tableContainer.addView(row)
    }
}
