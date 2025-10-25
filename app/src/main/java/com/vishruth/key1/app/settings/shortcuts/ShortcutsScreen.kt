package com.vishruth.key1.app.settings.shortcuts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.vishruth.key1.R
import com.vishruth.key1.app.FlorisPreferenceStore
import com.vishruth.sendright.ime.nlp.ShortcutManager
import com.vishruth.key1.shortcutManager
import org.florisboard.lib.android.showShortToastSync
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by FlorisPreferenceStore
    val shortcutManager = context.shortcutManager().value
    
    // Use actual preferences
    var shortcutsEnabled by remember { mutableStateOf(prefs.shortcuts.enabled.get()) }
    var useDefaultShortcuts by remember { mutableStateOf(prefs.shortcuts.useDefaultShortcuts.get()) }
    
    var customShortcuts by remember { mutableStateOf(mapOf<String, String>()) }
    var showDialog by remember { mutableStateOf(false) }
    var editingShortcut by remember { mutableStateOf<Pair<String, String>?>(null) }
    var triggerText by remember { mutableStateOf("") }
    var expansionText by remember { mutableStateOf("") }
    
    // Load shortcuts on first composition
    LaunchedEffect(Unit) {
        shortcutManager.initialize()
        val (defaultShortcuts, customShortcutsList) = shortcutManager.getAllShortcuts()
        customShortcuts = customShortcutsList.associate { it.trigger to it.expansion }
    }
    
    // Default shortcuts for display
    val defaultShortcuts = ShortcutManager.DEFAULT_SHORTCUTS.associate { it.trigger to it.expansion }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Short Cuts") }
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
            // Main toggle
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable shortcuts",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Enable text expansion shortcuts like \"hru\" → \"How are you\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = shortcutsEnabled,
                                onCheckedChange = { 
                                    shortcutsEnabled = it
                                    scope.launch {
                                        prefs.shortcuts.enabled.set(it)
                                        println("DEBUG: Set shortcuts.enabled to $it")
                                        // When disabled, also disable default shortcuts
                                        if (!it) {
                                            useDefaultShortcuts = false
                                            prefs.shortcuts.useDefaultShortcuts.set(false)
                                            println("DEBUG: Disabled useDefaultShortcuts")
                                        }
                                        shortcutManager.refresh()
                                        println("DEBUG: Called shortcutManager.refresh()")
                                    }
                                }
                            )
                        }
                    }
                }
            }
            
            // Use default shortcuts toggle
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Use default shortcuts",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Include built-in shortcuts like \"hru\", \"dng\", \"omw\", etc.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useDefaultShortcuts,
                                enabled = shortcutsEnabled, // Only enable when shortcuts are enabled
                                onCheckedChange = { 
                                    useDefaultShortcuts = it
                                    scope.launch {
                                        prefs.shortcuts.useDefaultShortcuts.set(it)
                                        println("DEBUG: Set useDefaultShortcuts to $it")
                                        shortcutManager.refresh()
                                        println("DEBUG: Called shortcutManager.refresh() for useDefaultShortcuts")
                                    }
                                }
                            )
                        }
                    }
                }
            }
            
            // Info text
            item {
                Text(
                    text = "Text expansion shortcuts allow you to quickly type common phrases. Type a shortcut trigger followed by space to expand it into the full text. For example, typing \"hru \" expands to \"How are you \".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            
            // Default shortcuts section
            if (shortcutsEnabled && useDefaultShortcuts) {
                item {
                    Text(
                        text = "Default shortcuts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Text(
                        text = "These shortcuts are built-in and cannot be modified:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                items(defaultShortcuts.toList()) { (trigger, expansion) ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = trigger,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = expansion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // Custom shortcuts section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Custom shortcuts",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Create your own shortcuts:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    FloatingActionButton(
                        onClick = {
                            editingShortcut = null
                            triggerText = ""
                            expansionText = ""
                            showDialog = true
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add shortcut")
                    }
                }
            }
            
            if (customShortcuts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No custom shortcuts yet. Tap \"Add custom\" to create your first shortcut.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(customShortcuts.toList()) { (trigger, expansion) ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = trigger,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = expansion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Row {
                                IconButton(
                                    onClick = {
                                        editingShortcut = trigger to expansion
                                        triggerText = trigger
                                        expansionText = expansion
                                        showDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                                }
                                
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val success = shortcutManager.removeShortcut(trigger)
                                            if (success) {
                                                val (_, updatedCustom) = shortcutManager.getAllShortcuts()
                                                customShortcuts = updatedCustom.associate { it.trigger to it.expansion }
                                                context.showShortToastSync("Shortcut deleted")
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Add/Edit Dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = if (editingShortcut == null) {
                        "Add shortcut"
                    } else {
                        "Edit shortcut"
                    }
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Enter a short trigger (like \"hru\") that will expand to the full text when followed by space.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    OutlinedTextField(
                        value = triggerText,
                        onValueChange = { triggerText = it },
                        label = { Text("Trigger text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = expansionText,
                        onValueChange = { expansionText = it },
                        label = { Text("Expansion text") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (triggerText.isNotBlank() && expansionText.isNotBlank()) {
                            scope.launch {
                                if (editingShortcut != null) {
                                    // Remove old shortcut if trigger changed
                                    if (editingShortcut!!.first != triggerText) {
                                        shortcutManager.removeShortcut(editingShortcut!!.first)
                                    }
                                }
                                
                                val success = shortcutManager.addShortcut(triggerText.trim(), expansionText.trim())
                                println("DEBUG: Added shortcut '${triggerText.trim()}' -> '${expansionText.trim()}', success: $success")
                                if (success) {
                                    val (_, updatedCustom) = shortcutManager.getAllShortcuts()
                                    customShortcuts = updatedCustom.associate { it.trigger to it.expansion }
                                    println("DEBUG: Updated custom shortcuts: $customShortcuts")
                                    showDialog = false
                                    context.showShortToastSync(
                                        if (editingShortcut == null) "Shortcut added" else "Shortcut updated"
                                    )
                                }
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}