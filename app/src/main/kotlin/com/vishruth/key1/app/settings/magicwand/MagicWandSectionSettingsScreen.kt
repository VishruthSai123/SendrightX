/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vishruth.key1.app.settings.magicwand

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.vishruth.key1.ime.smartbar.MagicWandUsageTracker
import com.vishruth.key1.ime.smartbar.SectionSettings
import org.florisboard.lib.android.showShortToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagicWandSectionSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Get the usage tracker and section settings
    val usageTracker = remember { MagicWandUsageTracker.getInstance(context) }
    val sectionSettings by usageTracker.sectionSettings.collectAsState()
    
    // Local state for dragging
    var isDragging by remember { mutableStateOf(false) }
    var draggedItem by remember { mutableStateOf<String?>(null) }
    
    // Default section order
    val defaultSectionOrder = listOf(
        "AI Workspace",
        "Enhance", 
        "Tone Changer",
        "Advanced",
        "Study",
        "Others"
    )
    
    // Current section order (manual if set, otherwise default)
    val currentSectionOrder = if (sectionSettings.manualOrder.isNotEmpty()) {
        sectionSettings.manualOrder
    } else {
        defaultSectionOrder
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Section Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Auto Arrange Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "Auto Arrange",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sections Will Be Arranged",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Automatically According Your Usage",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = {
                                scope.launch {
                                    usageTracker.toggleAutoArrangeMode()
                                    val message = if (!sectionSettings.isAutoArrangeEnabled) {
                                        "Auto arrange enabled"
                                    } else {
                                        "Auto arrange disabled"
                                    }
                                    context.showShortToast(message)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (sectionSettings.isAutoArrangeEnabled) {
                                    Color(0xFF4CAF50) // Green when enabled
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        ) {
                            Text(
                                text = if (sectionSettings.isAutoArrangeEnabled) "Enabled" else "Enable",
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            // Manual Reorder Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column {
                            Text(
                                text = "Manual Reorder",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (sectionSettings.isAutoArrangeEnabled) {
                                    "Enable manual mode to reorder sections"
                                } else {
                                    "Use ↑↓ buttons to reorder sections"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            
            // Section Items with simple reorder buttons
            itemsIndexed(
                items = currentSectionOrder,
                key = { _, item -> item }
            ) { index, sectionTitle ->
                ReorderableSectionItem(
                    sectionTitle = sectionTitle,
                    isEnabled = !sectionSettings.isAutoArrangeEnabled,
                    onMoveUp = if (index > 0) {
                        {
                            scope.launch {
                                val newOrder = currentSectionOrder.toMutableList()
                                val item = newOrder.removeAt(index)
                                newOrder.add(index - 1, item)
                                usageTracker.updateManualOrder(newOrder)
                            }
                        }
                    } else null,
                    onMoveDown = if (index < currentSectionOrder.size - 1) {
                        {
                            scope.launch {
                                val newOrder = currentSectionOrder.toMutableList()
                                val item = newOrder.removeAt(index)
                                newOrder.add(index + 1, item)
                                usageTracker.updateManualOrder(newOrder)
                            }
                        }
                    } else null,
                    position = "${index + 1} of ${currentSectionOrder.size}"
                )
            }
        }
    }
}

@Composable
private fun ReorderableSectionItem(
    sectionTitle: String,
    isEnabled: Boolean,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    position: String
) {
    var isAnimatingUp by remember { mutableStateOf(false) }
    var isAnimatingDown by remember { mutableStateOf(false) }
    
    // Animate the item when reordering
    val animatedScale by animateFloatAsState(
        targetValue = if (isAnimatingUp || isAnimatingDown) 1.05f else 1f,
        animationSpec = tween(300),
        finishedListener = {
            isAnimatingUp = false
            isAnimatingDown = false
        }
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isEnabled) 1f else 0.6f)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isAnimatingUp || isAnimatingDown) 8.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Position indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isEnabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${position.split(" ")[0]}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    }
                )
            }
            
            Spacer(modifier = Modifier.width(20.dp))
            
            // Section info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    }
                )
                Text(
                    text = if (isEnabled) "Position $position" else "Reordering disabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    },
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            
            // Reorder buttons
            if (isEnabled) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Move up button
                    IconButton(
                        onClick = {
                            if (onMoveUp != null) {
                                isAnimatingUp = true
                                onMoveUp()
                            }
                        },
                        enabled = onMoveUp != null,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = if (onMoveUp != null) {
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move up",
                            modifier = Modifier.size(24.dp),
                            tint = if (onMoveUp != null) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            }
                        )
                    }
                    
                    // Move down button
                    IconButton(
                        onClick = {
                            if (onMoveDown != null) {
                                isAnimatingDown = true
                                onMoveDown()
                            }
                        },
                        enabled = onMoveDown != null,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = if (onMoveDown != null) {
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Move down",
                            modifier = Modifier.size(24.dp),
                            tint = if (onMoveDown != null) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            }
                        )
                    }
                }
            } else {
                // Show disabled buttons with consistent styling instead of text indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Disabled move up button
                    IconButton(
                        onClick = { },
                        enabled = false,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move up disabled",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    
                    // Disabled move down button
                    IconButton(
                        onClick = { },
                        enabled = false,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Move down disabled",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}