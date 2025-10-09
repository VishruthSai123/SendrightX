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

package com.vishruth.key1.app

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.vishruth.key1.app.devtools.AndroidLocalesScreen
import com.vishruth.key1.app.devtools.AndroidSettingsScreen
import com.vishruth.key1.app.devtools.DevtoolsScreen
import com.vishruth.key1.app.devtools.ExportDebugLogScreen
import com.vishruth.key1.app.ext.CheckUpdatesScreen
import com.vishruth.key1.app.ext.ExtensionEditScreen
import com.vishruth.key1.app.ext.ExtensionExportScreen
import com.vishruth.key1.app.ext.ExtensionHomeScreen
import com.vishruth.key1.app.ext.ExtensionImportScreen
import com.vishruth.key1.app.ext.ExtensionImportScreenType
import com.vishruth.key1.app.ext.ExtensionListScreen
import com.vishruth.key1.app.ext.ExtensionListScreenType
import com.vishruth.key1.app.ext.ExtensionViewScreen
import com.vishruth.key1.app.settings.HomeScreen
import com.vishruth.key1.app.settings.ReportFeedbackScreen
import com.vishruth.key1.app.settings.about.AboutScreen
import com.vishruth.key1.app.settings.about.ProjectLicenseScreen
import com.vishruth.key1.app.settings.about.ThirdPartyLicensesScreen
import com.vishruth.key1.app.settings.advanced.BackupScreen
import com.vishruth.key1.app.settings.advanced.OtherScreen
import com.vishruth.key1.app.settings.advanced.PhysicalKeyboardScreen
import com.vishruth.key1.app.settings.advanced.RestoreScreen
import com.vishruth.key1.app.settings.clipboard.ClipboardScreen
import com.vishruth.key1.app.settings.dictionary.DictionaryScreen
import com.vishruth.key1.app.settings.dictionary.UserDictionaryScreen
import com.vishruth.key1.app.settings.dictionary.UserDictionaryType
import com.vishruth.key1.app.settings.gestures.GesturesScreen
import com.vishruth.key1.app.settings.keyboard.InputFeedbackScreen
import com.vishruth.key1.app.settings.keyboard.KeyboardScreen
import com.vishruth.key1.app.settings.localization.LanguagePackManagerScreen
import com.vishruth.key1.app.settings.localization.LanguagePackManagerScreenAction
import com.vishruth.key1.app.settings.localization.LocalizationScreen
import com.vishruth.key1.app.settings.localization.SelectLocaleScreen
import com.vishruth.key1.app.settings.localization.SubtypeEditorScreen
import com.vishruth.key1.app.settings.media.MediaScreen
import com.vishruth.key1.app.settings.smartbar.SmartbarScreen
import com.vishruth.key1.app.settings.theme.ThemeManagerScreen
import com.vishruth.key1.app.settings.theme.ThemeManagerScreenAction
import com.vishruth.key1.app.settings.theme.ThemeScreen
import com.vishruth.key1.app.settings.typing.TypingScreen
import com.vishruth.key1.app.onboarding.OnboardingScreen1
import com.vishruth.key1.app.onboarding.OnboardingScreen2
import com.vishruth.key1.app.onboarding.OnboardingScreen3
import com.vishruth.key1.app.onboarding.OnboardingScreen4
import com.vishruth.key1.app.setup.SetupScreen
import com.vishruth.key1.app.setup.EnableImeScreen
import com.vishruth.key1.app.setup.SelectImeScreen
import com.vishruth.key1.app.setup.NotificationPermissionScreen
import com.vishruth.key1.app.setup.StartCustomizationScreen
import com.vishruth.key1.ui.screens.SubscriptionScreen
import com.vishruth.key1.app.settings.aiworkspace.AIWorkspaceScreen
import com.vishruth.key1.app.settings.aiworkspace.CreateCustomAssistanceScreen
import com.vishruth.key1.app.settings.context.PersonalDetailsScreen

import com.vishruth.key1.app.settings.magicwand.MagicWandSectionSettingsScreen
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Deeplink(val path: String)

