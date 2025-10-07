/*
 * Copyright (C) 2025 SendRight 4.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.app.settings.context

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

// Green theme colors - consistent across app
private val GreenPrimary = Color(0xFF46BB23)
private val GreenContainer = Color(0xFF46BB23).copy(alpha = 0.12f)
private val GreenOnContainer = Color(0xFF1B5E20)
private val GreenSurface = Color(0xFF46BB23).copy(alpha = 0.05f)
private val GreenBorder = Color(0xFF46BB23).copy(alpha = 0.3f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextConfigurationScreen(
    onNavigateBack: () -> Unit,
    onNavigateToExampleLibrary: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contextManager = remember { ContextManager.getInstance(context) }
    
    // Observe state
    val personalDetails by contextManager.personalDetails
    val customVariables = contextManager.customVariables
    val refreshCounter by contextManager.refreshCounter
    
    // Form state
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var preferredLanguage by remember { mutableStateOf("English") }
    var typingStyle by remember { mutableStateOf("Professional") }
    var email by remember { mutableStateOf("") }
    
    // Custom variable creation state
    var showNewVariableForm by remember { mutableStateOf(false) }
    var newVariableName by remember { mutableStateOf("") }
    var newVariableDescription by remember { mutableStateOf("") }
    
    // Track changes for save button state
    val hasChanges = remember {
        derivedStateOf {
            name != personalDetails.name ||
            status != personalDetails.status ||
            age != (personalDetails.age?.toString() ?: "") ||
            preferredLanguage != personalDetails.preferredLanguage ||
            typingStyle != personalDetails.typingStyle ||
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
        email = personalDetails.email
    }
    
    // Load configuration on screen start
    LaunchedEffect(Unit) {
        contextManager.loadConfiguration()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Context") },
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
        },
        floatingActionButton = {
            val canAddMore = customVariables.size < 5
            FloatingActionButton(
                onClick = { 
                    if (canAddMore) {
                        showNewVariableForm = true 
                    } else {
                        scope.launch {
                            context.showShortToast("⚠️ Maximum 5 lists allowed")
                        }
                    }
                },
                containerColor = if (canAddMore) GreenPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                contentColor = if (canAddMore) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            ) {
                Icon(
                    Icons.Default.Add, 
                    contentDescription = if (canAddMore) "Add Context List" else "Maximum lists reached"
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Personal Details Section
            item {
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
                    email = email,
                    onEmailChange = { email = it }
                )
            }
            
            // Custom Variables Section Header
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Custom Context Lists",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Create personalized context lists to enhance AI responses for specific situations",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Variable count indicator
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (customVariables.size >= 5) {
                                Color(0xFFFF6B6B).copy(alpha = 0.1f) // Red for limit reached
                            } else {
                                GreenPrimary.copy(alpha = 0.1f)
                            }
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = "${customVariables.size}/5 lists created",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (customVariables.size >= 5) {
                                Color(0xFFD32F2F)
                            } else {
                                GreenPrimary
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            
            // New Variable Form (if shown)
            if (showNewVariableForm) {
                item {
                    NewVariableForm(
                        variableName = newVariableName,
                        onVariableNameChange = { newVariableName = it },
                        variableDescription = newVariableDescription,
                        onVariableDescriptionChange = { newVariableDescription = it },
                        onSave = {
                            scope.launch {
                                if (newVariableName.isNotBlank() && newVariableDescription.isNotBlank()) {
                                    val result = contextManager.addCustomVariable(
                                        newVariableName.trim(),
                                        newVariableDescription.trim()
                                    )
                                    if (result != null) {
                                        newVariableName = ""
                                        newVariableDescription = ""
                                        showNewVariableForm = false
                                        context.showShortToast("✓ List added")
                                    } else {
                                        context.showShortToast("⚠️ Maximum 5 lists allowed")
                                    }
                                }
                            }
                        },
                        onCancel = {
                            newVariableName = ""
                            newVariableDescription = ""
                            showNewVariableForm = false
                        }
                    )
                }
            }
            
            // Custom Variables List
            items(customVariables, key = { it.id }) { variable ->
                CustomVariableCard(
                    variable = variable,
                    onDelete = {
                        scope.launch {
                            contextManager.deleteCustomVariable(variable.id)
                            context.showShortToast("✓ List deleted")
                        }
                    }
                )
            }
            
            // Empty state for custom variables
            if (customVariables.isEmpty() && !showNewVariableForm) {
                item {
                    EmptyVariablesState()
                }
            }
            
            // Example Library Button
            item {
                ExampleLibraryButton(
                    onClick = onNavigateToExampleLibrary
                )
            }
            
            // Bottom spacing for FAB
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

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
                text = "Personal Details & Preferences",
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

@Composable
private fun NewVariableForm(
    variableName: String,
    onVariableNameChange: (String) -> Unit,
    variableDescription: String,
    onVariableDescriptionChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = GreenPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New Context List",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = GreenOnContainer
                )
            }
            
            OutlinedTextField(
                value = variableName,
                onValueChange = onVariableNameChange,
                label = { Text("Context Name", color = GreenOnContainer) },
                placeholder = { Text("e.g., Telugu Sir, Project Alpha") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GreenPrimary,
                    unfocusedBorderColor = GreenPrimary.copy(alpha = 0.2f), // 20% opacity border
                    focusedLabelColor = GreenPrimary,
                    cursorColor = GreenPrimary
                )
            )
            
            OutlinedTextField(
                value = variableDescription,
                onValueChange = onVariableDescriptionChange,
                label = { Text("Description", color = GreenOnContainer) },
                placeholder = { Text("Detailed instruction or context...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GreenPrimary,
                    unfocusedBorderColor = GreenPrimary.copy(alpha = 0.2f), // 20% opacity border
                    focusedLabelColor = GreenPrimary,
                    cursorColor = GreenPrimary
                ),
                minLines = 3,
                maxLines = 5
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = GreenOnContainer
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(GreenBorder))
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onSave,
                    enabled = variableName.isNotBlank() && variableDescription.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GreenPrimary,
                        contentColor = Color.White,
                        disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun CustomVariableCard(
    variable: CustomVariable,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        tint = GreenPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = variable.contextName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = GreenOnContainer
                    )
                }
                IconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color(0xFFD32F2F)
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = variable.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray.copy(alpha = 0.8f),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun EmptyVariablesState() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Empty state card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = GreenSurface
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = GreenPrimary.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "No Custom Lists Yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = GreenOnContainer
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Create context lists to personalize AI responses for specific situations",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray.copy(alpha = 0.8f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        
        // Privacy consent card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF4CAF50)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Privacy Notice",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Text(
                        text = "We do not collect, store, or share your personal details with any third parties. All your context lists and personal information are completely saved in your local device storage and remain private.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ExampleLibraryButton(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = GreenContainer
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.LibraryBooks,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = GreenPrimary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Browse Example Library",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = GreenOnContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Explore pre-made context lists for common scenarios like emails, meetings, coding, and more",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenPrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.Explore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Open Example Library",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}