package com.local.bookocr.data.repository

sealed interface TitleValidationResult {
    data object Valid : TitleValidationResult
    data object Blank : TitleValidationResult
}

object BookValidation {
    fun validateTitle(title: String): TitleValidationResult =
        if (title.isBlank()) TitleValidationResult.Blank else TitleValidationResult.Valid
}
