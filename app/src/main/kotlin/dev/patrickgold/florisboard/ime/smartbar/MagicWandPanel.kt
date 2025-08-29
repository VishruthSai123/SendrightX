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

package dev.patrickgold.florisboard.ime.smartbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.theme.FlorisImeTheme
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggButton
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggText

data class MagicWandSection(
    val title: String,
    val buttons: List<String>
)

data class MagicWandButton(
    val title: String
)

@Composable
fun MagicWandPanel(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val scope = rememberCoroutineScope()
    
    val magicWandSections = remember {
        listOf(
            MagicWandSection(
                title = "Advanced",
                buttons = listOf("Rewrite", "Summarise", "Explain", "Letter", "Optimise", "Formal", "Post Ready")
            ),
            MagicWandSection(
                title = "Tone Changer", 
                buttons = listOf("Casual", "Friendly", "Professional", "Flirty", "Anger", "Happy")
            ),
            MagicWandSection(
                title = "Other",
                buttons = listOf("Emojie", "Translate", "Ask")
            )
        )
    }

    SnyggBox(
        elementName = FlorisImeUi.SmartbarActionsOverflow.elementName,
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardUiHeight())
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(magicWandSections) { section ->
                MagicWandSectionItem(
                    section = section,
                    onButtonClick = { buttonTitle ->
                        scope.launch {
                            handleMagicWandButtonClick(
                                buttonTitle = buttonTitle,
                                editorInstance = editorInstance,
                                context = context
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MagicWandSectionItem(
    section: MagicWandSection,
    onButtonClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SnyggText(
                elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                text = section.title,
                modifier = Modifier.weight(1f)
            )
            
            SnyggIcon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                modifier = Modifier.size(20.dp)
            )
        }
        
        // Expanded Content
        if (isExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chunks = section.buttons.chunked(2)
                chunks.forEach { rowButtons ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowButtons.forEach { buttonTitle ->
                            MagicWandButton(
                                button = MagicWandButton(title = buttonTitle),
                                onClick = { onButtonClick(buttonTitle) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Fill remaining space if odd number of buttons
                        if (rowButtons.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MagicWandButton(
    button: MagicWandButton,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SnyggButton(
        elementName = FlorisImeUi.SmartbarActionTile.elementName,
        onClick = onClick,
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            SnyggText(
                elementName = FlorisImeUi.SmartbarActionTileText.elementName,
                text = button.title
            )
        }
    }
}

private suspend fun handleMagicWandButtonClick(
    buttonTitle: String,
    editorInstance: dev.patrickgold.florisboard.ime.editor.EditorInstance,
    context: android.content.Context
) {
    try {
        // Get all text from the input field
        val activeContent = editorInstance.activeContent
        val allText = buildString {
            append(activeContent.textBeforeSelection)
            append(activeContent.selectedText)
            append(activeContent.textAfterSelection)
        }
        
        if (allText.isBlank()) {
            context.showShortToast("Please type some text to transform")
            return
        }
        
        // Show processing message
        context.showShortToast("Processing...")
        
        // Get instruction for the button
        val instruction = MagicWandInstructions.getInstructionForButton(buttonTitle)
        
        // Call Gemini API
        val result = GeminiApiService.transformText(allText, instruction)
        
        result.onSuccess { transformedText ->
            // Replace all text with transformed text
            val activeContent = editorInstance.activeContent
            val totalTextLength = activeContent.textBeforeSelection.length + 
                                 activeContent.selectedText.length + 
                                 activeContent.textAfterSelection.length
            
            // Select all text by setting selection from 0 to total length
            editorInstance.setSelection(0, totalTextLength)
            editorInstance.deleteSelectedText()
            editorInstance.commitText(transformedText)
            context.showShortToast("Text transformed!")
        }.onFailure { error ->
            context.showShortToast("❌ Error: ${error.message}")
        }
        
    } catch (e: Exception) {
        context.showShortToast("❌ Unexpected error: ${e.message}")
    }
}
