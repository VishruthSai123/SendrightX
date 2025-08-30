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

package com.vishruth.sendright.app.settings.advanced

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import com.vishruth.sendright.R
import com.vishruth.sendright.app.AppTheme
import com.vishruth.sendright.app.LocalNavController
import com.vishruth.sendright.app.Routes
import com.vishruth.sendright.app.enumDisplayEntriesOf
import com.vishruth.sendright.ime.core.DisplayLanguageNamesIn
import com.vishruth.sendright.lib.FlorisLocale
import com.vishruth.sendright.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.datastore.ui.ColorPickerPreference
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.datastore.ui.isMaterialYou
import dev.patrickgold.jetpref.datastore.ui.listPrefEntries
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.color.ColorMappings
import org.florisboard.lib.compose.stringRes


@Composable
fun OtherScreen() = FlorisScreen {
    title = stringRes(R.string.settings__other__title)
    previewFieldVisible = false

    val navController = LocalNavController.current
    val context = LocalContext.current

    content {
        ListPreference(
            prefs.other.settingsTheme,
            icon = Icons.Default.Palette,
            title = stringRes(R.string.pref__other__settings_theme__label),
            entries = enumDisplayEntriesOf(AppTheme::class),
        )
        ColorPickerPreference(
            pref = prefs.other.accentColor,
            title = stringRes(R.string.pref__other__settings_accent_color__label),
            defaultValueLabel = stringRes(R.string.action__default),
            icon = Icons.Default.FormatColorFill,
            defaultColors = ColorMappings.colors,
            showAlphaSlider = false,
            enableAdvancedLayout = false,
            colorOverride = {
                if (it.isMaterialYou(context)) {
                    Color.Unspecified
                } else {
                    it
                }
            }
        )
        ListPreference(
            prefs.other.settingsLanguage,
            icon = Icons.Default.Language,
            title = stringRes(R.string.pref__other__settings_language__label),
            entries = listPrefEntries {
                listOf(
                    "auto",
                    "ar",
                    "bg",
                    "bs",
                    "ca",
                    "ckb",
                    "cs",
                    "da",
                    "de",
                    "el",
                    "en",
                    "eo",
                    "es",
                    "fa",
                    "fi",
                    "fr",
                    "hr",
                    "hu",
                    "in",
                    "it",
                    "iw",
                    "ja",
                    "ko-KR",
                    "ku",
                    "lv-LV",
                    "mk",
                    "nds-DE",
                    "nl",
                    "no",
                    "pl",
                    "pt",
                    "pt-BR",
                    "ru",
                    "sk",
                    "sl",
                    "sr",
                    "sv",
                    "tr",
                    "uk",
                    "zgh",
                    "zh-CN",
                ).map { languageTag ->
                    if (languageTag == "auto") {
                        entry(
                            key = "auto",
                            label = stringRes(R.string.settings__system_default),
                        )
                    } else {
                        val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.observeAsState()
                        val locale = FlorisLocale.fromTag(languageTag)
                        entry(locale.languageTag(), when (displayLanguageNamesIn) {
                            DisplayLanguageNamesIn.SYSTEM_LOCALE -> locale.displayName()
                            DisplayLanguageNamesIn.NATIVE_LOCALE -> locale.displayName(locale)
                        })
                    }
                }
            }
        )
        Preference(
            icon = ImageVector.vectorResource(R.drawable.ic_keyboard_keys),
            title = stringRes(R.string.physical_keyboard__title),
            onClick = { navController.navigate(Routes.Settings.PhysicalKeyboard) },
        )
        Preference(
            icon = Icons.Default.RateReview,
            title = stringRes(R.string.report_feedback__title),
            onClick = { navController.navigate(Routes.Settings.ReportFeedback) },
        )
    }
}
