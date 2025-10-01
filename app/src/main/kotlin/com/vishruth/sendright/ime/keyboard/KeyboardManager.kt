/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package com.vishruth.key1.ime.keyboard

import android.content.Context
import android.icu.lang.UCharacter
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.MutableLiveData
import com.vishruth.key1.FlorisImeService
import com.vishruth.key1.R
import com.vishruth.key1.app.FlorisPreferenceStore
import com.vishruth.key1.appContext
import com.vishruth.key1.clipboardManager
import com.vishruth.key1.editorInstance
import com.vishruth.key1.extensionManager
import com.vishruth.key1.glideTypingManager
import com.vishruth.key1.ime.ImeUiMode
import com.vishruth.key1.ime.core.DisplayLanguageNamesIn
import com.vishruth.key1.ime.core.Subtype
import com.vishruth.key1.ime.core.SubtypePreset
import com.vishruth.key1.ime.editor.EditorContent
import com.vishruth.key1.ime.editor.FlorisEditorInfo
import com.vishruth.key1.ime.editor.ImeOptions
import com.vishruth.key1.ime.editor.InputAttributes
import com.vishruth.key1.ime.editor.OperationUnit
import com.vishruth.key1.ime.input.CapitalizationBehavior
import com.vishruth.key1.ime.input.InputEventDispatcher
import com.vishruth.key1.ime.input.InputKeyEventReceiver
import com.vishruth.key1.ime.input.InputShiftState
import com.vishruth.key1.ime.nlp.ClipboardSuggestionCandidate
import com.vishruth.key1.ime.nlp.PunctuationRule
import com.vishruth.key1.ime.nlp.SuggestionCandidate
import com.vishruth.key1.ime.onehanded.OneHandedMode
import com.vishruth.key1.ime.popup.PopupMappingComponent
import com.vishruth.key1.ime.text.composing.Composer
import com.vishruth.key1.ime.text.gestures.SwipeAction
import com.vishruth.key1.ime.text.key.KeyCode
import com.vishruth.key1.ime.text.key.KeyType
import com.vishruth.key1.ime.text.key.UtilityKeyAction
import com.vishruth.key1.ime.text.keyboard.TextKeyData
import com.vishruth.key1.ime.text.keyboard.TextKeyboardCache
import com.vishruth.key1.ime.media.emoji.Emoji
import com.vishruth.key1.lib.devtools.LogTopic
import com.vishruth.key1.lib.devtools.flogError
import com.vishruth.key1.lib.ext.ExtensionComponentName
import com.vishruth.key1.lib.titlecase
import com.vishruth.key1.lib.uppercase
import com.vishruth.key1.lib.util.InputMethodUtils
import com.vishruth.key1.nlpManager
import com.vishruth.key1.subtypeManager
import java.lang.ref.WeakReference

// Autosave feature imports
import com.vishruth.key1.ime.dictionary.DictionaryManager
import com.vishruth.key1.ime.dictionary.FREQUENCY_DEFAULT
import com.vishruth.key1.ime.dictionary.FREQUENCY_MAX
import com.vishruth.key1.ime.dictionary.UserDictionaryEntry
import com.vishruth.key1.ime.nlp.latin.LatinLanguageProvider
import com.vishruth.key1.ime.text.gestures.StatisticalGlideTypingClassifier
import com.vishruth.key1.lib.devtools.flogDebug
import org.florisboard.lib.kotlin.guardedByLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.florisboard.lib.android.AndroidKeyguardManager
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.android.showLongToastSync
import org.florisboard.lib.android.showShortToastSync
import org.florisboard.lib.android.systemService
import org.florisboard.lib.kotlin.collectIn
import org.florisboard.lib.kotlin.collectLatestIn
import java.util.concurrent.atomic.AtomicInteger

private val DoubleSpacePeriodMatcher = """([^.!?‽\s]\s)""".toRegex()

class KeyboardManager(context: Context) : InputKeyEventReceiver {
    private val prefs by FlorisPreferenceStore
    private val appContext by context.appContext()
    private val clipboardManager by context.clipboardManager()
    private val editorInstance by context.editorInstance()
    private val extensionManager by context.extensionManager()
    private val nlpManager by context.nlpManager()
    private val subtypeManager by context.subtypeManager()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val layoutManager = LayoutManager(context)
    private val keyboardCache = TextKeyboardCache()

    val resources = KeyboardManagerResources()
    val activeState = ObservableKeyboardState.new()
    var smartbarVisibleDynamicActionsCount by mutableIntStateOf(0)
    private var lastToastReference = WeakReference<Toast>(null)

    private val activeEvaluatorGuard = Mutex(locked = false)
    private var activeEvaluatorVersion = AtomicInteger(0)
    private val _activeEvaluator = MutableStateFlow<ComputingEvaluator>(DefaultComputingEvaluator)
    val activeEvaluator get() = _activeEvaluator.asStateFlow()
    private val _activeSmartbarEvaluator = MutableStateFlow<ComputingEvaluator>(DefaultComputingEvaluator)
    val activeSmartbarEvaluator get() = _activeSmartbarEvaluator.asStateFlow()
    private val _lastCharactersEvaluator = MutableStateFlow<ComputingEvaluator>(DefaultComputingEvaluator)
    val lastCharactersEvaluator get() = _lastCharactersEvaluator.asStateFlow()

    val inputEventDispatcher = InputEventDispatcher.new(
        repeatableKeyCodes = intArrayOf(
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.DELETE,
            KeyCode.FORWARD_DELETE,
            KeyCode.UNDO,
            KeyCode.REDO,
        )
    ).also { it.keyEventReceiver = this }

    init {
        scope.launch(Dispatchers.Main.immediate) {
            resources.anyChanged.observeForever {
                updateActiveEvaluators {
                    keyboardCache.clear()
                }
            }
            prefs.keyboard.numberRow.asFlow().collectLatestIn(scope) {
                updateActiveEvaluators {
                    keyboardCache.clear(KeyboardMode.CHARACTERS)
                }
            }
            prefs.keyboard.hintedNumberRowEnabled.asFlow().collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            prefs.keyboard.hintedSymbolsEnabled.asFlow().collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            prefs.keyboard.utilityKeyEnabled.asFlow().collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            prefs.keyboard.utilityKeyAction.asFlow().collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            activeState.collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            subtypeManager.subtypesFlow.collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            subtypeManager.activeSubtypeFlow.collectLatestIn(scope) {
                reevaluateInputShiftState()
                updateActiveEvaluators()
                editorInstance.refreshComposing()
                resetSuggestions(editorInstance.activeContent)
            }
            clipboardManager.primaryClipFlow.collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            editorInstance.activeContentFlow.collectIn(scope) { content ->
                resetSuggestions(content)
            }
            prefs.devtools.enabled.asFlow().collectLatestIn(scope) {
                reevaluateDebugFlags()
            }
            prefs.devtools.showDragAndDropHelpers.asFlow().collectLatestIn(scope) {
                reevaluateDebugFlags()
            }
        }
    }

