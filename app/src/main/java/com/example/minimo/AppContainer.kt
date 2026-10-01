package com.example.minimo

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.minimo.data.CourseRepository

/** Manual dependency injection: one instance of everything shared, created with the application. */
class AppContainer(context: Context) {
    val courseRepository = CourseRepository(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("minimo") },
    )
}
