/*
 * SendRight - AI-Enhanced Android Keyboard
 * Built upon FlorisBoard by The FlorisBoard Contributors
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

package com.vishruth.sendright.app.settings.about

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vishruth.sendright.BuildConfig
import com.vishruth.sendright.R
import com.vishruth.sendright.lib.compose.FlorisScreen
import org.florisboard.lib.compose.stringRes

enum class ReportType(val displayName: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    BUG_REPORT("Bug Report", Icons.Default.BugReport),
    FEATURE_REQUEST("Feature Request", Icons.Default.Feedback),
    AI_CONTENT_ISSUE("AI Content Issue", Icons.Default.BugReport),
    APP_CRASH("App Crash", Icons.Default.BugReport),
    PERFORMANCE_ISSUE("Performance Issue", Icons.Default.BugReport),
    UI_UX_FEEDBACK("UI/UX Feedback", Icons.Default.Feedback),
    GENERAL_FEEDBACK("General Feedback", Icons.Default.Feedback),
    OTHER("Other", Icons.Default.Feedback)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportFeedbackScreen() = FlorisScreen {
    title = stringRes(R.string.report_feedback__title)
    
    val context = LocalContext.current
    var selectedReportType by remember { mutableStateOf(ReportType.BUG_REPORT) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var stepsToReproduce by remember { mutableStateOf("") }

    content {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Information
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringRes(R.string.report_feedback__info_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringRes(R.string.report_feedback__info_description),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // AI Content Reporting Notice
            if (selectedReportType == ReportType.AI_CONTENT_ISSUE) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringRes(R.string.report_feedback__ai_content_notice_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringRes(R.string.report_feedback__ai_content_notice_description),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Report Type Dropdown
            ExposedDropdownMenuBox(
                expanded = isDropdownExpanded,
                onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedReportType.displayName,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text(stringRes(R.string.report_feedback__type_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false }
                ) {
                    ReportType.values().forEach { reportType ->
                        DropdownMenuItem(
                            text = { Text(reportType.displayName) },
                            onClick = {
                                selectedReportType = reportType
                                isDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Description Field
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringRes(R.string.report_feedback__description_label)) },
                placeholder = { Text(stringRes(R.string.report_feedback__description_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 6
            )

            // Steps to Reproduce (for bugs)
            if (selectedReportType in listOf(ReportType.BUG_REPORT, ReportType.APP_CRASH, ReportType.PERFORMANCE_ISSUE, ReportType.AI_CONTENT_ISSUE)) {
                OutlinedTextField(
                    value = stepsToReproduce,
                    onValueChange = { stepsToReproduce = it },
                    label = { Text(stringRes(R.string.report_feedback__steps_label)) },
                    placeholder = { Text(stringRes(R.string.report_feedback__steps_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 5
                )
            }

            // Device Information Preview
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringRes(R.string.report_feedback__device_info_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringRes(R.string.report_feedback__device_info_description),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    SelectionContainer {
                        Text(
                            text = getDeviceInfo(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Send Report Button
            Button(
                onClick = {
                    sendEmailReport(
                        context = context,
                        reportType = selectedReportType,
                        description = description,
                        stepsToReproduce = stepsToReproduce
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = description.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null
                    )
                    Text(
                        text = stringRes(R.string.report_feedback__send_button),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun getDeviceInfo(): String {
    return buildString {
        appendLine("SendRight Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Build: ${Build.ID}")
        appendLine("Architecture: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown"}")
    }
}

private fun sendEmailReport(
    context: android.content.Context,
    reportType: ReportType,
    description: String,
    stepsToReproduce: String
) {
    val subject = "SendRight ${reportType.displayName} - ${BuildConfig.VERSION_NAME}"
    val body = buildString {
        appendLine("Report Type: ${reportType.displayName}")
        appendLine("SendRight Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine()
        
        if (reportType == ReportType.AI_CONTENT_ISSUE) {
            appendLine("⚠️ AI Content Issue Report")
            appendLine("This report concerns AI-generated content that may violate Google Play policies.")
            appendLine("Please review the content described below for compliance with current policies.")
            appendLine()
        }
        
        appendLine("Description:")
        appendLine(description)
        appendLine()
        
        if (stepsToReproduce.isNotBlank()) {
            appendLine("Steps to Reproduce:")
            appendLine(stepsToReproduce)
            appendLine()
        }
        
        appendLine("Device Information:")
        appendLine("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Build: ${Build.ID}")
        appendLine("Architecture: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown"}")
        appendLine()
        
        appendLine("Additional Context:")
        appendLine("Please provide any additional context or screenshots if applicable.")
        appendLine()
        appendLine("Thank you for helping improve SendRight!")
    }

    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:vishruthsait@gmail.com")
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }

    try {
        context.startActivity(emailIntent)
    } catch (e: Exception) {
        // Fallback to general email intent
        val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("vishruthsait@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(fallbackIntent, "Send Email"))
    }
}