    private fun updateActiveEvaluators(action: () -> Unit = { }) = scope.launch {
        activeEvaluatorGuard.withLock {
            action()
            val editorInfo = editorInstance.activeInfo
            val state = activeState.snapshot()
            val subtype = subtypeManager.activeSubtype
            val mode = state.keyboardMode
            // We need to reset the snapshot input shift state for non-character layouts, because the shift mechanic
            // only makes sense for the character layouts.
            if (mode != KeyboardMode.CHARACTERS) {
                state.inputShiftState = InputShiftState.UNSHIFTED
            }
            val computedKeyboard = keyboardCache.getOrElseAsync(mode, subtype) {
                layoutManager.computeKeyboardAsync(
                    keyboardMode = mode,
                    subtype = subtype,
                ).await()
            }
            val computingEvaluator = ComputingEvaluatorImpl(
                version = activeEvaluatorVersion.getAndAdd(1),
                keyboard = computedKeyboard,
                editorInfo = editorInfo,
                state = state,
                subtype = subtype,
            )
            for (key in computedKeyboard.keys()) {
                key.compute(computingEvaluator)
                key.computeLabelsAndDrawables(computingEvaluator)
            }
            _activeEvaluator.value = computingEvaluator
            _activeSmartbarEvaluator.value = computingEvaluator.asSmartbarQuickActionsEvaluator()
            if (computedKeyboard.mode == KeyboardMode.CHARACTERS) {
                _lastCharactersEvaluator.value = computingEvaluator
            }
        }
    }

    fun reevaluateInputShiftState() {
        if (activeState.inputShiftState != InputShiftState.CAPS_LOCK && !inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
            val shift = prefs.correction.autoCapitalization.get()
                && subtypeManager.activeSubtype.primaryLocale.supportsCapitalization
                && editorInstance.activeCursorCapsMode != InputAttributes.CapsMode.NONE
            activeState.inputShiftState = when {
                shift -> InputShiftState.SHIFTED_AUTOMATIC
                else -> InputShiftState.UNSHIFTED
            }
        }
    }

    fun resetSuggestions(content: EditorContent) {
        // Suggestions should only show if:
        // 1. Composing is enabled (safe for this input field type) AND
        // 2. Suggestions are enabled in preferences
        if (!activeState.isComposingEnabled || !nlpManager.isSuggestionOn()) {
            nlpManager.clearSuggestions()
            return
        }
        // Always trigger suggestion update, even if composing might be disabled temporarily
        nlpManager.suggest(subtypeManager.activeSubtype, content)
    }

    /**
     * @return If the language switch should be shown.
     */
    fun shouldShowLanguageSwitch(): Boolean {
        return subtypeManager.subtypes.size > 1
    }

    suspend fun toggleOneHandedMode() {
        prefs.keyboard.oneHandedModeEnabled.set(!prefs.keyboard.oneHandedModeEnabled.get())
    }

    fun executeSwipeAction(swipeAction: SwipeAction) {
        val keyData = when (swipeAction) {
            SwipeAction.CYCLE_TO_PREVIOUS_KEYBOARD_MODE -> when (activeState.keyboardMode) {
                KeyboardMode.CHARACTERS -> TextKeyData.VIEW_NUMERIC_ADVANCED
                KeyboardMode.NUMERIC_ADVANCED -> TextKeyData.VIEW_SYMBOLS2
                KeyboardMode.SYMBOLS2 -> TextKeyData.VIEW_SYMBOLS
                else -> TextKeyData.VIEW_CHARACTERS
            }
            SwipeAction.CYCLE_TO_NEXT_KEYBOARD_MODE -> when (activeState.keyboardMode) {
                KeyboardMode.CHARACTERS -> TextKeyData.VIEW_SYMBOLS
                KeyboardMode.SYMBOLS -> TextKeyData.VIEW_SYMBOLS2
                KeyboardMode.SYMBOLS2 -> TextKeyData.VIEW_NUMERIC_ADVANCED
                else -> TextKeyData.VIEW_CHARACTERS
            }
            SwipeAction.DELETE_WORD -> TextKeyData.DELETE_WORD
            SwipeAction.HIDE_KEYBOARD -> TextKeyData.IME_HIDE_UI
            SwipeAction.INSERT_SPACE -> TextKeyData.SPACE
            SwipeAction.MOVE_CURSOR_DOWN -> TextKeyData.ARROW_DOWN
            SwipeAction.MOVE_CURSOR_UP -> TextKeyData.ARROW_UP
            SwipeAction.MOVE_CURSOR_LEFT -> TextKeyData.ARROW_LEFT
            SwipeAction.MOVE_CURSOR_RIGHT -> TextKeyData.ARROW_RIGHT
            SwipeAction.MOVE_CURSOR_START_OF_LINE -> TextKeyData.MOVE_START_OF_LINE
            SwipeAction.MOVE_CURSOR_END_OF_LINE -> TextKeyData.MOVE_END_OF_LINE
            SwipeAction.MOVE_CURSOR_START_OF_PAGE -> TextKeyData.MOVE_START_OF_PAGE
            SwipeAction.MOVE_CURSOR_END_OF_PAGE -> TextKeyData.MOVE_END_OF_PAGE
            SwipeAction.SHIFT -> TextKeyData.SHIFT
            SwipeAction.REDO -> TextKeyData.REDO
            SwipeAction.UNDO -> TextKeyData.UNDO
            SwipeAction.SHOW_INPUT_METHOD_PICKER -> TextKeyData.SYSTEM_INPUT_METHOD_PICKER
            SwipeAction.SHOW_SUBTYPE_PICKER -> TextKeyData.SHOW_SUBTYPE_PICKER
            SwipeAction.SWITCH_TO_CLIPBOARD_CONTEXT -> TextKeyData.IME_UI_MODE_CLIPBOARD
            SwipeAction.SWITCH_TO_PREV_SUBTYPE -> TextKeyData.IME_PREV_SUBTYPE
            SwipeAction.SWITCH_TO_NEXT_SUBTYPE -> TextKeyData.IME_NEXT_SUBTYPE
            SwipeAction.SWITCH_TO_PREV_KEYBOARD -> TextKeyData.SYSTEM_PREV_INPUT_METHOD
            SwipeAction.TOGGLE_SMARTBAR_VISIBILITY -> TextKeyData.TOGGLE_SMARTBAR_VISIBILITY
            else -> null
        }
        if (keyData != null) {
            inputEventDispatcher.sendDownUp(keyData)
        }
    }

    fun commitCandidate(candidate: SuggestionCandidate) {
        scope.launch {
            candidate.sourceProvider?.notifySuggestionAccepted(subtypeManager.activeSubtype, candidate)
        }
        when (candidate) {
            is ClipboardSuggestionCandidate -> editorInstance.commitClipboardItem(candidate.clipboardItem)
            else -> editorInstance.commitCompletion(candidate)
        }
    }

    fun commitGesture(word: String) {
        editorInstance.commitGesture(fixCase(word))
    }

    /**
     * Changes a word to the current case.
     * eg if [KeyboardState.isUppercase] is true, abc -> ABC
     *    if [caps]     is true, abc -> Abc
     *    otherwise            , abc -> abc
     */
    fun fixCase(word: String): String {
        return when(activeState.inputShiftState) {
            InputShiftState.CAPS_LOCK -> {
                word.uppercase(subtypeManager.activeSubtype.primaryLocale)
            }
            InputShiftState.SHIFTED_MANUAL, InputShiftState.SHIFTED_AUTOMATIC -> {
                word.titlecase(subtypeManager.activeSubtype.primaryLocale)
            }
            else -> word
        }
    }

