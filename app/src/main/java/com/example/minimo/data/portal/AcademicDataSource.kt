package com.example.minimo.data.portal

/** Where courses come from, other than manual entry. */
interface AcademicDataSource {
    /**
     * Reads the current cycle's courses. Reports progress as (modules read, modules to read).
     * @throws PortalException when the portal cannot be read.
     */
    suspend fun fetchCourses(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): PortalSnapshot
}
