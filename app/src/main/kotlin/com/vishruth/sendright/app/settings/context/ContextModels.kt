/*
 * Copyright (C) 2025 SendRight 4.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.app.settings.context

import kotlinx.serialization.Serializable

/**
 * User's personal details and preferences for AI context
 */
@Serializable
data class PersonalDetails(
    val name: String = "",
    val status: String = "", // Company, Job Title, Student, etc.
    val age: Int? = null,
    val preferredLanguage: String = "English",
    val typingStyle: String = "Professional", // Polite, Professional, Casual, Gen Z
    val email: String = ""
)

/**
 * Custom variable/context that user can define
 */
@Serializable
data class CustomVariable(
    val id: String,
    val contextName: String,
    val description: String
)

/**
 * Complete context configuration containing both personal details and custom variables
 */
@Serializable
data class ContextConfiguration(
    val personalDetails: PersonalDetails = PersonalDetails(),
    val customVariables: List<CustomVariable> = emptyList(),
    val isContextActionEnabled: Boolean = false
)

/**
 * Enumeration of available languages
 */
enum class PreferredLanguage(val displayName: String) {
    ENGLISH("English"),
    HINDI("Hindi"),
    TELUGU("Telugu"),
    TAMIL("Tamil"),
    KANNADA("Kannada"),
    GUJARATI("Gujarati"),
    MARATHI("Marathi"),
    BENGALI("Bengali"),
    PUNJABI("Punjabi"),
    MALAYALAM("Malayalam")
}

/**
 * Enumeration of available typing styles
 */
enum class TypingStyle(val displayName: String) {
    POLITE("Polite"),
    PROFESSIONAL("Professional"), 
    CASUAL("Casual"),
    GEN_Z("Gen Z")
}