    /**
     * Handles [KeyCode] arrow and move events, behaves differently depending on text selection.
     */
    fun handleArrow(code: Int, count: Int = 1) = editorInstance.apply {
        val isShiftPressed = activeState.isManualSelectionMode || inputEventDispatcher.isPressed(KeyCode.SHIFT)
        val content = activeContent
        val selection = content.selection
        when (code) {
            KeyCode.ARROW_LEFT -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, meta(shift = isShiftPressed), count)
            }
            KeyCode.ARROW_RIGHT -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, meta(shift = isShiftPressed), count)
            }
            KeyCode.ARROW_UP -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_UP, meta(shift = isShiftPressed), count)
            }
            KeyCode.ARROW_DOWN -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, meta(shift = isShiftPressed), count)
            }
            KeyCode.MOVE_START_OF_PAGE -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_UP, meta(alt = true, shift = isShiftPressed), count)
            }
            KeyCode.MOVE_END_OF_PAGE -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, meta(alt = true, shift = isShiftPressed), count)
            }
            KeyCode.MOVE_START_OF_LINE -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, meta(alt = true, shift = isShiftPressed), count)
            }
            KeyCode.MOVE_END_OF_LINE -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, meta(alt = true, shift = isShiftPressed), count)
            }
        }
    }

    /**
     * Handles a [KeyCode.CLIPBOARD_SELECT] event.
     */
    private fun handleClipboardSelect() {
        val activeSelection = editorInstance.activeContent.selection
        activeState.isManualSelectionMode = if (activeSelection.isSelectionMode) {
            if (activeState.isManualSelectionMode && activeState.isManualSelectionModeStart) {
                editorInstance.setSelection(activeSelection.start, activeSelection.start)
            } else {
                editorInstance.setSelection(activeSelection.end, activeSelection.end)
            }
            false
        } else {
            !activeState.isManualSelectionMode
        }
    }

    private fun revertPreviouslyAcceptedCandidate() {
        editorInstance.phantomSpace.candidateForRevert?.let { candidateForRevert ->
            candidateForRevert.sourceProvider?.let { sourceProvider ->
                scope.launch {
                    sourceProvider.notifySuggestionReverted(
                        subtype = subtypeManager.activeSubtype,
                        candidate = candidateForRevert,
                    )
                }
            }
        }
    }

    /**
     * Handles a [KeyCode.DELETE] event.
     */
    private fun handleBackwardDelete(unit: OperationUnit) {
        if (inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
            return handleForwardDelete(unit)
        }
        activeState.batchEdit {
            it.isManualSelectionMode = false
            it.isManualSelectionModeStart = false
            it.isManualSelectionModeEnd = false
        }
        revertPreviouslyAcceptedCandidate()
        editorInstance.deleteBackwards(unit)
    }

    /**
     * Handles a [KeyCode.FORWARD_DELETE] event.
     */
    private fun handleForwardDelete(unit: OperationUnit) {
        activeState.batchEdit {
            it.isManualSelectionMode = false
            it.isManualSelectionModeStart = false
            it.isManualSelectionModeEnd = false
        }
        revertPreviouslyAcceptedCandidate()
        editorInstance.deleteForwards(unit)
    }

    /**
     * Handles a [KeyCode.ENTER] event.
     */
    private fun handleEnter() {
        val info = editorInstance.activeInfo
        val isShiftPressed = inputEventDispatcher.isPressed(KeyCode.SHIFT)
        if (editorInstance.tryPerformEnterCommitRaw()) {
            return
        }
        if (info.imeOptions.flagNoEnterAction || info.inputAttributes.flagTextMultiLine && isShiftPressed) {
            editorInstance.performEnter()
        } else {
            when (val action = info.imeOptions.action) {
                ImeOptions.Action.DONE,
                ImeOptions.Action.GO,
                ImeOptions.Action.NEXT,
                ImeOptions.Action.PREVIOUS,
                ImeOptions.Action.SEARCH,
                ImeOptions.Action.SEND -> {
                    editorInstance.performEnterAction(action)
                }
                else -> editorInstance.performEnter()
            }
        }
    }

    /**
     * Handles a [KeyCode.LANGUAGE_SWITCH] event. Also handles if the language switch should cycle
     * FlorisBoard internal or system-wide.
     */
    private fun handleLanguageSwitch() {
        when (prefs.keyboard.utilityKeyAction.get()) {
            UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS,
            UtilityKeyAction.SWITCH_LANGUAGE -> subtypeManager.switchToNextSubtype()
            else -> FlorisImeService.switchToNextInputMethod()
        }
    }

    /**
     * Handles a [KeyCode.SHIFT] down event.
     */
    private fun handleShiftDown(data: KeyData) {
        val prefs = prefs.keyboard.capitalizationBehavior
        when (prefs.get()) {
            CapitalizationBehavior.CAPSLOCK_BY_DOUBLE_TAP -> {
                if (inputEventDispatcher.isConsecutiveDown(data)) {
                    activeState.inputShiftState = InputShiftState.CAPS_LOCK
                } else {
                    if (activeState.inputShiftState == InputShiftState.UNSHIFTED) {
                        activeState.inputShiftState = InputShiftState.SHIFTED_MANUAL
                    } else {
                        activeState.inputShiftState = InputShiftState.UNSHIFTED
                    }
                }
            }
            CapitalizationBehavior.CAPSLOCK_BY_CYCLE -> {
                activeState.inputShiftState = when (activeState.inputShiftState) {
                    InputShiftState.UNSHIFTED -> InputShiftState.SHIFTED_MANUAL
                    InputShiftState.SHIFTED_MANUAL -> InputShiftState.CAPS_LOCK
                    InputShiftState.SHIFTED_AUTOMATIC -> InputShiftState.UNSHIFTED
                    InputShiftState.CAPS_LOCK -> InputShiftState.UNSHIFTED
                }
            }
        }
    }

    /**
     * Handles a [KeyCode.SHIFT] up event.
     */
    private fun handleShiftUp(data: KeyData) {
        if (activeState.inputShiftState != InputShiftState.CAPS_LOCK && !inputEventDispatcher.isAnyPressed() &&
            !inputEventDispatcher.isUninterruptedEventSequence(data)) {
            activeState.inputShiftState = InputShiftState.UNSHIFTED
        }
    }

    /**
     * Handles a [KeyCode.CAPS_LOCK] event.
     */
    private fun handleCapsLock() {
        activeState.inputShiftState = InputShiftState.CAPS_LOCK
    }

    /**
     * Handles a [KeyCode.SHIFT] cancel event.
     */
    private fun handleShiftCancel() {
        activeState.inputShiftState = InputShiftState.UNSHIFTED
    }

    /**
     * Handles a hardware [KeyEvent.KEYCODE_SPACE] event. Same as [handleSpace],
     * but skips handling changing to characters keyboard and double space periods.
     */
    fun handleHardwareKeyboardSpace() {
        val candidate = nlpManager.getAutoCommitCandidate()
        // Auto-commit if we have a candidate that's explicitly eligible for auto-commit
        // Only auto-commit for high-confidence candidates to prevent unwanted changes
        // This prevents issues where "the" gets inserted when space is pressed
        if (candidate != null && candidate.isEligibleForAutoCommit && candidate.confidence > 0.9) {
            commitCandidate(candidate)
        }
        // Skip handling changing to characters keyboard and double space periods
        // TODO: this is whether we commit space after selecting candidate. Should be determined by SuggestionProvider
        if (!subtypeManager.activeSubtype.primaryLocale.supportsAutoSpace &&
                candidate != null) { /* Do nothing */ } else {
            editorInstance.commitText(KeyCode.SPACE.toChar().toString())
        }
    }

    /**
     * Handles a [KeyCode.SPACE] event. Also handles the auto-correction of two space taps if
     * enabled by the user.
     */
    private fun handleSpace(data: KeyData) {
        val candidate = nlpManager.getAutoCommitCandidate()
        
        // Auto-commit if we have a candidate that's explicitly eligible for auto-commit
        // Only auto-commit for high-confidence candidates to prevent unwanted changes
        // This prevents issues where "the" gets inserted when space is pressed
        if (candidate != null && candidate.isEligibleForAutoCommit && candidate.confidence > 0.9) {
            commitCandidate(candidate)
        }
        
        // Autosave the previous word when space is pressed (similar to Gboard)
        autosavePreviousWord()
        
        if (prefs.keyboard.spaceBarSwitchesToCharacters.get()) {
            when (activeState.keyboardMode) {
                KeyboardMode.NUMERIC_ADVANCED,
                KeyboardMode.SYMBOLS,
                KeyboardMode.SYMBOLS2 -> {
                    activeState.keyboardMode = KeyboardMode.CHARACTERS
                }
                else -> { /* Do nothing */ }
            }
        }
        if (prefs.correction.doubleSpacePeriod.get()) {
            if (inputEventDispatcher.isConsecutiveUp(data)) {
                val text = editorInstance.run { activeContent.getTextBeforeCursor(2) }
                if (text.length == 2 && DoubleSpacePeriodMatcher.matches(text)) {
                    editorInstance.deleteBackwards(OperationUnit.CHARACTERS)
                    editorInstance.commitText(". ")
                    return
                }
            }
        }
        // TODO: this is whether we commit space after selecting candidate. Should be determined by SuggestionProvider
        if (!subtypeManager.activeSubtype.primaryLocale.supportsAutoSpace &&
                candidate != null) { /* Do nothing */ } else {
            editorInstance.commitText(KeyCode.SPACE.toChar().toString())
        }
    }

    /**
     * Autosaves the previous word when space is pressed, similar to Gboard functionality.
     * This captures words that users type which are not in the static dictionary.
     */
    private fun autosavePreviousWord() {
        scope.launch {
            try {
                // Get the current editor content
                val content = editorInstance.activeContent
                
                // Get text before cursor to extract the last word
                // We want to get the text before the space was pressed, so we get 100 characters before cursor
                // and then extract the last word from that text
                val textBeforeCursor = editorInstance.run { content.getTextBeforeCursor(100) }
                
                // Extract the last word (everything after the last space or from the beginning if no space)
                val lastWord = if (textBeforeCursor.isNotEmpty()) {
                    // Split by whitespace and get the last non-empty part
                    val words = textBeforeCursor.trim().split("\\s+".toRegex())
                    words.lastOrNull { it.isNotEmpty() }?.trim() ?: ""
                } else {
                    ""
                }
                
                flogDebug { "Autosave debug - textBeforeCursor: '$textBeforeCursor', lastWord: '$lastWord'" }
                
                // Only save non-empty words that contain at least one letter and are longer than 1 character
                if (lastWord.isNotEmpty() && lastWord.length > 1 && lastWord.any { it.isLetter() }) {
                    // Get the dictionary manager and check if word exists in static dictionary
                    val dictionaryManager = DictionaryManager.default()
                    dictionaryManager.loadUserDictionariesIfNecessary()
                    
                    // Check if the word already exists in the static dictionary by getting the list of words
                    val staticWords = nlpManager.getListOfWords(subtypeManager.activeSubtype)
                    flogDebug { "Static words count: ${staticWords.size}" }
                    flogDebug { "Sample static words: ${staticWords.take(10)}" }
                    
                    val isAlreadyInStaticDict = staticWords.contains(lastWord) || staticWords.contains(lastWord.lowercase())
                    
                    flogDebug { "Autosave debug - isAlreadyInStaticDict: $isAlreadyInStaticDict" }
                    
                    // Only save words that are not already in our static dictionary
                    if (!isAlreadyInStaticDict) {
                        val userDictionaryDao = dictionaryManager.florisUserDictionaryDao()
                        
                        flogDebug { "UserDictionaryDao in autosave: $userDictionaryDao" }
                        
                        // Check if the word already exists in the user dictionary
                        val existingEntries = userDictionaryDao?.queryExact(lastWord, subtypeManager.activeSubtype.primaryLocale)
                        flogDebug { "Existing entries for '$lastWord': $existingEntries" }
                        
                        if (existingEntries != null && existingEntries.isEmpty()) {
                            // Word doesn't exist in user dictionary, so add it with a higher frequency
                            val entry = UserDictionaryEntry(
                                id = 0, // 0 means auto-generate ID
                                word = lastWord,
                                freq = kotlin.math.min(FREQUENCY_DEFAULT + 35, FREQUENCY_MAX), // Higher boost for recently used words
                                locale = subtypeManager.activeSubtype.primaryLocale.localeTag(),
                                shortcut = null
                            )
                            userDictionaryDao.insert(entry)
                            flogDebug { "Autosaved previous word to user dictionary: $lastWord with boosted frequency" }
                            
                            // Force refresh the glide typing classifier to include the new word
                            try {
                                // Get the glide typing manager from the application context
                                val glideTypingManager = appContext.glideTypingManager
                                // Force refresh the word data in the classifier
                                // flogDebug { "About to refresh glide typing classifier for autosaved word: $lastWord" }
                                glideTypingManager.value.refreshWordData()
                                // flogDebug { "Forced refresh of glide typing classifier for word: $lastWord" }
                            } catch (e: Exception) {
                                // flogDebug { "Failed to force refresh glide typing classifier: ${e.message}" }
                                e.printStackTrace()
                            }
                        } else if (existingEntries != null && existingEntries.isNotEmpty()) {
                            // Word exists, try to increase its frequency
                            flogDebug { "Previous word already exists in user dictionary: $lastWord" }
                            
                            // Try to increase the frequency of existing words to improve suggestions
                            try {
                                val existingEntry = existingEntries.first()
                                val newFreq = kotlin.math.min(existingEntry.freq + 10, FREQUENCY_MAX)
                                if (newFreq > existingEntry.freq) {
                                    val updatedEntry = existingEntry.copy(freq = newFreq)
                                    userDictionaryDao.update(updatedEntry)
                                    flogDebug { "Updated frequency for existing word '$lastWord' from ${existingEntry.freq} to $newFreq" }
                                    
                                    // Refresh glide typing data
                                    val glideTypingManager = appContext.glideTypingManager
                                    glideTypingManager.value.refreshWordData()
                                    flogDebug { "Refreshed glide typing classifier after frequency update for word: $lastWord" }
                                }
                            } catch (e: Exception) {
                                flogDebug { "Failed to update frequency for existing word '$lastWord': ${e.message}" }
                            }
                        } else {
                            flogDebug { "User dictionary DAO is null or query failed" }
                        }
                    } else {
                        flogDebug { "Word already exists in static dictionary: $lastWord" }
                    }
                } else {
                    flogDebug { "Word is empty, too short, or contains no letters: '$lastWord'" }
                }
            } catch (e: Exception) {
                flogDebug { "Failed to autosave previous word: ${e.message}" }
                e.printStackTrace()
            }
        }
    }

    /**
     * Handles a [KeyCode.TOGGLE_INCOGNITO_MODE] event.
     */
    private suspend fun handleToggleIncognitoMode() {
        prefs.suggestion.forceIncognitoModeFromDynamic.set(!prefs.suggestion.forceIncognitoModeFromDynamic.get())
        val newState = !activeState.isIncognitoMode
        activeState.isIncognitoMode = newState
        lastToastReference.get()?.cancel()
        lastToastReference = WeakReference(
            if (newState) {
                appContext.showLongToast(
                    R.string.incognito_mode__toast_after_enabled,
                    "app_name" to appContext.getString(R.string.floris_app_name),
                )
            } else {
                appContext.showLongToast(
                    R.string.incognito_mode__toast_after_disabled,
                    "app_name" to appContext.getString(R.string.floris_app_name),
                )
            }
        )
    }

    /**
     * Handles a [KeyCode.TOGGLE_AUTOCORRECT] event.
     */
    private suspend fun handleToggleAutocorrect() {
        val currentState = prefs.correction.autoCorrectEnabled.get()
        prefs.correction.autoCorrectEnabled.set(!currentState)
        
        val message = if (!currentState) {
            "Auto-correct enabled"
        } else {
            "Auto-correct disabled"
        }
        
        lastToastReference.get()?.cancel()
        lastToastReference = WeakReference(
            appContext.showLongToastSync(message)
        )
    }

    /**
     * Handles a [KeyCode.MAGIC_WAND] event.
     * Toggles the magic wand panel with customizable buttons.
     * Also handles closing the action result panel when visible.
     */
    private fun handleMagicWand() {
        // If action result panel is visible, close it and return to magic wand panel
        if (activeState.isActionResultPanelVisible) {
            closeActionResultPanel()
            return
        }
        
        // Otherwise, toggle magic wand panel normally
        activeState.isMagicWandPanelVisible = !activeState.isMagicWandPanelVisible
        
        if (activeState.isMagicWandPanelVisible) {
            // Hide other panels when showing magic wand panel
            activeState.isActionsOverflowVisible = false
            activeState.isActionsEditorVisible = false
        }
    }

    /**
     * Handles a [KeyCode.AI_CHAT] event.
     * Directly triggers AI chat functionality with the same behavior as chat in MagicWand panel.
     */
    private fun handleAiChat() {
        scope.launch {
            // Set loading state
            activeState.isAiChatLoading = true
            
            try {
                // Get all text from the input field (same as MagicWand chat)
                val activeContent = editorInstance.activeContent
                val allText = buildString {
                    append(activeContent.textBeforeSelection)
                    append(activeContent.selectedText)
                    append(activeContent.textAfterSelection)
                }
                
                if (allText.isBlank()) {
                    lastToastReference = WeakReference(appContext.showShortToastSync("Please type some text first"))
                    return@launch
                }
                
                // Check network connectivity
                if (!com.vishruth.sendright.lib.network.NetworkUtils.checkNetworkAndShowToast(appContext)) {
                    return@launch
                }
                
                // Create temporary AI usage tracker instance
                val aiUsageTracker = com.vishruth.key1.ime.ai.AiUsageTracker.getInstance()
                
                // Use the MagicWand chat handler directly (original functionality)
                com.vishruth.key1.ime.smartbar.handleMagicWandButtonClick(
                    buttonTitle = "Chat",
                    editorInstance = editorInstance,
                    context = appContext,
                    aiUsageTracker = aiUsageTracker
                )
                
            } catch (e: Exception) {
                lastToastReference = WeakReference(appContext.showShortToastSync("Something went wrong. Please try again."))
            } finally {
                // Clear loading state
                activeState.isAiChatLoading = false
            }
        }
    }

    /**
     * Closes the magic wand panel if it's currently visible.
     * Used for auto-close functionality when user interacts with input area.
     */
    fun closeMagicWandPanel() {
        if (activeState.isMagicWandPanelVisible) {
            activeState.isMagicWandPanelVisible = false
        }
    }

    /**
     * Shows the action result panel and hides other panels
     */
    fun showActionResultPanel() {
        activeState.isActionResultPanelVisible = true
        activeState.isMagicWandPanelVisible = false
        activeState.isActionsOverflowVisible = false
        activeState.isActionsEditorVisible = false
    }

    /**
     * Closes the action result panel and returns to magic wand panel
     */
    fun closeActionResultPanel() {
        activeState.isActionResultPanelVisible = false
        activeState.isMagicWandPanelVisible = true
    }

    /**
     * Closes the action result panel completely
     */
    fun dismissActionResultPanel() {
        activeState.isActionResultPanelVisible = false
    }

    /**
     * Handles magic wand button actions
     */
    fun handleMagicWandButton(buttonIndex: Int) {
        lastToastReference.get()?.cancel()
        
        when (buttonIndex) {
            1 -> handleMagicWandButton1()
            2 -> handleMagicWandButton2()
            3 -> handleMagicWandButton3()
            4 -> handleMagicWandButton4()
            5 -> handleMagicWandButton5()
            6 -> handleMagicWandButton6()
            7 -> handleMagicWandButton7()
            8 -> handleMagicWandButton8()
            else -> {
                lastToastReference = WeakReference(
                    appContext.showLongToastSync("✨ Button $buttonIndex pressed!")
                )
            }
        }
    }

    private fun handleMagicWandButton1() {
        // Smart Text Case Toggle
        val selectedText = editorInstance.getSelectedText()
        if (!selectedText.isNullOrEmpty()) {
            val transformed = when {
                selectedText.all { it.isUpperCase() || !it.isLetter() } -> selectedText.split(" ").joinToString(" ") { 
                    it.lowercase().replaceFirstChar { char -> char.uppercase() } 
                }
                else -> selectedText.uppercase()
            }
            editorInstance.deleteSelectedText()
            editorInstance.commitText(transformed)
            lastToastReference = WeakReference(appContext.showLongToastSync("✨ Text case transformed"))
        } else {
            lastToastReference = WeakReference(appContext.showLongToastSync("✨ Select text to transform case"))
        }
    }

    private fun handleMagicWandButton2() {
        // Quick Email Template
        val emailTemplate = "Hello,\n\nI hope this email finds you well.\n\nBest regards,\n"
        editorInstance.commitText(emailTemplate)
        lastToastReference = WeakReference(appContext.showLongToastSync("✨ Email template inserted"))
    }

    private fun handleMagicWandButton3() {
        // Smart Date/Time Insertion
        val currentDateTime = java.time.LocalDateTime.now()
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a")
        val dateTimeString = currentDateTime.format(formatter)
        editorInstance.commitText(dateTimeString)
        lastToastReference = WeakReference(appContext.showLongToastSync("✨ Current date/time inserted"))
    }

    private fun handleMagicWandButton4() {
        // Quick Symbols Panel
        val symbols = "• → ← ↑ ↓ ★ ♥ ✓ ✗ © ® ™ § ¶ "
        editorInstance.commitText(symbols)
        lastToastReference = WeakReference(appContext.showLongToastSync("✨ Symbol set inserted"))
    }

    private fun handleMagicWandButton5() {
        // Text Cleanup
        val selectedText = editorInstance.getSelectedText()
        if (!selectedText.isNullOrEmpty()) {
            val cleaned = selectedText.trim().replace("\\s+".toRegex(), " ")
            editorInstance.deleteSelectedText()
            editorInstance.commitText(cleaned)
            lastToastReference = WeakReference(appContext.showLongToastSync("✨ Text cleaned"))
        } else {
            lastToastReference = WeakReference(appContext.showLongToastSync("✨ Select text to clean"))
        }
    }

    private fun handleMagicWandButton6() {
        // Quick Phone Number Format
        val selectedText = editorInstance.getSelectedText()
        if (!selectedText.isNullOrEmpty() && selectedText.matches("\\d{10}".toRegex())) {
            val formatted = "${selectedText.substring(0, 3)}-${selectedText.substring(3, 6)}-${selectedText.substring(6)}"
            editorInstance.deleteSelectedText()
            editorInstance.commitText(formatted)
            lastToastReference = WeakReference(appContext.showLongToastSync("✨ Phone number formatted"))
        } else {
            lastToastReference = WeakReference(appContext.showLongToastSync("✨ Select 10-digit number to format"))
        }
    }

    private fun handleMagicWandButton7() {
        // Smart URL Completion
        val currentWord = editorInstance.getCurrentWord()
        when {
            currentWord.startsWith("www.") && !currentWord.contains("://") -> {
                editorInstance.selectCurrentWord()
                editorInstance.deleteSelectedText()
                editorInstance.commitText("https://$currentWord")
                lastToastReference = WeakReference(appContext.showLongToastSync("✨ URL protocol added"))
            }
            currentWord.contains("@") && !currentWord.contains(".") -> {
                editorInstance.selectCurrentWord()
                editorInstance.deleteSelectedText()
                editorInstance.commitText("$currentWord.com")
                lastToastReference = WeakReference(appContext.showLongToastSync("✨ Domain completed"))
            }
            else -> {
                lastToastReference = WeakReference(appContext.showLongToastSync("✨ Position cursor on URL/email to enhance"))
            }
        }
    }

    private fun handleMagicWandButton8() {
        // Text Reverser
        val selectedText = editorInstance.getSelectedText()
        if (!selectedText.isNullOrEmpty()) {
            editorInstance.deleteSelectedText()
            editorInstance.commitText(selectedText.reversed())
            lastToastReference = WeakReference(appContext.showLongToastSync("✨ Text reversed"))
        } else {
            lastToastReference = WeakReference(appContext.showLongToastSync("✨ Select text to reverse"))
        }
    }

    /**
     * Handles a [KeyCode.KANA_SWITCHER] event
     */
    private fun handleKanaSwitch() {
        activeState.batchEdit {
            it.isKanaKata = !it.isKanaKata
            it.isCharHalfWidth = false
        }
    }

    /**
     * Handles a [KeyCode.KANA_HIRA] event
     */
    private fun handleKanaHira() {
        activeState.batchEdit {
            it.isKanaKata = false
            it.isCharHalfWidth = false
        }
    }

    /**
     * Handles a [KeyCode.KANA_KATA] event
     */
    private fun handleKanaKata() {
        activeState.batchEdit {
            it.isKanaKata = true
            it.isCharHalfWidth = false
        }
    }

    /**
     * Handles a [KeyCode.KANA_HALF_KATA] event
     */
    private fun handleKanaHalfKata() {
        activeState.batchEdit {
            it.isKanaKata = true
            it.isCharHalfWidth = true
        }
    }

    /**
     * Handles a [KeyCode.CHAR_WIDTH_SWITCHER] event
     */
    private fun handleCharWidthSwitch() {
        activeState.isCharHalfWidth = !activeState.isCharHalfWidth
    }

    /**
     * Handles a [KeyCode.CHAR_WIDTH_SWITCHER] event
     */
    private fun handleCharWidthFull() {
        activeState.isCharHalfWidth = false
    }

    /**
     * Handles a [KeyCode.CHAR_WIDTH_SWITCHER] event
     */
    private fun handleCharWidthHalf() {
        activeState.isCharHalfWidth = true
    }

    override fun onInputKeyDown(data: KeyData) {
        when (data.code) {
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_START_OF_PAGE,
            KeyCode.MOVE_END_OF_PAGE,
            KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE -> {
                editorInstance.massSelection.begin()
            }
            KeyCode.SHIFT -> handleShiftDown(data)
        }
    }

    override fun onInputKeyUp(data: KeyData) = activeState.batchEdit {
        when (data.code) {
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_START_OF_PAGE,
            KeyCode.MOVE_END_OF_PAGE,
            KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE -> {
                editorInstance.massSelection.end()
                handleArrow(data.code)
            }
            KeyCode.CAPS_LOCK -> handleCapsLock()
            KeyCode.CHAR_WIDTH_SWITCHER -> handleCharWidthSwitch()
            KeyCode.CHAR_WIDTH_FULL -> handleCharWidthFull()
            KeyCode.CHAR_WIDTH_HALF -> handleCharWidthHalf()
            KeyCode.CLIPBOARD_CUT -> editorInstance.performClipboardCut()
            KeyCode.CLIPBOARD_COPY -> editorInstance.performClipboardCopy()
            KeyCode.CLIPBOARD_PASTE -> editorInstance.performClipboardPaste()
            KeyCode.CLIPBOARD_SELECT -> handleClipboardSelect()
            KeyCode.CLIPBOARD_SELECT_ALL -> editorInstance.performClipboardSelectAll()
            KeyCode.CLIPBOARD_CLEAR_HISTORY -> clipboardManager.clearHistory()
            KeyCode.CLIPBOARD_CLEAR_FULL_HISTORY -> clipboardManager.clearFullHistory()
            KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP -> {
                if (prefs.clipboard.clearPrimaryClipDeletesLastItem.get()) {
                    clipboardManager.primaryClip?.let { clipboardManager.deleteClip(it) }
                }
                clipboardManager.updatePrimaryClip(null)
                appContext.showShortToastSync(R.string.clipboard__cleared_primary_clip)
            }
            KeyCode.TOGGLE_COMPACT_LAYOUT -> scope.launch { toggleOneHandedMode() }
            KeyCode.COMPACT_LAYOUT_TO_LEFT -> scope.launch {
                prefs.keyboard.oneHandedMode.set(OneHandedMode.START)
                toggleOneHandedMode()
            }
            KeyCode.COMPACT_LAYOUT_TO_RIGHT -> scope.launch {
                prefs.keyboard.oneHandedMode.set(OneHandedMode.END)
                toggleOneHandedMode()
            }
            KeyCode.DELETE -> handleBackwardDelete(OperationUnit.CHARACTERS)
            KeyCode.DELETE_WORD -> handleBackwardDelete(OperationUnit.WORDS)
            KeyCode.ENTER -> handleEnter()
            KeyCode.FORWARD_DELETE -> handleForwardDelete(OperationUnit.CHARACTERS)
            KeyCode.FORWARD_DELETE_WORD -> handleForwardDelete(OperationUnit.WORDS)
            KeyCode.IME_SHOW_UI -> FlorisImeService.showUi()
            KeyCode.IME_HIDE_UI -> FlorisImeService.hideUi()
            KeyCode.IME_PREV_SUBTYPE -> subtypeManager.switchToPrevSubtype()
            KeyCode.IME_NEXT_SUBTYPE -> subtypeManager.switchToNextSubtype()
            KeyCode.IME_UI_MODE_TEXT -> activeState.imeUiMode = ImeUiMode.TEXT
            KeyCode.IME_UI_MODE_MEDIA -> activeState.imeUiMode = ImeUiMode.MEDIA
            KeyCode.IME_UI_MODE_CLIPBOARD -> activeState.imeUiMode = ImeUiMode.CLIPBOARD
            KeyCode.VOICE_INPUT -> FlorisImeService.switchToVoiceInputMethod()
            KeyCode.KANA_SWITCHER -> handleKanaSwitch()
            KeyCode.KANA_HIRA -> handleKanaHira()
            KeyCode.KANA_KATA -> handleKanaKata()
            KeyCode.KANA_HALF_KATA -> handleKanaHalfKata()
            KeyCode.LANGUAGE_SWITCH -> handleLanguageSwitch()
            KeyCode.REDO -> editorInstance.performRedo()
            KeyCode.SETTINGS -> FlorisImeService.launchSettings()
            KeyCode.SHIFT -> handleShiftUp(data)
            KeyCode.SPACE -> handleSpace(data)
            KeyCode.SYSTEM_INPUT_METHOD_PICKER -> InputMethodUtils.showImePicker(appContext)
            KeyCode.SHOW_SUBTYPE_PICKER -> {
                appContext.keyboardManager.value.activeState.isSubtypeSelectionVisible = true
            }
            KeyCode.SYSTEM_PREV_INPUT_METHOD -> FlorisImeService.switchToPrevInputMethod()
            KeyCode.SYSTEM_NEXT_INPUT_METHOD -> FlorisImeService.switchToNextInputMethod()
            KeyCode.TOGGLE_SMARTBAR_VISIBILITY -> scope.launch {
                prefs.smartbar.enabled.let { it.set(!it.get()) }
            }
            KeyCode.TOGGLE_ACTIONS_OVERFLOW -> {
                activeState.isActionsOverflowVisible = !activeState.isActionsOverflowVisible
            }
            KeyCode.TOGGLE_ACTIONS_EDITOR -> {
                activeState.isActionsEditorVisible = !activeState.isActionsEditorVisible
            }
            KeyCode.TOGGLE_INCOGNITO_MODE -> scope.launch { handleToggleIncognitoMode() }
            KeyCode.TOGGLE_AUTOCORRECT -> scope.launch { handleToggleAutocorrect() }
            KeyCode.MAGIC_WAND -> handleMagicWand()
            KeyCode.AI_CHAT -> handleAiChat()
            KeyCode.UNDO -> editorInstance.performUndo()
            KeyCode.VIEW_CHARACTERS -> activeState.keyboardMode = KeyboardMode.CHARACTERS
            KeyCode.VIEW_NUMERIC -> activeState.keyboardMode = KeyboardMode.NUMERIC
            KeyCode.VIEW_NUMERIC_ADVANCED -> activeState.keyboardMode = KeyboardMode.NUMERIC_ADVANCED
            KeyCode.VIEW_PHONE -> activeState.keyboardMode = KeyboardMode.PHONE
            KeyCode.VIEW_PHONE2 -> activeState.keyboardMode = KeyboardMode.PHONE2
            KeyCode.VIEW_SYMBOLS -> activeState.keyboardMode = KeyboardMode.SYMBOLS
            KeyCode.VIEW_SYMBOLS2 -> activeState.keyboardMode = KeyboardMode.SYMBOLS2
            else -> {
                if (activeState.imeUiMode == ImeUiMode.MEDIA) {
                    // Special handling for emojis to prevent auto-correction
                    if (data is Emoji) {
                        // For emojis, we should commit directly without triggering auto-correction
                        editorInstance.commitText(data.asString(isForDisplay = false))
                        // Reset shift state after emoji input
                        if (activeState.inputShiftState != InputShiftState.CAPS_LOCK && !inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
                            activeState.inputShiftState = InputShiftState.UNSHIFTED
                        }
                        return@batchEdit
                    } else {
                        val candidate = nlpManager.getAutoCommitCandidate()
                        if (candidate != null && candidate.confidence > 0.8) {
                            commitCandidate(candidate)
                        }
                        editorInstance.commitText(data.asString(isForDisplay = false))
                        return@batchEdit
                    }
                }
                when (activeState.keyboardMode) {
                    KeyboardMode.NUMERIC,
                    KeyboardMode.NUMERIC_ADVANCED,
                    KeyboardMode.PHONE,
                    KeyboardMode.PHONE2 -> when (data.type) {
                        KeyType.CHARACTER,
                        KeyType.NUMERIC -> {
                            val text = data.asString(isForDisplay = false)
                            editorInstance.commitText(text)
                        }
                        else -> when (data.code) {
                            KeyCode.PHONE_PAUSE,
                            KeyCode.PHONE_WAIT -> {
                                val text = data.asString(isForDisplay = false)
                                editorInstance.commitText(text)
                            }
                        }
                    }
                    else -> when (data.type) {
                        KeyType.CHARACTER, KeyType.NUMERIC ->{
                            val text = data.asString(isForDisplay = false)
                            // Special handling for emojis to prevent auto-correction
                            if (data is Emoji) {
                                // For emojis, we should commit directly without triggering auto-correction
                                editorInstance.commitText(text)
                                // Reset shift state after emoji input
                                if (activeState.inputShiftState != InputShiftState.CAPS_LOCK && !inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
                                    activeState.inputShiftState = InputShiftState.UNSHIFTED
                                }
                                return@batchEdit
                            } else {
                                // Only auto-commit for space or punctuation characters, not for all non-alphabetic characters
                                // But be more selective about when to auto-commit to prevent unwanted changes
                                if (!UCharacter.isUAlphabetic(UCharacter.codePointAt(text, 0)) && 
                                    (text == " " || text in ",.!?;:")) {
                                    val candidate = nlpManager.getAutoCommitCandidate()
                                    // Be more careful about auto-commit to prevent drastic word changes
                                    // Only auto-commit for high-confidence candidates that are explicitly eligible
                                    // This prevents unwanted changes like inserting "the" when space is pressed
                                    if (candidate != null && candidate.isEligibleForAutoCommit && candidate.confidence > 0.9) {
                                        commitCandidate(candidate)
                                    }
                                }
                                editorInstance.commitChar(text)
                            }
                        }
                        else -> {
                            flogError(LogTopic.KEY_EVENTS) { "Received unknown key: $data" }
                        }
                    }
                }
                if (activeState.inputShiftState != InputShiftState.CAPS_LOCK && !inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
                    activeState.inputShiftState = InputShiftState.UNSHIFTED
                }
            }
        }
    }

    override fun onInputKeyCancel(data: KeyData) {
        when (data.code) {
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_START_OF_PAGE,
            KeyCode.MOVE_END_OF_PAGE,
            KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE -> {
                editorInstance.massSelection.end()
            }
            KeyCode.SHIFT -> handleShiftCancel()
        }
    }

    override fun onInputKeyRepeat(data: KeyData) {
        FlorisImeService.inputFeedbackController()?.keyRepeatedAction(data)
        when (data.code) {
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_START_OF_PAGE,
            KeyCode.MOVE_END_OF_PAGE,
            KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE -> handleArrow(data.code)
            else -> onInputKeyUp(data)
        }
    }

    private fun reevaluateDebugFlags() {
        val devtoolsEnabled = prefs.devtools.enabled.get()
        activeState.batchEdit {
            activeState.debugShowDragAndDropHelpers = devtoolsEnabled && prefs.devtools.showDragAndDropHelpers.get()
        }
    }

    fun onHardwareKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> {
                handleHardwareKeyboardSpace()
                return true
            }
            KeyEvent.KEYCODE_ENTER -> {
                handleEnter()
                return true
            }
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                inputEventDispatcher.sendDown(TextKeyData.SHIFT)
                return true
            }
            else -> return false
        }
    }

    fun onHardwareKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                inputEventDispatcher.sendUp(TextKeyData.SHIFT)
                return true
            }
            else -> return false
        }
    }

    inner class KeyboardManagerResources {
        val composers = MutableLiveData<Map<ExtensionComponentName, Composer>>(emptyMap())
        val currencySets = MutableLiveData<Map<ExtensionComponentName, CurrencySet>>(emptyMap())
        val layouts = MutableLiveData<Map<LayoutType, Map<ExtensionComponentName, LayoutArrangementComponent>>>(emptyMap())
        val popupMappings = MutableLiveData<Map<ExtensionComponentName, PopupMappingComponent>>(emptyMap())
        val punctuationRules = MutableLiveData<Map<ExtensionComponentName, PunctuationRule>>(emptyMap())
        val subtypePresets = MutableLiveData<List<SubtypePreset>>(emptyList())

        private val anyChangedGuard = Mutex(locked = false)
        val anyChanged = MutableLiveData(Unit)

        init {
            scope.launch(Dispatchers.Main.immediate) {
                extensionManager.keyboardExtensions.observeForever { keyboardExtensions ->
                    scope.launch {
                        anyChangedGuard.withLock {
                            parseKeyboardExtensions(keyboardExtensions)
                        }
                    }
                }
            }
        }

        private fun parseKeyboardExtensions(keyboardExtensions: List<KeyboardExtension>) {
            val localComposers = mutableMapOf<ExtensionComponentName, Composer>()
            val localCurrencySets = mutableMapOf<ExtensionComponentName, CurrencySet>()
            val localLayouts = mutableMapOf<LayoutType, MutableMap<ExtensionComponentName, LayoutArrangementComponent>>()
            val localPopupMappings = mutableMapOf<ExtensionComponentName, PopupMappingComponent>()
            val localPunctuationRules = mutableMapOf<ExtensionComponentName, PunctuationRule>()
            val localSubtypePresets = mutableListOf<SubtypePreset>()
            for (layoutType in LayoutType.entries) {
                localLayouts[layoutType] = mutableMapOf()
            }
            for (keyboardExtension in keyboardExtensions) {
                keyboardExtension.composers.forEach { composer ->
                    localComposers[ExtensionComponentName(keyboardExtension.meta.id, composer.id)] = composer
                }
                keyboardExtension.currencySets.forEach { currencySet ->
                    localCurrencySets[ExtensionComponentName(keyboardExtension.meta.id, currencySet.id)] = currencySet
                }
                keyboardExtension.layouts.forEach { (type, layoutComponents) ->
                    for (layoutComponent in layoutComponents) {
                        localLayouts[LayoutType.entries.first { it.id == type }]!![ExtensionComponentName(keyboardExtension.meta.id, layoutComponent.id)] = layoutComponent
                    }
                }
                keyboardExtension.popupMappings.forEach { popupMapping ->
                    localPopupMappings[ExtensionComponentName(keyboardExtension.meta.id, popupMapping.id)] = popupMapping
                }
                keyboardExtension.punctuationRules.forEach { punctuationRule ->
                    localPunctuationRules[ExtensionComponentName(keyboardExtension.meta.id, punctuationRule.id)] = punctuationRule
                }
                localSubtypePresets.addAll(keyboardExtension.subtypePresets)
            }
            localSubtypePresets.sortBy { it.locale.displayName() }
            for (languageCode in listOf("en-CA", "en-AU", "en-UK", "en-US")) {
                val index: Int = localSubtypePresets.indexOfFirst { it.locale.languageTag() == languageCode }
                if (index > 0) {
                    localSubtypePresets.add(0, localSubtypePresets.removeAt(index))
                }
            }
            subtypePresets.postValue(localSubtypePresets)
            composers.postValue(localComposers)
            currencySets.postValue(localCurrencySets)
            layouts.postValue(localLayouts)
            popupMappings.postValue(localPopupMappings)
            punctuationRules.postValue(localPunctuationRules)
            anyChanged.postValue(Unit)
        }
    }

    private inner class ComputingEvaluatorImpl(
        override val version: Int,
        override val keyboard: Keyboard,
        override val editorInfo: FlorisEditorInfo,
        override val state: KeyboardState,
        override val subtype: Subtype,
    ) : ComputingEvaluator {

        override fun context(): Context = appContext

        val androidKeyguardManager = context().systemService(AndroidKeyguardManager::class)

        override fun displayLanguageNamesIn(): DisplayLanguageNamesIn {
            return prefs.localization.displayLanguageNamesIn.get()
        }

        override fun evaluateEnabled(data: KeyData): Boolean {
            return when (data.code) {
                KeyCode.CLIPBOARD_COPY,
                KeyCode.CLIPBOARD_CUT -> {
                    state.isSelectionMode && editorInfo.isRichInputEditor
                }
                KeyCode.CLIPBOARD_PASTE -> {
                    !androidKeyguardManager.let { it.isDeviceLocked || it.isKeyguardLocked }
                        && clipboardManager.canBePasted(clipboardManager.primaryClip)
                }
                KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP -> {
                    clipboardManager.canBePasted(clipboardManager.primaryClip)
                }
                KeyCode.CLIPBOARD_SELECT_ALL -> {
                    editorInfo.isRichInputEditor
                }
                KeyCode.TOGGLE_INCOGNITO_MODE -> when (prefs.suggestion.incognitoMode.get()) {
                    IncognitoMode.FORCE_OFF, IncognitoMode.FORCE_ON -> false
                    IncognitoMode.DYNAMIC_ON_OFF -> !editorInfo.imeOptions.flagNoPersonalizedLearning
                }
                KeyCode.LANGUAGE_SWITCH -> {
                    subtypeManager.subtypes.size > 1
                }
                else -> true
            }
        }

        override fun evaluateVisible(data: KeyData): Boolean {
            return when (data.code) {
                KeyCode.IME_UI_MODE_TEXT,
                KeyCode.IME_UI_MODE_MEDIA -> {
                    val tempUtilityKeyAction = when {
                        prefs.keyboard.utilityKeyEnabled.get() -> prefs.keyboard.utilityKeyAction.get()
                        else -> UtilityKeyAction.DISABLED
                    }
                    when (tempUtilityKeyAction) {
                        UtilityKeyAction.DISABLED,
                        UtilityKeyAction.SWITCH_LANGUAGE,
                        UtilityKeyAction.SWITCH_KEYBOARD_APP -> false
                        UtilityKeyAction.SWITCH_TO_EMOJIS -> true
                        UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS -> !shouldShowLanguageSwitch()
                    }
                }
                KeyCode.LANGUAGE_SWITCH -> {
                    val tempUtilityKeyAction = when {
                        prefs.keyboard.utilityKeyEnabled.get() -> prefs.keyboard.utilityKeyAction.get()
                        else -> UtilityKeyAction.DISABLED
                    }
                    when (tempUtilityKeyAction) {
                        UtilityKeyAction.DISABLED,
                        UtilityKeyAction.SWITCH_TO_EMOJIS -> false
                        UtilityKeyAction.SWITCH_LANGUAGE,
                        UtilityKeyAction.SWITCH_KEYBOARD_APP -> true
                        UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS -> shouldShowLanguageSwitch()
                    }
                }
                else -> true
            }
        }

        override fun isSlot(data: KeyData): Boolean {
            return CurrencySet.isCurrencySlot(data.code)
        }

        override fun slotData(data: KeyData): KeyData? {
            return subtypeManager.getCurrencySet(subtype).getSlot(data.code)
        }

        fun asSmartbarQuickActionsEvaluator(): ComputingEvaluatorImpl {
            return ComputingEvaluatorImpl(
                version = version,
                keyboard = SmartbarQuickActionsKeyboard,
                editorInfo = editorInfo,
                state = state,
                subtype = Subtype.DEFAULT,
            )
        }
    }
}
