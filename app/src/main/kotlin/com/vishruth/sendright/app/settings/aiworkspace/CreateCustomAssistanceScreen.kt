/*
 * Copyright (C) 2025 SendRight 4.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.app.settings.aiworkspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import com.vishruth.key1.ime.smartbar.GeminiApiService
import org.florisboard.lib.android.showShortToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCustomAssistanceScreen(
    onNavigateBack: () -> Unit,
    onActionCreated: () -> Unit
) {
    val context = LocalContext.current
    val aiWorkspaceManager = remember { AIWorkspaceManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    var titleText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }
    var promptText by remember { mutableStateOf("") }
    var includePersonalDetails by remember { mutableStateOf(false) }
    var includeDateTime by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    
    val isFormValid = titleText.isNotBlank() && 
                     descriptionText.isNotBlank() && 
                     promptText.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Custom Assistance") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Write Instructions or Prompt For AI:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Examples:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val examples = listOf(
                        "1. Summarise the content in 50 words",
                        "2. Add more hashtags in the content",
                        "3. Write a instagram caption",
                        "4. Rewrite in natural, human-like way"
                    )
                    
                    examples.forEach { example ->
                        Text(
                            text = example,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
            
            // Title Input
            OutlinedTextField(
                value = titleText,
                onValueChange = { titleText = it },
                label = { Text("Action Title") },
                placeholder = { Text("e.g., Summarize Text", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1976D2), // Fixed blue color
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = Color(0xFF1976D2)
                )
            )
            
            // Description Input
            OutlinedTextField(
                value = descriptionText,
                onValueChange = { descriptionText = it },
                label = { Text("Short Description") },
                placeholder = { Text("e.g., Summarize content in 50 words", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1976D2), // Fixed blue color
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = Color(0xFF1976D2)
                )
            )
            
            // Prompt Input with AI Optimization Button (Expandable)
            Column(modifier = Modifier.fillMaxWidth()) {
                // AI Optimization Button - Outside input field
                var isOptimizing by remember { mutableStateOf(false) }
                
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    label = { Text("AI Prompt/Instructions") },
                    placeholder = { 
                        Text(
                            "e.g., Summarize the following text in exactly 50 words, maintaining the key points and main message:",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ) 
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 240.dp), // Expandable height
                    maxLines = Int.MAX_VALUE, // Unlimited lines for expansion
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1976D2), // Fixed blue color
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = Color(0xFF1976D2)
                    )
                )
                
                // AI Optimization Button positioned outside the input field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Button(
                        onClick = {
                            if (!isOptimizing && promptText.isNotBlank()) {
                                isOptimizing = true
                                scope.launch {
                                    try {
                                        val optimizedPrompt = optimizePromptWithAI(promptText, context)
                                        promptText = optimizedPrompt
                                    } catch (e: Exception) {
                                        context.showShortToast("Failed to optimize prompt: ${e.message}")
                                    } finally {
                                        isOptimizing = false
                                    }
                                }
                            }
                        },
                        enabled = promptText.isNotBlank() && !isOptimizing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (promptText.isNotBlank() && !isOptimizing) 
                                Color(0xFF1976D2) 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isOptimizing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Optimize Prompt with AI",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = if (isOptimizing) "Optimizing..." else "AI Optimize",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            // Context Options Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Context Options",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = "Choose what additional context to include with your AI prompt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Personal Details Checkbox
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includePersonalDetails,
                            onCheckedChange = { includePersonalDetails = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF46BB23),
                                uncheckedColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Include personal details",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Name, language preference, typing style etc.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Date/Time Checkbox
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeDateTime,
                            onCheckedChange = { includeDateTime = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF46BB23),
                                uncheckedColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Include date/time",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Current date and time for context",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Create Button
            Button(
                onClick = {
                    if (isFormValid && !isCreating) {
                        isCreating = true
                        scope.launch {
                            aiWorkspaceManager.createCustomAction(
                                title = titleText.trim(),
                                description = descriptionText.trim(),
                                prompt = promptText.trim(),
                                includePersonalDetails = includePersonalDetails,
                                includeDateTime = includeDateTime
                            )
                            onActionCreated()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = isFormValid && !isCreating,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF46BB23)
                )
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = "Create Custom Assistance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Optimizes a human-written prompt into a professional LLM prompt using AI
 */
private suspend fun optimizePromptWithAI(humanPrompt: String, context: android.content.Context): String {
    val optimizationInstruction = """
        Transform this human-written instruction into a clear, professional, and effective AI prompt.
        
        Requirements:
        • Make it precise and unambiguous
        • Use professional language while keeping it concise
        • Add specific formatting or style requirements if missing
        • Ensure it follows best practices for AI prompts
        • Maintain the original intent but improve clarity
        • Add helpful constraints or guidelines where appropriate
        • IMPORTANT: Preserve user perspective - if user says "my coach/boss/friend" or asks to "write email for leave", maintain that it's about the USER'S relationships and the USER needs help communicating
        • Ensure the prompt clearly indicates you're helping the user, not acting as the user
        
        Human instruction: "$humanPrompt"
        
        Provide ONLY the optimized prompt as your response - no explanations or prefixes.
    """.trimIndent()
    
    val result = GeminiApiService.transformText(humanPrompt, optimizationInstruction, context)
    
    return result.getOrElse { error ->
        throw Exception(error.message ?: "Failed to optimize prompt")
    }
}