inline fun <reified T : Any> NavGraphBuilder.composableWithDeepLink(
    kClass: KClass<T>,
    noinline content: @Composable (AnimatedContentScope.(NavBackStackEntry) -> Unit),
) {
    val deeplink = requireNotNull(kClass.annotations.firstOrNull { it is Deeplink } as? Deeplink) {
        "faulty class: $kClass with annotations ${kClass.annotations}"
    }
    composable<T>(
        deepLinks = listOf(navDeepLink<T>(basePath = "ui://florisboard/${deeplink.path}")),
        content = content,
    )
}

object Routes {
    object Onboarding {
        @Serializable
        @Deeplink("onboarding/screen1")
        object Screen1
        
        @Serializable
        @Deeplink("onboarding/screen2")
        object Screen2
        
        @Serializable
        @Deeplink("onboarding/screen3")
        object Screen3
        
        @Serializable
        @Deeplink("onboarding/screen4")
        object Screen4
    }

    object Setup {
        @Serializable
        object Screen
        
        @Serializable
        @Deeplink("setup/enable-ime")
        object EnableIme
        
        @Serializable
        @Deeplink("setup/select-ime")
        object SelectIme
        
        @Serializable
        @Deeplink("setup/notification-permission")
        object NotificationPermission
        
        @Serializable
        @Deeplink("setup/start-customization")
        object StartCustomization
    }

    object Settings {
        @Serializable
        @Deeplink("settings/home")
        object Home

        @Serializable
        @Deeplink("settings/localization")
        object Localization

        @Serializable
        @Deeplink("settings/localization/select-locale")
        object SelectLocale

        @Serializable
        @Deeplink("settings/localization/language-pack-manage")
        data class LanguagePackManager(val action: LanguagePackManagerScreenAction)

        @Serializable
        @Deeplink("settings/localization/subtype/add")
        object SubtypeAdd

        @Serializable
        @Deeplink("settings/localization/subtype/edit")
        data class SubtypeEdit(val id: Long)

        @Serializable
        @Deeplink("settings/theme")
        object Theme

        @Serializable
        @Deeplink("settings/theme/manage")
        data class ThemeManager(val action: ThemeManagerScreenAction)

        @Serializable
        @Deeplink("settings/keyboard")
        object Keyboard

        @Serializable
        @Deeplink("settings/keyboard/input-feedback")
        object InputFeedback

        @Serializable
        @Deeplink("settings/smartbar")
        object Smartbar

        @Serializable
        @Deeplink("settings/typing")
        object Typing

        @Serializable
        @Deeplink("settings/dictionary")
        object Dictionary

        @Serializable
        @Deeplink("settings/dictionary/user-dictionary")
        data class UserDictionary(val type: UserDictionaryType)

        @Serializable
        @Deeplink("settings/gestures")
        object Gestures

        @Serializable
        @Deeplink("settings/clipboard")
        object Clipboard

        @Serializable
        @Deeplink("settings/media")
        object Media

        @Serializable
        @Deeplink("settings/ai-workspace")
        object AIWorkspace

        @Serializable
        @Deeplink("settings/ai-workspace/create")
        object CreateCustomAssistance

        @Serializable
        @Deeplink("settings/ai-workspace/context")
        object ContextConfiguration



        @Serializable
        @Deeplink("settings/magicwand-sections")
        object MagicWandSectionSettings

        @Serializable
        @Deeplink("settings/other")
        object Other

        @Serializable
        @Deeplink("settings/other/physical-keyboard")
        object PhysicalKeyboard

        @Serializable
        @Deeplink("settings/other/backup")
        object Backup

        @Serializable
        @Deeplink("settings/other/restore")
        object Restore

        @Serializable
        @Deeplink("settings/about")
        object About

        @Serializable
        @Deeplink("settings/about/project-license")
        object ProjectLicense

        @Serializable
        @Deeplink("settings/about/third-party-licenses")
        object ThirdPartyLicenses

        @Serializable
        @Deeplink("settings/report-feedback")
        object ReportFeedback
    }

    object Subscription {
        @Serializable
        @Deeplink("subscription")
        object Screen
    }

