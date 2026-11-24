package com.group12.wifitrace

import android.graphics.Color

/** The three obstacle states compared in the study. */
enum class Obstacle(
    val key: String,
    val title: String,
    val color: Int,
    val dash: List<Float>?
) {
    NO_WALL("no_wall", "No wall", Color.parseColor("#26A69A"), null),
    WALL("wall", "Wall", Color.parseColor("#FFA726"), listOf(10f, 6f)),
    BODY("body_blocked", "Body blocked", Color.parseColor("#AB47BC"), listOf(2f, 6f));

    companion object {
        fun fromKey(key: String): Obstacle? = values().firstOrNull { it.key == key }
    }
}

/** The experiment plan from the project proposal: 11 conditions x 3 repeats = 33 trials. */
object Plan {
    const val REPEATS = 3
    const val READINGS_PER_TRIAL = 10

    val LOS_DISTANCES = listOf(1, 3, 5, 10, 15)
    val BLOCKED_DISTANCES = listOf(3, 5, 10)

    private val LABEL_REGEX = Regex("^(\\d+)m_(.+)$")

    fun labelFor(distance: Int, obstacle: Obstacle): String = "${distance}m_${obstacle.key}"

    val conditions: List<String> = buildList {
        LOS_DISTANCES.forEach { add(labelFor(it, Obstacle.NO_WALL)) }
        BLOCKED_DISTANCES.forEach { add(labelFor(it, Obstacle.WALL)) }
        BLOCKED_DISTANCES.forEach { add(labelFor(it, Obstacle.BODY)) }
    }

    val totalTrials: Int get() = conditions.size * REPEATS

    /** "5m_wall" -> (5, Obstacle.WALL), or null if the label is not in this format. */
    fun parse(label: String): Pair<Int, Obstacle>? {
        val m = LABEL_REGEX.matchEntire(label) ?: return null
        val obstacle = Obstacle.fromKey(m.groupValues[2]) ?: return null
        return m.groupValues[1].toInt() to obstacle
    }

    /** "5m_wall" -> "5 m · Wall" */
    fun pretty(label: String): String {
        val p = parse(label) ?: return label
        return "${p.first} m · ${p.second.title}"
    }
}
