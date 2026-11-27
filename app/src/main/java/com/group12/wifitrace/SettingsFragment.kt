package com.group12.wifitrace

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Tab 4: theme (System / Light / Dark), CSV export, about. */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()

        // ----- Theme -----
        val group = view.findViewById<RadioGroup>(R.id.themeGroup)
        val checkedId = when (ThemeManager.saved(ctx)) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.id.rbLight
            AppCompatDelegate.MODE_NIGHT_YES -> R.id.rbDark
            else -> R.id.rbSystem
        }
        group.check(checkedId)
        group.setOnCheckedChangeListener { _, id ->
            val mode = when (id) {
                R.id.rbLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.rbDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            ThemeManager.set(ctx, mode) // the activity recreates itself with the new theme
        }

        // ----- Data -----
        view.findViewById<TextView>(R.id.tvPath).text =
            "File: ${LogRepository.file(ctx).absolutePath}"
        view.findViewById<MaterialButton>(R.id.btnShare).setOnClickListener { shareLog() }
        view.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { saveToDownloads() }
        view.findViewById<MaterialButton>(R.id.btnClear).setOnClickListener { confirmClear() }

        // ----- About -----
        view.findViewById<TextView>(R.id.tvAbout).text =
            "WifiTrace 2.0 · Group 12 · Advanced Mobile Computing\n" +
                "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\n" +
                "Reads RSSI from WifiManager only. SSID, BSSID and MAC addresses are never read or saved."
    }

    private fun shareLog() {
        val ctx = context ?: return
        val file = LogRepository.file(ctx)
        if (!file.exists() || LogRepository.readAll(ctx).isEmpty()) {
            Toast.makeText(ctx, "No log yet. Run a trial first.", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share wifi_logs.csv"))
    }

    private fun saveToDownloads() {
        val ctx = context ?: return
        if (LogRepository.readAll(ctx).isEmpty()) {
            Toast.makeText(ctx, "No log yet. Run a trial first.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val name = LogRepository.exportToDownloads(ctx)
            Toast.makeText(ctx, "Saved to Downloads/$name", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmClear() {
        val ctx = context ?: return
        if (LogRepository.readAll(ctx).isEmpty()) {
            Toast.makeText(ctx, "No data to delete", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Delete all data?")
            .setMessage("This permanently deletes wifi_logs.csv from the phone. Share or save a copy first if you need it.")
            .setPositiveButton("Delete") { _, _ ->
                LogRepository.clear(ctx)
                Toast.makeText(ctx, "All data deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
