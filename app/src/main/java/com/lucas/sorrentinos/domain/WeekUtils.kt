package com.lucas.sorrentinos.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class WeekRange(
    val weekId: String,
    val label: String
)

object WeekUtils {
    private val idFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    private val labelFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun currentWeekRange(nowMillis: Long = System.currentTimeMillis()): WeekRange = weekRangeFor(nowMillis)

    fun weekRangeFor(timestamp: Long): WeekRange {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val start = startOfBusinessWeek(cal)
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 6) }
        val weekId = "${idFormat.format(start.time)}_${idFormat.format(end.time)}"
        val label = "${labelFormat.format(start.time)} - ${labelFormat.format(end.time)}"
        return WeekRange(weekId = weekId, label = label)
    }

    fun labelFromWeekId(weekId: String): String {
        val parts = weekId.split("_")
        if (parts.size != 2) return weekId
        val start = idFormat.parse(parts[0]) ?: return weekId
        val end = idFormat.parse(parts[1]) ?: return weekId
        return "${labelFormat.format(start)} - ${labelFormat.format(end)}"
    }

    private fun startOfBusinessWeek(reference: Calendar): Calendar {
        val start = reference.clone() as Calendar
        val day = start.get(Calendar.DAY_OF_WEEK)
        val offset = when (day) {
            Calendar.FRIDAY -> 0
            Calendar.SATURDAY -> 1
            Calendar.SUNDAY -> 2
            Calendar.MONDAY -> 3
            Calendar.TUESDAY -> 4
            Calendar.WEDNESDAY -> 5
            Calendar.THURSDAY -> 6
            else -> 0
        }
        start.add(Calendar.DAY_OF_MONTH, -offset)
        start.set(Calendar.HOUR_OF_DAY, 0)
        start.set(Calendar.MINUTE, 0)
        start.set(Calendar.SECOND, 0)
        start.set(Calendar.MILLISECOND, 0)
        return start
    }
}
