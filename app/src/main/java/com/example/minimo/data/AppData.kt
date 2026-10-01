package com.example.minimo.data

import com.example.minimo.domain.Course
import com.example.minimo.domain.GradeSettings

/** Everything the app stores locally. */
data class AppData(
    val settings: GradeSettings = GradeSettings(),
    val courses: List<Course> = emptyList(),
)
