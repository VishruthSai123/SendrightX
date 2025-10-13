package com.vishruth.key1.app.settings

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vishruth.key1.BuildConfig
import com.vishruth.key1.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.Preference

enum class IssueType(val displayName: String, val emailSubject: String) {
    BUG_REPORT("Bug Report", "Bug Report"),
    FEATURE_REQUEST("Feature Request", "Feature Request"),
    AI_CONTENT_ISSUE("AI Content Issue", "AI Content Policy Issue"),
    PERFORMANCE_ISSUE("Performance Issue", "Performance Issue"),
    ACCESSIBILITY_ISSUE("Accessibility Issue", "Accessibility Issue"),
    CRASH_REPORT("App Crash", "Crash Report"),
    OTHER("Other", "General Feedback")
}

@Composable
fun ReportFeedbackScreen() = FlorisScreen {
    title = "Report & Feedback"
    previewFieldVisible = false

    content {
        val context = LocalContext.current
        
        var selectedIssueType by remember { mutableStateOf(IssueType.BUG_REPORT) }
        var isDropdownExpanded by remember { mutableStateOf(false) }
        var userDescription by remember { mutableStateOf("") }
        var additionalInfo by remember { mutableStateOf("") }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Submit Feedback",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Text(
                text = "Please fill out the form below. Your submission will be formatted and sent via email.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Issue Type Dropdown
                    Text(
                        text = "Issue Type *",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    OutlinedButton(
                        onClick = { isDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedIssueType.displayName)
                        Icons.Default.KeyboardArrowDown
                    }
                    
                    DropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        IssueType.values().forEach { issueType ->
                            DropdownMenuItem(
                                text = { Text(issueType.displayName) },
                                onClick = {
                                    selectedIssueType = issueType
                                    isDropdownExpanded = false
                                }
                            )
                        }
                    }
                    
                    // Description Field
                    Text(
                        text = "Description *",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    OutlinedTextField(
                        value = userDescription,
                        onValueChange = { userDescription = it },
                        placeholder = { 
                            Text(when (selectedIssueType) {
                                IssueType.BUG_REPORT -> "Describe the bug and steps to reproduce..."
                                IssueType.FEATURE_REQUEST -> "Describe the feature you'd like to see..."
                                IssueType.AI_CONTENT_ISSUE -> "Describe the AI content issue..."
                                IssueType.CRASH_REPORT -> "Describe what you were doing when the app crashed..."
                                else -> "Describe your issue or feedback..."
                            })
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            keyboardType = KeyboardType.Text
                        )
                    )
                    
                    // Additional Information Field
                    Text(
                        text = "Additional Information",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    OutlinedTextField(
                        value = additionalInfo,
                        onValueChange = { additionalInfo = it },
                        placeholder = { Text("Any additional details, error messages, or context...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            keyboardType = KeyboardType.Text
                        )
                    )
                    
                    // Submit Button
                    Button(
                        onClick = {
                            val deviceInfo = """
Device: ${Build.MANUFACTURER} ${Build.MODEL}
Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
Device Model: ${Build.DEVICE}""".trimIndent()
                            
                            val emailBody = buildString {
                                appendLine("Issue Type: ${selectedIssueType.displayName}")
                                appendLine()
                                appendLine("Description:")
                                appendLine(userDescription.ifBlank { "No description provided" })
                                appendLine()
                                if (additionalInfo.isNotBlank()) {
                                    appendLine("Additional Information:")
                                    appendLine(additionalInfo)
                                    appendLine()
                                }
                                appendLine("Device Information:")
                                appendLine(deviceInfo)
                                appendLine()
                                appendLine("---")
                                appendLine("Thank you for helping improve SendRight!")
                            }
                            
                            val subject = "[SendRight ${selectedIssueType.emailSubject}] - ${Build.MODEL}"
                            
                            // Direct Gmail app intent
                            val gmailIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                setPackage("com.google.android.gm") // Force Gmail app
                                putExtra(Intent.EXTRA_EMAIL, arrayOf("sendrightai@gmail.com"))
                                putExtra(Intent.EXTRA_SUBJECT, subject)
                                putExtra(Intent.EXTRA_TEXT, emailBody)
                            }
                            
                            try {
                                context.startActivity(gmailIntent)
                            } catch (e: Exception) {
                                // If Gmail app not available, try generic email
                                val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "message/rfc822"
                                    putExtra(Intent.EXTRA_EMAIL, arrayOf("sendrightai@gmail.com"))
                                    putExtra(Intent.EXTRA_SUBJECT, subject)
                                    putExtra(Intent.EXTRA_TEXT, emailBody)
                                }
                                try {
                                    context.startActivity(fallbackIntent)
                                } catch (e2: Exception) {
                                    Toast.makeText(context, "Gmail app not found. Please install Gmail.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = userDescription.isNotBlank()
                    ) {
                        Text("Send via Gmail App")
                    }
                }
            }
        }
        
        Preference(
            icon = Icons.Default.BugReport,
            title = "Quick Gmail App Report",
            summary = "Send a quick bug report directly via Gmail app",
            onClick = { 
                val quickSubject = "[SendRight Quick Report] - ${Build.MODEL}"
                val quickBody = """Quick Bug Report

Please describe the issue you encountered:


Steps to Reproduce:
1. 
2. 
3. 

Device Information:
- Device: ${Build.MANUFACTURER} ${Build.MODEL}
- Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
- App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})

---
Thank you for helping improve SendRight!"""
                
                // Direct Gmail app intent
                val gmailIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage("com.google.android.gm") // Force Gmail app
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("sendrightai@gmail.com"))
                    putExtra(Intent.EXTRA_SUBJECT, quickSubject)
                    putExtra(Intent.EXTRA_TEXT, quickBody)
                }
                
                try {
                    context.startActivity(gmailIntent)
                } catch (e: Exception) {
                    // If Gmail app not available, try generic email
                    val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "message/rfc822"
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("sendrightai@gmail.com"))
                        putExtra(Intent.EXTRA_SUBJECT, quickSubject)
                        putExtra(Intent.EXTRA_TEXT, quickBody)
                    }
                    try {
                        context.startActivity(fallbackIntent)
                    } catch (e2: Exception) {
                        Toast.makeText(context, "Gmail app not found. Please install Gmail.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
        
        Preference(
            icon = Icons.AutoMirrored.Filled.Help,
            title = "Contact Information",
            summary = "Email: sendrightai@gmail.com • Response within 24-48 hours"
        )
    }
}
