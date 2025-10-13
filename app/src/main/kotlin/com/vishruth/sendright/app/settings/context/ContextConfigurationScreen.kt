/*
 * Copyright (C) 2025 SendRight 4.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.app.settings.context

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showShortToast

@ExperimentalMaterial3Api

// Green theme colors - consistent across app
private val GreenPrimary = Color(0xFF46BB23)
private val GreenContainer = Color(0xFF46BB23).copy(alpha = 0.12f)
private val GreenOnContainer = Color(0xFF1B5E20)
private val GreenSurface = Color(0xFF46BB23).copy(alpha = 0.05f)
private val GreenBorder = Color(0xFF46BB23).copy(alpha = 0.3f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalDetailsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contextManager = remember { ContextManager.getInstance(context) }
    
    // Observe state
    val personalDetails by contextManager.personalDetails
    val refreshCounter by contextManager.refreshCounter
    
    // Form state
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var preferredLanguage by remember { mutableStateOf("English") }
    var typingStyle by remember { mutableStateOf("Professional") }
    var responseLength by remember { mutableStateOf("Medium") }
    var email by remember { mutableStateOf("") }
    

    
    // Track changes for save button state
    val hasChanges = remember {
        derivedStateOf {
            name != personalDetails.name ||
            status != personalDetails.status ||
            age != (personalDetails.age?.toString() ?: "") ||
            preferredLanguage != personalDetails.preferredLanguage ||
            typingStyle != personalDetails.typingStyle ||
            responseLength != personalDetails.responseLength ||
            email != personalDetails.email
        }
    }
    
    // Initialize form with loaded data
    LaunchedEffect(personalDetails, refreshCounter) {
        name = personalDetails.name
        status = personalDetails.status
        age = personalDetails.age?.toString() ?: ""
        preferredLanguage = personalDetails.preferredLanguage
        typingStyle = personalDetails.typingStyle
        responseLength = personalDetails.responseLength
        email = personalDetails.email
    }
    
    // Load configuration on screen start
    LaunchedEffect(Unit) {
        contextManager.loadConfiguration()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preferences") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            scope.launch {
                                val ageInt = age.toIntOrNull()
                                val details = PersonalDetails(
                                    name = name.trim(),
                                    status = status.trim(),
                                    age = ageInt,
                                    preferredLanguage = preferredLanguage,
                                    typingStyle = typingStyle,
                                    responseLength = responseLength,
                                    email = email.trim()
                                )
                                contextManager.updatePersonalDetails(details)
                                // Show success message
                                context.showShortToast("✓ Saved")
                            }
                        },
                        enabled = hasChanges.value,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasChanges.value) GreenPrimary else Color.Gray.copy(alpha = 0.3f),
                            contentColor = Color.White,
                            disabledContainerColor = Color.Gray.copy(alpha = 0.3f),
                            disabledContentColor = Color.Gray
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = if (hasChanges.value) "Save Changes" else "Saved",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Personal Details Section
            PersonalDetailsSection(
                name = name,
                onNameChange = { name = it },
                status = status,
                onStatusChange = { status = it },
                age = age,
                onAgeChange = { age = it },
                preferredLanguage = preferredLanguage,
                onPreferredLanguageChange = { preferredLanguage = it },
                typingStyle = typingStyle,
                onTypingStyleChange = { typingStyle = it },
                responseLength = responseLength,
                onResponseLengthChange = { responseLength = it },
                email = email,
                onEmailChange = { email = it }
            )
            
            // Privacy Notice Section
            PrivacyNoticeSection()
            
            // Bottom spacing
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalDetailsSection(
    name: String,
    onNameChange: (String) -> Unit,
    status: String,
    onStatusChange: (String) -> Unit,
    age: String,
    onAgeChange: (String) -> Unit,
    preferredLanguage: String,
    onPreferredLanguageChange: (String) -> Unit,
    typingStyle: String,
    onTypingStyleChange: (String) -> Unit,
    responseLength: String,
    onResponseLengthChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Preferences and Details",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = GreenOnContainer
            )
            
            // Name field - full width
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Name", color = GreenOnContainer) },
                placeholder = { Text("Your name for personalization") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GreenPrimary,
                    unfocusedBorderColor = GreenPrimary.copy(alpha = 0.2f),
                    focusedLabelColor = GreenPrimary,
                    cursorColor = GreenPrimary
                ),
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = GreenOnContainer) }
            )
            
            // Status field - full width
            OutlinedTextField(
                value = status,
                onValueChange = onStatusChange,
                label = { Text("Status", color = GreenOnContainer) },
                placeholder = { Text("e.g., Software Engineer") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GreenPrimary,
                    unfocusedBorderColor = GreenPrimary.copy(alpha = 0.2f),
                    focusedLabelColor = GreenPrimary,
                    cursorColor = GreenPrimary
                ),
                leadingIcon = { Icon(Icons.Default.Work, contentDescription = null, tint = GreenOnContainer) }
            )
            
            // Age field - full width
            OutlinedTextField(
                value = age,
                onValueChange = onAgeChange,
                label = { Text("Age", color = GreenOnContainer) },
                placeholder = { Text("Optional") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GreenPrimary,
                    unfocusedBorderColor = GreenPrimary.copy(alpha = 0.2f),
                    focusedLabelColor = GreenPrimary,
                    cursorColor = GreenPrimary
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                leadingIcon = { Icon(Icons.Default.Cake, contentDescription = null, tint = GreenOnContainer) }
            )
            
            // Language dropdown - full width
            LanguageDropdown(
                selectedLanguage = preferredLanguage,
                onLanguageSelected = onPreferredLanguageChange,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Typing Style dropdown - full width
            TypingStyleDropdown(
                selectedStyle = typingStyle,
                onStyleSelected = onTypingStyleChange,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Response Length dropdown - full width
            ResponseLengthDropdown(
                selectedLength = responseLength,
                onLengthSelected = onResponseLengthChange,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Row 4: Email (full width)
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("Email", color = GreenOnContainer) },
                placeholder = { Text("For formal communications") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GreenPrimary,
                    unfocusedBorderColor = GreenPrimary.copy(alpha = 0.2f), // 20% opacity border
                    focusedLabelColor = GreenPrimary,
                    cursorColor = GreenPrimary
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = GreenOnContainer) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val languages = PreferredLanguage.values()
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLanguage,
            onValueChange = {},
            readOnly = true,
            label = { Text("Language", color = GreenOnContainer) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GreenPrimary,
                unfocusedBorderColor = GreenPrimary.copy(alpha = 0.2f), // 20% opacity border
                focusedLabelColor = GreenPrimary,
                cursorColor = GreenPrimary
            ),
            leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, tint = GreenOnContainer) }
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            languages.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.displayName) },
                    onClick = {
                        onLanguageSelected(language.displayName)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypingStyleDropdown(
    selectedStyle: String,
    onStyleSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val styles = TypingStyle.values()
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedStyle,
            onValueChange = {},
            readOnly = true,
            label = { Text("Typing Style", color = GreenOnContainer) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GreenPrimary,
                unfocusedBorderColor = GreenPrimary.copy(alpha = 0.2f),
                focusedLabelColor = GreenPrimary,
                cursorColor = GreenPrimary
            ),
            leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null, tint = GreenOnContainer) }
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            styles.forEach { style ->
                DropdownMenuItem(
                    text = { Text(style.displayName) },
                    onClick = {
                        onStyleSelected(style.displayName)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResponseLengthDropdown(
    selectedLength: String,
    onLengthSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val lengths = ResponseLength.values()
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLength,
            onValueChange = {},
            readOnly = true,
            label = { Text("Response Length", color = GreenOnContainer) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GreenPrimary,
                unfocusedBorderColor = GreenPrimary.copy(alpha = 0.2f),
                focusedLabelColor = GreenPrimary,
                cursorColor = GreenPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            lengths.forEach { length ->
                DropdownMenuItem(
                    text = { 
                        Column {
                            Text(length.displayName)
                            Text(
                                text = length.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onLengthSelected(length.displayName)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PrivacyNoticeSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color(0xFF4CAF50)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Privacy Notice",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "We do not collect, store, or share your preferences and details with any third parties. All your information is completely saved in your local device storage and remains private.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

