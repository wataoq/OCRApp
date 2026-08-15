package com.local.bookocr

import android.app.Application

class BookOcrApplication : Application() {
    val container: AppContainer by lazy { DefaultAppContainer(this) }
}
