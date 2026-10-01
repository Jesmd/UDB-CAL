package com.example.minimo.data.portal

import com.example.minimo.domain.Course
import com.example.minimo.domain.CourseSource
import com.example.minimo.domain.Evaluation
import com.example.minimo.domain.EvaluationStatus

/** One module read from the portal, with its activities. */
data class PortalCourseData(val module: PortalModule, val activities: List<PortalActivity>)

/** Everything one sync read. [cycle] is the selected cycle on the Notas page, e.g. "02 2026". */
data class PortalSnapshot(val cycle: String?, val courses: List<PortalCourseData>)

/** Turns portal data into courses, applying the 0.00 rule. */
object PortalCourses {
    /** Stable id, so a course keeps its goal across syncs. */
    fun courseId(code: String) = "portal-$code"

    /** Key of a "this 0.00 is a real zero" choice: course code + activity name. */
    fun zeroKey(courseCode: String, activityName: String) = "$courseCode|$activityName"

    /**
     * The portal shows ungraded activities as 0.00. A grade above 0 is graded; 0.00 is pending unless the
     * student marked it as a real zero ([confirmedZeros]). If the activity is renamed, the choice no longer
     * matches and it goes back to pending.
     */
    fun toCourse(data: PortalCourseData, confirmedZeros: Set<String>): Course {
        val code = data.module.code
        return Course(
            id = courseId(code),
            code = code,
            name = data.module.name,
            source = CourseSource.PORTAL,
            goal = null,
            evaluations = data.activities.mapIndexed { index, activity ->
                val status = when {
                    activity.grade.signum() > 0 -> EvaluationStatus.Graded(activity.grade)
                    zeroKey(code, activity.name) in confirmedZeros -> EvaluationStatus.Graded(activity.grade)
                    else -> EvaluationStatus.Pending
                }
                Evaluation("portal-$code-$index", activity.name, activity.weight, status)
            },
        )
    }
}
