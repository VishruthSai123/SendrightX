/*
 * Copyright (C) 2025 SendRight 4.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.app.settings.aiworkspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
            
            // Prompt Input
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
                    .height(120.dp),
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1976D2), // Fixed blue color
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = Color(0xFF1976D2)
                )
            )
            
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
                                prompt = promptText.trim()
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