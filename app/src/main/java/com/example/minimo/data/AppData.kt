package com.example.minimo.data

import com.example.minimo.domain.Course
import com.example.minimo.domain.GradeSettings

/** Everything the app stores locally. */
data class AppData(
    val settings: GradeSettings = GradeSettings(),
    val courses: List<Course> = emptyList(),
    /** Shows developer tools (such as saving a portal page as HTML). */
    val diagnosticMode: Boolean = false,
    /** Portal activities shown as 0.00 that the student marked as a real zero (see PortalCourses.zeroKey). */
    val zeroConfirmations: Set<String> = emptySet(),
    /** The last successful sync with the portal, or `null` if there was none. */
    val lastSync: SyncInfo? = null,
)

/** @property cycle the cycle read from the portal, e.g. "02 2026". */
data class SyncInfo(val cycle: String?, val syncedAtMillis: Long)
