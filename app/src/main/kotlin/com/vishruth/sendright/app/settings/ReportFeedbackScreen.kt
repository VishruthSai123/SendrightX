/*
 * Copyright (C) 2021 Patrick Goldinger
 * Copyright (C) 2024 Vishruth
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

package com.vishruth.sendright.app.settings

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vishruth.sendright.BuildConfig
import com.vishruth.sendright.R
import com.vishruth.sendright.app.LocalNavController
import com.vishruth.sendright.lib.compose.FlorisScreen
import org.florisboard.lib.compose.stringRes

// Enum for different types of reports/feedback
enum class ReportType(val displayName: String, val emailSubjectPrefix: String) {
    BUG_REPORT("Bug Report", "[BUG]"),
    FEATURE_REQUEST("Feature Request", "[FEATURE]"),
    AI_CONTENT_ISSUE("AI Content Issue", "[AI_CONTENT]"),
    PERFORMANCE_ISSUE("Performance Issue", "[PERFORMANCE]"),
    UI_UX_FEEDBACK("UI/UX Feedback", "[UI_UX]"),
    ACCESSIBILITY_ISSUE("Accessibility Issue", "[ACCESSIBILITY]"),
    PRIVACY_CONCERN("Privacy Concern", "[PRIVACY]"),
    GENERAL_FEEDBACK("General Feedback", "[FEEDBACK]")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportFeedbackScreen() = FlorisScreen {
    title = stringRes(R.string.report_feedback__title)
    previewFieldVisible = false

    content {
        val context = LocalContext.current
        val scrollState = rememberScrollState()
        
        // State variables
        var selectedReportType by remember { mutableStateOf<ReportType?>(null) }
        var expanded by remember { mutableStateOf(false) }
        var subject by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Description text
            Text(
                text = stringRes(R.string.report_feedback__info_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Report Type Dropdown
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedReportType?.displayName ?: "",
                    onValueChange = { },
                    readOnly = true,
                    label = { Text(stringRes(R.string.report_feedback__type_label)) },
                    placeholder = { Text("Select type of report") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    ReportType.values().forEach { reportType ->
                        DropdownMenuItem(
                            text = { Text(reportType.displayName) },
                            onClick = {
                                selectedReportType = reportType
                                expanded = false
                            }
                        )
                    }
                }
            }

            // Subject field
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject") },
                placeholder = { Text("Brief description of your issue or suggestion") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Message field
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text(stringRes(R.string.report_feedback__description_label)) },
                placeholder = { Text(stringRes(R.string.report_feedback__description_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                maxLines = 8
            )

            // AI Content Notice (if AI content issue is selected)
            if (selectedReportType == ReportType.AI_CONTENT_ISSUE) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = stringRes(R.string.report_feedback__ai_content_notice_description),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Privacy Notice
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringRes(R.string.report_feedback__device_info_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = stringRes(R.string.report_feedback__device_info_title),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    val deviceInfo = getDeviceInfo()
                    Text(
                        text = deviceInfo,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Send button
            Button(
                onClick = {
                    sendEmailReport(
                        context = context,
                        reportType = selectedReportType,
                        subject = subject,
                        message = message
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = selectedReportType != null && subject.isNotBlank() && message.isNotBlank()
            ) {
                Text(
                    text = stringRes(R.string.report_feedback__send_button),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

private fun getDeviceInfo(): String {
    return buildString {
        appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Build: ${Build.DISPLAY}")
        appendLine("Architecture: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
    }
}

private fun sendEmailReport(
    context: android.content.Context,
    reportType: ReportType?,
    subject: String,
    message: String
) {
    if (reportType == null) return
    
    val emailSubject = "${reportType.emailSubjectPrefix} $subject"
    val deviceInfo = getDeviceInfo()
    
    val emailBody = buildString {
        appendLine("Report Type: ${reportType.displayName}")
        appendLine("Subject: $subject")
        appendLine()
        appendLine("Description:")
        appendLine(message)
        appendLine()
        appendLine("--- Device Information ---")
        append(deviceInfo)
        
        if (reportType == ReportType.AI_CONTENT_ISSUE) {
            appendLine()
            appendLine("--- AI Content Report ---")
            appendLine("This report is submitted in compliance with Google Play policies regarding AI-generated content.")
        }
    }
    
    val emailIntent = Intent(Intent.ACTION_SEND).apply {
        type = "message/rfc822"
        putExtra(Intent.EXTRA_EMAIL, arrayOf("vishruthsait@gmail.com"))
        putExtra(Intent.EXTRA_SUBJECT, emailSubject)
        putExtra(Intent.EXTRA_TEXT, emailBody)
    }
    
    try {
        context.startActivity(Intent.createChooser(emailIntent, "Send Email"))
    } catch (e: Exception) {
        // Handle case where no email app is available
        // Could show a toast or copy to clipboard as fallback
    }
}
