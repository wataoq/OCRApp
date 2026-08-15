package com.local.bookocr.ui

import android.content.Context
import com.local.bookocr.AppContainer
import com.local.bookocr.BookOcrApplication

fun Context.bookOcrContainer(): AppContainer = (applicationContext as BookOcrApplication).container
