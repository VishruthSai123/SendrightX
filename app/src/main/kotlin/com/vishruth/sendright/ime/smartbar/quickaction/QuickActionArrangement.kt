/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

package com.vishruth.key1.ime.smartbar.quickaction

import com.vishruth.key1.ime.text.keyboard.TextKeyData
import com.vishruth.key1.lib.io.DefaultJsonConfig
import dev.patrickgold.jetpref.datastore.model.PreferenceSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic

val QuickActionJsonConfig = Json(DefaultJsonConfig) {
    classDiscriminator = "$"
    encodeDefaults = false
    ignoreUnknownKeys = true
    isLenient = false

    serializersModule += SerializersModule {
        polymorphic(QuickAction::class) {
            subclass(QuickAction.InsertKey::class, QuickAction.InsertKey.serializer())
            subclass(QuickAction.InsertText::class, QuickAction.InsertText.serializer())
            defaultDeserializer { QuickAction.InsertKey.serializer() }
        }
    }
}

@Serializable
data class QuickActionArrangement(
    val stickyAction: QuickAction?,
    val dynamicActions: List<QuickAction>,
    val hiddenActions: List<QuickAction>,
) {
    operator fun contains(action: QuickAction): Boolean {
        return stickyAction == action || dynamicActions.contains(action) || hiddenActions.contains(action)
    }

    fun distinct(): QuickActionArrangement {
        val distinctSet = mutableSetOf<QuickAction>()
        if (stickyAction != null) {
            distinctSet.add(stickyAction)
        }
        val distinctDynamicActions = dynamicActions.filter { distinctSet.add(it) }
        val distinctHiddenActions = hiddenActions.filter { distinctSet.add(it) }
        return QuickActionArrangement(
            stickyAction = stickyAction,
            dynamicActions = distinctDynamicActions,
            hiddenActions = distinctHiddenActions,
        )
    }

    companion object {
        val Default = QuickActionArrangement(
            stickyAction = QuickAction.InsertKey(TextKeyData.VOICE_INPUT), // Voice Input as sticky action
            dynamicActions = listOf(
                QuickAction.InsertKey(TextKeyData.MAGIC_WAND),          // 1. Magic Wand
                QuickAction.InsertKey(TextKeyData.AI_CHAT),             // 2. AI Chat
                QuickAction.InsertKey(TextKeyData.IME_UI_MODE_MEDIA),   // 4. Emoji
                QuickAction.InsertKey(TextKeyData.IME_UI_MODE_CLIPBOARD), // 5. Clipboard
                QuickAction.InsertKey(TextKeyData.SETTINGS),            // 6. Settings
                QuickAction.InsertKey(TextKeyData.UNDO),                // 7. Undo
                QuickAction.InsertKey(TextKeyData.REDO),                // 8. Redo
                QuickAction.InsertKey(TextKeyData.CLIPBOARD_CUT),       // 9. Cut
                QuickAction.InsertKey(TextKeyData.CLIPBOARD_SELECT_ALL), // 10. Select All
                QuickAction.InsertKey(TextKeyData.CLIPBOARD_COPY),      // 11. Copy
                QuickAction.InsertKey(TextKeyData.CLIPBOARD_PASTE),     // 12. Paste
                QuickAction.InsertKey(TextKeyData.TOGGLE_INCOGNITO_MODE), // 13. Incognito
                QuickAction.InsertKey(TextKeyData.LANGUAGE_SWITCH),     // 14. Switch Language
                QuickAction.InsertKey(TextKeyData.TOGGLE_COMPACT_LAYOUT), // 15. One Handed
                QuickAction.InsertKey(TextKeyData.FORWARD_DELETE),      // 16. Forward Delete
            ),
            hiddenActions = listOf(),
        )
    }

    object Serializer : PreferenceSerializer<QuickActionArrangement> {
        override fun serialize(value: QuickActionArrangement): String {
            return QuickActionJsonConfig.encodeToString(value)
        }

        override fun deserialize(value: String): QuickActionArrangement {
            return QuickActionJsonConfig.decodeFromString(value)
        }
    }
}