    object Devtools {
        @Serializable
        @Deeplink("devtools")
        object Home

        @Serializable
        @Deeplink("devtools/android/locales")
        object AndroidLocales

        @Serializable
        @Deeplink("devtools/android/settings")
        data class AndroidSettings(val name: String)

        @Serializable
        @Deeplink("export-debug-log")
        object ExportDebugLog
    }

    object Ext {
        @Serializable
        @Deeplink("ext")
        object Home

        @Serializable
        @Deeplink("ext/list")
        data class List(val type: ExtensionListScreenType, val showUpdate: Boolean? = null)

        @Serializable
        @Deeplink("ext/edit")
        data class Edit(val id: String, @SerialName("create") val serialType: String? = null)

        @Serializable
        @Deeplink("ext/export")
        data class Export(val id: String)

        @Serializable
        @Deeplink("ext/import")
        data class Import(val type: ExtensionImportScreenType, val uuid: String? = null)

        @Serializable
        @Deeplink("ext/view")
        data class View(val id: String)

        @Serializable
        @Deeplink("ext/check-updates")
        object CheckUpdates
    }

    @Composable
    fun AppNavHost(
        modifier: Modifier,
        navController: NavHostController,
        startDestination: KClass<*>,
    ) {
        NavHost(
            modifier = modifier,
            navController = navController,
            startDestination = startDestination,
            enterTransition = {
                slideIn { IntOffset(it.width, 0) } + fadeIn()
            },
            exitTransition = {
                slideOut { IntOffset(-it.width, 0) } + fadeOut()
            },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = {
                scaleOut(
                    targetScale = 0.85F,
                    transformOrigin = TransformOrigin(pivotFractionX = 0.8f, pivotFractionY = 0.5f)
                ) + fadeOut(spring(stiffness = Spring.StiffnessMedium))
            },
        ) {
            composableWithDeepLink(Onboarding.Screen1::class) { OnboardingScreen1() }
            composableWithDeepLink(Onboarding.Screen2::class) { OnboardingScreen2() }
            composableWithDeepLink(Onboarding.Screen3::class) { OnboardingScreen3() }
            composableWithDeepLink(Onboarding.Screen4::class) { OnboardingScreen4() }

            composable<Setup.Screen> { SetupScreen() }
            composableWithDeepLink(Setup.EnableIme::class) { EnableImeScreen() }
            composableWithDeepLink(Setup.SelectIme::class) { SelectImeScreen() }
            composableWithDeepLink(Setup.NotificationPermission::class) { NotificationPermissionScreen() }
            composableWithDeepLink(Setup.StartCustomization::class) { StartCustomizationScreen() }

            composableWithDeepLink(Settings.Home::class) { HomeScreen() }

            composableWithDeepLink(Settings.Localization::class) { LocalizationScreen() }
            composableWithDeepLink(Settings.SelectLocale::class) { SelectLocaleScreen() }
            composableWithDeepLink(Settings.LanguagePackManager::class) { navBackStack ->
                val payload = navBackStack.toRoute<Settings.LanguagePackManager>()
                LanguagePackManagerScreen(payload.action)
            }
            composableWithDeepLink(Settings.SubtypeAdd::class) { SubtypeEditorScreen(null) }
            composableWithDeepLink(Settings.SubtypeEdit::class) { navBackStack ->
                val payload = navBackStack.toRoute<Settings.SubtypeEdit>()
                SubtypeEditorScreen(payload.id)
            }

            composableWithDeepLink(Settings.Theme::class) { ThemeScreen() }
            composableWithDeepLink(Settings.ThemeManager::class) { navBackStack ->
                val payload = navBackStack.toRoute<Settings.ThemeManager>()
                ThemeManagerScreen(payload.action)
            }

            composableWithDeepLink(Settings.Keyboard::class) { KeyboardScreen() }
            composableWithDeepLink(Settings.InputFeedback::class) { InputFeedbackScreen() }

            composableWithDeepLink(Settings.Smartbar::class) { SmartbarScreen() }

            composableWithDeepLink(Settings.Typing::class) { TypingScreen() }

            composableWithDeepLink(Settings.Dictionary::class) { DictionaryScreen() }
            composableWithDeepLink(Settings.UserDictionary::class) { navBackStack ->
                val payload = navBackStack.toRoute<Settings.UserDictionary>()
                UserDictionaryScreen(payload.type)
            }

            composableWithDeepLink(Settings.Gestures::class) { GesturesScreen() }

            composableWithDeepLink(Settings.Clipboard::class) { ClipboardScreen() }

            composableWithDeepLink(Settings.Media::class) { MediaScreen() }

            composableWithDeepLink(Settings.AIWorkspace::class) { 
                AIWorkspaceScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCreateCustom = { navController.navigate(Settings.CreateCustomAssistance) },
                    onNavigateToContext = { navController.navigate(Settings.ContextConfiguration) },
                    onNavigateToMagicWandSettings = { navController.navigate(Settings.MagicWandSectionSettings) }
                )
            }

            composableWithDeepLink(Settings.CreateCustomAssistance::class) {
                CreateCustomAssistanceScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onActionCreated = { navController.popBackStack() }
                )
            }

