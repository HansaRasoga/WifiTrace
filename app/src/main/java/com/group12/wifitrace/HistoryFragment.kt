package com.group12.wifitrace

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/** Tab 2: every logged trial, newest first, with an "Undo last" safety button. */
class HistoryFragment : Fragment(R.layout.fragment_history) {

    private lateinit var tvCount: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var rvTrials: RecyclerView
    private val adapter = TrialAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvCount = view.findViewById(R.id.tvCount)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        rvTrials = view.findViewById(R.id.rvTrials)
        rvTrials.layoutManager = LinearLayoutManager(requireContext())
        rvTrials.adapter = adapter

        view.findViewById<MaterialButton>(R.id.btnUndo).setOnClickListener { confirmUndo() }
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
        val trials = LogRepository.readAll(requireContext()).reversed()
        adapter.submit(trials)
        tvCount.text = "${trials.size} trial${if (trials.size == 1) "" else "s"} logged"
        tvEmpty.visibility = if (trials.isEmpty()) View.VISIBLE else View.GONE
        rvTrials.visibility = if (trials.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun confirmUndo() {
        if (LogRepository.readAll(requireContext()).isEmpty()) {
            Toast.makeText(requireContext(), "Nothing to undo", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove the last trial?")
            .setMessage("The most recent row will be deleted from wifi_logs.csv.")
            .setPositiveButton("Remove") { _, _ ->
                LogRepository.deleteLast(requireContext())
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private class TrialAdapter : RecyclerView.Adapter<TrialAdapter.VH>() {

        private var items: List<Trial> = emptyList()

        fun submit(list: List<Trial>) {
            items = list
            notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.tvTitle)
            val avg: TextView = v.findViewById(R.id.tvAvg)
            val time: TextView = v.findViewById(R.id.tvTime)
            val readings: TextView = v.findViewById(R.id.tvReadings)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_trial, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val t = items[position]
            holder.title.text = "${Plan.pretty(t.label)}  ·  trial ${t.number}"
            holder.avg.text = String.format(Locale.US, "%.1f dBm", t.average)
            holder.time.text = t.timestamp.replace('T', ' ')
            holder.readings.text = "R1-R10: " + t.readings.joinToString(", ")
        }
    }
}
