package com.group12.wifitrace

import kotlin.math.log10
import kotlin.math.pow

object SignalEstimator {

    /**
     * Expected RSSI = BaselineRSSI - 10 * n * log10(d / d0) - ObstacleLoss
     * d0 = 1m
     */
    fun expectedRssi(
        baseline1mDbm: Double,
        distanceMeters: Int,
        obstacle: Obstacle,
        is5Ghz: Boolean
    ): Double {
        val n = if (is5Ghz) 3.0 else 2.5
        val distanceLoss = 10.0 * n * log10(distanceMeters.coerceAtLeast(1).toDouble())
        
        val obstacleLoss = when (obstacle) {
            Obstacle.NO_WALL -> 0.0
            Obstacle.BODY -> if (is5Ghz) 5.0 else 3.5
            Obstacle.WALL -> if (is5Ghz) 12.0 else 7.0
        }
        
        return baseline1mDbm - distanceLoss - obstacleLoss
    }

    /**
     * Fits n in: RSSI = Baseline - 10 * n * log10(d)
     * n = (Baseline - RSSI) / (10 * log10(d))
     */
    fun estimatePathLossExponent(baseline1mDbm: Double, points: List<Pair<Int, Double>>): Double? {
        val validPoints = points.filter { it.first > 1 }
        if (validPoints.isEmpty()) return null
        
        return validPoints.map { (d, rssi) ->
            (baseline1mDbm - rssi) / (10.0 * log10(d.toDouble()))
        }.average()
    }

    data class Diagnostic(
        val wallLoss: Double,
        val bodyLoss: Double,
        val pathLossExponent: Double,
        val recommendations: List<String>
    )

    fun generateDiagnostics(
        baseline1mDbm: Double,
        measuredData: Map<String, Double>,
        is5Ghz: Boolean
    ): Diagnostic {
        // Calculate Wall Loss: avg(d_wall - d_no_wall)
        val wallLosses = mutableListOf<Double>()
        val bodyLosses = mutableListOf<Double>()
        
        Plan.BLOCKED_DISTANCES.forEach { d ->
            val noWall = measuredData["${d}m_no_wall"]
            val wall = measuredData["${d}m_wall"]
            val body = measuredData["${d}m_body_blocked"]
            
            if (noWall != null && wall != null) wallLosses.add(noWall - wall)
            if (noWall != null && body != null) bodyLosses.add(noWall - body)
        }
        
        val avgWallLoss = if (wallLosses.isNotEmpty()) wallLosses.average() else 0.0
        val avgBodyLoss = if (bodyLosses.isNotEmpty()) bodyLosses.average() else 0.0
        
        val noWallPoints = Plan.LOS_DISTANCES.mapNotNull { d ->
            measuredData["${d}m_no_wall"]?.let { d to it }
        }
        val estimatedN = estimatePathLossExponent(baseline1mDbm, noWallPoints) ?: (if (is5Ghz) 3.0 else 2.5)
        
        val recs = mutableListOf<String>()
        
        if (is5Ghz && avgWallLoss > 10.0) {
            recs.add("High wall attenuation detected. Consider switching to 2.4 GHz for better penetration.")
        }
        
        if (avgBodyLoss > 6.0) {
            recs.add("Significant body shielding observed. Elevate your router above head height to minimize signal blocking.")
        }
        
        if (estimatedN > 3.5) {
            recs.add("Signal drops quickly with distance (n=%.1f). Move router to a central, open location.".format(estimatedN))
        } else if (estimatedN < 2.0) {
             recs.add("Excellent signal propagation (n=%.1f). Your environment has minimal multipath interference.".format(estimatedN))
        }

        if (recs.isEmpty()) {
            recs.add("Signal performance is within normal indoor parameters.")
        }

        return Diagnostic(avgWallLoss, avgBodyLoss, estimatedN, recs)
    }
}
