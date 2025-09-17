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

package com.vishruth.key1.lib.ads

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Singleton object responsible for initializing the Google Mobile Ads SDK.
 * Ensures that initialization occurs only once during the application lifecycle.
 */
object AdManager {
    private const val TAG = "AdManager"
    private val isInitialized = AtomicBoolean(false)
    private val initializationLatch = CountDownLatch(1)
    private var initializationStarted = false

    /**
     * Initializes the Google Mobile Ads SDK asynchronously on a background thread.
     * This method should be called once during application startup.
     *
     * @param context The application context
     */
    fun initialize(context: Context) {
        // Use compareAndSet to ensure only one initialization attempt
        if (initializationStarted) {
            Log.d(TAG, "AdMob SDK initialization already started")
            return
        }
        
        initializationStarted = true
        
        if (!isInitialized.compareAndSet(false, true)) {
            Log.d(TAG, "AdMob SDK initialization already in progress or completed")
            initializationLatch.countDown() // Ensure latch is counted down
            return
        }

        Log.d(TAG, "Starting AdMob SDK initialization")
        Log.d(TAG, "Application ID from manifest: ${getAppIdFromManifest(context)}")
        
        // Initialize the Google Mobile Ads SDK on a background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Initializing AdMob SDK on background thread")
                Log.d(TAG, "Context package name: ${context.packageName}")
                Log.d(TAG, "Context class: ${context.javaClass.name}")
                
                MobileAds.initialize(context) { initializationStatus ->
                    Log.d(TAG, "AdMob SDK initialization complete")
                    // Log the initialization status for debugging
                    var allAdaptersSuccessful = true
                    for ((adapter, status) in initializationStatus.adapterStatusMap) {
                        Log.d(TAG, "Adapter: $adapter, Status: ${status.initializationState}, Description: ${status.description}")
                        if (status.initializationState != com.google.android.gms.ads.initialization.AdapterStatus.State.READY) {
                            allAdaptersSuccessful = false
                        }
                    }
                    
                    if (allAdaptersSuccessful) {
                        Log.d(TAG, "All adapters initialized successfully")
                    } else {
                        Log.e(TAG, "Some adapters failed to initialize")
                    }
                    
                    // Count down the latch to signal completion
                    initializationLatch.countDown()
                }
                
                // Update ad targeting info asynchronously without blocking
                updateAdTargetingInfoAsync(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing AdMob SDK", e)
                // Count down the latch even in case of error to prevent blocking
                initializationLatch.countDown()
            }
        }
    }
    
    private fun getAppIdFromManifest(context: Context): String {
        return try {
            context.getString(context.resources.getIdentifier("com.google.android.gms.ads.APPLICATION_ID", "string", context.packageName))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Application ID from manifest", e)
            "Not found"
        }
    }
    
    /**
     * Updates ad targeting information based on user's advertising ID asynchronously.
     *
     * @param context The application context
     */
    private fun updateAdTargetingInfoAsync(context: Context) {
        // Run this asynchronously on IO thread without blocking the initialization
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Updating ad targeting info")
                // Configure ad request with test device IDs
                val requestConfiguration = RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf("33BE2250B43518CCDA7DE426D04EE231")) // Sample test device ID
                    .build()
                
                MobileAds.setRequestConfiguration(requestConfiguration)
                Log.d(TAG, "Ad targeting info updated")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating ad targeting info", e)
            }
        }
    }

    /**
     * Checks if the AdMob SDK has been initialized.
     *
     * @return true if initialized, false otherwise
     */
    fun isInitialized(): Boolean = isInitialized.get()

    /**
     * Waits for the AdMob SDK to be initialized with a timeout.
     *
     * @param timeoutMillis The maximum time to wait in milliseconds
     * @return true if initialized within the timeout, false otherwise
     */
    fun waitForInitialization(timeoutMillis: Long = 5000): Boolean {
        if (isInitialized.get()) {
            Log.d(TAG, "AdMob SDK already initialized, returning true immediately")
            return true
        }
        
        Log.d(TAG, "Waiting for AdMob SDK initialization with timeout: ${timeoutMillis}ms")
        try {
            val result = initializationLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)
            Log.d(TAG, "AdMob SDK initialization wait completed. Result: $result, Initialized: ${isInitialized.get()}")
            return result && isInitialized.get()
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while waiting for AdMob SDK initialization", e)
            return isInitialized.get()
        }
    }
    
    /**
     * Ensures that the AdMob SDK is initialized before proceeding.
     * This method can be used to check initialization status and initialize if needed.
     *
     * @param context The application context
     */
    fun ensureInitialized(context: Context) {
        // Only attempt initialization if not already started
        if (!initializationStarted) {
            initialize(context)
        }
    }
}