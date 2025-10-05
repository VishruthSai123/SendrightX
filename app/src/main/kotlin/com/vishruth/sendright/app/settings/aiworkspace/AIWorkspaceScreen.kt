/*
 * Copyright (C) 2025 SendRight 4.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.app.settings.aiworkspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIWorkspaceScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCreateCustom: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val aiWorkspaceManager = remember { AIWorkspaceManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Popular", "Custom Assistance")
    
    // Scroll state for FAB animation
    val listState = remember { androidx.compose.foundation.lazy.LazyListState() }
    val isScrollingDown by remember {
        derivedStateOf {
            listState.firstVisibleItemScrollOffset > 50
        }
    }
    
    // Load actions when screen is first composed
    LaunchedEffect(Unit) {
        aiWorkspaceManager.loadActions()
    }
    
    val enabledPopularActions = aiWorkspaceManager.enabledPopularActions
    val customActions = aiWorkspaceManager.customActions
    val availablePopularActions = aiWorkspaceManager.getAvailablePopularActions()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Writing Assistance") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            androidx.compose.animation.AnimatedVisibility(
                visible = !isScrollingDown,
                enter = androidx.compose.animation.slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.tween(300)
                ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
                exit = androidx.compose.animation.slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.tween(300)
                ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
            ) {
                FloatingActionButton(
                    onClick = onNavigateToCreateCustom,
                    containerColor = Color(0xFF46BB23),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Create",
                            tint = Color.White
                        )
                        Text(
                            text = "Create",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Row with proper hover handling
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (selectedTab == index) Color(0xFF46BB23)
                                else Color.Transparent
                            )
                            .clickable { selectedTab = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (selectedTab == index) Color.White 
                                   else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold 
                                        else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Content based on selected tab
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    0 -> { // Popular tab
                        // Enabled popular actions
                        if (enabledPopularActions.isNotEmpty()) {
                            items(enabledPopularActions) { action ->
                                AIActionCard(
                                    action = action,
                                    isEnabled = true,
                                    onActionClick = {
                                        scope.launch {
                                            aiWorkspaceManager.removePopularAction(action.id)
                                        }
                                    },
                                    actionButtonText = "Remove",
                                    showRemoveButton = true
                                )
                            }
                        }
                        
                        // Available popular actions
                        if (availablePopularActions.isNotEmpty()) {
                            items(availablePopularActions) { action ->
                                AIActionCard(
                                    action = action,
                                    isEnabled = false,
                                    onActionClick = {
                                        scope.launch {
                                            aiWorkspaceManager.addPopularAction(action)
                                        }
                                    },
                                    actionButtonText = "Add",
                                    showRemoveButton = false
                                )
                            }
                        }
                    }
                    1 -> { // Custom Assistance tab
                        if (customActions.isNotEmpty()) {
                            items(customActions) { action ->
                                AIActionCard(
                                    action = action,
                                    isEnabled = true,
                                    onActionClick = {
                                        scope.launch {
                                            aiWorkspaceManager.removeCustomAction(action.id)
                                        }
                                    },
                                    actionButtonText = "Remove",
                                    showRemoveButton = true
                                )
                            }
                        } else {
                            item {
                                EmptyCustomActionsCard(onCreateClick = onNavigateToCreateCustom)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AIActionCard(
    action: AIAction,
    isEnabled: Boolean,
    onActionClick: () -> Unit,
    actionButtonText: String,
    showRemoveButton: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (action.isPopular) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = action.getIcon(),
                    contentDescription = null,
                    tint = if (action.isPopular) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = action.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Action Button
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showRemoveButton && isEnabled) {
                        Color(0xFF46BB23).copy(alpha = 0.1f) // Green for remove
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) // Blue/primary for add
                    }
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (showRemoveButton && isEnabled) {
                    Text(
                        text = "− $actionButtonText",
                        color = Color(0xFF46BB23), // Green text for remove
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "+ $actionButtonText",
                        color = MaterialTheme.colorScheme.primary, // Primary color for add
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCustomActionsCard(onCreateClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "No Custom Actions Yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Create your own AI assistance with custom prompts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onCreateClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF46BB23)
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Custom Action", color = Color.White)
            }
        }
    }
}