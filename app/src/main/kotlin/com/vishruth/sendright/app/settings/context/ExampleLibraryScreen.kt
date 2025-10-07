/*
 * Copyright (C) 2025 SendRight 4.0
 * Licensed under the Apache License, Version 2.0
 */

package com.vishruth.key1.app.settings.context

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

data class ExampleContextItem(
    val id: String,
    val name: String,
    val description: String,
    val contextInstruction: String,
    val category: String,
    val icon: ImageVector,
    val isPopular: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExampleLibraryScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contextManager = remember { ContextManager.getInstance(context) }
    
    // Example context items
    val exampleItems = remember {
        listOf(
            ExampleContextItem(
                id = "professional_email",
                name = "Professional Email",
                description = "For formal business communications and professional correspondence",
                contextInstruction = "Please write in a professional, formal tone suitable for business emails. Use proper salutations, clear structure, and courteous language. Maintain a respectful and business-appropriate style.",
                category = "Communication",
                icon = Icons.Default.Email,
                isPopular = true
            ),
            ExampleContextItem(
                id = "coding_assistant",
                name = "Coding Assistant",
                description = "For programming help, code reviews, and technical discussions",
                contextInstruction = "Act as an experienced software developer. Provide clear, well-commented code examples. Explain technical concepts in detail and suggest best practices. Focus on clean, efficient, and maintainable code.",
                category = "Programming",
                icon = Icons.Default.Code,
                isPopular = true
            ),
            ExampleContextItem(
                id = "creative_writing",
                name = "Creative Writing",
                description = "For storytelling, creative content, and imaginative writing",
                contextInstruction = "Write in a creative, engaging style. Use vivid descriptions, compelling narratives, and imaginative language. Focus on storytelling elements like character development, plot progression, and atmospheric details.",
                category = "Writing",
                icon = Icons.Default.Edit,
                isPopular = false
            ),
            ExampleContextItem(
                id = "academic_research",
                name = "Academic Research",
                description = "For scholarly writing, research papers, and academic discussions",
                contextInstruction = "Use formal academic language with proper citations and references. Provide evidence-based arguments, maintain objectivity, and follow scholarly writing conventions. Include relevant research and data to support points.",
                category = "Academic",
                icon = Icons.Default.School,
                isPopular = true
            ),
            ExampleContextItem(
                id = "casual_conversation",
                name = "Casual Conversation",
                description = "For friendly, informal chats and everyday conversations",
                contextInstruction = "Use a friendly, conversational tone. Be approachable and relatable. Use everyday language and expressions. Keep the mood light and engaging while being helpful and informative.",
                category = "Social",
                icon = Icons.Default.Chat,
                isPopular = false
            ),
            ExampleContextItem(
                id = "meeting_notes",
                name = "Meeting Notes",
                description = "For structured meeting summaries and action items",
                contextInstruction = "Organize information in a clear, structured format. Use bullet points, headings, and numbered lists. Focus on key decisions, action items, and important details. Maintain professional clarity and conciseness.",
                category = "Business",
                icon = Icons.Default.Assignment,
                isPopular = true
            ),
            ExampleContextItem(
                id = "customer_support",
                name = "Customer Support",
                description = "For helpful, patient customer service interactions",
                contextInstruction = "Be empathetic, patient, and solution-focused. Use clear, helpful language. Acknowledge concerns and provide step-by-step guidance. Maintain a positive, professional tone throughout the interaction.",
                category = "Support",
                icon = Icons.Default.Support,
                isPopular = false
            ),
            ExampleContextItem(
                id = "social_media",
                name = "Social Media",
                description = "For engaging social media posts and online content",
                contextInstruction = "Write in an engaging, social media-friendly tone. Use hashtags appropriately, keep content concise and shareable. Focus on engagement and audience interaction while maintaining brand voice.",
                category = "Marketing",
                icon = Icons.Default.Share,
                isPopular = true
            )
        )
    }
    
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Communication", "Programming", "Writing", "Academic", "Social", "Business", "Support", "Marketing")
    
    val filteredItems = remember(selectedCategory) {
        if (selectedCategory == "All") {
            exampleItems.sortedByDescending { it.isPopular }
        } else {
            exampleItems.filter { it.category == selectedCategory }.sortedByDescending { it.isPopular }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Example Library") },
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            item {
                // Header section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = GreenSurface
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = null
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = GreenPrimary.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(64.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = null
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    Icons.Default.LibraryBooks,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = GreenPrimary
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Ready-Made Context Lists",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = GreenOnContainer,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Choose from professionally crafted context lists for different scenarios. Each list is designed to enhance AI responses for specific use cases.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
            
            item {
                // Category filter
                CategoryFilterRow(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it }
                )
            }
            
            items(filteredItems, key = { it.id }) { item ->
                ExampleContextCard(
                    item = item,
                    onAddClick = {
                        scope.launch {
                            val result = contextManager.addCustomVariable(
                                item.name,
                                item.contextInstruction
                            )
                            if (result != null) {
                                context.showShortToast("✓ ${item.name} added to your lists")
                            } else {
                                context.showShortToast("⚠️ Maximum 5 lists allowed")
                            }
                        }
                    }
                )
            }
            
            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Categories",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Horizontal scrollable row with full-width text-based chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(categories) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                    label = { 
                        Text(
                            text = category,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    },
                    modifier = Modifier.height(36.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GreenPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = GreenContainer,
                        labelColor = GreenOnContainer
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedCategory == category,
                        borderColor = if (selectedCategory == category) GreenPrimary else GreenPrimary.copy(alpha = 0.3f),
                        selectedBorderColor = GreenPrimary,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.dp
                    )
                )
            }
        }
    }
}

@Composable
private fun ExampleContextCard(
    item: ExampleContextItem,
    onAddClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header row with icon, title, popular badge and add button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Icon
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = GreenContainer
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = GreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Title and category section
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        
                        // Popular badge
                        if (item.isPopular) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFF9800).copy(alpha = 0.15f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "Popular",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE65100),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = GreenPrimary.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = item.category,
                            style = MaterialTheme.typography.labelMedium,
                            color = GreenPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Add button
                Button(
                    onClick = onAddClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GreenPrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Description
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Preview section
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = GreenContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    Text(
                        text = "Preview",
                        style = MaterialTheme.typography.labelMedium,
                        color = GreenPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Text(
                        text = item.contextInstruction.take(120) + if (item.contextInstruction.length > 120) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = GreenOnContainer,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}