            composableWithDeepLink(Settings.ContextConfiguration::class) {
                PersonalDetailsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composableWithDeepLink(Settings.MagicWandSectionSettings::class) {
                MagicWandSectionSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composableWithDeepLink(Settings.Other::class) { OtherScreen() }
            composableWithDeepLink(Settings.PhysicalKeyboard::class) { PhysicalKeyboardScreen() }
            composableWithDeepLink(Settings.Backup::class) { BackupScreen() }
            composableWithDeepLink(Settings.Restore::class) { RestoreScreen() }

            composableWithDeepLink(Settings.About::class) { AboutScreen() }
            composableWithDeepLink(Settings.ProjectLicense::class) { ProjectLicenseScreen() }
            composableWithDeepLink(Settings.ThirdPartyLicenses::class) { ThirdPartyLicensesScreen() }
            composableWithDeepLink(Settings.ReportFeedback::class) { ReportFeedbackScreen() }

            composableWithDeepLink(Devtools.Home::class) { DevtoolsScreen() }
            composableWithDeepLink(Devtools.AndroidLocales::class) { AndroidLocalesScreen() }
            composableWithDeepLink(Devtools.AndroidSettings::class) { navBackStack ->
                val payload = navBackStack.toRoute<Devtools.AndroidSettings>()
                AndroidSettingsScreen(payload.name)
            }
            composableWithDeepLink(Devtools.ExportDebugLog::class) { ExportDebugLogScreen() }

            composableWithDeepLink(Ext.Home::class) { ExtensionHomeScreen() }
            composableWithDeepLink(Ext.List::class) { navBackStack ->
                val payload = navBackStack.toRoute<Ext.List>()
                val showUpdate = payload.showUpdate != null && payload.showUpdate
                ExtensionListScreen(payload.type, showUpdate)
            }
            composableWithDeepLink(Ext.Edit::class) { navBackStack ->
                val payload = navBackStack.toRoute<Ext.Edit>()
                val extensionId = payload.id
                val serialType = payload.serialType
                ExtensionEditScreen(
                    id = extensionId,
                    createSerialType = serialType.takeIf { !it.isNullOrBlank() },
                )
            }
            composableWithDeepLink(Ext.Export::class) { navBackStack ->
                val payload = navBackStack.toRoute<Ext.Export>()
                val extensionId = payload.id
                ExtensionExportScreen(id = extensionId)
            }
            composableWithDeepLink(Ext.Import::class) { navBackStack ->
                val payload = navBackStack.toRoute<Ext.Import>()
                val uuid = payload.uuid
                ExtensionImportScreen(payload.type, uuid)
            }
            composableWithDeepLink(Ext.View::class) { navBackStack ->
                val payload = navBackStack.toRoute<Ext.View>()
                val extensionId = payload.id
                ExtensionViewScreen(id = extensionId)
            }
            composableWithDeepLink(Ext.CheckUpdates::class) {
                CheckUpdatesScreen()
            }
            
            composableWithDeepLink(Subscription.Screen::class) { 
                SubscriptionScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
