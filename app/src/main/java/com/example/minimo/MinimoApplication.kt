package com.example.minimo

import android.app.Application

class MinimoApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
