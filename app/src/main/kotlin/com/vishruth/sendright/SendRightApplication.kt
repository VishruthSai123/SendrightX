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

package com.vishruth.key1

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.util.Log
import androidx.core.os.UserManagerCompat
import com.google.firebase.FirebaseApp
import com.vishruth.key1.app.FlorisPreferenceModel
import com.vishruth.key1.app.FlorisPreferenceStore
import com.vishruth.key1.ime.ai.AiUsageTracker
import com.vishruth.key1.ime.clipboard.ClipboardManager
import com.vishruth.key1.ime.editor.EditorInstance
import com.vishruth.key1.ime.keyboard.KeyboardManager
import com.vishruth.key1.ime.media.emoji.FlorisEmojiCompat
import com.vishruth.key1.ime.nlp.NlpManager
import com.vishruth.key1.ime.text.gestures.GlideTypingManager
import com.vishruth.key1.ime.theme.ThemeManager
import com.vishruth.key1.lib.ads.AdManager
import com.vishruth.key1.lib.cache.CacheManager
import com.vishruth.key1.lib.crashutility.CrashUtility
import com.vishruth.key1.lib.devtools.Flog
import com.vishruth.key1.lib.devtools.LogTopic
import com.vishruth.key1.lib.devtools.flogError
import com.vishruth.key1.lib.ext.ExtensionManager
import com.vishruth.key1.user.UserManager
import com.vishruth.key1.ime.dictionary.DictionaryManager
import com.vishruth.key1.ime.core.SubtypeManager

import dev.patrickgold.jetpref.datastore.runtime.initAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.florisboard.lib.kotlin.io.deleteContentsRecursively
import org.florisboard.lib.kotlin.tryOrNull
//import org.florisboard.libnative.dummyAdd
import java.lang.ref.WeakReference

/**
 * Global weak reference for the [SendRightApplication] class. This is needed as in certain scenarios an application
 * context is not available.
 */
private var SendRightApplicationReference = WeakReference<SendRightApplication?>(null)

@Suppress("unused")
class SendRightApplication : Application() {
    companion object {
        init {
            // Native library loading disabled for builds without native module
            // try {
            //     System.loadLibrary("fl_native")
            // } catch (_: UnsatisfiedLinkError) {
            //     // Native library not available - this is expected when building without native module
            // } catch (_: Exception) {
            //     // Other errors loading the library
            // }
        }
    }

    private val mainHandler by lazy { Handler(mainLooper) }
    private val scope = CoroutineScope(Dispatchers.Default)
    val preferenceStoreLoaded = MutableStateFlow(false)

    val cacheManager = lazy { CacheManager(this) }
    val clipboardManager = lazy { ClipboardManager(this) }
    val editorInstance = lazy { EditorInstance(this) }
    val extensionManager = lazy { ExtensionManager(this) }
    val glideTypingManager = lazy { GlideTypingManager(this) }
    val keyboardManager = lazy { KeyboardManager(this) }
    val nlpManager = lazy { NlpManager(this) }
    val subtypeManager: Lazy<SubtypeManager> = lazy { SubtypeManager(this) }
    val themeManager = lazy { ThemeManager(this) }

    override fun onCreate() {
        super.onCreate()
        SendRightApplicationReference = WeakReference(this)
        try {
            Flog.install(
                context = this,
                isFloggingEnabled = BuildConfig.DEBUG,
                flogTopics = LogTopic.ALL,
                flogLevels = Flog.LEVEL_ALL,
                flogOutputs = Flog.OUTPUT_CONSOLE,
            )
            CrashUtility.install(this)
            FlorisEmojiCompat.init(this)
            //flogError { "dummy result: ${dummyAdd(3,4)}" }

            // Initialize Firebase
            FirebaseApp.initializeApp(this)
            Log.d("SendRightApplication", "Firebase initialized successfully")

            // Initialize AdMob SDK asynchronously without blocking
            Log.d("SendRightApplication", "Initializing AdMob SDK asynchronously")
            Log.d("SendRightApplication", "Application package name: ${this.packageName}")
            AdManager.initialize(this)
            
            // Don't wait for AdMob SDK initialization - let it happen in the background
            // The ad loading code will handle waiting when needed
            
            // Initialize AI Usage Tracker asynchronously - delay to reduce memory pressure
            scope.launch {
                delay(2000) // Wait 2 seconds to reduce initial memory pressure
                AiUsageTracker.getInstance().initialize(this@SendRightApplication)
                // Load initial usage stats after initialization
                AiUsageTracker.getInstance().loadInitialUsageStats()
            }

            // Initialize UserManager with more delay to prevent memory pressure
            scope.launch {
                delay(3000) // Wait 3 seconds before initializing UserManager
                UserManager.getInstance().initialize(this@SendRightApplication)
            }

            if (!UserManagerCompat.isUserUnlocked(this)) {
                cacheDir?.deleteContentsRecursively()
                extensionManager.value.init()
                registerReceiver(BootComplete(), IntentFilter(Intent.ACTION_USER_UNLOCKED))
                return
            }

            init()
        } catch (e: Exception) {
            Log.e("SendRightApplication", "Error in onCreate", e)
            CrashUtility.stageException(e)
            return
        }
    }

    fun init() {
        cacheDir?.deleteContentsRecursively()
        scope.launch {
            val result = FlorisPreferenceStore.initAndroid(
                context = this@SendRightApplication,
                datastoreName = FlorisPreferenceModel.NAME,
            )
            Log.i("PREFS", result.toString())
            preferenceStoreLoaded.value = true
        }
        extensionManager.value.init()
        clipboardManager.value.initializeForContext(this)
        DictionaryManager.init(this)
    }

    private inner class BootComplete : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (intent.action == Intent.ACTION_USER_UNLOCKED) {
                try {
                    unregisterReceiver(this)
                } catch (e: Exception) {
                    flogError { e.toString() }
                }
                mainHandler.post { init() }
            }
        }
    }
}

private tailrec fun Context.sendRightApplication(): SendRightApplication {
    return when (this) {
        is SendRightApplication -> this
        is ContextWrapper -> when {
            this.baseContext != null -> this.baseContext.sendRightApplication()
            else -> SendRightApplicationReference.get()!!
        }
        else -> tryOrNull { this.applicationContext as SendRightApplication } ?: SendRightApplicationReference.get()!!
    }
}

fun Context.appContext() = lazyOf(this.sendRightApplication())

fun Context.cacheManager() = this.sendRightApplication().cacheManager

fun Context.clipboardManager() = this.sendRightApplication().clipboardManager

fun Context.editorInstance() = this.sendRightApplication().editorInstance

fun Context.extensionManager() = this.sendRightApplication().extensionManager

fun Context.glideTypingManager() = this.sendRightApplication().glideTypingManager

fun Context.keyboardManager() = this.sendRightApplication().keyboardManager

fun Context.nlpManager() = this.sendRightApplication().nlpManager

fun Context.subtypeManager() = this.sendRightApplication().subtypeManager

fun Context.themeManager() = this.sendRightApplication().themeManager