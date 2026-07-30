package com.bifel.voxreader

import android.app.Application

class VoxReaderApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

