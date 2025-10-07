/*
 * Copyright (C) 2025 SendRight 4.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.app.settings.aiworkspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import com.vishruth.key1.app.settings.context.ContextManager
import org.florisboard.lib.android.showShortToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIWorkspaceScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCreateCustom: () -> Unit,
    onNavigateToContext: () -> Unit = {},
    onNavigateToMagicWandSettings: () -> Unit = {}
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
    
    // Dialog states for custom action management
    var showCustomActionDialog by remember { mutableStateOf(false) }
    var selectedCustomAction by remember { mutableStateOf<AIAction?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    
    // Load actions when screen is first composed and on refresh counter changes
    LaunchedEffect(Unit) {
        aiWorkspaceManager.loadActions()
    }
    
    // Force reload when refresh counter changes
    LaunchedEffect(aiWorkspaceManager.refreshCounter.value) {
        if (aiWorkspaceManager.refreshCounter.value > 0) {
            // Small delay to ensure operations complete
            kotlinx.coroutines.delay(100)
            // Force a full reload from storage to ensure consistency
            aiWorkspaceManager.loadActions()
        }
    }
    
    val enabledPopularActions = aiWorkspaceManager.enabledPopularActions
    val customActions = aiWorkspaceManager.customActions
    val availablePopularActions = aiWorkspaceManager.getAvailablePopularActions()
    
    // Force recomposition by observing refresh counter
    val refreshCounter by aiWorkspaceManager.refreshCounter
    
    // Filter custom actions reactively - this will recompose when customActions or refreshCounter changes
    // Using remember with keys to ensure proper updates
    val enabledCustomActions by remember(customActions.size, refreshCounter) {
        derivedStateOf { customActions.filter { it.isEnabled } }
    }
    val disabledCustomActions by remember(customActions.size, refreshCounter) {
        derivedStateOf { customActions.filter { !it.isEnabled } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Writing Assistance") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToMagicWandSettings
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Section Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
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
                            items(
                                items = enabledPopularActions,
                                key = { action -> action.id }
                            ) { action ->
                                AIActionCard(
                                    action = action,
                                    isEnabled = true,
                                    onActionClick = {
                                        scope.launch {
                                            try {
                                                aiWorkspaceManager.removePopularAction(action.id)
                                            } catch (e: Exception) {
                                                // Handle error silently or show toast
                                                e.printStackTrace()
                                            }
                                        }
                                    },
                                    actionButtonText = "Remove",
                                    showRemoveButton = true
                                )
                            }
                        }
                        
                        // Available popular actions
                        if (availablePopularActions.isNotEmpty()) {
                            items(
                                items = availablePopularActions,
                                key = { action -> action.id }
                            ) { action ->
                                AIActionCard(
                                    action = action,
                                    isEnabled = false,
                                    onActionClick = {
                                        scope.launch {
                                            try {
                                                aiWorkspaceManager.addPopularAction(action)
                                            } catch (e: Exception) {
                                                // Handle error silently or show toast
                                                e.printStackTrace()
                                            }
                                        }
                                    },
                                    actionButtonText = "Add",
                                    showRemoveButton = false
                                )
                            }
                        }
                    }
                    1 -> { // Custom Assistance tab
                        // Personal Details Section (only in Custom tab)
                        item {
                            ContextActionCard(
                                onNavigateToContext = onNavigateToContext
                            )
                        }
                        
                        // Enabled custom actions
                        if (enabledCustomActions.isNotEmpty()) {
                            items(
                                items = enabledCustomActions,
                                key = { action -> action.id }
                            ) { action ->
                                CustomAIActionCard(
                                    action = action,
                                    isEnabled = true,
                                    onLongPress = {
                                        selectedCustomAction = action
                                        showCustomActionDialog = true
                                    },
                                    onRemoveClick = {
                                        scope.launch {
                                            try {
                                                aiWorkspaceManager.toggleCustomAction(action.id)
                                            } catch (e: Exception) {
                                                // Handle error silently or show toast
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        
                        // Disabled custom actions
                        if (disabledCustomActions.isNotEmpty()) {
                            items(
                                items = disabledCustomActions,
                                key = { action -> action.id }
                            ) { action ->
                                CustomAIActionCard(
                                    action = action,
                                    isEnabled = false,
                                    onLongPress = {
                                        selectedCustomAction = action
                                        showCustomActionDialog = true
                                    },
                                    onRemoveClick = {
                                        scope.launch {
                                            try {
                                                aiWorkspaceManager.toggleCustomAction(action.id)
                                            } catch (e: Exception) {
                                                // Handle error silently or show toast
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        
                        // Show empty state only if no actions at all
                        if (enabledCustomActions.isEmpty() && disabledCustomActions.isEmpty()) {
                            item {
                                EmptyCustomActionsCard(onCreateClick = onNavigateToCreateCustom)
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Custom Action Management Dialog
    if (showCustomActionDialog && selectedCustomAction != null) {
        AlertDialog(
            onDismissRequest = { 
                showCustomActionDialog = false
                selectedCustomAction = null
            },
            title = { Text("Manage Action") },
            text = { Text("What would you like to do with \"${selectedCustomAction!!.title}\"?") },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            showCustomActionDialog = false
                            showEditDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            showCustomActionDialog = false
                            showDeleteConfirmDialog = true
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showCustomActionDialog = false
                    selectedCustomAction = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog && selectedCustomAction != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteConfirmDialog = false
                selectedCustomAction = null
            },
            title = { Text("Delete Action") },
            text = { Text("Are you sure you want to delete \"${selectedCustomAction!!.title}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val actionId = selectedCustomAction?.id
                        if (actionId != null) {
                            scope.launch {
                                try {
                                    aiWorkspaceManager.deleteCustomAction(actionId)
                                } catch (e: Exception) {
                                    // Handle error silently or show toast
                                    e.printStackTrace()
                                }
                            }
                        }
                        showDeleteConfirmDialog = false
                        selectedCustomAction = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteConfirmDialog = false
                    selectedCustomAction = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Edit Dialog
    if (showEditDialog && selectedCustomAction != null) {
        EditCustomActionDialog(
            action = selectedCustomAction!!,
            onDismiss = {
                showEditDialog = false
                selectedCustomAction = null
            },
            onSave = { title, description, prompt ->
                val action = selectedCustomAction
                val actionId = action?.id
                if (actionId != null && action != null) {
                    scope.launch {
                        try {
                            aiWorkspaceManager.updateCustomAction(
                                actionId = actionId,
                                title = title,
                                description = description,
                                prompt = prompt,
                                includePersonalDetails = action.includePersonalDetails,
                                includeDateTime = action.includeDateTime
                            )
                        } catch (e: Exception) {
                            // Handle error silently or show toast
                            e.printStackTrace()
                        }
                    }
                }
                showEditDialog = false
                selectedCustomAction = null
            }
        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomAIActionCard(
    action: AIAction,
    isEnabled: Boolean,
    onLongPress: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { /* Handle regular click if needed */ },
                onLongClick = onLongPress
            ),
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
                        if (isEnabled) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = action.getIcon(),
                    contentDescription = null,
                    tint = if (isEnabled) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                    fontWeight = if (isEnabled) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = action.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Remove/Add Button based on state
            Button(
                onClick = onRemoveClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEnabled) {
                        Color(0xFF46BB23).copy(alpha = 0.12f) // Green for remove
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (isEnabled) {
                    Text(
                        text = "− Remove",
                        color = Color(0xFF46BB23),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "+ Add",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContextActionCard(
    onNavigateToContext: () -> Unit
) {
    val context = LocalContext.current
    val contextManager = remember { ContextManager.getInstance(context) }
    
    // Load context configuration
    LaunchedEffect(Unit) {
        contextManager.loadConfiguration()
    }
    
    val isConfigured = contextManager.isContextConfigured()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onNavigateToContext() },
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
                        if (isConfigured) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = if (isConfigured) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Personal Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = if (isConfigured) {
                        "Tap to view and edit your personal details"
                    } else {
                        "Add your personal details for AI personalization"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Navigation Arrow
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = "Open Personal Details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
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

@Composable
private fun EditCustomActionDialog(
    action: AIAction,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var titleText by remember { mutableStateOf(action.title) }
    var descriptionText by remember { mutableStateOf(action.description) }
    var promptText by remember { mutableStateOf(action.prompt) }
    
    val isFormValid = titleText.isNotBlank() && 
                     descriptionText.isNotBlank() && 
                     promptText.isNotBlank()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Custom Action") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Action Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    label = { Text("Short Description") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    label = { Text("AI Prompt/Instructions") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isFormValid) {
                        onSave(titleText.trim(), descriptionText.trim(), promptText.trim())
                    }
                },
                enabled = isFormValid
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}